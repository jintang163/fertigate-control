package com.fertigate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fertigate.entity.Alert;
import com.fertigate.entity.Device;
import com.fertigate.entity.Zone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AlertWebSocketService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AlertWebSocketService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket session connected: {}, total online clients: {}", session.getId(), sessions.size());
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        log.info("WebSocket session disconnected: {}, total online clients: {}", session.getId(), sessions.size());
    }

    public void sendAlertToAll(Alert alert) {
        if (sessions.isEmpty()) {
            log.debug("No online clients, skip sending alert");
            return;
        }

        AlertWebSocketMessage message = convertToMessage(alert);
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Failed to serialize alert message", e);
            return;
        }

        TextMessage textMessage = new TextMessage(jsonMessage);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    log.debug("Alert sent to session {}: {}", entry.getKey(), message.getId());
                } catch (IOException e) {
                    log.error("Failed to send alert to session {}", entry.getKey(), e);
                }
            }
        }
        log.info("Alert pushed to {} clients: {}", sessions.size(), message.getId());
    }

    public void sendHeartbeat(WebSocketSession session) {
        if (!session.isOpen()) {
            return;
        }

        HeartbeatMessage heartbeat = new HeartbeatMessage();
        heartbeat.setType("heartbeat");
        heartbeat.setTimestamp(LocalDateTime.now());

        try {
            String jsonMessage = objectMapper.writeValueAsString(heartbeat);
            session.sendMessage(new TextMessage(jsonMessage));
            log.debug("Heartbeat sent to session: {}", session.getId());
        } catch (IOException e) {
            log.error("Failed to send heartbeat to session {}", session.getId(), e);
        }
    }

    public int getOnlineClientCount() {
        return sessions.size();
    }

    public Map<String, WebSocketSession> getSessions() {
        return sessions;
    }

    private AlertWebSocketMessage convertToMessage(Alert alert) {
        AlertWebSocketMessage message = new AlertWebSocketMessage();
        message.setType("alert");
        message.setId(alert.getId());
        message.setAlertType(alert.getAlertType());
        message.setLevel(alert.getLevel());
        message.setMessage(alert.getMessage());
        message.setSensorValue(alert.getSensorValue());
        message.setThresholdValue(alert.getThresholdValue());
        message.setCreatedAt(alert.getCreatedAt());

        Device device = alert.getDevice();
        if (device != null) {
            message.setDeviceName(device.getName());
            Zone zone = device.getZone();
            if (zone != null) {
                message.setZoneName(zone.getName());
            }
        }

        return message;
    }

    @lombok.Data
    public static class AlertWebSocketMessage {
        private String type = "alert";
        private UUID id;
        private String alertType;
        private String level;
        private String message;
        private BigDecimal sensorValue;
        private BigDecimal thresholdValue;
        private LocalDateTime createdAt;
        private String zoneName;
        private String deviceName;
    }

    @lombok.Data
    public static class HeartbeatMessage {
        private String type = "heartbeat";
        private LocalDateTime timestamp;
    }
}
