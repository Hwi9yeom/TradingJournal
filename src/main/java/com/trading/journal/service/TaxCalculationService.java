package com.trading.journal.service;

import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxCalculationService {
    
    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;
    
    // 한국 주식 양도소득세율 (2024년 기준)
    private static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000"); // 기본공제 250만원
    private static final BigDecimal TAX_RATE = new BigDecimal("0.22"); // 세율 22% (지방소득세 포함)
    
    public TaxCalculationDto calculateTax(Integer year) {
        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);
        
        List<Transaction> yearTransactions = transactionRepository.findByDateRange(startDate, endDate);
        
        // 매도 거래만 필터링
        List<Transaction> sellTransactions = yearTransactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .collect(Collectors.toList());
        
        if (sellTransactions.isEmpty()) {
            return buildEmptyTaxCalculation(year);
        }
        
        // 종목별로 그룹화
        Map<Long, List<Transaction>> transactionsByStock = yearTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getStock().getId()));
        
        List<TaxCalculationDto.TaxDetailDto> taxDetails = new ArrayList<>();
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        
        // 각 종목별로 세금 계산
        for (Transaction sellTransaction : sellTransactions) {
            TaxCalculationDto.TaxDetailDto detail = calculateTaxDetail(
                    sellTransaction, 
                    transactionsByStock.get(sellTransaction.getStock().getId()));
            
            if (detail != null) {
                taxDetails.add(detail);
                if (detail.getProfit().compareTo(BigDecimal.ZERO) > 0) {
                    totalProfit = totalProfit.add(detail.getProfit());
                } else {
                    totalLoss = totalLoss.add(detail.getLoss());
                }
            }
        }
        
        // 순이익 계산 (이익 - 손실)
        BigDecimal netProfit = totalProfit.subtract(totalLoss);
        
        // 과세표준 계산 (순이익 - 기본공제)
        BigDecimal taxableAmount = netProfit.subtract(BASIC_DEDUCTION);
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            taxableAmount = BigDecimal.ZERO;
        }
        
        // 예상 세금 계산
        BigDecimal estimatedTax = taxableAmount.multiply(TAX_RATE)
                .setScale(0, RoundingMode.HALF_UP);
        
        // 총 매도금액 계산
        BigDecimal totalSellAmount = sellTransactions.stream()
                .map(Transaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return TaxCalculationDto.builder()
                .taxYear(year)
                .totalSellAmount(totalSellAmount)
                .totalBuyAmount(calculateTotalBuyAmount(taxDetails))
                .totalProfit(totalProfit)
                .totalLoss(totalLoss)
                .netProfit(netProfit)
                .taxableAmount(taxableAmount)
                .estimatedTax(estimatedTax)
                .taxRate(TAX_RATE.multiply(new BigDecimal("100")))
                .taxDetails(taxDetails)
                .build();
    }
    
    private TaxCalculationDto.TaxDetailDto calculateTaxDetail(
            Transaction sellTransaction, List<Transaction> stockTransactions) {
        
        // FIFO 방식으로 매수 거래 매칭
        List<Transaction> buyTransactions = stockTransactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .filter(t -> t.getTransactionDate().isBefore(sellTransaction.getTransactionDate()))
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());
        
        if (buyTransactions.isEmpty()) {
            log.warn("No matching buy transaction found for sell transaction: {}", 
                    sellTransaction.getId());
            return null;
        }
        
        // 간단한 구현: 평균 매수가 사용
        BigDecimal totalBuyQuantity = buyTransactions.stream()
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal totalBuyAmount = buyTransactions.stream()
                .map(Transaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        if (totalBuyQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        
        BigDecimal avgBuyPrice = totalBuyAmount.divide(totalBuyQuantity, 2, RoundingMode.HALF_UP);
        BigDecimal buyAmount = avgBuyPrice.multiply(sellTransaction.getQuantity());
        BigDecimal sellAmount = sellTransaction.getTotalAmount();
        
        BigDecimal profitLoss = sellAmount.subtract(buyAmount);
        
        // 첫 매수일로부터 보유 기간 계산
        LocalDate firstBuyDate = buyTransactions.get(0).getTransactionDate().toLocalDate();
        LocalDate sellDate = sellTransaction.getTransactionDate().toLocalDate();
        long holdingDays = ChronoUnit.DAYS.between(firstBuyDate, sellDate);
        boolean isLongTerm = holdingDays >= 365;
        
        return TaxCalculationDto.TaxDetailDto.builder()
                .stockSymbol(sellTransaction.getStock().getSymbol())
                .stockName(sellTransaction.getStock().getName())
                .buyDate(firstBuyDate)
                .sellDate(sellDate)
                .buyAmount(buyAmount)
                .sellAmount(sellAmount)
                .profit(profitLoss.compareTo(BigDecimal.ZERO) > 0 ? profitLoss : BigDecimal.ZERO)
                .loss(profitLoss.compareTo(BigDecimal.ZERO) < 0 ? profitLoss.abs() : BigDecimal.ZERO)
                .isLongTerm(isLongTerm)
                .taxAmount(calculateIndividualTax(profitLoss))
                .build();
    }
    
    private BigDecimal calculateIndividualTax(BigDecimal profit) {
        if (profit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 개별 거래에 대한 세금은 전체 세금 계산 시 통합 계산되므로
        // 여기서는 단순 참고용으로만 계산
        return profit.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateTotalBuyAmount(List<TaxCalculationDto.TaxDetailDto> details) {
        return details.stream()
                .map(TaxCalculationDto.TaxDetailDto::getBuyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private TaxCalculationDto buildEmptyTaxCalculation(Integer year) {
        return TaxCalculationDto.builder()
                .taxYear(year)
                .totalSellAmount(BigDecimal.ZERO)
                .totalBuyAmount(BigDecimal.ZERO)
                .totalProfit(BigDecimal.ZERO)
                .totalLoss(BigDecimal.ZERO)
                .netProfit(BigDecimal.ZERO)
                .taxableAmount(BigDecimal.ZERO)
                .estimatedTax(BigDecimal.ZERO)
                .taxRate(TAX_RATE.multiply(new BigDecimal("100")))
                .taxDetails(new ArrayList<>())
                .build();
    }
}