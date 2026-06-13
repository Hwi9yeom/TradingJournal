package com.trading.journal.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import com.trading.journal.security.JwtTokenProvider;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

@DisplayName("JwtHandshakeInterceptor")
class JwtHandshakeInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;
    private JwtHandshakeInterceptor interceptor;
    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userRepository = mock(UserRepository.class);
        interceptor = new JwtHandshakeInterceptor(jwtTokenProvider, userRepository);
        handler = mock(WebSocketHandler.class);
    }

    private ServerHttpRequest requestWithQuery(String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/alerts" + query));
        return request;
    }

    @Test
    @DisplayName("유효한 토큰이면 핸드셰이크를 허용하고 userId를 바인딩한다")
    void validToken_allowsAndBindsUserId() throws Exception {
        when(jwtTokenProvider.validateToken("good")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        User user = User.builder().id(42L).username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Map<String, Object> attributes = new HashMap<>();
        boolean result =
                interceptor.beforeHandshake(
                        requestWithQuery("?token=good"), null, handler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(42L);
    }

    @Test
    @DisplayName("토큰이 없으면 핸드셰이크를 거부한다")
    void missingToken_rejects() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        boolean result =
                interceptor.beforeHandshake(requestWithQuery(""), null, handler, attributes);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 핸드셰이크를 거부한다")
    void invalidToken_rejects() throws Exception {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();
        boolean result =
                interceptor.beforeHandshake(
                        requestWithQuery("?token=bad"), null, handler, attributes);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 핸드셰이크를 거부한다")
    void userNotFound_rejects() throws Exception {
        when(jwtTokenProvider.validateToken("good")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("good")).thenReturn("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        Map<String, Object> attributes = new HashMap<>();
        boolean result =
                interceptor.beforeHandshake(
                        requestWithQuery("?token=good"), null, handler, attributes);
        assertThat(result).isFalse();
    }
}
