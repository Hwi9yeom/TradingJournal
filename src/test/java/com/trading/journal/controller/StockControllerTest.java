package com.trading.journal.controller;

import com.trading.journal.entity.Stock;
import com.trading.journal.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockRepository stockRepository;

    private Stock mockStock;

    @BeforeEach
    void setUp() {
        mockStock = Stock.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .exchange("NASDAQ")
                .build();
    }

    @Test
    @DisplayName("모든 주식 조회")
    void getAllStocks() throws Exception {
        // Given
        List<Stock> stocks = Arrays.asList(mockStock);
        when(stockRepository.findAll()).thenReturn(stocks);

        // When & Then
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].exchange").value("NASDAQ"));

        verify(stockRepository).findAll();
    }

    @Test
    @DisplayName("심볼로 주식 조회 - 성공")
    void getStock_Success() throws Exception {
        // Given
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mockStock));

        // When & Then
        mockMvc.perform(get("/api/stocks/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.exchange").value("NASDAQ"));

        verify(stockRepository).findBySymbol("AAPL");
    }

    @Test
    @DisplayName("심볼로 주식 조회 - 존재하지 않음")
    void getStock_NotFound() throws Exception {
        // Given
        when(stockRepository.findBySymbol("NONEXISTENT")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/stocks/NONEXISTENT"))
                .andExpect(status().isNotFound());

        verify(stockRepository).findBySymbol("NONEXISTENT");
    }
}