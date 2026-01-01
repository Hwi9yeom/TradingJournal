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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
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
        stats.put("avgHoldingPeriod", calculateAverageHoldingPeriod(allTransactions));
        stats.put("winRate", winRate);
        stats.put("avgReturn", avgReturn);
        stats.put("maxReturn", maxReturnPercent == Double.MIN_VALUE ? 0 : maxReturnPercent);
        stats.put("sharpeRatio", calculateSharpeRatio(allTransactions));
        stats.put("maxDrawdown", calculateMaxDrawdown(allTransactions));
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/asset-history")
    public ResponseEntity<Map<String, Object>> getAssetHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting asset history from {} to {}", startDate, endDate);
        
        List<Transaction> transactions = transactionRepository.findByDateRange(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        // 일별 포트폴리오 가치 계산
        Map<LocalDate, BigDecimal> dailyValues = new TreeMap<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        
        // 날짜순으로 정렬
        transactions.sort((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()));
        
        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate().toLocalDate();
            
            if (transaction.getType() == TransactionType.BUY) {
                runningBalance = runningBalance.add(transaction.getTotalAmount());
            } else {
                runningBalance = runningBalance.subtract(transaction.getTotalAmount());
            }
            
            dailyValues.put(date, runningBalance);
        }
        
        // 빈 날짜 채우기 (마지막 값으로)
        LocalDate current = startDate;
        BigDecimal lastValue = BigDecimal.ZERO;
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        
        while (!current.isAfter(endDate)) {
            if (dailyValues.containsKey(current)) {
                lastValue = dailyValues.get(current);
            }
            
            labels.add(current.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            values.add(lastValue);
            current = current.plusDays(1);
        }
        
        Map<String, Object> history = new HashMap<>();
        history.put("labels", labels);
        history.put("values", values);
        
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/monthly-returns")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyReturns() {
        log.info("Getting monthly returns");
        
        List<Transaction> allTransactions = transactionRepository.findAll();
        Map<String, BigDecimal> monthlyReturns = new HashMap<>();
        Map<String, BigDecimal> monthlyInvestment = new HashMap<>();
        
        // 월별로 거래 그룹화
        Map<String, List<Transaction>> monthlyTransactions = allTransactions.stream()
                .collect(Collectors.groupingBy(t -> 
                        t.getTransactionDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))));
        
        for (Map.Entry<String, List<Transaction>> entry : monthlyTransactions.entrySet()) {
            String month = entry.getKey();
            List<Transaction> transactions = entry.getValue();
            
            BigDecimal monthlyBuy = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
            BigDecimal monthlySell = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal monthlyReturn = monthlySell.subtract(monthlyBuy);
            BigDecimal monthlyReturnRate = BigDecimal.ZERO;
            
            if (monthlyBuy.compareTo(BigDecimal.ZERO) > 0) {
                monthlyReturnRate = monthlyReturn.divide(monthlyBuy, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            monthlyReturns.put(month, monthlyReturnRate);
            monthlyInvestment.put(month, monthlyBuy);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : monthlyReturns.entrySet()) {
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", entry.getKey());
            monthData.put("returnRate", entry.getValue());
            monthData.put("investment", monthlyInvestment.get(entry.getKey()));
            result.add(monthData);
        }
        
        // 월별로 정렬
        result.sort((a, b) -> ((String) a.get("month")).compareTo((String) b.get("month")));
        
        return ResponseEntity.ok(result);
    }
    
    private double calculateAverageHoldingPeriod(List<Transaction> transactions) {
        Map<String, List<Transaction>> stockTransactions = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));
        
        long totalDays = 0;
        int completedTrades = 0;
        
        for (List<Transaction> stockTxs : stockTransactions.values()) {
            List<Transaction> buys = stockTxs.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
                    .collect(Collectors.toList());
                    
            List<Transaction> sells = stockTxs.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
                    .collect(Collectors.toList());
            
            // 매수-매도 매칭 (FIFO)
            for (Transaction sell : sells) {
                for (Transaction buy : buys) {
                    if (buy.getTransactionDate().isBefore(sell.getTransactionDate())) {
                        long days = ChronoUnit.DAYS.between(
                                buy.getTransactionDate().toLocalDate(),
                                sell.getTransactionDate().toLocalDate());
                        totalDays += days;
                        completedTrades++;
                        break;
                    }
                }
            }
        }
        
        return completedTrades > 0 ? (double) totalDays / completedTrades : 0;
    }
    
    private double calculateSharpeRatio(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;
        
        // 월별 수익률 계산
        Map<String, List<Transaction>> monthlyTransactions = transactions.stream()
                .collect(Collectors.groupingBy(t -> 
                        t.getTransactionDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))));
        
        List<Double> monthlyReturns = new ArrayList<>();
        
        for (List<Transaction> monthTxs : monthlyTransactions.values()) {
            BigDecimal monthlyBuy = monthTxs.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
            BigDecimal monthlySell = monthTxs.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (monthlyBuy.compareTo(BigDecimal.ZERO) > 0) {
                double returnRate = monthlySell.subtract(monthlyBuy)
                        .divide(monthlyBuy, 4, RoundingMode.HALF_UP)
                        .doubleValue();
                monthlyReturns.add(returnRate);
            }
        }
        
        if (monthlyReturns.size() < 2) return 0;
        
        // 평균 수익률
        double avgReturn = monthlyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        // 표준편차 계산
        double variance = monthlyReturns.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        // 샤프 비율 = (평균 수익률 - 무위험 수익률) / 표준편차
        // 무위험 수익률을 0.02/12 (연 2%)로 가정
        double riskFreeRate = 0.02 / 12;
        
        return stdDev != 0 ? (avgReturn - riskFreeRate) / stdDev : 0;
    }
    
    private double calculateMaxDrawdown(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;
        
        // 일별 누적 수익률 계산
        transactions.sort((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()));
        
        BigDecimal runningValue = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        double maxDrawdown = 0;
        
        for (Transaction transaction : transactions) {
            if (transaction.getType() == TransactionType.BUY) {
                runningValue = runningValue.add(transaction.getTotalAmount());
            } else {
                runningValue = runningValue.subtract(transaction.getTotalAmount());
            }
            
            if (runningValue.compareTo(peak) > 0) {
                peak = runningValue;
            }
            
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peak.subtract(runningValue);
                double drawdownPercent = drawdown.divide(peak, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                maxDrawdown = Math.max(maxDrawdown, drawdownPercent);
            }
        }
        
        return maxDrawdown;
    }
}