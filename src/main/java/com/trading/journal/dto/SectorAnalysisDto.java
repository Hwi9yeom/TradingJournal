package com.trading.journal.dto;

import com.trading.journal.entity.Sector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 섹터별 분석 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorAnalysisDto {

    /** 분석 시작일 */
    private LocalDate startDate;

    /** 분석 종료일 */
    private LocalDate endDate;

    /** 현재 섹터별 배분 */
    private List<SectorAllocation> currentAllocation;

    /** 섹터별 성과 */
    private List<SectorPerformance> sectorPerformance;

    /** 섹터 로테이션 히스토리 */
    private List<SectorRotation> rotationHistory;

    /** 전체 종목 수 */
    private Integer totalStocks;

    /** 섹터 분류된 종목 수 */
    private Integer classifiedStocks;

    /** 미분류 종목 수 */
    private Integer unclassifiedStocks;

    /** 최고 성과 섹터 */
    private Sector topPerformingSector;

    /** 최저 성과 섹터 */
    private Sector worstPerformingSector;

    /** 섹터 분산도 (HHI) */
    private BigDecimal sectorConcentrationIndex;

    /** 섹터 분산 평가 */
    private String diversificationRating;

    /**
     * 섹터별 현재 배분
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorAllocation {
        private Sector sector;
        private String sectorLabel;
        private BigDecimal value;
        private BigDecimal weight;
        private Integer stockCount;
        private List<StockInSector> stocks;
    }

    /**
     * 섹터 내 종목 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockInSector {
        private String symbol;
        private String name;
        private BigDecimal currentValue;
        private BigDecimal profitLoss;
        private BigDecimal profitLossPercent;
    }

    /**
     * 섹터별 성과
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorPerformance {
        private Sector sector;
        private String sectorLabel;
        private BigDecimal totalReturn;
        private BigDecimal realizedPnl;
        private BigDecimal unrealizedPnl;
        private BigDecimal contribution;
        private BigDecimal winRate;
        private Integer tradeCount;
        private Integer winCount;
        private Integer lossCount;
        private BigDecimal avgHoldingDays;
        private BigDecimal bestTrade;
        private BigDecimal worstTrade;
    }

    /**
     * 섹터 로테이션 히스토리
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorRotation {
        private String period;  // YYYY-MM
        private Map<Sector, BigDecimal> sectorWeights;
        private Sector dominantSector;
        private BigDecimal dominantWeight;
    }

    /**
     * 섹터 옵션 (UI용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorOption {
        private String value;
        private String label;
        private String labelEn;
        private String description;
    }
}
