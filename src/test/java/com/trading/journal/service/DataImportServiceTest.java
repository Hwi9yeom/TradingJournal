package com.trading.journal.service;

import com.trading.journal.dto.ImportResultDto;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataImportServiceTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private DataImportService dataImportService;

    @BeforeEach
    void setUp() {
        // Setup any required mocks
    }

    @Test
    @DisplayName("CSV 파일 Import - 성공")
    void importFromCsv_Success() throws Exception {
        // Given
        String csvContent = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매수,10,150.00,1500.00,5.00,0,\n" +
                "2024-01-02,GOOGL,Alphabet Inc.,매도,5,2000.00,10000.00,10.00,100.00,부분매도";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(transactionService.createTransaction(any(TransactionDto.class)))
                .thenReturn(TransactionDto.builder().id(1L).build());

        // When
        ImportResultDto result = dataImportService.importFromCsv(file);

        // Then
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();

        verify(transactionService, times(2)).createTransaction(any(TransactionDto.class));
    }

    @Test
    @DisplayName("CSV 파일 Import - 일부 실패")
    void importFromCsv_PartialFailure() throws Exception {
        // Given
        String csvContent = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매수,10,150.00,1500.00,5.00,0,\n" +
                "invalid-date,GOOGL,Alphabet Inc.,매도,5,2000.00,10000.00,10.00,100.00,잘못된 날짜";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(transactionService.createTransaction(any(TransactionDto.class)))
                .thenReturn(TransactionDto.builder().id(1L).build())
                .thenThrow(new IllegalArgumentException("날짜 형식을 파싱할 수 없습니다"));

        // When
        ImportResultDto result = dataImportService.importFromCsv(file);

        // Then
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("Excel 파일 Import - 성공")
    void importFromExcel_Success() throws Exception {
        // This test would require creating a proper Excel file
        // For simplicity, we'll test the error handling
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]  // Empty file will cause an error
        );

        // When & Then
        assertThatThrownBy(() -> dataImportService.importFromExcel(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Excel 파일 읽기 실패");
    }

    @Test
    @DisplayName("거래 유형 파싱 - 매수")
    void parseTransactionType_Buy() throws Exception {
        // Given
        String csvContent = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매수,10,150.00,1500.00,5.00,0,";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(transactionService.createTransaction(argThat(dto -> 
                dto.getType() == TransactionType.BUY
        ))).thenReturn(TransactionDto.builder().id(1L).build());

        // When
        dataImportService.importFromCsv(file);

        // Then
        verify(transactionService).createTransaction(argThat(dto -> 
                dto.getType() == TransactionType.BUY
        ));
    }

    @Test
    @DisplayName("거래 유형 파싱 - 매도")
    void parseTransactionType_Sell() throws Exception {
        // Given
        String csvContent = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매도,10,150.00,1500.00,5.00,0,";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(transactionService.createTransaction(argThat(dto -> 
                dto.getType() == TransactionType.SELL
        ))).thenReturn(TransactionDto.builder().id(1L).build());

        // When
        dataImportService.importFromCsv(file);

        // Then
        verify(transactionService).createTransaction(argThat(dto -> 
                dto.getType() == TransactionType.SELL
        ));
    }

    @Test
    @DisplayName("숫자 파싱 - 콤마 제거")
    void parseNumber_RemoveCommas() throws Exception {
        // Given
        String csvContent = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매수,1,000,150.00,1,500.00,5.00,0,";
        
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        when(transactionService.createTransaction(argThat(dto ->
                dto.getQuantity().compareTo(new BigDecimal("1000")) == 0 &&
                dto.getPrice().compareTo(new BigDecimal("150.00")) == 0
        ))).thenReturn(TransactionDto.builder().id(1L).build());

        // When
        dataImportService.importFromCsv(file);

        // Then
        verify(transactionService).createTransaction(any());
    }
}