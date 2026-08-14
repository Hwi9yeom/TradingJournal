package com.trading.journal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 단독 사용 모드({@code app.auth.enabled=false})에서 모든 요청을 기본 관리자 사용자로 인증한다.
 *
 * <p>서비스/컨트롤러 계층은 {@link SecurityContextHolder}에서 현재 사용자를 조회하므로, 로그인 절차를 생략하더라도 컨텍스트에는 실제 사용자가 있어야
 * 한다. JWT 검증 대신 이 필터가 그 자리를 채운다. {@link com.trading.journal.config.SecurityConfig}가 로컬 모드일 때만 체인에
 * 등록한다.
 */
@RequiredArgsConstructor
@Slf4j
public class LocalUserAuthenticationFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;
    private final String localUsername;

    // JwtAuthenticationFilter와 동일한 이유: async dispatch에서 컨텍스트를 복원할 수 있도록
    // request attribute에도 저장한다.
    private final SecurityContextRepository securityContextRepository =
            new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(localUsername);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        } catch (Exception ex) {
            // 사용자 레코드가 아직 없으면(초기화 이전 등) 미인증으로 통과시킨다.
            log.warn(
                    "Local auth mode: could not load user '{}': {}",
                    localUsername,
                    ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
