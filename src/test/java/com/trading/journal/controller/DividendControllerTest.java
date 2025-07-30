package com.trading.journal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.journal.dto.DividendDto;
import com.trading.journal.service.DividendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DividendController.class)
class DividendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DividendService dividendService;

    private ObjectMapper objectMapper;
    private DividendDto mockDividendDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockDividendDto = DividendDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.75"))
                .netAmount(new BigDecimal("21.25"))
                .memo("분기 배당")
                .build();
    }

    @Test
    @DisplayName("배당금 기록 생성 - 성공")
    void createDividend_Success() throws Exception {
        // Given
        DividendDto requestDto = DividendDto.builder()
                .stockSymbol("AAPL")
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.75"))
                .netAmount(new BigDecimal("21.25"))
                .memo("분기 배당")
                .build();

        when(dividendService.createDividend(any(DividendDto.class)))
                .thenReturn(mockDividendDto);

        // When & Then
        mockMvc.perform(post("/api/dividends")
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
    @DisplayName("배당금 기록 생성 - 유효성 검증 실패")
    void createDividend_ValidationFailure() throws Exception {
        // Given
        DividendDto invalidDto = DividendDto.builder()
                .stockSymbol("") // 빈 값
                .exDividendDate(null) // null
                .paymentDate(null) // null
                .dividendPerShare(new BigDecimal("-0.25")) // 음수
                .quantity(new BigDecimal("0")) // 0
                .build();

        // When & Then
        mockMvc.perform(post("/api/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(dividendService, never()).createDividend(any());
    }

    @Test
    @DisplayName("모든 배당금 조회 - 페이지네이션")
    void getAllDividends_WithPagination() throws Exception {
        // Given
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);
        Page<DividendDto> dividendPage = new PageImpl<>(dividends, PageRequest.of(0, 10), 1);
        
        when(dividendService.getAllDividends(any())).thenReturn(dividendPage);

        // When & Then
        mockMvc.perform(get("/api/dividends")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(dividendService).getAllDividends(any());
    }

    @Test
    @DisplayName("배당금 ID로 조회")
    void getDividendById() throws Exception {
        // Given
        when(dividendService.getDividendById(1L)).thenReturn(mockDividendDto);

        // When & Then
        mockMvc.perform(get("/api/dividends/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"));

        verify(dividendService).getDividendById(1L);
    }

    @Test
    @DisplayName("종목별 배당금 조회")
    void getDividendsBySymbol() throws Exception {
        // Given
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);
        when(dividendService.getDividendsBySymbol("AAPL")).thenReturn(dividends);

        // When & Then
        mockMvc.perform(get("/api/dividends/symbol/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].stockSymbol").value("AAPL"));

        verify(dividendService).getDividendsBySymbol("AAPL");
    }

    @Test
    @DisplayName("연도별 배당금 조회")
    void getDividendsByYear() throws Exception {
        // Given
        List<DividendDto> dividends = Arrays.asList(mockDividendDto);
        when(dividendService.getDividendsByYear(2024)).thenReturn(dividends);

        // When & Then
        mockMvc.perform(get("/api/dividends/year/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(dividendService).getDividendsByYear(2024);
    }

    @Test
    @DisplayName("배당금 수정")
    void updateDividend() throws Exception {
        // Given
        DividendDto updateDto = DividendDto.builder()
                .dividendPerShare(new BigDecimal("0.30"))
                .quantity(new BigDecimal("120"))
                .totalAmount(new BigDecimal("36.00"))
                .taxAmount(new BigDecimal("5.40"))
                .netAmount(new BigDecimal("30.60"))
                .memo("수정된 배당")
                .build();

        DividendDto updatedDividend = DividendDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.30"))
                .quantity(new BigDecimal("120"))
                .totalAmount(new BigDecimal("36.00"))
                .taxAmount(new BigDecimal("5.40"))
                .netAmount(new BigDecimal("30.60"))
                .memo("수정된 배당")
                .build();

        when(dividendService.updateDividend(eq(1L), any(DividendDto.class)))
                .thenReturn(updatedDividend);

        // When & Then
        mockMvc.perform(put("/api/dividends/1")
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
        mockMvc.perform(delete("/api/dividends/1"))
                .andExpect(status().isNoContent());

        verify(dividendService).deleteDividend(1L);
    }

    @Test
    @DisplayName("연간 배당금 통계")
    void getYearlyStats() throws Exception {
        // Given
        Map<String, Object> stats = new HashMap<>();
        stats.put("year", 2024);
        stats.put("totalGross", new BigDecimal("1000.00"));
        stats.put("totalTax", new BigDecimal("150.00"));
        stats.put("totalNet", new BigDecimal("850.00"));
        stats.put("count", 10);

        when(dividendService.getYearlyDividendStats(2024)).thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/dividends/stats/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.totalGross").value(1000.00))
                .andExpect(jsonPath("$.totalTax").value(150.00))
                .andExpect(jsonPath("$.totalNet").value(850.00))
                .andExpect(jsonPath("$.count").value(10));

        verify(dividendService).getYearlyDividendStats(2024);
    }

    @Test
    @DisplayName("세금 계산")
    void calculateTax() throws Exception {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal expectedTax = new BigDecimal("15.00");
        
        when(dividendService.calculateTax(totalAmount)).thenReturn(expectedTax);

        // When & Then
        mockMvc.perform(get("/api/dividends/calculate-tax")
                        .param("totalAmount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.taxAmount").value(15.00))
                .andExpect(jsonPath("$.netAmount").value(85.00));

        verify(dividendService).calculateTax(totalAmount);
    }
}