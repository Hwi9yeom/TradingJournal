package com.trading.journal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.journal.config.TestSecurityConfig;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import(TestSecurityConfig.class)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TransactionService transactionService;

    private ObjectMapper objectMapper;
    private TransactionDto mockTransactionDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockTransactionDto =
                TransactionDto.builder()
                        .id(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal("150.00"))
                        .commission(new BigDecimal("5.00"))
                        .transactionDate(LocalDateTime.now())
                        .notes("Test transaction")
                        .totalAmount(new BigDecimal("1505.00"))
                        .build();
    }

    @Test
    @DisplayName("거래 생성 - 성공")
    void createTransaction_Success() throws Exception {
        // Given
        TransactionDto requestDto =
                TransactionDto.builder()
                        .stockSymbol("AAPL")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal("150.00"))
                        .commission(new BigDecimal("5.00"))
                        .transactionDate(LocalDateTime.now())
                        .notes("Test transaction")
                        .build();

        when(transactionService.createTransaction(any(TransactionDto.class)))
                .thenReturn(mockTransactionDto);

        // When & Then
        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.totalAmount").value(1505.00));

        verify(transactionService).createTransaction(any(TransactionDto.class));
    }

    @Test
    @DisplayName("거래 생성 - 유효성 검증 실패")
    void createTransaction_ValidationFailure() throws Exception {
        // Given
        TransactionDto invalidDto =
                TransactionDto.builder()
                        .stockSymbol("") // 빈 값
                        .type(null) // null
                        .quantity(new BigDecimal("-10")) // 음수
                        .price(new BigDecimal("0")) // 0
                        .transactionDate(null) // null
                        .build();

        // When & Then
        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).createTransaction(any());
    }

    @Test
    @DisplayName("모든 거래 조회")
    void getAllTransactions() throws Exception {
        // Given
        List<TransactionDto> transactions = Arrays.asList(mockTransactionDto);
        Page<TransactionDto> transactionPage = new PageImpl<>(transactions);
        when(transactionService.getAllTransactions(any(Pageable.class)))
                .thenReturn(transactionPage);

        // When & Then
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].stockSymbol").value("AAPL"));

        verify(transactionService).getAllTransactions(any(Pageable.class));
    }

    @Test
    @DisplayName("거래 ID로 조회")
    void getTransactionById() throws Exception {
        // Given
        when(transactionService.getTransactionById(1L)).thenReturn(mockTransactionDto);

        // When & Then
        mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"));

        verify(transactionService).getTransactionById(1L);
    }

    @Test
    @DisplayName("종목별 거래 조회")
    void getTransactionsBySymbol() throws Exception {
        // Given
        List<TransactionDto> transactions = Arrays.asList(mockTransactionDto);
        when(transactionService.getTransactionsBySymbol("AAPL")).thenReturn(transactions);

        // When & Then
        mockMvc.perform(get("/api/transactions/symbol/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].stockSymbol").value("AAPL"));

        verify(transactionService).getTransactionsBySymbol("AAPL");
    }

    @Test
    @DisplayName("날짜 범위로 거래 조회")
    void getTransactionsByDateRange() throws Exception {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        List<TransactionDto> transactions = Arrays.asList(mockTransactionDto);

        when(transactionService.getTransactionsByDateRange(
                        any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(transactions);

        // When & Then
        mockMvc.perform(
                        get("/api/transactions/date-range")
                                .param("startDate", startDate.toString())
                                .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(transactionService)
                .getTransactionsByDateRange(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("거래 수정")
    void updateTransaction() throws Exception {
        // Given
        TransactionDto updateDto =
                TransactionDto.builder()
                        .stockSymbol("AAPL")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("20"))
                        .price(new BigDecimal("160.00"))
                        .commission(new BigDecimal("10.00"))
                        .transactionDate(LocalDateTime.now())
                        .notes("Updated transaction")
                        .build();

        TransactionDto updatedTransaction =
                TransactionDto.builder()
                        .id(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("20"))
                        .price(new BigDecimal("160.00"))
                        .commission(new BigDecimal("10.00"))
                        .transactionDate(updateDto.getTransactionDate())
                        .notes("Updated transaction")
                        .totalAmount(new BigDecimal("3210.00"))
                        .build();

        when(transactionService.updateTransaction(eq(1L), any(TransactionDto.class)))
                .thenReturn(updatedTransaction);

        // When & Then
        mockMvc.perform(
                        put("/api/transactions/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantity").value(20))
                .andExpect(jsonPath("$.price").value(160.00));

        verify(transactionService).updateTransaction(eq(1L), any(TransactionDto.class));
    }

    @Test
    @DisplayName("거래 삭제")
    void deleteTransaction() throws Exception {
        // Given
        doNothing().when(transactionService).deleteTransaction(1L);

        // When & Then
        mockMvc.perform(delete("/api/transactions/1")).andExpect(status().isNoContent());

        verify(transactionService).deleteTransaction(1L);
    }
}
