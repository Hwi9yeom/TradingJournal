package com.trading.journal.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trading.journal.security.CustomUserDetailsService;
import com.trading.journal.security.JwtTokenProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test security configuration that permits all requests. Use this in tests to bypass
 * authentication.
 */
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Primary
    public JwtTokenProvider jwtTokenProvider() {
        JwtTokenProvider mockProvider = mock(JwtTokenProvider.class);
        when(mockProvider.validateToken(anyString())).thenReturn(false);
        return mockProvider;
    }

    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername("testuser").password("{noop}password").roles("USER").build());
    }

    @Bean
    @Primary
    public CustomUserDetailsService customUserDetailsService() {
        return mock(CustomUserDetailsService.class);
    }
}
