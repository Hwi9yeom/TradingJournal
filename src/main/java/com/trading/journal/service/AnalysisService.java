package com.trading.journal.service;

import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalysisService {
    
    private final TransactionRepository transactionRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    
    public PeriodAnalysisDto analyzePeriod(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        
        List<Transaction> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        
        // 기본 통계 계산
        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        int buyCount = 0;
        int sellCount = 0;
        
        for (Transaction transaction : transactions) {
            if (transaction.getType() == TransactionType.BUY) {
                totalBuyAmount = totalBuyAmount.add(transaction.getTotalAmount());
                buyCount++;
            } else {
                totalSellAmount = totalSellAmount.add(transaction.getTotalAmount());
                sellCount++;
            }
        }
        
        // 실현 손익 계산
        BigDecimal realizedProfit = calculateRealizedProfit(transactions);
        BigDecimal realizedProfitRate = BigDecimal.ZERO;
        if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            realizedProfitRate = realizedProfit.divide(totalBuyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        // 미실현 손익 계산 (현재 포트폴리오 기준)
        PortfolioSummaryDto portfolioSummary = portfolioAnalysisService.getPortfolioSummary();
        BigDecimal unrealizedProfit = portfolioSummary.getTotalProfitLoss();
        BigDecimal unrealizedProfitRate = portfolioSummary.getTotalProfitLossPercent();
        
        // 총 손익
        BigDecimal totalProfit = realizedProfit.add(unrealizedProfit);
        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = totalProfit.divide(totalBuyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        // 월별 분석
        List<PeriodAnalysisDto.MonthlyAnalysisDto> monthlyAnalysis = 
                analyzeMonthly(transactions, startDate, endDate);
        
        return PeriodAnalysisDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalBuyAmount(totalBuyAmount)
                .totalSellAmount(totalSellAmount)
                .realizedProfit(realizedProfit)
                .realizedProfitRate(realizedProfitRate)
                .unrealizedProfit(unrealizedProfit)
                .unrealizedProfitRate(unrealizedProfitRate)
                .totalProfit(totalProfit)
                .totalProfitRate(totalProfitRate)
                .totalTransactions(transactions.size())
                .buyTransactions(buyCount)
                .sellTransactions(sellCount)
                .monthlyAnalysis(monthlyAnalysis)
                .build();
    }
    
    private BigDecimal calculateRealizedProfit(List<Transaction> transactions) {
        // 간단한 실현 손익 계산 (FIFO 방식)
        // TODO: 더 정확한 계산을 위해 매수/매도 매칭 알고리즘 구현 필요
        BigDecimal totalSellAmount = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(Transaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal totalSellQuantity = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal averageBuyPrice = calculateAverageBuyPrice(transactions);
        BigDecimal totalBuyCost = averageBuyPrice.multiply(totalSellQuantity);
        
        return totalSellAmount.subtract(totalBuyCost);
    }
    
    private BigDecimal calculateAverageBuyPrice(List<Transaction> transactions) {
        List<Transaction> buyTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .collect(Collectors.toList());
                
        if (buyTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        
        for (Transaction t : buyTransactions) {
            totalAmount = totalAmount.add(t.getTotalAmount());
            totalQuantity = totalQuantity.add(t.getQuantity());
        }
        
        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalAmount.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }
    
    private List<PeriodAnalysisDto.MonthlyAnalysisDto> analyzeMonthly(
            List<Transaction> transactions, LocalDate startDate, LocalDate endDate) {
        
        Map<YearMonth, List<Transaction>> monthlyTransactions = transactions.stream()
                .collect(Collectors.groupingBy(t -> 
                        YearMonth.from(t.getTransactionDate())));
        
        List<PeriodAnalysisDto.MonthlyAnalysisDto> monthlyAnalysis = new ArrayList<>();
        
        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        
        while (!current.isAfter(end)) {
            List<Transaction> monthTransactions = monthlyTransactions.getOrDefault(current, new ArrayList<>());
            
            BigDecimal buyAmount = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
            BigDecimal sellAmount = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal profit = sellAmount.subtract(buyAmount);
            BigDecimal profitRate = BigDecimal.ZERO;
            
            if (buyAmount.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = profit.divide(buyAmount, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            monthlyAnalysis.add(PeriodAnalysisDto.MonthlyAnalysisDto.builder()
                    .yearMonth(current.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .buyAmount(buyAmount)
                    .sellAmount(sellAmount)
                    .profit(profit)
                    .profitRate(profitRate)
                    .transactionCount(monthTransactions.size())
                    .build());
            
            current = current.plusMonths(1);
        }
        
        return monthlyAnalysis;
    }
}