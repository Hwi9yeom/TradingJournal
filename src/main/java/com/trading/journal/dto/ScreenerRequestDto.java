package com.trading.journal.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerRequestDto {

    // Valuation filters
    private BigDecimal minPe;
    private BigDecimal maxPe;
    private BigDecimal minPb;
    private BigDecimal maxPb;
    private BigDecimal minPeg;
    private BigDecimal maxPeg;

    // Profitability filters
    private BigDecimal minRoe;
    private BigDecimal maxRoe;
    private BigDecimal minRoa;
    private BigDecimal minProfitMargin;

    // Dividend filters
    private BigDecimal minDividendYield;
    private BigDecimal maxDividendYield;

    // Size filters
    private BigDecimal minMarketCap;
    private BigDecimal maxMarketCap;

    // Growth filters
    private BigDecimal minRevenueGrowth;
    private BigDecimal minEarningsGrowth;

    // Debt filters
    private BigDecimal maxDebtToEquity;
    private BigDecimal minCurrentRatio;

    // Category filters
    private List<String> sectors;
    private List<String> industries;
    private List<String> exchanges;

    // Pagination
    @Builder.Default private Integer page = 0;

    @Builder.Default private Integer size = 50;

    // Sorting
    private String sortBy;
    private String sortDirection;
}
