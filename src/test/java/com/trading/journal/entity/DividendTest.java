package com.trading.journal.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DividendTest {

    private Stock testStock;
    private Dividend dividend;

    @BeforeEach
    void setUp() {
        testStock = Stock.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .exchange("NASDAQ")
                .build();

        dividend = Dividend.builder()
                .id(1L)
                .stock(testStock)
                .exDividendDate(LocalDate.of(2024, 3, 10))
                .paymentDate(LocalDate.of(2024, 3, 15))
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.85"))
                .netAmount(new BigDecimal("21.15"))
                .memo("Q1 2024 배당")
                .build();
    }

    @Test
    @DisplayName("Dividend 엔티티 생성 및 필드 확인")
    void createDividend() {
        // Then
        assertThat(dividend.getId()).isEqualTo(1L);
        assertThat(dividend.getStock()).isEqualTo(testStock);
        assertThat(dividend.getExDividendDate()).isEqualTo(LocalDate.of(2024, 3, 10));
        assertThat(dividend.getPaymentDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(dividend.getDividendPerShare()).isEqualTo(new BigDecimal("0.25"));
        assertThat(dividend.getQuantity()).isEqualTo(new BigDecimal("100"));
        assertThat(dividend.getTotalAmount()).isEqualTo(new BigDecimal("25.00"));
        assertThat(dividend.getTaxAmount()).isEqualTo(new BigDecimal("3.85"));
        assertThat(dividend.getNetAmount()).isEqualTo(new BigDecimal("21.15"));
        assertThat(dividend.getMemo()).isEqualTo("Q1 2024 배당");
    }

    @Test
    @DisplayName("Dividend 필드 수정 테스트")
    void updateDividend() {
        // When
        dividend.setDividendPerShare(new BigDecimal("0.30"));
        dividend.setQuantity(new BigDecimal("150"));
        dividend.setTotalAmount(new BigDecimal("45.00"));
        dividend.setTaxAmount(new BigDecimal("6.93"));
        dividend.setNetAmount(new BigDecimal("38.07"));
        dividend.setMemo("수정된 배당");

        // Then
        assertThat(dividend.getDividendPerShare()).isEqualTo(new BigDecimal("0.30"));
        assertThat(dividend.getQuantity()).isEqualTo(new BigDecimal("150"));
        assertThat(dividend.getTotalAmount()).isEqualTo(new BigDecimal("45.00"));
        assertThat(dividend.getTaxAmount()).isEqualTo(new BigDecimal("6.93"));
        assertThat(dividend.getNetAmount()).isEqualTo(new BigDecimal("38.07"));
        assertThat(dividend.getMemo()).isEqualTo("수정된 배당");
    }

    @Test
    @DisplayName("Builder 패턴으로 Dividend 생성")
    void buildDividend() {
        // Given & When
        Dividend newDividend = Dividend.builder()
                .stock(testStock)
                .exDividendDate(LocalDate.of(2024, 6, 10))
                .paymentDate(LocalDate.of(2024, 6, 15))
                .dividendPerShare(new BigDecimal("0.28"))
                .quantity(new BigDecimal("200"))
                .totalAmount(new BigDecimal("56.00"))
                .taxAmount(new BigDecimal("8.62"))
                .netAmount(new BigDecimal("47.38"))
                .memo("Q2 2024 배당")
                .build();

        // Then
        assertThat(newDividend.getStock()).isEqualTo(testStock);
        assertThat(newDividend.getExDividendDate()).isEqualTo(LocalDate.of(2024, 6, 10));
        assertThat(newDividend.getPaymentDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(newDividend.getDividendPerShare()).isEqualTo(new BigDecimal("0.28"));
        assertThat(newDividend.getQuantity()).isEqualTo(new BigDecimal("200"));
        assertThat(newDividend.getTotalAmount()).isEqualTo(new BigDecimal("56.00"));
        assertThat(newDividend.getTaxAmount()).isEqualTo(new BigDecimal("8.62"));
        assertThat(newDividend.getNetAmount()).isEqualTo(new BigDecimal("47.38"));
        assertThat(newDividend.getMemo()).isEqualTo("Q2 2024 배당");
    }

    @Test
    @DisplayName("equals 및 hashCode 테스트")
    void testEqualsAndHashCode() {
        // Given
        Dividend dividend1 = Dividend.builder()
                .id(1L)
                .stock(testStock)
                .exDividendDate(LocalDate.of(2024, 3, 10))
                .paymentDate(LocalDate.of(2024, 3, 15))
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.85"))
                .netAmount(new BigDecimal("21.15"))
                .build();

        Dividend dividend2 = Dividend.builder()
                .id(1L)
                .stock(testStock)
                .exDividendDate(LocalDate.of(2024, 3, 10))
                .paymentDate(LocalDate.of(2024, 3, 15))
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.85"))
                .netAmount(new BigDecimal("21.15"))
                .build();

        // When & Then
        assertThat(dividend1).isEqualTo(dividend2);
        assertThat(dividend1.hashCode()).isEqualTo(dividend2.hashCode());
    }
}