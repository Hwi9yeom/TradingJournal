package com.trading.journal.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로컬 개인 사용 모드(app.auth.enabled=false) 계약을 고정한다.
 *
 * <ul>
 *   <li>토큰 없이 보호 API에 접근 가능해야 한다.
 *   <li>모든 요청은 기본 관리자 사용자로 인증되어 사용자 의존 로직이 동작해야 한다.
 *   <li>/api/auth/config가 프론트엔드에 authEnabled=false를 알려야 한다.
 *   <li>admin.password와 jwt.secret이 비어 있어도 부팅되어야 한다(로그인이 없으므로).
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "app.auth.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:localauthmodetest",
            "spring.h2.console.enabled=false",
            "spring.cache.type=simple",
            "spring.data.redis.enabled=false",
            // 로컬 모드에서는 비어 있어도 부팅되어야 한다.
            "jwt.secret=",
            "admin.password="
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalAuthModeIntegrationTest {

    @Autowired private MockMvc mockMvc;

    // .properties가 .yml보다 우선하므로 실효 admin.username을 직접 주입받는다.
    @org.springframework.beans.factory.annotation.Value("${admin.username}")
    private String adminUsername;

    @Test
    @DisplayName("보호된 API가 토큰 없이 접근 가능하다")
    void protectedApiAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/data/template/csv")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("모든 요청이 기본 관리자 사용자로 인증된다")
    void requestsRunAsDefaultAdminUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername));
    }

    @Test
    @DisplayName("/api/auth/config가 authEnabled=false를 반환한다")
    void authConfigReportsDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled").value(false));
    }

    @Test
    @DisplayName("정적 리소스도 그대로 접근 가능하다")
    void staticResourcesAccessible() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
