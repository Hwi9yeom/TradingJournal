package com.trading.journal.service;

import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private Stock mockStock;
    private Portfolio mockPortfolio;

    @BeforeEach
    void setUp() {
        mockStock = Stock.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .build();

        mockPortfolio = Portfolio.builder()
                .id(1L)
                .stock(mockStock)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("150.00"))
                .totalInvestment(new BigDecimal("1500.00"))
                .build();
    }

    @Test
    @DisplayName("포트폴리오 업데이트 - 첫 매수")
    void updatePortfolio_FirstBuy() {
        // Given
        Transaction buyTransaction = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.00"))
                .commission(new BigDecimal("5.00"))
                .build();

        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.empty());

        // When
        portfolioService.updatePortfolio(buyTransaction);

        // Then
        verify(portfolioRepository).save(argThat(portfolio -> {
            assertThat(portfolio.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(portfolio.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("1505.00"));
            assertThat(portfolio.getAveragePrice()).isEqualByComparingTo(new BigDecimal("150.50"));
            return true;
        }));
    }

    @Test
    @DisplayName("포트폴리오 업데이트 - 추가 매수")
    void updatePortfolio_AdditionalBuy() {
        // Given
        Transaction buyTransaction = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("160.00"))
                .commission(new BigDecimal("3.00"))
                .build();

        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.of(mockPortfolio));

        // When
        portfolioService.updatePortfolio(buyTransaction);

        // Then
        verify(portfolioRepository).save(argThat(portfolio -> {
            assertThat(portfolio.getQuantity()).isEqualByComparingTo(new BigDecimal("15"));
            assertThat(portfolio.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("2303.00"));
            assertThat(portfolio.getAveragePrice()).isEqualByComparingTo(new BigDecimal("153.53"));
            return true;
        }));
    }

    @Test
    @DisplayName("포트폴리오 업데이트 - 부분 매도")
    void updatePortfolio_PartialSell() {
        // Given
        Transaction sellTransaction = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("170.00"))
                .commission(new BigDecimal("3.00"))
                .build();

        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.of(mockPortfolio));

        // When
        portfolioService.updatePortfolio(sellTransaction);

        // Then
        verify(portfolioRepository).save(argThat(portfolio -> {
            assertThat(portfolio.getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
            assertThat(portfolio.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("750.00"));
            return true;
        }));
    }

    @Test
    @DisplayName("포트폴리오 업데이트 - 전량 매도")
    void updatePortfolio_CompleteSell() {
        // Given
        Transaction sellTransaction = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("170.00"))
                .build();

        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.of(mockPortfolio));

        // When
        portfolioService.updatePortfolio(sellTransaction);

        // Then
        verify(portfolioRepository).delete(mockPortfolio);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("포트폴리오 재계산 - 거래 내역이 있는 경우")
    void recalculatePortfolio_WithTransactions() {
        // Given
        Transaction buy1 = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.00"))
                .commission(new BigDecimal("5.00"))
                .transactionDate(LocalDateTime.now().minusDays(3))
                .build();

        Transaction buy2 = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("160.00"))
                .commission(new BigDecimal("3.00"))
                .transactionDate(LocalDateTime.now().minusDays(2))
                .build();

        Transaction sell = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("3"))
                .price(new BigDecimal("170.00"))
                .transactionDate(LocalDateTime.now().minusDays(1))
                .build();

        when(transactionRepository.findByStockIdOrderByTransactionDateDesc(1L))
                .thenReturn(Arrays.asList(sell, buy2, buy1));
        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.empty());

        // When
        portfolioService.recalculatePortfolio(1L);

        // Then
        verify(portfolioRepository).save(argThat(portfolio -> {
            assertThat(portfolio.getQuantity()).isEqualByComparingTo(new BigDecimal("12"));
            assertThat(portfolio.getAveragePrice()).isNotNull();
            return true;
        }));
    }

    @Test
    @DisplayName("포트폴리오 재계산 - 거래 내역이 없는 경우")
    void recalculatePortfolio_NoTransactions() {
        // Given
        when(transactionRepository.findByStockIdOrderByTransactionDateDesc(1L))
                .thenReturn(Collections.emptyList());
        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.of(mockPortfolio));

        // When
        portfolioService.recalculatePortfolio(1L);

        // Then
        verify(portfolioRepository).delete(mockPortfolio);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("포트폴리오 재계산 - 전량 매도된 경우")
    void recalculatePortfolio_AllSold() {
        // Given
        Transaction buy = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.00"))
                .transactionDate(LocalDateTime.now().minusDays(2))
                .build();

        Transaction sell = Transaction.builder()
                .stock(mockStock)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("170.00"))
                .transactionDate(LocalDateTime.now().minusDays(1))
                .build();

        when(transactionRepository.findByStockIdOrderByTransactionDateDesc(1L))
                .thenReturn(Arrays.asList(sell, buy));
        when(portfolioRepository.findByStockId(1L)).thenReturn(Optional.of(mockPortfolio));

        // When
        portfolioService.recalculatePortfolio(1L);

        // Then
        verify(portfolioRepository).delete(mockPortfolio);
        verify(portfolioRepository, never()).save(any());
    }
}