package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationDto {
    private Integer taxYear;
    private BigDecimal totalSellAmount;
    private BigDecimal totalBuyAmount;
    private BigDecimal totalProfit;
    private BigDecimal totalLoss;
    private BigDecimal netProfit;
    private BigDecimal taxableAmount;
    private BigDecimal estimatedTax;
    private BigDecimal taxRate;
    private List<TaxDetailDto> taxDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaxDetailDto {
        private String stockSymbol;
        private String stockName;
        private LocalDate buyDate;
        private LocalDate sellDate;
        private BigDecimal buyAmount;
        private BigDecimal sellAmount;
        private BigDecimal profit;
        private BigDecimal loss;
        private Boolean isLongTerm; // 1년 이상 보유 여부
        private BigDecimal taxAmount;
    }
}
