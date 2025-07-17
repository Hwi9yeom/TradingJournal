package com.trading.journal.controller;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.service.PortfolioAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioAnalysisService portfolioAnalysisService;

    private PortfolioDto mockPortfolioDto;
    private PortfolioSummaryDto mockPortfolioSummaryDto;

    @BeforeEach
    void setUp() {
        mockPortfolioDto = PortfolioDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("150.00"))
                .totalInvestment(new BigDecimal("1500.00"))
                .currentPrice(new BigDecimal("160.00"))
                .currentValue(new BigDecimal("1600.00"))
                .profitLoss(new BigDecimal("100.00"))
                .profitLossPercent(new BigDecimal("6.67"))
                .dayChange(new BigDecimal("20.00"))
                .dayChangePercent(new BigDecimal("1.27"))
                .lastUpdated(LocalDateTime.now())
                .build();

        List<PortfolioDto> holdings = Arrays.asList(mockPortfolioDto);

        mockPortfolioSummaryDto = PortfolioSummaryDto.builder()
                .totalInvestment(new BigDecimal("1500.00"))
                .totalCurrentValue(new BigDecimal("1600.00"))
                .totalProfitLoss(new BigDecimal("100.00"))
                .totalProfitLossPercent(new BigDecimal("6.67"))
                .totalDayChange(new BigDecimal("20.00"))
                .totalDayChangePercent(new BigDecimal("1.27"))
                .holdings(holdings)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("포트폴리오 요약 조회")
    void getPortfolioSummary() throws Exception {
        // Given
        when(portfolioAnalysisService.getPortfolioSummary()).thenReturn(mockPortfolioSummaryDto);

        // When & Then
        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvestment").value(1500.00))
                .andExpect(jsonPath("$.totalCurrentValue").value(1600.00))
                .andExpect(jsonPath("$.totalProfitLoss").value(100.00))
                .andExpect(jsonPath("$.totalProfitLossPercent").value(6.67))
                .andExpect(jsonPath("$.totalDayChange").value(20.00))
                .andExpect(jsonPath("$.totalDayChangePercent").value(1.27))
                .andExpect(jsonPath("$.holdings").isArray())
                .andExpect(jsonPath("$.holdings[0].stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].stockName").value("Apple Inc."))
                .andExpect(jsonPath("$.holdings[0].quantity").value(10))
                .andExpect(jsonPath("$.holdings[0].currentPrice").value(160.00))
                .andExpect(jsonPath("$.holdings[0].profitLoss").value(100.00));

        verify(portfolioAnalysisService).getPortfolioSummary();
    }

    @Test
    @DisplayName("종목별 포트폴리오 조회 - 성공")
    void getPortfolioBySymbol_Success() throws Exception {
        // Given
        when(portfolioAnalysisService.getPortfolioBySymbol("AAPL")).thenReturn(mockPortfolioDto);

        // When & Then
        mockMvc.perform(get("/api/portfolio/symbol/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.stockName").value("Apple Inc."))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.averagePrice").value(150.00))
                .andExpect(jsonPath("$.totalInvestment").value(1500.00))
                .andExpect(jsonPath("$.currentPrice").value(160.00))
                .andExpect(jsonPath("$.currentValue").value(1600.00))
                .andExpect(jsonPath("$.profitLoss").value(100.00))
                .andExpect(jsonPath("$.profitLossPercent").value(6.67))
                .andExpect(jsonPath("$.dayChange").value(20.00))
                .andExpect(jsonPath("$.dayChangePercent").value(1.27));

        verify(portfolioAnalysisService).getPortfolioBySymbol("AAPL");
    }

    @Test
    @DisplayName("종목별 포트폴리오 조회 - 존재하지 않는 종목")
    void getPortfolioBySymbol_NotFound() throws Exception {
        // Given
        when(portfolioAnalysisService.getPortfolioBySymbol("INVALID"))
                .thenThrow(new RuntimeException("Portfolio not found for symbol: INVALID"));

        // When & Then
        mockMvc.perform(get("/api/portfolio/symbol/INVALID"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Portfolio not found for symbol: INVALID"));

        verify(portfolioAnalysisService).getPortfolioBySymbol("INVALID");
    }

    @Test
    @DisplayName("빈 포트폴리오 요약 조회")
    void getEmptyPortfolioSummary() throws Exception {
        // Given
        PortfolioSummaryDto emptyPortfolio = PortfolioSummaryDto.builder()
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalProfitLossPercent(BigDecimal.ZERO)
                .totalDayChange(BigDecimal.ZERO)
                .totalDayChangePercent(BigDecimal.ZERO)
                .holdings(Arrays.asList())
                .lastUpdated(LocalDateTime.now())
                .build();

        when(portfolioAnalysisService.getPortfolioSummary()).thenReturn(emptyPortfolio);

        // When & Then
        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvestment").value(0))
                .andExpect(jsonPath("$.totalCurrentValue").value(0))
                .andExpect(jsonPath("$.totalProfitLoss").value(0))
                .andExpect(jsonPath("$.totalProfitLossPercent").value(0))
                .andExpect(jsonPath("$.holdings").isEmpty());

        verify(portfolioAnalysisService).getPortfolioSummary();
    }
}