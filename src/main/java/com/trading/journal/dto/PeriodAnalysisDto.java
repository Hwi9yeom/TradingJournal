package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodAnalysisDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalBuyAmount;
    private BigDecimal totalSellAmount;
    private BigDecimal realizedProfit;
    private BigDecimal realizedProfitRate;
    private BigDecimal unrealizedProfit;
    private BigDecimal unrealizedProfitRate;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer totalTransactions;
    private Integer buyTransactions;
    private Integer sellTransactions;
    private List<MonthlyAnalysisDto> monthlyAnalysis;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyAnalysisDto {
        private String yearMonth;
        private BigDecimal buyAmount;
        private BigDecimal sellAmount;
        private BigDecimal profit;
        private BigDecimal profitRate;
        private Integer transactionCount;
    }
}