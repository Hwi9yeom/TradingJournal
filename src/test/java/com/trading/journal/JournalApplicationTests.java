package com.trading.journal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "jwt.secret=test-jwt-secret-key-for-unit-testing-minimum-32-characters-required",
            "admin.password=TestAdminPassword123!"
        })
class JournalApplicationTests {

    @Test
    void contextLoads() {}
}
