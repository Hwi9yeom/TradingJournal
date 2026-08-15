package com.trading.journal.config;

import com.trading.journal.security.CustomUserDetailsService;
import com.trading.journal.security.JwtAuthenticationEntryPoint;
import com.trading.journal.security.JwtAuthenticationFilter;
import com.trading.journal.security.LocalUserAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * 인증 활성화 여부. false면 로그인/JWT 없이 모든 요청을 허용하고, 요청마다 기본 관리자 사용자를 SecurityContext에 주입한다(로컬 개인 사용
     * 모드). 네트워크에 노출되는 배포에서는 반드시 true여야 한다.
     */
    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!authEnabled) {
            return localSecurityFilterChain(http);
        }
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(
                        exception ->
                                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> {
                            // Public endpoints
                            auth.requestMatchers("/api/auth/**")
                                    .permitAll()
                                    // Static resources (HTML, CSS, JS) - auth checked via
                                    // JavaScript. "/" forwards to the index.html welcome page.
                                    .requestMatchers("/", "/*.html", "/*.css", "/*.js")
                                    .permitAll()
                                    .requestMatchers(
                                            "/css/**", "/js/**", "/images/**", "/favicon.ico")
                                    .permitAll()
                                    .requestMatchers(
                                            "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                                    .permitAll()
                                    .requestMatchers("/error")
                                    .permitAll();
                            // Conditional matchers must precede anyRequest(); registering one
                            // afterwards throws IllegalStateException at startup.
                            if (h2ConsoleEnabled) {
                                auth.requestMatchers("/h2-console/**").permitAll();
                            }
                            // All API endpoints require authentication (except /api/auth/**)
                            auth.requestMatchers("/api/**")
                                    .authenticated()
                                    .anyRequest()
                                    .authenticated();
                        })
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 로컬 개인 사용 모드: 인가 없이 전부 허용, 기본 관리자 사용자를 항상 인증 컨텍스트에 주입. */
    private SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.addFilterBefore(
                new LocalUserAuthenticationFilter(customUserDetailsService, adminUsername),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:8080", "http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
