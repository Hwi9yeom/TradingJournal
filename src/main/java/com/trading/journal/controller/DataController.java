package com.trading.journal.controller;

import com.trading.journal.dto.ImportResultDto;
import com.trading.journal.service.DataExportService;
import com.trading.journal.service.DataImportService;
import com.trading.journal.service.FifoCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Slf4j
public class DataController {

    private final DataImportService dataImportService;
    private final DataExportService dataExportService;
    private final FifoCalculationService fifoCalculationService;
    
    @PostMapping("/import/csv")
    public ResponseEntity<ImportResultDto> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }
        
        ImportResultDto result = dataImportService.importFromCsv(file);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/import/excel")
    public ResponseEntity<ImportResultDto> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }
        
        ImportResultDto result = dataImportService.importFromExcel(file);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csvData = dataExportService.exportTransactionsToCsv();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", 
            "transactions_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }
    
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() {
        byte[] excelData = dataExportService.exportTransactionsToExcel();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", 
            "transactions_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
    
    @GetMapping("/template/csv")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String csvTemplate = "거래일시,종목코드,종목명,거래구분,수량,단가,금액,수수료,세금,비고\n" +
                "2024-01-01,AAPL,Apple Inc.,매수,10,150.00,1500.00,5.00,0,\n" +
                "2024-01-02,GOOGL,Alphabet Inc.,매도,5,2000.00,10000.00,10.00,100.00,부분매도";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "import_template.csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvTemplate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @PostMapping("/migrate/fifo")
    public ResponseEntity<java.util.Map<String, Object>> migrateFifoData() {
        log.info("Starting FIFO migration for all existing transactions");
        try {
            fifoCalculationService.migrateAllExistingSellTransactions();
            log.info("FIFO migration completed successfully");
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "FIFO 마이그레이션이 완료되었습니다"
            ));
        } catch (Exception e) {
            log.error("FIFO migration failed", e);
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                    "success", false,
                    "message", "FIFO 마이그레이션 실패: " + e.getMessage()
            ));
        }
    }
}