package com.trading.journal.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("WebSocketSessionRegistry 유저별 전송")
class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    private WebSocketSession mockSession(String id, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }

    @Test
    @DisplayName("sendToUser는 해당 userId 세션에만 전송한다")
    void sendToUser_onlyToMatchingUser() throws Exception {
        WebSocketSession s1 = mockSession("s1", 1L);
        WebSocketSession s2 = mockSession("s2", 2L);
        registry.addSession(s1);
        registry.addSession(s2);

        registry.sendToUser(1L, "hello");

        verify(s1, times(1)).sendMessage(any(TextMessage.class));
        verify(s2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendToUser는 닫힌 세션에 전송하지 않는다")
    void sendToUser_skipsClosedSession() throws Exception {
        WebSocketSession s1 = mockSession("s1", 1L);
        when(s1.isOpen()).thenReturn(false);
        registry.addSession(s1);

        registry.sendToUser(1L, "hello");

        verify(s1, never()).sendMessage(any(TextMessage.class));
    }
}
