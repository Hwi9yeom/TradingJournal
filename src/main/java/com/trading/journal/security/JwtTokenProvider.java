package com.trading.journal.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    private static final int MIN_SECRET_LENGTH = 32; // 256 bits for HMAC-SHA256

    /** Custom claim distinguishing access tokens from refresh tokens. */
    public static final String CLAIM_TOKEN_TYPE = "type";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @PostConstruct
    public void validateConfiguration() {
        // 로컬 개인 사용 모드에서는 토큰을 발급/검증할 일이 없다. 설정 강제 대신 무작위 시크릿을 생성해
        // 빈을 유효하게 유지한다(서명 불가능한 토큰만 만들어질 뿐).
        if (!authEnabled && (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH)) {
            jwtSecret =
                    java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString();
            log.info("Auth disabled: generated ephemeral JWT secret");
            return;
        }

        // SECURITY: Validate JWT secret is properly configured
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "SECURITY ERROR: JWT_SECRET environment variable must be set. "
                            + "Cannot start application without JWT secret.");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "SECURITY ERROR: JWT_SECRET must be at least "
                            + MIN_SECRET_LENGTH
                            + " characters (256 bits). "
                            + "Current length: "
                            + jwtSecret.length()
                            + ". Generate a secure secret using: openssl rand -base64 32");
        }
        log.info("JWT configuration validated successfully");
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername(), jwtExpiration, TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername(), refreshExpiration, TOKEN_TYPE_REFRESH);
    }

    public String generateAccessToken(String username) {
        return generateToken(username, jwtExpiration, TOKEN_TYPE_ACCESS);
    }

    private String generateToken(String username, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        return claims.getSubject();
    }

    /**
     * Returns true only if the token is signature-valid, unexpired, AND carries {@code
     * type=access}. Refresh tokens (and any token issued without an explicit access type) return
     * false, so a refresh token cannot be used to authenticate an API request or WebSocket
     * handshake.
     */
    public boolean isAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(getSigningKey())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        } catch (SecurityException ex) {
            log.error("JWT signature validation failed");
        }
        return false;
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }
}
