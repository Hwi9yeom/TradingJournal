package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendSummaryDto {
    private BigDecimal totalDividends; // 총 배당금 (세후)
    private BigDecimal totalTax; // 총 납부 세금
    private BigDecimal yearlyDividends; // 올해 배당금
    private BigDecimal monthlyAverage; // 월평균 배당금
    private BigDecimal dividendYield; // 배당 수익률
    private List<StockDividendDto> topDividendStocks; // 배당금 TOP 종목
    private List<MonthlyDividendDto> monthlyDividends; // 월별 배당금
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockDividendDto {
        private String stockSymbol;
        private String stockName;
        private BigDecimal totalDividend;
        private BigDecimal dividendYield;
        private Integer paymentCount;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyDividendDto {
        private Integer year;
        private Integer month;
        private BigDecimal amount;
    }
}