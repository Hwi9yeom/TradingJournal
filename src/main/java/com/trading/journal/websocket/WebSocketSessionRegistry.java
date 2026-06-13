package com.trading.journal.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
@Slf4j
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket session added: {}", session.getId());
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("WebSocket session removed: {}", sessionId);
    }

    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.values()
                .forEach(
                        session -> {
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(textMessage);
                                }
                            } catch (IOException e) {
                                log.error(
                                        "Error broadcasting message to session {}: {}",
                                        session.getId(),
                                        e.getMessage());
                            }
                        });
    }

    /** 특정 유저의 열린 세션에만 메시지를 전송한다. */
    public void sendToUser(Long userId, String message) {
        if (userId == null) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        sessions.values().stream()
                .filter(session -> userId.equals(session.getAttributes().get("userId")))
                .forEach(
                        session -> {
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(textMessage);
                                }
                            } catch (IOException e) {
                                log.error(
                                        "Error sending message to user {} session {}: {}",
                                        userId,
                                        session.getId(),
                                        e.getMessage());
                            }
                        });
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
