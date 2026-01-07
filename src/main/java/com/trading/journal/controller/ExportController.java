package com.trading.journal.controller;

import com.trading.journal.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 데이터 내보내기 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "데이터 내보내기 API")
public class ExportController {

    private final ExportService exportService;

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";

    /**
     * 거래 내역 Excel 내보내기
     */
    @GetMapping("/transactions/excel")
    @Operation(summary = "거래 내역 Excel", description = "거래 내역을 Excel 파일로 다운로드")
    public ResponseEntity<byte[]> exportTransactionsToExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("거래 내역 Excel 내보내기: {} ~ {}", startDate, endDate);

        byte[] data = exportService.exportTransactionsToExcel(startDate, endDate);
        String filename = generateFilename("transactions", "xlsx");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 거래 내역 CSV 내보내기
     */
    @GetMapping("/transactions/csv")
    @Operation(summary = "거래 내역 CSV", description = "거래 내역을 CSV 파일로 다운로드")
    public ResponseEntity<byte[]> exportTransactionsToCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("거래 내역 CSV 내보내기: {} ~ {}", startDate, endDate);

        byte[] data = exportService.exportTransactionsToCsv(startDate, endDate);
        String filename = generateFilename("transactions", "csv");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 포트폴리오 분석 Excel 내보내기
     */
    @GetMapping("/portfolio/excel")
    @Operation(summary = "포트폴리오 분석 Excel", description = "포트폴리오 분석 데이터를 Excel 파일로 다운로드")
    public ResponseEntity<byte[]> exportPortfolioToExcel() {
        log.info("포트폴리오 분석 Excel 내보내기");

        byte[] data = exportService.exportPortfolioAnalysisToExcel();
        String filename = generateFilename("portfolio_analysis", "xlsx");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 목표 현황 Excel 내보내기
     */
    @GetMapping("/goals/excel")
    @Operation(summary = "목표 현황 Excel", description = "목표 현황을 Excel 파일로 다운로드")
    public ResponseEntity<byte[]> exportGoalsToExcel() {
        log.info("목표 현황 Excel 내보내기");

        byte[] data = exportService.exportGoalsToExcel();
        String filename = generateFilename("goals", "xlsx");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 종합 리포트 Excel 내보내기
     */
    @GetMapping("/full-report/excel")
    @Operation(summary = "종합 리포트 Excel", description = "모든 데이터를 포함한 종합 리포트 Excel 다운로드")
    public ResponseEntity<byte[]> exportFullReportToExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("종합 리포트 Excel 내보내기: {} ~ {}", startDate, endDate);

        byte[] data = exportService.exportFullReportToExcel(startDate, endDate);
        String filename = generateFilename("full_report", "xlsx");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 파일명 생성
     */
    private String generateFilename(String prefix, String extension) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s_%s.%s", prefix, dateStr, extension);
    }
}
