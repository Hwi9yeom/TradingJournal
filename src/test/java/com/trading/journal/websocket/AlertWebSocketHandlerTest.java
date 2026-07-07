package com.trading.journal.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("AlertWebSocketHandler")
class AlertWebSocketHandlerTest {

    private WebSocketSessionRegistry sessionRegistry;
    private AlertWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(WebSocketSessionRegistry.class);
        handler = new AlertWebSocketHandler(sessionRegistry);
    }

    @Test
    @DisplayName("transport 오류 시 세션을 닫고 레지스트리에서 제거한다 (레지스트리 누수 방지)")
    void handleTransportError_closesAndRemovesSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);

        handler.handleTransportError(session, new RuntimeException("boom"));

        verify(sessionRegistry).removeSession("s1");
        verify(session).close(any(CloseStatus.class));
    }
}
