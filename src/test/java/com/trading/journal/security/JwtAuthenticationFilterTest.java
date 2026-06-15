package com.trading.journal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/transactions");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("refresh 토큰으로는 인증 컨텍스트를 설정하지 않는다")
    void refreshToken_doesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer refresh");
        when(tokenProvider.validateToken("refresh")).thenReturn(true);
        when(tokenProvider.isAccessToken("refresh")).thenReturn(false);
        // Even if the rest were stubbed, an access-type check must short-circuit first.
        when(tokenProvider.getUsernameFromToken("refresh")).thenReturn("bob");
        UserDetails bob = mock(UserDetails.class);
        when(bob.getAuthorities()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(bob);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("access 토큰이면 인증 컨텍스트를 설정한다")
    void accessToken_authenticates() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer access");
        when(tokenProvider.validateToken("access")).thenReturn(true);
        when(tokenProvider.isAccessToken("access")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("access")).thenReturn("alice");
        UserDetails alice = mock(UserDetails.class);
        when(alice.getAuthorities()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(alice);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(alice);
        verify(chain).doFilter(request, response);
    }
}
