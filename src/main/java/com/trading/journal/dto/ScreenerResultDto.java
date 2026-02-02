package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerResultDto {

    private List<StockResult> stocks;
    private Integer totalResults;
    private Integer page;
    private Integer totalPages;
    private FilterSummary appliedFilters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockResult {
        private Long id;
        private String symbol;
        private String companyName;
        private String exchange;
        private String sector;
        private String industry;
        private BigDecimal peRatio;
        private BigDecimal pbRatio;
        private BigDecimal roe;
        private BigDecimal dividendYield;
        private BigDecimal marketCap;
        private BigDecimal revenueGrowth;
        private BigDecimal earningsGrowth;
        private BigDecimal debtToEquity;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterSummary {
        private Integer filterCount;
        private List<String> activeFilters;
    }
}
