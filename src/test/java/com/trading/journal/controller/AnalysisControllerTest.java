package com.trading.journal.controller;

import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.service.AnalysisService;
import com.trading.journal.service.StockAnalysisService;
import com.trading.journal.service.TaxCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AnalysisService analysisService;
    
    @MockBean
    private StockAnalysisService stockAnalysisService;
    
    @MockBean
    private TaxCalculationService taxCalculationService;
    
    @Test
    void analyzePeriod_ShouldReturnPeriodAnalysis() throws Exception {
        PeriodAnalysisDto analysis = PeriodAnalysisDto.builder()
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 3, 31))
            .totalBuyAmount(new BigDecimal("10000000"))
            .totalSellAmount(new BigDecimal("12000000"))
            .realizedProfit(new BigDecimal("2000000"))
            .unrealizedProfit(new BigDecimal("500000"))
            .totalProfit(new BigDecimal("2500000"))
            .totalTransactions(10)
            .buyTransactions(6)
            .sellTransactions(4)
            .monthlyAnalysis(Arrays.asList(
                PeriodAnalysisDto.MonthlyAnalysisDto.builder()
                    .yearMonth("2024-01")
                    .buyAmount(new BigDecimal("5000000"))
                    .sellAmount(new BigDecimal("3000000"))
                    .profit(new BigDecimal("500000"))
                    .build()
            ))
            .build();
        
        when(analysisService.analyzePeriod(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(analysis);
        
        mockMvc.perform(get("/api/analysis/period")
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBuyAmount").value(10000000))
            .andExpect(jsonPath("$.totalSellAmount").value(12000000))
            .andExpect(jsonPath("$.realizedProfit").value(2000000))
            .andExpect(jsonPath("$.totalTransactions").value(10))
            .andExpect(jsonPath("$.monthlyAnalysis[0].yearMonth").value("2024-01"));
    }
    
    @Test
    void analyzePeriod_WithInvalidDates_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/analysis/period")
                .param("startDate", "2024-03-31")
                .param("endDate", "2024-01-01"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void analyzeYear_ShouldReturnYearAnalysis() throws Exception {
        PeriodAnalysisDto analysis = PeriodAnalysisDto.builder()
            .startDate(LocalDate.of(2024, 1, 1))
            .endDate(LocalDate.of(2024, 12, 31))
            .totalBuyAmount(new BigDecimal("50000000"))
            .totalSellAmount(new BigDecimal("55000000"))
            .build();
        
        when(analysisService.analyzePeriod(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(analysis);
        
        mockMvc.perform(get("/api/analysis/period/year/2024"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBuyAmount").value(50000000))
            .andExpect(jsonPath("$.totalSellAmount").value(55000000));
    }
    
    @Test
    void analyzeStock_ShouldReturnStockAnalysis() throws Exception {
        StockAnalysisDto analysis = StockAnalysisDto.builder()
            .stockSymbol("005930")
            .stockName("삼성전자")
            .totalBuyCount(5)
            .totalSellCount(3)
            .totalBuyQuantity(new BigDecimal("500"))
            .totalSellQuantity(new BigDecimal("300"))
            .averageBuyPrice(new BigDecimal("50000"))
            .averageSellPrice(new BigDecimal("55000"))
            .realizedProfit(new BigDecimal("1500000"))
            .currentHolding(new BigDecimal("200"))
            .holdingDays(120)
            .tradingPatterns(Arrays.asList(
                StockAnalysisDto.TradingPatternDto.builder()
                    .pattern("평균 보유 기간")
                    .value("45.5일")
                    .description("매수 후 매도까지의 평균 기간")
                    .build()
            ))
            .build();
        
        when(stockAnalysisService.analyzeStock(anyString())).thenReturn(analysis);
        
        mockMvc.perform(get("/api/analysis/stock/005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockSymbol").value("005930"))
            .andExpect(jsonPath("$.stockName").value("삼성전자"))
            .andExpect(jsonPath("$.totalBuyCount").value(5))
            .andExpect(jsonPath("$.realizedProfit").value(1500000))
            .andExpect(jsonPath("$.tradingPatterns[0].pattern").value("평균 보유 기간"));
    }
    
    @Test
    void calculateTax_ShouldReturnTaxCalculation() throws Exception {
        TaxCalculationDto taxCalculation = TaxCalculationDto.builder()
            .taxYear(2024)
            .totalSellAmount(new BigDecimal("50000000"))
            .totalBuyAmount(new BigDecimal("45000000"))
            .totalProfit(new BigDecimal("5000000"))
            .totalLoss(new BigDecimal("500000"))
            .netProfit(new BigDecimal("4500000"))
            .taxableAmount(new BigDecimal("2000000"))
            .estimatedTax(new BigDecimal("440000"))
            .taxRate(new BigDecimal("22"))
            .taxDetails(Arrays.asList(
                TaxCalculationDto.TaxDetailDto.builder()
                    .stockSymbol("005930")
                    .stockName("삼성전자")
                    .buyDate(LocalDate.of(2024, 1, 10))
                    .sellDate(LocalDate.of(2024, 6, 15))
                    .buyAmount(new BigDecimal("5000000"))
                    .sellAmount(new BigDecimal("6000000"))
                    .profit(new BigDecimal("1000000"))
                    .loss(BigDecimal.ZERO)
                    .isLongTerm(false)
                    .build()
            ))
            .build();
        
        when(taxCalculationService.calculateTax(anyInt())).thenReturn(taxCalculation);
        
        mockMvc.perform(get("/api/analysis/tax/2024"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxYear").value(2024))
            .andExpect(jsonPath("$.totalProfit").value(5000000))
            .andExpect(jsonPath("$.netProfit").value(4500000))
            .andExpect(jsonPath("$.estimatedTax").value(440000))
            .andExpect(jsonPath("$.taxRate").value(22))
            .andExpect(jsonPath("$.taxDetails[0].stockSymbol").value("005930"));
    }
    
    @Test
    void calculateTax_WithInvalidYear_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/analysis/tax/1999"))
            .andExpect(status().isBadRequest());
        
        mockMvc.perform(get("/api/analysis/tax/2030"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void calculateCurrentYearTax_ShouldReturnCurrentYearTax() throws Exception {
        int currentYear = LocalDate.now().getYear();
        TaxCalculationDto taxCalculation = TaxCalculationDto.builder()
            .taxYear(currentYear)
            .totalSellAmount(new BigDecimal("30000000"))
            .estimatedTax(new BigDecimal("200000"))
            .taxRate(new BigDecimal("22"))
            .build();
        
        when(taxCalculationService.calculateTax(anyInt())).thenReturn(taxCalculation);
        
        mockMvc.perform(get("/api/analysis/tax/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxYear").value(currentYear))
            .andExpect(jsonPath("$.estimatedTax").value(200000));
    }
}