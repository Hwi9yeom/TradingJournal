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
public class PortfolioTreemapDto {
    private List<TreemapCell> cells;
    private String period;
    private LocalDateTime lastUpdated;
    private BigDecimal totalInvestment;
    private BigDecimal totalPerformance;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TreemapCell {
        private String symbol;
        private String name;
        private BigDecimal investmentAmount;
        private BigDecimal performancePercent;
        private BigDecimal currentPrice;
        private BigDecimal priceChange;
        private String sector;
        private Boolean hasData;
    }
}
