package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAnalysisDto {
    private String stockSymbol;
    private String stockName;
    private Integer totalBuyCount;
    private Integer totalSellCount;
    private BigDecimal totalBuyQuantity;
    private BigDecimal totalSellQuantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal averageSellPrice;
    private BigDecimal realizedProfit;
    private BigDecimal realizedProfitRate;
    private BigDecimal currentHolding;
    private BigDecimal currentValue;
    private BigDecimal unrealizedProfit;
    private BigDecimal unrealizedProfitRate;
    private LocalDateTime firstTransactionDate;
    private LocalDateTime lastTransactionDate;
    private Integer holdingDays;
    private List<TradingPatternDto> tradingPatterns;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradingPatternDto {
        private String pattern; // e.g., "매수 후 평균 3일 보유", "손절매 비율 20%"
        private String value;
        private String description;
    }
}