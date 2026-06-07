package com.fertigate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fertigate.service.AlertWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AlertWebSocketController extends TextWebSocketHandler {

    private final AlertWebSocketService alertWebSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> subscribedSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        alertWebSocketService.addSession(session);
        subscribedSessions.add(session.getId());
        sendWelcomeMessage(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from session {}: {}", session.getId(), payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String action = jsonNode.path("action").asText();

            switch (action) {
                case "subscribe":
                    handleSubscribe(session);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    log.warn("Unknown action: {}", action);
                    sendError(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error handling message from session {}", session.getId(), e);
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        alertWebSocketService.removeSession(session);
        subscribedSessions.remove(session.getId());
        log.info("Session {} closed with status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for session {}", session.getId(), exception);
        alertWebSocketService.removeSession(session);
        subscribedSessions.remove(session.getId());
    }

    private void handleSubscribe(WebSocketSession session) {
        subscribedSessions.add(session.getId());
        log.info("Session {} subscribed to alerts", session.getId());
        sendResponse(session, "subscribed", "Successfully subscribed to alerts");
    }

    private void handleUnsubscribe(WebSocketSession session) {
        subscribedSessions.remove(session.getId());
        log.info("Session {} unsubscribed from alerts", session.getId());
        sendResponse(session, "unsubscribed", "Successfully unsubscribed from alerts");
    }

    private void handlePing(WebSocketSession session) {
        sendResponse(session, "pong", "pong");
    }

    private void sendWelcomeMessage(WebSocketSession session) {
        try {
            Map<String, Object> welcome = Map.of(
                    "type", "welcome",
                    "message", "Connected to alert WebSocket service",
                    "onlineClients", alertWebSocketService.getOnlineClientCount(),
                    "subscribed", subscribedSessions.contains(session.getId())
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
        } catch (IOException e) {
            log.error("Failed to send welcome message to session {}", session.getId(), e);
        }
    }

    private void sendResponse(WebSocketSession session, String status, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> response = Map.of(
                    "type", "response",
                    "status", status,
                    "message", message
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            log.error("Failed to send response to session {}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> error = Map.of(
                    "type", "error",
                    "message", message
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.error("Failed to send error to session {}", session.getId(), e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void sendHeartbeatToAll() {
        if (subscribedSessions.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} subscribed sessions", subscribedSessions.size());

        for (Map.Entry<String, WebSocketSession> entry : alertWebSocketService.getSessions().entrySet()) {
            if (subscribedSessions.contains(entry.getKey())) {
                WebSocketSession session = entry.getValue();
                if (session.isOpen()) {
                    alertWebSocketService.sendHeartbeat(session);
                }
            }
        }
    }
}
