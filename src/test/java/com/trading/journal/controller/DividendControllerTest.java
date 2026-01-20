package com.trading.journal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.journal.config.TestSecurityConfig;
import com.trading.journal.dto.DividendDto;
import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.service.DividendService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DividendController.class)
@Import(TestSecurityConfig.class)
class DividendControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DividendService dividendService;

    private ObjectMapper objectMapper;
    private DividendDto mockDividendDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockDividendDto =
                DividendDto.builder()
                        .id(1L)
                        .stockId(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .exDividendDate(LocalDate.now().minusDays(30))
                        .paymentDate(LocalDate.now())
                        .dividendPerShare(new BigDecimal("0.25"))
                        .quantity(new BigDecimal("100"))
                        .totalAmount(new BigDecimal("25.00"))
                        .taxAmount(new BigDecimal("3.75"))
                        .netAmount(new BigDecimal("21.25"))
                        .taxRate(new BigDecimal("15.0"))
                        .memo("분기 배당")
                        .build();
    }

    @Test
    @DisplayName("배당금 기록 생성")
    void createDividend_Success() throws Exception {
        // Given
        DividendDto requestDto =
                DividendDto.builder()
                        .stockId(1L)
                        .exDividendDate(LocalDate.now().minusDays(30))
                        .paymentDate(LocalDate.now())
                        .dividendPerShare(new BigDecimal("0.25"))
                        .quantity(new BigDecimal("100"))
                        .memo("분기 배당")
                        .build();

        when(dividendService.createDividend(any(DividendDto.class))).thenReturn(mockDividendDto);

        // When & Then
        mockMvc.perform(
                        post("/api/dividends")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.dividendPerShare").value(0.25))
                .andExpect(jsonPath("$.totalAmount").value(25.00))
                .andExpect(jsonPath("$.netAmount").value(21.25));

        verify(dividendService).createDividend(any(DividendDto.class));
    }

    @Test
    @DisplayName("배당금 수정")
    void updateDividend() throws Exception {
        // Given
        DividendDto updateDto =
                DividendDto.builder()
                        .dividendPerShare(new BigDecimal("0.30"))
                        .quantity(new BigDecimal("120"))
                        .memo("수정된 배당")
                        .build();

        DividendDto updatedDividend =
                DividendDto.builder()
                        .id(1L)
                        .stockId(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .exDividendDate(LocalDate.now().minusDays(30))
                        .paymentDate(LocalDate.now())
                        .dividendPerShare(new BigDecimal("0.30"))
                        .quantity(new BigDecimal("120"))
                        .totalAmount(new BigDecimal("36.00"))
                        .taxAmount(new BigDecimal("5.40"))
                        .netAmount(new BigDecimal("30.60"))
                        .taxRate(new BigDecimal("15.0"))
                        .memo("수정된 배당")
                        .build();

        when(dividendService.updateDividend(eq(1L), any(DividendDto.class)))
                .thenReturn(updatedDividend);

        // When & Then
        mockMvc.perform(
                        put("/api/dividends/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.dividendPerShare").value(0.30))
                .andExpect(jsonPath("$.quantity").value(120))
                .andExpect(jsonPath("$.memo").value("수정된 배당"));

        verify(dividendService).updateDividend(eq(1L), any(DividendDto.class));
    }

    @Test
    @DisplayName("배당금 삭제")
    void deleteDividend() throws Exception {
        // Given
        doNothing().when(dividendService).deleteDividend(1L);

        // When & Then
        mockMvc.perform(delete("/api/dividends/1")).andExpect(status().isNoContent());

        verify(dividendService).deleteDividend(1L);
    }

    @Test
    @DisplayName("배당금 ID로 조회")
    void getDividend() throws Exception {
        // Given
        when(dividendService.getDividend(1L)).thenReturn(mockDividendDto);

        // When & Then
        mockMvc.perform(get("/api/dividends/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"));

        verify(dividendService).getDividend(1L);
    }

    @Test
    @DisplayName("배당금 목록 조회 - 기본(최근 1년)")
    void getDividends_Default() throws Exception {
        // Given
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);

        when(dividendService.getDividendsByPeriod(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(dividends);

        // When & Then
        mockMvc.perform(get("/api/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].stockSymbol").value("AAPL"));

        verify(dividendService).getDividendsByPeriod(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("종목별 배당금 조회")
    void getDividendsByStock() throws Exception {
        // Given
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);
        when(dividendService.getDividendsByStock("AAPL")).thenReturn(dividends);

        // When & Then
        mockMvc.perform(get("/api/dividends").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].stockSymbol").value("AAPL"));

        verify(dividendService).getDividendsByStock("AAPL");
    }

    @Test
    @DisplayName("기간별 배당금 조회")
    void getDividendsByPeriod() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);

        when(dividendService.getDividendsByPeriod(startDate, endDate)).thenReturn(dividends);

        // When & Then
        mockMvc.perform(
                        get("/api/dividends")
                                .param("startDate", "2024-01-01")
                                .param("endDate", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(dividendService).getDividendsByPeriod(startDate, endDate);
    }

    @Test
    @DisplayName("배당금 요약 정보 조회")
    void getDividendSummary() throws Exception {
        // Given
        DividendSummaryDto summary =
                DividendSummaryDto.builder()
                        .totalDividends(new BigDecimal("1000.00"))
                        .totalTax(new BigDecimal("150.00"))
                        .yearlyDividends(new BigDecimal("500.00"))
                        .monthlyAverage(new BigDecimal("41.67"))
                        .dividendYield(new BigDecimal("3.50"))
                        .build();

        when(dividendService.getDividendSummary()).thenReturn(summary);

        // When & Then
        mockMvc.perform(get("/api/dividends/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDividends").value(1000.00))
                .andExpect(jsonPath("$.totalTax").value(150.00))
                .andExpect(jsonPath("$.yearlyDividends").value(500.00))
                .andExpect(jsonPath("$.monthlyAverage").value(41.67))
                .andExpect(jsonPath("$.dividendYield").value(3.50));

        verify(dividendService).getDividendSummary();
    }
}
