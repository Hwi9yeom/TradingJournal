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

    // 필드 초기값 true: Spring 밖에서 생성되더라도(유닛 테스트 등) 인증이 조용히 꺼지지 않도록 한다.
    @org.springframework.beans.factory.annotation.Value("${app.auth.enabled:true}")
    private boolean authEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${admin.username:admin}")
    private String adminUsername = "admin";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        // 로컬 개인 사용 모드: 토큰 없이 기본 관리자로 바인딩한다(HTTP 체인의 LocalUserAuthenticationFilter와 동일 정책).
        if (!authEnabled) {
            return userRepository
                    .findByUsername(adminUsername)
                    .map(
                            user -> {
                                attributes.put("userId", user.getId());
                                return true;
                            })
                    .orElseGet(
                            () -> {
                                log.warn(
                                        "WebSocket handshake rejected: local user not found: {}",
                                        adminUsername);
                                return false;
                            });
        }

        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("token");

        if (token == null
                || token.isBlank()
                || !jwtTokenProvider.validateToken(token)
                || !jwtTokenProvider.isAccessToken(token)) {
            // Reject missing/invalid/expired tokens AND refresh tokens (only access tokens
            // may open a WebSocket); fail before any user lookup.
            log.warn("WebSocket handshake rejected: missing or invalid access token");
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
