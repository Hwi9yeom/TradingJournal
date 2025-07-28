package com.trading.journal.controller;

import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.service.AnalysisService;
import com.trading.journal.service.StockAnalysisService;
import com.trading.journal.service.TaxCalculationService;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AnalysisController {
    
    private final AnalysisService analysisService;
    private final StockAnalysisService stockAnalysisService;
    private final TaxCalculationService taxCalculationService;
    private final TransactionRepository transactionRepository;
    
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
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTradeStatistics() {
        log.info("Getting trade statistics");
        Map<String, Object> stats = new HashMap<>();
        
        // 전체 거래 통계 계산
        List<Transaction> allTransactions = transactionRepository.findAll();
        long totalTrades = allTransactions.size();
        long uniqueStocks = allTransactions.stream()
                .map(Transaction::getStock)
                .distinct()
                .count();
        
        // 실현 손익 거래 분석
        List<Transaction> sellTransactions = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .collect(Collectors.toList());
        
        long winningTrades = 0;
        double totalReturnPercent = 0;
        double maxReturnPercent = Double.MIN_VALUE;
        
        for (Transaction sellTx : sellTransactions) {
            // 해당 종목의 매수 평균가 계산
            List<Transaction> buyTxs = allTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .filter(t -> t.getStock().equals(sellTx.getStock()))
                    .filter(t -> t.getTransactionDate().isBefore(sellTx.getTransactionDate()))
                    .collect(Collectors.toList());
            
            if (!buyTxs.isEmpty()) {
                BigDecimal avgBuyPrice = buyTxs.stream()
                        .map(Transaction::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(buyTxs.size()), 2, RoundingMode.HALF_UP);
                
                double returnPercent = sellTx.getPrice().subtract(avgBuyPrice)
                        .divide(avgBuyPrice, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .doubleValue();
                
                if (returnPercent > 0) winningTrades++;
                totalReturnPercent += returnPercent;
                maxReturnPercent = Math.max(maxReturnPercent, returnPercent);
            }
        }
        
        double winRate = sellTransactions.isEmpty() ? 0 : 
                (double) winningTrades / sellTransactions.size() * 100;
        double avgReturn = sellTransactions.isEmpty() ? 0 : 
                totalReturnPercent / sellTransactions.size();
        
        stats.put("totalTrades", totalTrades);
        stats.put("uniqueStocks", uniqueStocks);
        stats.put("avgHoldingPeriod", 0); // TODO: 평균 보유 기간 계산
        stats.put("winRate", winRate);
        stats.put("avgReturn", avgReturn);
        stats.put("maxReturn", maxReturnPercent == Double.MIN_VALUE ? 0 : maxReturnPercent);
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/asset-history")
    public ResponseEntity<Map<String, Object>> getAssetHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting asset history from {} to {}", startDate, endDate);
        
        // TODO: 실제 자산 가치 히스토리 계산 로직 구현
        Map<String, Object> history = new HashMap<>();
        history.put("labels", new ArrayList<>());
        history.put("values", new ArrayList<>());
        
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/monthly-returns")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyReturns() {
        log.info("Getting monthly returns");
        
        // TODO: 실제 월별 수익률 계산 로직 구현
        List<Map<String, Object>> monthlyReturns = new ArrayList<>();
        
        return ResponseEntity.ok(monthlyReturns);
    }
}