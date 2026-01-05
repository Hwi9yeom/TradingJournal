package com.trading.journal.controller;

import com.trading.journal.service.PdfReportService;
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
 * 리포트 생성 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "리포트 생성 API")
public class ReportController {

    private final PdfReportService pdfReportService;

    /**
     * 포트폴리오 성과 PDF 리포트 생성
     */
    @GetMapping("/portfolio/pdf")
    @Operation(summary = "포트폴리오 PDF 리포트 생성", description = "지정된 기간의 포트폴리오 성과를 PDF 리포트로 생성합니다")
    public ResponseEntity<byte[]> generatePortfolioReport(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("PDF 리포트 생성 요청: accountId={}, period={} ~ {}", accountId, startDate, endDate);

        // 유효성 검증
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일이 종료일보다 늦을 수 없습니다");
        }

        byte[] pdfBytes = pdfReportService.generatePortfolioReport(accountId, startDate, endDate);

        String filename = String.format("portfolio_report_%s_%s.pdf",
                startDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                endDate.format(DateTimeFormatter.BASIC_ISO_DATE));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * 월간 포트폴리오 PDF 리포트 생성
     */
    @GetMapping("/portfolio/pdf/monthly")
    @Operation(summary = "월간 PDF 리포트 생성", description = "지정된 월의 포트폴리오 성과를 PDF 리포트로 생성합니다")
    public ResponseEntity<byte[]> generateMonthlyReport(
            @RequestParam(required = false) Long accountId,
            @RequestParam int year,
            @RequestParam int month) {

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        return generatePortfolioReport(accountId, startDate, endDate);
    }

    /**
     * 연간 포트폴리오 PDF 리포트 생성
     */
    @GetMapping("/portfolio/pdf/yearly")
    @Operation(summary = "연간 PDF 리포트 생성", description = "지정된 연도의 포트폴리오 성과를 PDF 리포트로 생성합니다")
    public ResponseEntity<byte[]> generateYearlyReport(
            @RequestParam(required = false) Long accountId,
            @RequestParam int year) {

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return generatePortfolioReport(accountId, startDate, endDate);
    }

    /**
     * YTD (Year-to-Date) PDF 리포트 생성
     */
    @GetMapping("/portfolio/pdf/ytd")
    @Operation(summary = "YTD PDF 리포트 생성", description = "올해 1월 1일부터 현재까지의 포트폴리오 성과를 PDF 리포트로 생성합니다")
    public ResponseEntity<byte[]> generateYtdReport(
            @RequestParam(required = false) Long accountId) {

        LocalDate startDate = LocalDate.now().withDayOfYear(1);
        LocalDate endDate = LocalDate.now();

        return generatePortfolioReport(accountId, startDate, endDate);
    }
}
