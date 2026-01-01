package com.trading.journal.controller;

import com.trading.journal.dto.StockPriceDto;
import com.trading.journal.service.StockPriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.trading.journal.config.TestSecurityConfig;
import org.springframework.test.web.servlet.MockMvc;
import yahoofinance.Stock;
import yahoofinance.quotes.stock.StockQuote;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockPriceController.class)
@Import(TestSecurityConfig.class)
class StockPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockPriceService stockPriceService;

    @Test
    @DisplayName("주식 가격 조회 - 성공")
    void getStockPrice_Success() throws Exception {
        // Given
        Stock mockStock = mock(Stock.class);
        StockQuote mockQuote = mock(StockQuote.class);
        
        when(mockStock.getSymbol()).thenReturn("AAPL");
        when(mockStock.getName()).thenReturn("Apple Inc.");
        when(mockStock.getQuote()).thenReturn(mockQuote);
        when(mockQuote.getPrice()).thenReturn(new BigDecimal("150.00"));
        when(mockQuote.getPreviousClose()).thenReturn(new BigDecimal("148.00"));
        
        when(stockPriceService.getStockInfo("AAPL")).thenReturn(mockStock);

        // When & Then
        mockMvc.perform(get("/api/stocks/price/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.currentPrice").value(150.00))
                .andExpect(jsonPath("$.previousClose").value(148.00))
                .andExpect(jsonPath("$.changeAmount").value(2.00));

        verify(stockPriceService).getStockInfo("AAPL");
    }

    @Test
    @DisplayName("주식 가격 조회 - 주식 정보 없음")
    void getStockPrice_StockNotFound() throws Exception {
        // Given
        when(stockPriceService.getStockInfo("INVALID")).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/stocks/price/INVALID"))
                .andExpect(status().isNotFound());

        verify(stockPriceService).getStockInfo("INVALID");
    }

    @Test
    @DisplayName("주식 가격 조회 - Quote 정보 없음")
    void getStockPrice_QuoteNotFound() throws Exception {
        // Given
        Stock mockStock = mock(Stock.class);
        when(mockStock.getQuote()).thenReturn(null);
        when(stockPriceService.getStockInfo("AAPL")).thenReturn(mockStock);

        // When & Then
        mockMvc.perform(get("/api/stocks/price/AAPL"))
                .andExpect(status().isNotFound());

        verify(stockPriceService).getStockInfo("AAPL");
    }

    @Test
    @DisplayName("주식 가격 조회 - 서비스 예외 발생")
    void getStockPrice_ServiceException() throws Exception {
        // Given
        when(stockPriceService.getStockInfo("AAPL")).thenThrow(new RuntimeException("Service error"));

        // When & Then
        mockMvc.perform(get("/api/stocks/price/AAPL"))
                .andExpect(status().isBadRequest());

        verify(stockPriceService).getStockInfo("AAPL");
    }

    @Test
    @DisplayName("주식 가격 조회 - 가격 정보 불완전")
    void getStockPrice_IncompletePrice() throws Exception {
        // Given
        Stock mockStock = mock(Stock.class);
        StockQuote mockQuote = mock(StockQuote.class);
        
        when(mockStock.getQuote()).thenReturn(mockQuote);
        when(mockQuote.getPrice()).thenReturn(null); // 현재가가 null
        when(mockQuote.getPreviousClose()).thenReturn(new BigDecimal("148.00"));
        
        when(stockPriceService.getStockInfo("AAPL")).thenReturn(mockStock);

        // When & Then
        mockMvc.perform(get("/api/stocks/price/AAPL"))
                .andExpect(status().isNotFound());

        verify(stockPriceService).getStockInfo("AAPL");
    }
}