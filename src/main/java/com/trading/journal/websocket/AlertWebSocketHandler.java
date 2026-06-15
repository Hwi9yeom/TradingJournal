package com.trading.journal.websocket;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.addSession(session);
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.removeSession(session.getId());
        log.info("WebSocket connection closed: {} with status {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Received message from {}: {}", session.getId(), message.getPayload());
        // Handle ping/pong or client messages if needed
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error(
                "WebSocket transport error for session {}: {}",
                session.getId(),
                exception.getMessage());
        // Proactively close and deregister: afterConnectionClosed may not fire on an
        // abrupt transport failure, which would otherwise leak the session in the registry.
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException e) {
            log.warn(
                    "Failed to close errored WebSocket session {}: {}",
                    session.getId(),
                    e.getMessage());
        } finally {
            sessionRegistry.removeSession(session.getId());
        }
    }
}
