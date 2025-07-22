package com.trading.journal.controller;

import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.service.AnalysisService;
import com.trading.journal.service.StockAnalysisService;
import com.trading.journal.service.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalysisController {
    
    private final AnalysisService analysisService;
    private final StockAnalysisService stockAnalysisService;
    private final TaxCalculationService taxCalculationService;
    
    @GetMapping("/period")
    public ResponseEntity<PeriodAnalysisDto> analyzePeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/period/year/{year}")
    public ResponseEntity<PeriodAnalysisDto> analyzeYear(@PathVariable Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/period/month/{year}/{month}")
    public ResponseEntity<PeriodAnalysisDto> analyzeMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/stock/{symbol}")
    public ResponseEntity<StockAnalysisDto> analyzeStock(@PathVariable String symbol) {
        StockAnalysisDto analysis = stockAnalysisService.analyzeStock(symbol.toUpperCase());
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/tax/{year}")
    public ResponseEntity<TaxCalculationDto> calculateTax(@PathVariable Integer year) {
        if (year < 2000 || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("유효하지 않은 연도입니다");
        }
        
        TaxCalculationDto taxCalculation = taxCalculationService.calculateTax(year);
        return ResponseEntity.ok(taxCalculation);
    }
    
    @GetMapping("/tax/current")
    public ResponseEntity<TaxCalculationDto> calculateCurrentYearTax() {
        Integer currentYear = LocalDate.now().getYear();
        TaxCalculationDto taxCalculation = taxCalculationService.calculateTax(currentYear);
        return ResponseEntity.ok(taxCalculation);
    }
}