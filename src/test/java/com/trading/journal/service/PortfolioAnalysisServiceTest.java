package com.trading.journal.service;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Stock;
import com.trading.journal.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private PortfolioAnalysisService portfolioAnalysisService;

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
    @DisplayName("포트폴리오 요약 조회 - 단일 종목")
    void getPortfolioSummary_SingleStock() {
        // Given
        List<Portfolio> portfolios = Arrays.asList(mockPortfolio);
        when(portfolioRepository.findAll()).thenReturn(portfolios);
        when(stockPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("160.00"));
        when(stockPriceService.getPreviousClose("AAPL")).thenReturn(new BigDecimal("158.00"));

        // When
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo(new BigDecimal("1600.00"));
        assertThat(summary.getTotalProfitLoss()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(summary.getTotalProfitLossPercent()).isEqualByComparingTo(new BigDecimal("6.67"));
        assertThat(summary.getHoldings()).hasSize(1);

        PortfolioDto holding = summary.getHoldings().get(0);
        assertThat(holding.getStockSymbol()).isEqualTo("AAPL");
        assertThat(holding.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("160.00"));
        assertThat(holding.getProfitLoss()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("포트폴리오 요약 조회 - 여러 종목")
    void getPortfolioSummary_MultipleStocks() {
        // Given
        Stock stock2 = Stock.builder()
                .id(2L)
                .symbol("GOOGL")
                .name("Alphabet Inc.")
                .build();

        Portfolio portfolio2 = Portfolio.builder()
                .id(2L)
                .stock(stock2)
                .quantity(new BigDecimal("5"))
                .averagePrice(new BigDecimal("2000.00"))
                .totalInvestment(new BigDecimal("10000.00"))
                .build();

        List<Portfolio> portfolios = Arrays.asList(mockPortfolio, portfolio2);
        when(portfolioRepository.findAll()).thenReturn(portfolios);
        when(stockPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("160.00"));
        when(stockPriceService.getPreviousClose("AAPL")).thenReturn(new BigDecimal("158.00"));
        when(stockPriceService.getCurrentPrice("GOOGL")).thenReturn(new BigDecimal("2100.00"));
        when(stockPriceService.getPreviousClose("GOOGL")).thenReturn(new BigDecimal("2050.00"));

        // When
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        // Then
        assertThat(summary.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("11500.00"));
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo(new BigDecimal("12100.00"));
        assertThat(summary.getTotalProfitLoss()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(summary.getHoldings()).hasSize(2);
    }

    @Test
    @DisplayName("포트폴리오 요약 조회 - 빈 포트폴리오")
    void getPortfolioSummary_EmptyPortfolio() {
        // Given
        when(portfolioRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        // Then
        assertThat(summary.getTotalInvestment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalProfitLoss()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalProfitLossPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getHoldings()).isEmpty();
    }

    @Test
    @DisplayName("종목별 포트폴리오 조회 - 성공")
    void getPortfolioBySymbol_Success() {
        // Given
        when(portfolioRepository.findByStockSymbol("AAPL")).thenReturn(Optional.of(mockPortfolio));
        when(stockPriceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("160.00"));
        when(stockPriceService.getPreviousClose("AAPL")).thenReturn(new BigDecimal("158.00"));

        // When
        PortfolioDto portfolio = portfolioAnalysisService.getPortfolioBySymbol("AAPL");

        // Then
        assertThat(portfolio).isNotNull();
        assertThat(portfolio.getStockSymbol()).isEqualTo("AAPL");
        assertThat(portfolio.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("160.00"));
        assertThat(portfolio.getCurrentValue()).isEqualByComparingTo(new BigDecimal("1600.00"));
        assertThat(portfolio.getProfitLoss()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(portfolio.getDayChange()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("종목별 포트폴리오 조회 - 종목 없음")
    void getPortfolioBySymbol_NotFound() {
        // Given
        when(portfolioRepository.findByStockSymbol("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> portfolioAnalysisService.getPortfolioBySymbol("INVALID"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Portfolio not found for symbol: INVALID");
    }

    @Test
    @DisplayName("포트폴리오 메트릭 계산 - API 오류 처리")
    void calculatePortfolioMetrics_ApiError() {
        // Given
        when(portfolioRepository.findAll()).thenReturn(Arrays.asList(mockPortfolio));
        when(stockPriceService.getCurrentPrice("AAPL")).thenThrow(new RuntimeException("API Error"));

        // When
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        // Then
        assertThat(summary.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalProfitLoss()).isEqualByComparingTo(new BigDecimal("-1500.00"));
        
        PortfolioDto holding = summary.getHoldings().get(0);
        assertThat(holding.getCurrentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(holding.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(holding.getProfitLoss()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}