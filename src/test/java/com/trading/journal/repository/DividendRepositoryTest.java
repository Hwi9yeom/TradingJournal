package com.trading.journal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Stock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class DividendRepositoryTest {

    @Autowired private TestEntityManager entityManager;

    @Autowired private DividendRepository dividendRepository;

    @Autowired private StockRepository stockRepository;

    private Stock testStock;

    @BeforeEach
    void setUp() {
        testStock = Stock.builder().symbol("AAPL").name("Apple Inc.").exchange("NASDAQ").build();
        testStock = stockRepository.save(testStock);

        // 테스트 배당금 데이터 생성
        Dividend dividend1 =
                Dividend.builder()
                        .stock(testStock)
                        .exDividendDate(LocalDate.of(2024, 3, 10))
                        .paymentDate(LocalDate.of(2024, 3, 15))
                        .dividendPerShare(new BigDecimal("0.25"))
                        .quantity(new BigDecimal("100"))
                        .totalAmount(new BigDecimal("25.00"))
                        .taxAmount(new BigDecimal("3.85"))
                        .netAmount(new BigDecimal("21.15"))
                        .memo("Q1 2024")
                        .build();

        Dividend dividend2 =
                Dividend.builder()
                        .stock(testStock)
                        .exDividendDate(LocalDate.of(2024, 6, 10))
                        .paymentDate(LocalDate.of(2024, 6, 15))
                        .dividendPerShare(new BigDecimal("0.27"))
                        .quantity(new BigDecimal("100"))
                        .totalAmount(new BigDecimal("27.00"))
                        .taxAmount(new BigDecimal("4.16"))
                        .netAmount(new BigDecimal("22.84"))
                        .memo("Q2 2024")
                        .build();

        dividendRepository.save(dividend1);
        dividendRepository.save(dividend2);

        entityManager.flush();
    }

    @Test
    @DisplayName("주식별 배당금 조회 - 지급일 역순")
    void findByStockOrderByPaymentDateDesc() {
        // When
        List<Dividend> dividends = dividendRepository.findByStockOrderByPaymentDateDesc(testStock);

        // Then
        assertThat(dividends).hasSize(2);
        assertThat(dividends.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2024, 6, 15)); // 최신순
        assertThat(dividends.get(1).getPaymentDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    @DisplayName("기간별 배당금 조회")
    void findByPaymentDateBetweenOrderByPaymentDateDesc() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);

        // When
        List<Dividend> dividends =
                dividendRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(
                        startDate, endDate);

        // Then
        assertThat(dividends).hasSize(2);
        assertThat(dividends.get(0).getPaymentDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(dividends.get(1).getPaymentDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    @DisplayName("주식 심볼로 배당금 조회")
    void findByStockSymbol() {
        // When
        List<Dividend> dividends = dividendRepository.findByStockSymbol("AAPL");

        // Then
        assertThat(dividends).hasSize(2);
        assertThat(dividends.get(0).getStock().getSymbol()).isEqualTo("AAPL");
        assertThat(dividends.get(1).getStock().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("기간별 총 배당금 계산")
    void getTotalDividendsByPeriod() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);

        // When
        BigDecimal total = dividendRepository.getTotalDividendsByPeriod(startDate, endDate);

        // Then
        assertThat(total).isEqualTo(new BigDecimal("43.99")); // 21.15 + 22.84
    }

    @Test
    @DisplayName("주식별 기간별 총 배당금 계산")
    void getTotalDividendsByStockAndPeriod() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);

        // When
        BigDecimal total =
                dividendRepository.getTotalDividendsByStockAndPeriod(testStock, startDate, endDate);

        // Then
        assertThat(total).isEqualTo(new BigDecimal("43.99")); // 21.15 + 22.84
    }

    @Test
    @DisplayName("존재하지 않는 주식 심볼로 조회")
    void findByStockSymbol_NotFound() {
        // When
        List<Dividend> dividends = dividendRepository.findByStockSymbol("INVALID");

        // Then
        assertThat(dividends).isEmpty();
    }

    @Test
    @DisplayName("미래 기간으로 총 배당금 조회")
    void getTotalDividendsByPeriod_FuturePeriod() {
        // Given
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);

        // When
        BigDecimal total = dividendRepository.getTotalDividendsByPeriod(startDate, endDate);

        // Then
        assertThat(total).isNull();
    }
}
