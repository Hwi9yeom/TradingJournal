package com.trading.journal.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 SecurityFilterChain을 활성화한 상태에서 경로별 인가 규칙을 검증한다. 정적 리소스(루트 welcome page 포함)는 공개, /api/**는 JWT
 * 필수라는 계약을 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:securitychaintest",
            "spring.h2.console.enabled=false",
            "spring.cache.type=simple",
            "spring.data.redis.enabled=false",
            "jwt.secret=test-jwt-secret-key-for-unit-testing-minimum-32-characters-required",
            "admin.password=TestAdminPassword123!"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SecurityFilterChainIntegrationTest {

    private static final String TEST_USERNAME = "security-chain-test-user";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername(TEST_USERNAME)) {
            userRepository.save(
                    User.builder()
                            .username(TEST_USERNAME)
                            .password(passwordEncoder.encode("irrelevant-password"))
                            .role("ROLE_USER")
                            .enabled(true)
                            .build());
        }
    }

    @Test
    @DisplayName("루트 경로(/)는 인증 없이 index.html로 포워드된다")
    void rootPathIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    @DisplayName("정적 HTML 페이지는 인증 없이 접근 가능하다")
    void staticHtmlPagesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/login.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호된 API는 토큰 없이 401을 반환한다")
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/data/template/csv")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 액세스 토큰이 있으면 템플릿 다운로드가 성공한다")
    void templateDownloadSucceedsWithValidToken() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);

        mockMvc.perform(get("/api/data/template/csv").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
