package com.trading.journal.controller;

import com.trading.journal.dto.DividendDto;
import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.service.DividendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
@Slf4j
public class DividendController {
    
    private final DividendService dividendService;
    
    @PostMapping
    public ResponseEntity<DividendDto> createDividend(@RequestBody DividendDto dividendDto) {
        log.info("Creating dividend for stock: {}", dividendDto.getStockSymbol());
        DividendDto created = dividendService.createDividend(dividendDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<DividendDto> updateDividend(@PathVariable Long id, @RequestBody DividendDto dividendDto) {
        log.info("Updating dividend: {}", id);
        DividendDto updated = dividendService.updateDividend(id, dividendDto);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDividend(@PathVariable Long id) {
        log.info("Deleting dividend: {}", id);
        dividendService.deleteDividend(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DividendDto> getDividend(@PathVariable Long id) {
        DividendDto dividend = dividendService.getDividend(id);
        return ResponseEntity.ok(dividend);
    }
    
    @GetMapping
    public ResponseEntity<List<DividendDto>> getDividends(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<DividendDto> dividends;
        
        if (symbol != null) {
            log.info("Getting dividends for stock: {}", symbol);
            dividends = dividendService.getDividendsByStock(symbol);
        } else if (startDate != null && endDate != null) {
            log.info("Getting dividends between {} and {}", startDate, endDate);
            dividends = dividendService.getDividendsByPeriod(startDate, endDate);
        } else {
            // 기본적으로 최근 1년 배당금 조회
            LocalDate now = LocalDate.now();
            LocalDate oneYearAgo = now.minusYears(1);
            dividends = dividendService.getDividendsByPeriod(oneYearAgo, now);
        }
        
        return ResponseEntity.ok(dividends);
    }
    
    @GetMapping("/summary")
    public ResponseEntity<DividendSummaryDto> getDividendSummary() {
        log.info("Getting dividend summary");
        DividendSummaryDto summary = dividendService.getDividendSummary();
        return ResponseEntity.ok(summary);
    }
}