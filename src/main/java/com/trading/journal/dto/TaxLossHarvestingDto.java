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
public class TaxLossHarvestingDto {

    private Long accountId;
    private LocalDateTime generatedAt;

    // Summary
    private Integer totalOpportunities;
    private BigDecimal totalUnrealizedLoss;
    private BigDecimal totalPotentialTaxSavings;

    // Thresholds used
    private BigDecimal minimumLossThreshold;
    private BigDecimal basicDeduction;
    private BigDecimal taxRate;

    // Opportunities list
    private List<HarvestingOpportunityDto> opportunities;

    // Wash sale warnings
    private Integer washSaleRiskCount;
    private List<HarvestingOpportunityDto> washSaleRiskPositions;

    // Recommendations
    private String recommendation;
    private Boolean hasSignificantOpportunities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxImpactSummary {
        private BigDecimal realizedGainsYTD;
        private BigDecimal realizedLossesYTD;
        private BigDecimal netCapitalGain;
        private BigDecimal estimatedTaxLiability;
        private BigDecimal potentialSavingsFromHarvesting;
    }
}
