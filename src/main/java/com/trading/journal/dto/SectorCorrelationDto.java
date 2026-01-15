package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 섹터별 상관관계 요약 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorCorrelationDto {

    /**
     * 섹터별 평균 상관계수
     */
    private List<SectorSummary> sectorSummaries;

    /**
     * 섹터 간 상관관계 매트릭스
     */
    private List<String> sectors;
    private List<List<BigDecimal>> sectorMatrix;

    /**
     * 가장 상관관계 낮은 섹터 쌍 (분산투자 추천)
     */
    private List<SectorPair> diversificationRecommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorSummary {
        private String sector;
        private String sectorLabel;
        private int stockCount;
        private BigDecimal internalCorrelation;  // 섹터 내 평균 상관계수
        private List<String> stocks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorPair {
        private String sector1;
        private String sector2;
        private BigDecimal correlation;
        private String recommendation;
    }
}
