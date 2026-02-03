package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;

/** 고급 분석 위젯 데이터 DTOs */
public class AdvancedWidgetDto {

    /** 몬테카를로 시뮬레이션 요약 위젯 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonteCarloSummaryWidget {
        private BigDecimal expectedReturn;
        private BigDecimal var95;
        private BigDecimal var99;
        private BigDecimal cvar95;
        private BigDecimal bestCase;
        private BigDecimal worstCase;
        private int simulationCount;
        private int timeHorizonDays;
        private LocalDate lastCalculated;
    }

    /** 몬테카를로 차트 데이터 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonteCarloChartWidget {
        private List<BigDecimal> percentiles; // [5, 25, 50, 75, 95]
        private List<List<BigDecimal>> paths; // Sample paths for visualization
        private Map<String, BigDecimal> distribution; // Histogram buckets
    }

    /** 스트레스 테스트 요약 위젯 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StressTestSummaryWidget {
        private BigDecimal worstScenarioImpact;
        private String worstScenarioName;
        private BigDecimal averageImpact;
        private int scenariosAnalyzed;
        private int criticalScenarios; // Scenarios with >20% loss
        private LocalDate lastCalculated;
    }

    /** 스트레스 테스트 시나리오 목록 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StressTestScenarioItem {
        private String scenarioName;
        private String description;
        private BigDecimal portfolioImpact;
        private BigDecimal impactPercent;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StressTestScenariosWidget {
        private List<StressTestScenarioItem> scenarios;
    }

    /** Tax-Loss Harvesting 기회 위젯 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxHarvestingOpportunityItem {
        private String symbol;
        private String stockName;
        private BigDecimal currentLoss;
        private BigDecimal potentialTaxSavings;
        private boolean washSaleRisk;
        private LocalDate lastPurchaseDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxHarvestingWidget {
        private List<TaxHarvestingOpportunityItem> opportunities;
        private BigDecimal totalPotentialSavings;
        private int opportunityCount;
    }

    /** 스크리너 관심종목 위젯 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenerWatchlistItem {
        private String symbol;
        private String stockName;
        private BigDecimal currentPrice;
        private BigDecimal priceChange;
        private BigDecimal priceChangePercent;
        private String matchedScreen; // Which saved screen matched
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenerWatchlistWidget {
        private List<ScreenerWatchlistItem> watchlist;
        private int totalMatches;
    }
}
