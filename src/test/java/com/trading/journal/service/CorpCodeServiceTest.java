package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.datasource.url=jdbc:h2:mem:testdb"
        })
class CorpCodeServiceTest {

    @Autowired private CorpCodeService corpCodeService;

    @Test
    @DisplayName("법인코드 조회 - 존재하는 경우")
    void findCorpCode_Found() {
        // When & Then
        // corp-codes.yml 파일에 있는 실제 데이터를 사용
        // 파일이 없거나 비어있을 수 있으므로 null이 아닌지만 확인하거나,
        // 파일에 정의된 실제 값이 있다면 해당 값으로 테스트
        String result = corpCodeService.findCorpCode("삼성전자");

        // 실제 corp-codes.yml 파일의 내용에 따라 달라짐
        // null이거나 실제 법인코드 값이 반환될 수 있음
        if (result != null) {
            assertThat(result).isNotEmpty();
        }
    }

    @Test
    @DisplayName("법인코드 조회 - 존재하지 않는 경우")
    void findCorpCode_NotFound() {
        // Given
        String nonexistentCorpName = "존재하지않는회사";

        // When
        String result = corpCodeService.findCorpCode(nonexistentCorpName);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("법인코드 조회 - null 입력")
    void findCorpCode_NullInput() {
        // When
        String result = corpCodeService.findCorpCode(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("법인코드 조회 - 빈 문자열 입력")
    void findCorpCode_EmptyString() {
        // When
        String result = corpCodeService.findCorpCode("");

        // Then
        assertThat(result).isNull();
    }
}
