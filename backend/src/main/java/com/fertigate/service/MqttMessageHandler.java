package com.fertigate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fertigate.config.MqttConfig;
import com.fertigate.dto.SensorDataDTO;
import com.fertigate.entity.Alert;
import com.fertigate.entity.Device;
import com.fertigate.repository.AlertRepository;
import com.fertigate.repository.DeviceRepository;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final Mqtt5AsyncClient mqttClient;
    private final MqttConfig mqttConfig;
    private final InfluxDBService influxDBService;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final IrrigationControlService irrigationControlService;

    @Value("${mqtt.topics.sensor-data}")
    private String sensorDataTopic;

    @Value("${mqtt.topics.device-status}")
    private String deviceStatusTopic;

    @Value("${mqtt.topics.alert}")
    private String alertTopic;

    @Value("${mqtt.topics.gateway-heartbeat}")
    private String gatewayHeartbeatTopic;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @PostConstruct
    public void init() {
        subscribeToTopic(sensorDataTopic, this::handleSensorData);
        subscribeToTopic(deviceStatusTopic, this::handleDeviceStatus);
        subscribeToTopic(alertTopic, this::handleAlert);
        subscribeToTopic(gatewayHeartbeatTopic, this::handleGatewayHeartbeat);
    }

    private void subscribeToTopic(String topic, java.util.function.Consumer<Mqtt5Publish> handler) {
        mqttClient.subscribeWith()
                .topicFilter(topic)
                .callback(handler)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to subscribe to topic {}: {}", topic, throwable.getMessage());
                    } else {
                        log.info("Subscribed to MQTT topic: {}", topic);
                    }
                });
    }

    private void handleSensorData(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.debug("Received sensor data: {}", payload);

            SensorDataDTO sensorData = objectMapper.readValue(payload, SensorDataDTO.class);
            
            if (sensorData.getTimestamp() == null) {
                sensorData.setTimestamp(LocalDateTime.now());
            }

            influxDBService.writeSensorData(sensorData);

            updateDeviceStatus(sensorData.getDeviceCode());

            irrigationControlService.updateLatestSensorData(
                    sensorData.getDeviceCode(), 
                    sensorData.getValues()
            );

        } catch (Exception e) {
            log.error("Error handling sensor data: {}", e.getMessage(), e);
        }
    }

    private void handleDeviceStatus(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.info("Received device status: {}", payload);

            Map<String, Object> statusData = objectMapper.readValue(payload, Map.class);
            String deviceCode = (String) statusData.get("device_code");
            Boolean isOpen = (Boolean) statusData.get("is_open");

            if (deviceCode != null) {
                deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
                    device.setStatus("online");
                    device.setLastHeartbeat(LocalDateTime.now());
                    deviceRepository.save(device);
                });
            }

        } catch (Exception e) {
            log.error("Error handling device status: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAlert(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.warn("Received alert: {}", payload);

            Map<String, Object> alertData = objectMapper.readValue(payload, Map.class);
            
            Alert alert = new Alert();
            alert.setAlertType((String) alertData.get("sensor_type"));
            alert.setLevel((String) alertData.getOrDefault("level", "warning"));
            alert.setMessage((String) alertData.get("message"));
            
            Object sensorValue = alertData.get("value");
            if (sensorValue instanceof Number) {
                alert.setSensorValue(BigDecimal.valueOf(((Number) sensorValue).doubleValue()));
            }
            
            Object thresholdValue = alertData.get("threshold");
            if (thresholdValue instanceof Number) {
                alert.setThresholdValue(BigDecimal.valueOf(((Number) thresholdValue).doubleValue()));
            }
            
            String deviceCode = (String) alertData.get("device_code");
            if (deviceCode != null) {
                Optional<Device> deviceOpt = deviceRepository.findByDeviceCode(deviceCode);
                deviceOpt.ifPresent(alert::setDevice);
            }
            
            alert.setCreatedAt(LocalDateTime.now());
            alertRepository.save(alert);
            
            log.info("Alert saved: {}", alert.getMessage());

            String action = (String) alertData.get("action");
            if ("stop_irrigation".equals(action)) {
                String zone = (String) alertData.get("zone");
                log.info("Triggering irrigation stop for zone: {}", zone);
            }

        } catch (Exception e) {
            log.error("Error handling alert: {}", e.getMessage(), e);
        }
    }

    private void handleGatewayHeartbeat(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.debug("Received gateway heartbeat: {}", payload);

            Map<String, Object> heartbeatData = objectMapper.readValue(payload, Map.class);
            String gatewayId = (String) heartbeatData.get("gateway_id");
            
            if (gatewayId != null) {
                deviceRepository.findByDeviceCode(gatewayId).ifPresentOrElse(
                        device -> {
                            device.setStatus("online");
                            device.setLastHeartbeat(LocalDateTime.now());
                            deviceRepository.save(device);
                        },
                        () -> log.debug("Gateway device not found: {}", gatewayId)
                );
            }

        } catch (Exception e) {
            log.error("Error handling gateway heartbeat: {}", e.getMessage(), e);
        }
    }

    private void updateDeviceStatus(String deviceCode) {
        deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
            if (!"online".equals(device.getStatus())) {
                device.setStatus("online");
            }
            device.setLastHeartbeat(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null) {
            return LocalDateTime.now();
        }
        try {
            if (timestampStr.endsWith("Z")) {
                timestampStr = timestampStr.replace("Z", "");
            }
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestampStr);
            return LocalDateTime.now();
        }
    }
}
