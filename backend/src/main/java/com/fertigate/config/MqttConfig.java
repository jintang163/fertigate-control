package com.fertigate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.lifecycle.Mqtt5ClientDisconnectedContext;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;



@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.topics.sensor-data}")
    private String sensorDataTopic;

    @Value("${mqtt.topics.device-status}")
    private String deviceStatusTopic;

    @Value("${mqtt.topics.alert}")
    private String alertTopic;

    @Value("${mqtt.topics.gateway-heartbeat}")
    private String gatewayHeartbeatTopic;

    @Value("${mqtt.reconnect.max-attempts:10}")
    private int maxReconnectAttempts;

    @Value("${mqtt.reconnect.base-delay:2}")
    private int reconnectBaseDelay;

    @Value("${mqtt.reconnect.max-delay:30}")
    private int reconnectMaxDelay;

    private Mqtt5AsyncClient client;
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean wasConnected = new AtomicBoolean(false);

    @Bean
    public Mqtt5AsyncClient mqttClient() {
        String host = broker.replace("tcp://", "").split(":")[0];
        int port = Integer.parseInt(broker.split(":")[2]);
        
        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port)
                .addDisconnectedListener(this::onDisconnected)
                .buildAsync();
        return client;
    }

    private void onDisconnected(Mqtt5ClientDisconnectedContext context) {
        log.warn("MQTT client disconnected: {}", context.getCause().getMessage());
        if (!isReconnecting.get()) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (reconnectAttempts.get() >= maxReconnectAttempts) {
            log.error("Max reconnection attempts ({}) reached", maxReconnectAttempts);
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();
        long delay = Math.min(reconnectBaseDelay * (1L << (attempt - 1)), reconnectMaxDelay);
        
        log.info("Scheduling MQTT reconnection attempt {} in {} seconds", attempt, delay);
        isReconnecting.set(true);
        
        reconnectScheduler.schedule(() -> {
            try {
                log.info("Attempting MQTT reconnection (attempt {}/{})", attempt, maxReconnectAttempts);
                Mqtt5ConnAck connAck = client.connectWith()
                        .simpleAuth()
                        .username(username)
                        .password(password.getBytes())
                        .applySimpleAuth()
                        .keepAlive(60)
                        .cleanStart(true)
                        .send()
                        .join();

                if (connAck.getReasonCode().isSuccess()) {
                    log.info("MQTT reconnected successfully");
                    isReconnecting.set(false);
                    reconnectAttempts.set(0);
                    wasConnected.set(true);
                    subscribeToTopics();
                } else {
                    log.error("MQTT reconnection failed: {}", connAck.getReasonCode());
                    scheduleReconnect();
                }
            } catch (Exception e) {
                log.error("Error during MQTT reconnection: {}", e.getMessage());
                scheduleReconnect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    @PostConstruct
    public void connect() {
        try {
            Mqtt5ConnAck connAck = client.connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes())
                    .applySimpleAuth()
                    .keepAlive(60)
                    .cleanStart(true)
                    .send()
                    .join();

            if (connAck.getReasonCode().isSuccess()) {
                log.info("Connected to MQTT broker successfully");
                wasConnected.set(true);
                subscribeToTopics();
            } else {
                log.error("Failed to connect to MQTT broker: {}", connAck.getReasonCode());
                scheduleReconnect();
            }
        } catch (Exception e) {
            log.error("Error connecting to MQTT broker: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void subscribeToTopics() {
        subscribe(sensorDataTopic);
        subscribe(deviceStatusTopic);
        subscribe(alertTopic);
        subscribe(gatewayHeartbeatTopic);
    }

    private void subscribe(String topic) {
        client.subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {
                    String payload = new String(publish.getPayloadAsBytes());
                    log.debug("Received message on topic {}: {}", topic, payload);
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to subscribe to topic {}: {}", topic, throwable.getMessage());
                    } else {
                        log.info("Subscribed to topic: {}", topic);
                    }
                });
    }

    public void publish(String topic, Object message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String payload = mapper.writeValueAsString(message);

            client.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes())
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .whenComplete((publishResult, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to publish to topic {}: {}", topic, throwable.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing to topic {}: {}", topic, e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        reconnectScheduler.shutdown();
        if (client != null && client.getState().isConnected()) {
            client.disconnect()
                    .whenComplete((v, t) -> log.info("MQTT client disconnected"));
        }
    }

    public boolean isConnected() {
        return client != null && client.getState().isConnected();
    }

    public boolean wasConnected() {
        return wasConnected.get();
    }

    public Mqtt5AsyncClient getClient() {
        return client;
    }
}
