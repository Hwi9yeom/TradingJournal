package com.trading.journal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtTokenProvider token type")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(
                provider,
                "jwtSecret",
                "test-jwt-secret-key-for-unit-testing-minimum-32-characters-required");
        ReflectionTestUtils.setField(provider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(provider, "refreshExpiration", 604800000L);
        provider.validateConfiguration();
    }

    @Test
    @DisplayName("access 토큰은 isAccessToken=true")
    void accessTokenIsAccessToken() {
        String token = provider.generateAccessToken("alice");
        assertThat(provider.isAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("refresh 토큰은 access 토큰으로 인정되지 않는다")
    void refreshTokenIsNotAccessToken() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("alice");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);

        String refresh = provider.generateRefreshToken(auth);

        assertThat(provider.isAccessToken(refresh)).isFalse();
    }
}
