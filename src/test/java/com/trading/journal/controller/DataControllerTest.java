package com.trading.journal.controller;

import com.trading.journal.dto.ImportResultDto;
import com.trading.journal.service.DataExportService;
import com.trading.journal.service.DataImportService;
import com.trading.journal.service.FifoCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.trading.journal.config.TestSecurityConfig;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataController.class)
@Import(TestSecurityConfig.class)
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataImportService dataImportService;

    @MockitoBean
    private DataExportService dataExportService;

    @MockitoBean
    private FifoCalculationService fifoCalculationService;

    private ImportResultDto mockImportResult;

    @BeforeEach
    void setUp() {
        mockImportResult = ImportResultDto.builder()
                .totalRows(10)
                .successCount(8)
                .failureCount(2)
                .errors(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("CSV 파일 Import - 성공")
    void importCsv_Success() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                "test data".getBytes()
        );

        when(dataImportService.importFromCsv(any())).thenReturn(mockImportResult);

        // When & Then
        mockMvc.perform(multipart("/api/data/import/csv")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(10))
                .andExpect(jsonPath("$.successCount").value(8))
                .andExpect(jsonPath("$.failureCount").value(2));
    }

    @Test
    @DisplayName("CSV 파일 Import - 빈 파일")
    void importCsv_EmptyFile() throws Exception {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.csv",
                "text/csv",
                new byte[0]
        );

        when(dataImportService.importFromCsv(any())).thenThrow(new IllegalArgumentException("파일이 비어있습니다"));

        // When & Then
        mockMvc.perform(multipart("/api/data/import/csv")
                        .file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Excel 파일 Import - 성공")
    void importExcel_Success() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "test data".getBytes()
        );

        when(dataImportService.importFromExcel(any())).thenReturn(mockImportResult);

        // When & Then
        mockMvc.perform(multipart("/api/data/import/excel")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(10))
                .andExpect(jsonPath("$.successCount").value(8))
                .andExpect(jsonPath("$.failureCount").value(2));
    }

    @Test
    @DisplayName("CSV Export - 성공")
    void exportCsv_Success() throws Exception {
        // Given
        byte[] csvData = "test,csv,data".getBytes(StandardCharsets.UTF_8);
        when(dataExportService.exportTransactionsToCsv()).thenReturn(csvData);

        // When & Then
        mockMvc.perform(get("/api/data/export/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", 
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", 
                        org.hamcrest.Matchers.containsString("transactions_")))
                .andExpect(content().bytes(csvData));
    }

    @Test
    @DisplayName("Excel Export - 성공")
    void exportExcel_Success() throws Exception {
        // Given
        byte[] excelData = new byte[]{0x50, 0x4B, 0x03, 0x04}; // Excel file signature
        when(dataExportService.exportTransactionsToExcel()).thenReturn(excelData);

        // When & Then
        mockMvc.perform(get("/api/data/export/excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", 
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", 
                        org.hamcrest.Matchers.containsString("transactions_")))
                .andExpect(content().bytes(excelData));
    }

    @Test
    @DisplayName("CSV 템플릿 다운로드")
    void downloadCsvTemplate() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/data/template/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", 
                        org.hamcrest.Matchers.containsString("import_template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("거래일시,종목코드")));
    }
}