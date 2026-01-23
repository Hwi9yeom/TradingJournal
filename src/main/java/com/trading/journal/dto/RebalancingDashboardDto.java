package com.trading.journal.dto;

import com.trading.journal.entity.Sector;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 리밸런싱 대시보드 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebalancingDashboardDto {

    // ===== 포트폴리오 요약 =====
    /** 총 포트폴리오 가치 */
    private BigDecimal totalPortfolioValue;

    /** 계좌 자본금 */
    private BigDecimal accountCapital;

    /** 목표 배분 합계 (%) */
    private BigDecimal totalTargetPercent;

    /** 미배분 비율 (100% - 목표 배분 합계) */
    private BigDecimal unallocatedPercent;

    /** 전체 드리프트 점수 (각 포지션 드리프트의 절대값 합) */
    private BigDecimal totalDriftScore;

    /** 리밸런싱 필요 여부 */
    private Boolean needsRebalancing;

    // ===== 포지션별 분석 =====
    /** 포지션별 리밸런싱 분석 */
    private List<PositionRebalanceAnalysis> positionAnalyses;

    // ===== 리밸런싱 추천 =====
    /** 리밸런싱 추천 목록 */
    private List<RebalanceRecommendation> recommendations;

    // ===== 섹터별 요약 =====
    /** 섹터별 배분 요약 */
    private List<SectorAllocationSummary> sectorSummaries;

    // ===== 목표 배분 목록 =====
    /** 설정된 목표 배분 목록 */
    private List<TargetAllocationDto> targetAllocations;

    /** 포지션별 리밸런싱 분석 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PositionRebalanceAnalysis {
        /** 종목 ID */
        private Long stockId;

        /** 종목 심볼 */
        private String stockSymbol;

        /** 종목명 */
        private String stockName;

        /** 섹터 */
        private Sector sector;

        /** 현재 보유 수량 */
        private BigDecimal currentQuantity;

        /** 현재가 */
        private BigDecimal currentPrice;

        /** 현재 포지션 가치 */
        private BigDecimal currentValue;

        /** 현재 배분율 (%) */
        private BigDecimal currentPercent;

        /** 목표 배분율 (%) */
        private BigDecimal targetPercent;

        /** 드리프트 (현재 - 목표) */
        private BigDecimal drift;

        /** 드리프트 임계값 (%) */
        private BigDecimal driftThreshold;

        /** 임계값 초과 여부 */
        private Boolean exceedsDriftThreshold;

        /** 목표 가치 */
        private BigDecimal targetValue;

        /** 필요 조정 금액 (양수: 매수, 음수: 매도) */
        private BigDecimal adjustmentAmount;

        /** 필요 조정 수량 (양수: 매수, 음수: 매도) */
        private BigDecimal adjustmentQuantity;

        /** 우선순위 */
        private Integer priority;
    }

    /** 리밸런싱 추천 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RebalanceRecommendation {
        /** 종목 ID */
        private Long stockId;

        /** 종목 심볼 */
        private String stockSymbol;

        /** 종목명 */
        private String stockName;

        /** 추천 행동 (BUY / SELL) */
        private String action;

        /** 추천 수량 */
        private BigDecimal quantity;

        /** 현재가 */
        private BigDecimal currentPrice;

        /** 예상 거래 금액 */
        private BigDecimal estimatedAmount;

        /** 드리프트 (현재 - 목표) */
        private BigDecimal drift;

        /** 우선순위 */
        private Integer priority;

        /** 추천 사유 */
        private String reason;
    }

    /** 섹터별 배분 요약 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectorAllocationSummary {
        /** 섹터 */
        private Sector sector;

        /** 섹터 라벨 */
        private String sectorLabel;

        /** 현재 가치 */
        private BigDecimal currentValue;

        /** 현재 배분율 (%) */
        private BigDecimal currentPercent;

        /** 목표 배분율 (%) - 해당 섹터 종목들의 목표 합계 */
        private BigDecimal targetPercent;

        /** 드리프트 (현재 - 목표) */
        private BigDecimal drift;

        /** 포지션 수 */
        private Integer positionCount;
    }
}
