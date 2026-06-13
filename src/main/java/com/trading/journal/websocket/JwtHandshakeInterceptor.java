package com.trading.journal.websocket;

import com.trading.journal.repository.UserRepository;
import com.trading.journal.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** WebSocket 핸드셰이크에서 JWT를 검증하고 세션에 userId를 바인딩한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("token");

        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket handshake rejected: missing or invalid token");
            return false;
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        return userRepository
                .findByUsername(username)
                .map(
                        user -> {
                            attributes.put("userId", user.getId());
                            return true;
                        })
                .orElseGet(
                        () -> {
                            log.warn("WebSocket handshake rejected: user not found");
                            return false;
                        });
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
