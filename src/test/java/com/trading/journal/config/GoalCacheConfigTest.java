package com.trading.journal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "jwt.secret=test-jwt-secret-key-for-unit-testing-minimum-32-characters-required",
            "admin.password=TestAdminPassword123!"
        })
@DisplayName("Goal 캐시 설정")
class GoalCacheConfigTest {

    @Autowired private CacheManager cacheManager;

    @Test
    @DisplayName("GoalService가 사용하는 goalSummary 캐시가 등록되어 있어야 한다")
    void goalSummaryCacheIsRegistered() {
        assertThat(cacheManager.getCache("goalSummary")).isNotNull();
    }
}
