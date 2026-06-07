package com.fertigate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AlertWebSocketService alertWebSocketService;

    @Value("${mqtt.topics.sensor-data}")
    private String sensorDataTopic;

    @Value("${mqtt.topics.device-status}")
    private String deviceStatusTopic;

    @Value("${mqtt.topics.alert}")
    private String alertTopic;

    @Value("${mqtt.topics.gateway-heartbeat}")
    private String gatewayHeartbeatTopic;

    @Value("${mqtt.topics.interlock-alert}")
    private String interlockAlertTopic;

    @Value("${mqtt.deduplication.enabled:true}")
    private boolean deduplicationEnabled;

    private final DeviceMonitoringService deviceMonitoringService;
    private final SafetyInterlockService safetyInterlockService;

    @Value("${mqtt.deduplication.max-size:10000}")
    private int deduplicationMaxSize;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Map<String, Long> messageIdCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > deduplicationMaxSize;
                }
            });

    private final Map<String, AtomicLong> topicCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> topicErrors = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        mqttConfig.addConnectCallback(this::onMqttConnected);
        subscribeAllTopics();
    }

    private void onMqttConnected(boolean isReconnect) {
        if (isReconnect) {
            log.info("MQTT reconnected, re-subscribing to all topics...");
        }
        subscribeAllTopics();
    }

    private void subscribeAllTopics() {
        if (!mqttConfig.isConnected()) {
            log.warn("MQTT not connected, will subscribe when connection is established");
            return;
        }
        subscribeToTopic(sensorDataTopic, this::handleSensorData);
        subscribeToTopic(deviceStatusTopic, this::handleDeviceStatus);
        subscribeToTopic(alertTopic, this::handleAlert);
        subscribeToTopic(gatewayHeartbeatTopic, this::handleGatewayHeartbeat);
        subscribeToTopic(interlockAlertTopic, this::handleInterlockAlert);
        log.info("All MQTT topics subscribed successfully");
    }

    private void subscribeToTopic(String topic, java.util.function.Consumer<Mqtt5Publish> handler) {
        if (!mqttConfig.isConnected()) {
            log.warn("MQTT not connected, cannot subscribe to topic: {}", topic);
            return;
        }
        mqttClient.subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {
                    try {
                        incrementCounter(topic);
                        handler.accept(publish);
                    } catch (Exception e) {
                        incrementError(topic);
                        log.error("Error handling message on topic {}: {}", topic, e.getMessage(), e);
                    }
                })
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

            if (deduplicationEnabled) {
                String messageId = generateMessageId(publish, payload);
                if (isDuplicate(messageId)) {
                    log.debug("Duplicate message detected, skipping: {}", messageId);
                    return;
                }
                cacheMessageId(messageId);
            }

            JsonNode rootNode = objectMapper.readTree(payload);
            
            if (rootNode.has("data") && rootNode.has("batchSize")) {
                handleSensorDataBatch(rootNode);
            } else {
                handleSingleSensorData(rootNode, payload);
            }

        } catch (Exception e) {
            log.error("Error handling sensor data: {}", e.getMessage(), e);
        }
    }

    private void handleSingleSensorData(JsonNode rootNode, String payload) {
        try {
            SensorDataDTO sensorData = parseSensorData(rootNode);
            
            if (sensorData.getTimestamp() == null) {
                sensorData.setTimestamp(LocalDateTime.now());
            }

            if (sensorData.getDeviceCode() == null || sensorData.getDeviceCode().isEmpty()) {
                log.warn("Sensor data missing device_code, skipping");
                return;
            }

            boolean success = influxDBService.writeSensorDataWithRetry(sensorData, 3);
            
            if (success) {
                updateDeviceStatus(sensorData.getDeviceCode());

                if (irrigationControlService != null && sensorData.getValues() != null) {
                    irrigationControlService.updateLatestSensorData(
                            sensorData.getDeviceCode(),
                            sensorData.getValues()
                    );
                }

                if (Boolean.TRUE.equals(sensorData.getIsRetransmission())) {
                    log.info("Retransmitted data persisted: device={}, originalTimestamp={}",
                            sensorData.getDeviceCode(), sensorData.getOriginalTimestamp());
                }
            } else {
                log.error("Failed to persist sensor data after retries: device={}",
                        sensorData.getDeviceCode());
            }

        } catch (Exception e) {
            log.error("Error handling single sensor data: {}", e.getMessage(), e);
        }
    }

    private void handleSensorDataBatch(JsonNode rootNode) {
        try {
            log.info("Received sensor data batch");
            
            List<SensorDataDTO> dataList = objectMapper.readValue(
                    rootNode.get("data").traverse(),
                    new TypeReference<List<SensorDataDTO>>() {}
            );

            if (dataList == null || dataList.isEmpty()) {
                log.warn("Empty batch received");
                return;
            }

            boolean isBackfill = rootNode.has("isBackfill") && rootNode.get("isBackfill").asBoolean(false);
            String gatewayId = rootNode.has("gatewayId") ? rootNode.get("gatewayId").asText() : null;

            log.info("Processing batch of {} sensor data entries (backfill={})", dataList.size(), isBackfill);

            for (SensorDataDTO sensorData : dataList) {
                try {
                    if (sensorData.getDeviceCode() == null) {
                        continue;
                    }

                    if (sensorData.getTimestamp() == null) {
                        sensorData.setTimestamp(LocalDateTime.now());
                    }

                    if (gatewayId != null && sensorData.getGatewayId() == null) {
                        sensorData.setGatewayId(gatewayId);
                    }

                    influxDBService.writeSensorDataWithRetry(sensorData, 2);
                    updateDeviceStatus(sensorData.getDeviceCode());

                } catch (Exception e) {
                    log.error("Error processing batch entry: {}", e.getMessage());
                }
            }

            log.info("Batch processing complete: {} entries", dataList.size());

        } catch (Exception e) {
            log.error("Error handling sensor data batch: {}", e.getMessage(), e);
        }
    }

    private SensorDataDTO parseSensorData(JsonNode rootNode) {
        SensorDataDTO sensorData = new SensorDataDTO();

        sensorData.setDeviceCode(getTextValue(rootNode, "device_code", "deviceCode"));
        sensorData.setDeviceName(getTextValue(rootNode, "device_name", "deviceName"));
        sensorData.setDeviceType(getTextValue(rootNode, "device_type", "deviceType"));
        sensorData.setZone(getTextValue(rootNode, "zone"));
        sensorData.setGatewayId(getTextValue(rootNode, "gateway_id", "gatewayId"));
        sensorData.setInterlockSafe(getBooleanValue(rootNode, "interlock_safe", "interlockSafe"));
        sensorData.setIsRetransmission(getBooleanValue(rootNode, "is_retransmission", "isRetransmission"));
        sensorData.setMessageId(getTextValue(rootNode, "message_id", "messageId"));

        String timestampStr = getTextValue(rootNode, "timestamp");
        if (timestampStr != null) {
            sensorData.setTimestamp(parseTimestamp(timestampStr));
        }

        String originalTimestampStr = getTextValue(rootNode, "original_timestamp", "originalTimestamp");
        if (originalTimestampStr != null) {
            sensorData.setOriginalTimestamp(parseTimestamp(originalTimestampStr));
        }

        if (rootNode.has("values")) {
            try {
                Map<String, Double> values = objectMapper.readValue(
                        rootNode.get("values").traverse(),
                        new TypeReference<Map<String, Double>>() {}
                );
                sensorData.setValues(values);
            } catch (Exception e) {
                log.warn("Failed to parse values field: {}", e.getMessage());
            }
        }

        return sensorData;
    }

    private String getTextValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private Boolean getBooleanValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asBoolean();
            }
        }
        return null;
    }

    private void handleDeviceStatus(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.info("Received device status: {}", payload);

            JsonNode rootNode = objectMapper.readTree(payload);
            String deviceCode = getTextValue(rootNode, "device_code", "deviceCode");
            Boolean isOpen = getBooleanValue(rootNode, "is_open", "isOpen");

            if (deviceCode != null) {
                deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
                    device.setStatus("online");
                    device.setLastHeartbeat(LocalDateTime.now());
                    if (isOpen != null) {
                        device.setStatus(isOpen ? "open" : "closed");
                    }
                    deviceRepository.save(device);
                    log.debug("Device status updated: {}", deviceCode);
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

            JsonNode rootNode = objectMapper.readTree(payload);
            
            Alert alert = new Alert();
            alert.setAlertType(getTextValue(rootNode, "sensor_type", "alertType", "type"));
            alert.setLevel(getTextValue(rootNode, "level"));
            if (alert.getLevel() == null) {
                alert.setLevel("warning");
            }
            alert.setMessage(getTextValue(rootNode, "message"));
            
            String valueStr = getTextValue(rootNode, "value", "sensorValue", "sensor_value");
            if (valueStr != null) {
                try {
                    alert.setSensorValue(new BigDecimal(valueStr));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse sensor value: {}", valueStr);
                }
            }
            
            String thresholdStr = getTextValue(rootNode, "threshold", "thresholdValue", "threshold_value");
            if (thresholdStr != null) {
                try {
                    alert.setThresholdValue(new BigDecimal(thresholdStr));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse threshold value: {}", thresholdStr);
                }
            }
            
            String deviceCode = getTextValue(rootNode, "device_code", "deviceCode");
            if (deviceCode != null) {
                Optional<Device> deviceOpt = deviceRepository.findByDeviceCode(deviceCode);
                deviceOpt.ifPresent(alert::setDevice);
            }
            
            alert.setCreatedAt(LocalDateTime.now());
            Alert savedAlert = alertRepository.save(alert);
            
            log.info("Alert saved: {}", savedAlert.getMessage());

            alertWebSocketService.sendAlertToAll(savedAlert);

            String action = getTextValue(rootNode, "action");
            if ("stop_irrigation".equals(action)) {
                String zone = getTextValue(rootNode, "zone");
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

            JsonNode rootNode = objectMapper.readTree(payload);
            String gatewayId = getTextValue(rootNode, "gateway_id", "gatewayId");
            
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
        if (deviceCode == null) {
            return;
        }
        deviceRepository.findByDeviceCode(deviceCode).ifPresent(device -> {
            if (!"online".equals(device.getStatus())) {
                device.setStatus("online");
            }
            device.setLastHeartbeat(LocalDateTime.now());
            deviceRepository.save(device);
        });
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            if (timestampStr.endsWith("Z")) {
                timestampStr = timestampStr.replace("Z", "");
            }
            if (timestampStr.contains("+")) {
                timestampStr = timestampStr.split("\\+")[0];
            }
            if (timestampStr.length() > 19 && timestampStr.charAt(10) == 'T') {
                timestampStr = timestampStr.substring(0, 19);
            }
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                log.warn("Failed to parse timestamp: {}, using current time", timestampStr);
                return LocalDateTime.now();
            }
        }
    }

    private String generateMessageId(Mqtt5Publish publish, String payload) {
        StringBuilder sb = new StringBuilder();
        sb.append(publish.getTopic()).append("|");
        sb.append(payload.hashCode());
        return sb.toString();
    }

    private boolean isDuplicate(String messageId) {
        return messageIdCache.containsKey(messageId);
    }

    private void cacheMessageId(String messageId) {
        messageIdCache.put(messageId, System.currentTimeMillis());
    }

    private void incrementCounter(String topic) {
        topicCounters.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
    }

    private void handleInterlockAlert(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes());
            log.warn("Received interlock alert: {}", payload);

            JsonNode rootNode = objectMapper.readTree(payload);
            String interlockType = getTextValue(rootNode, "interlockType", "type");
            String level = getTextValue(rootNode, "level");
            String message = getTextValue(rootNode, "message");
            String deviceCode = getTextValue(rootNode, "deviceCode", "device_code");

            if (safetyInterlockService != null) {
                Device device = null;
                if (deviceCode != null) {
                    Optional<Device> deviceOpt = deviceRepository.findByDeviceCode(deviceCode);
                    if (deviceOpt.isPresent()) {
                        device = deviceOpt.get();
                    }
                }

                String sensorValue = getTextValue(rootNode, "sensorValue", "sensor_value");
                String thresholdValue = getTextValue(rootNode, "thresholdValue", "threshold_value");
                boolean autoStop = rootNode.has("autoStopped") ? rootNode.get("autoStopped").asBoolean() : true;

                String interlockKey = interlockType + "_" + (deviceCode != null ? deviceCode : UUID.randomUUID());
                safetyInterlockService.triggerInterlock(
                        interlockKey,
                        interlockType != null ? interlockType : "unknown",
                        level != null ? level : "warning",
                        message != null ? message : "安全联锁告警",
                        device,
                        sensorValue,
                        thresholdValue,
                        autoStop
                );
            }

            if (deviceMonitoringService != null && deviceCode != null) {
                deviceMonitoringService.updateDeviceHeartbeat(deviceCode);
            }

        } catch (Exception e) {
            log.error("Error handling interlock alert: {}", e.getMessage(), e);
        }
    }

    private void incrementError(String topic) {
        topicErrors.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        Map<String, Long> counters = new java.util.HashMap<>();
        Map<String, Long> errors = new java.util.HashMap<>();
        
        topicCounters.forEach((k, v) -> counters.put(k, v.get()));
        topicErrors.forEach((k, v) -> errors.put(k, v.get()));
        
        stats.put("counters", counters);
        stats.put("errors", errors);
        stats.put("deduplicationCacheSize", messageIdCache.size());
        stats.put("mqttConnected", mqttConfig.isConnected());
        
        return stats;
    }
}
