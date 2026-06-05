package com.fertigate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



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

    private Mqtt5AsyncClient client;

    @Bean
    public Mqtt5AsyncClient mqttClient() {
        String host = broker.replace("tcp://", "").split(":")[0];
        int port = Integer.parseInt(broker.split(":")[2]);
        
        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port)
                .buildAsync();
        return client;
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
                subscribeToTopics();
            } else {
                log.error("Failed to connect to MQTT broker: {}", connAck.getReasonCode());
            }
        } catch (Exception e) {
            log.error("Error connecting to MQTT broker: {}", e.getMessage());
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
        if (client != null && client.getState().isConnected()) {
            client.disconnect()
                    .whenComplete((v, t) -> log.info("MQTT client disconnected"));
        }
    }

    public boolean isConnected() {
        return client != null && client.getState().isConnected();
    }

    public Mqtt5AsyncClient getClient() {
        return client;
    }
}
