package com.trading.journal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * h2-console이 활성화된 프로필(로컬 개발)에서 SecurityFilterChain이 정상 생성되는지 고정한다.
 *
 * <p>회귀 배경: h2-console 매처를 {@code anyRequest()} 이후에 등록하면 Spring Security가 {@code
 * IllegalStateException("Can't configure mvcMatchers after anyRequest")}를 던져 컨텍스트 자체가 뜨지 않는다 — 로컬
 * 프로필 부팅 불가. 이 테스트 클래스는 해당 설정으로 컨텍스트를 로드하는 것 자체가 1차 검증이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:h2consolesecuritytest",
            "spring.h2.console.enabled=true",
            "spring.cache.type=simple",
            "spring.data.redis.enabled=false",
            "jwt.secret=test-jwt-secret-key-for-unit-testing-minimum-32-characters-required",
            "admin.password=TestAdminPassword123!"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class H2ConsoleSecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("h2-console 활성화 상태에서 컨텍스트(SecurityFilterChain)가 정상 생성된다")
    void contextLoadsWithH2ConsoleEnabled() {
        // @SpringBootTest 컨텍스트 로드 자체가 검증이다.
        // 버그 상태에서는 IllegalStateException으로 여기까지 오지 못한다.
    }

    @Test
    @DisplayName("h2-console 경로는 인증 없이 접근해도 401이 아니다 (permitAll)")
    void h2ConsolePathIsPermittedWithoutAuth() throws Exception {
        // MOCK 환경에는 H2 콘솔 서블릿이 없으므로 200을 기대할 수는 없다.
        // 보안 계약의 핵심은 "인증 요구(401)로 차단되지 않는다"이다.
        int status = mockMvc.perform(get("/h2-console/")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }
}
