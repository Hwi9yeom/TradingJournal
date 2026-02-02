package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarvestingOpportunityDto {

    private Long stockId;
    private String symbol;
    private String stockName;
    private String sector;

    // Position details
    private Integer quantity;
    private BigDecimal costBasis;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;

    // Loss calculation
    private BigDecimal unrealizedLoss;
    private BigDecimal unrealizedLossPercent;

    // Tax impact
    private BigDecimal potentialTaxSavings;
    private BigDecimal effectiveTaxRate;

    // Wash sale risk
    private Boolean washSaleRisk;
    private LocalDate lastSaleDate;
    private Integer daysUntilWashSaleClears;

    // Holding period
    private LocalDate purchaseDate;
    private Integer holdingDays;
    private Boolean isLongTerm;
}
