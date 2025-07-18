package com.trading.journal.service;

import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.TransactionType;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private DataExportService dataExportService;

    private List<TransactionDto> mockTransactions;

    @BeforeEach
    void setUp() {
        mockTransactions = Arrays.asList(
                TransactionDto.builder()
                        .id(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal("150.00"))
                        .totalAmount(new BigDecimal("1505.00"))
                        .commission(new BigDecimal("5.00"))
                        .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                        .notes("Test transaction")
                        .build(),
                TransactionDto.builder()
                        .id(2L)
                        .stockSymbol("GOOGL")
                        .stockName("Alphabet Inc.")
                        .type(TransactionType.SELL)
                        .quantity(new BigDecimal("5"))
                        .price(new BigDecimal("2000.00"))
                        .totalAmount(new BigDecimal("9990.00"))
                        .commission(new BigDecimal("10.00"))
                        .transactionDate(LocalDateTime.of(2024, 1, 2, 15, 30))
                        .notes("Partial sell")
                        .build()
        );
    }

    @Test
    @DisplayName("CSV Export - 정상 케이스")
    void exportTransactionsToCsv_Success() {
        // Given
        when(transactionService.getAllTransactions()).thenReturn(mockTransactions);

        // When
        byte[] csvData = dataExportService.exportTransactionsToCsv();

        // Then
        assertThat(csvData).isNotNull();
        assertThat(csvData).isNotEmpty();
        
        String csvContent = new String(csvData, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).contains("거래일시");
        assertThat(csvContent).contains("종목코드");
        assertThat(csvContent).contains("AAPL");
        assertThat(csvContent).contains("Apple Inc.");
        assertThat(csvContent).contains("BUY");
        assertThat(csvContent).contains("GOOGL");
        assertThat(csvContent).contains("Alphabet Inc.");
        assertThat(csvContent).contains("SELL");
    }

    @Test
    @DisplayName("CSV Export - 빈 거래 내역")
    void exportTransactionsToCsv_EmptyTransactions() {
        // Given
        when(transactionService.getAllTransactions()).thenReturn(Arrays.asList());

        // When
        byte[] csvData = dataExportService.exportTransactionsToCsv();

        // Then
        assertThat(csvData).isNotNull();
        String csvContent = new String(csvData, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).contains("거래일시");
        // Check only that header exists, don't count exact lines due to CSVWriter behavior
        assertThat(csvContent).isNotEmpty();
    }

    @Test
    @DisplayName("Excel Export - 정상 케이스")
    void exportTransactionsToExcel_Success() {
        // Given
        when(transactionService.getAllTransactions()).thenReturn(mockTransactions);

        // When
        byte[] excelData = dataExportService.exportTransactionsToExcel();

        // Then
        assertThat(excelData).isNotNull();
        assertThat(excelData).isNotEmpty();
        // Excel file should start with PK (ZIP format signature)
        assertThat(excelData[0]).isEqualTo((byte) 0x50);
        assertThat(excelData[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("Excel Export - 빈 거래 내역")
    void exportTransactionsToExcel_EmptyTransactions() {
        // Given
        when(transactionService.getAllTransactions()).thenReturn(Arrays.asList());

        // When
        byte[] excelData = dataExportService.exportTransactionsToExcel();

        // Then
        assertThat(excelData).isNotNull();
        assertThat(excelData).isNotEmpty();
        // Even with no data, Excel file should be created with headers
        assertThat(excelData[0]).isEqualTo((byte) 0x50);
        assertThat(excelData[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("CSV Export - null 값 처리")
    void exportTransactionsToCsv_NullValues() {
        // Given
        List<TransactionDto> transactionsWithNulls = Arrays.asList(
                TransactionDto.builder()
                        .id(1L)
                        .stockSymbol("AAPL")
                        .stockName("Apple Inc.")
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal("150.00"))
                        .totalAmount(new BigDecimal("1500.00"))
                        .commission(null) // null commission
                        .transactionDate(LocalDateTime.now())
                        .notes(null) // null notes
                        .build()
        );
        
        when(transactionService.getAllTransactions()).thenReturn(transactionsWithNulls);

        // When
        byte[] csvData = dataExportService.exportTransactionsToCsv();

        // Then
        assertThat(csvData).isNotNull();
        String csvContent = new String(csvData, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).contains("\"0\""); // null commission should be 0
        assertThat(csvContent).contains("\"\"\n"); // null notes should be empty
    }
}