package com.trading.journal.dto;

import com.trading.journal.entity.Sector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 리스크 대시보드 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskDashboardDto {

    // ===== 현재 리스크 노출 =====
    /** 총 포트폴리오 가치 */
    private BigDecimal totalPortfolioValue;

    /** 총 오픈 리스크 금액 (손절가 설정된 포지션의 초기 리스크 합계) */
    private BigDecimal totalOpenRisk;

    /** 오픈 리스크 % */
    private BigDecimal openRiskPercent;

    /** 계좌 자본금 */
    private BigDecimal accountCapital;

    // ===== 일일/주간 P&L =====
    /** 오늘 실현 손익 */
    private BigDecimal todayPnl;

    /** 오늘 손익 % */
    private BigDecimal todayPnlPercent;

    /** 이번 주 실현 손익 */
    private BigDecimal weekPnl;

    /** 주간 손익 % */
    private BigDecimal weekPnlPercent;

    /** 이번 달 실현 손익 */
    private BigDecimal monthPnl;

    /** 월간 손익 % */
    private BigDecimal monthPnlPercent;

    // ===== 리스크 한도 상태 =====
    /** 일일 손실 한도 상태 */
    private RiskLimitStatus dailyLossStatus;

    /** 주간 손실 한도 상태 */
    private RiskLimitStatus weeklyLossStatus;

    /** 포지션 수 한도 상태 */
    private RiskLimitStatus positionCountStatus;

    /** 집중도 알림 목록 */
    private List<ConcentrationAlert> concentrationAlerts;

    // ===== R-Multiple 분석 =====
    /** R-multiple 분석 결과 */
    private RMultipleAnalysis rMultipleAnalysis;

    // ===== 포지션별 리스크 =====
    /** 포지션 리스크 요약 목록 */
    private List<PositionRiskSummary> positionRisks;

    // ===== 섹터 노출 =====
    /** 섹터별 노출 현황 */
    private List<SectorExposure> sectorExposures;

    // ===== 기존 리스크 지표 =====
    /** 기존 RiskMetrics */
    private RiskMetricsDto riskMetrics;

    /**
     * 리스크 한도 상태
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskLimitStatus {
        /** 한도 값 */
        private BigDecimal limit;

        /** 현재 값 */
        private BigDecimal current;

        /** 남은 여유분 */
        private BigDecimal remaining;

        /** 사용된 비율 (%) */
        private BigDecimal percentUsed;

        /** 한도 초과 여부 */
        private Boolean isBreached;

        /** 상태 라벨 (OK, WARNING, BREACHED) */
        private String statusLabel;
    }

    /**
     * 집중도 알림
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConcentrationAlert {
        /** 종목 심볼 */
        private String stockSymbol;

        /** 종목명 */
        private String stockName;

        /** 섹터 */
        private Sector sector;

        /** 현재 집중도 (%) */
        private BigDecimal concentration;

        /** 한도 (%) */
        private BigDecimal limit;

        /** 알림 타입 (STOCK, SECTOR) */
        private String alertType;
    }

    /**
     * R-Multiple 분석
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RMultipleAnalysis {
        /** 평균 R-multiple */
        private BigDecimal averageRMultiple;

        /** 중간값 R-multiple */
        private BigDecimal medianRMultiple;

        /** 최고 R-multiple */
        private BigDecimal bestRMultiple;

        /** 최저 R-multiple */
        private BigDecimal worstRMultiple;

        /** 양수 R 거래 수 */
        private Integer tradesWithPositiveR;

        /** 음수 R 거래 수 */
        private Integer tradesWithNegativeR;

        /** 기대값 (Expectancy) = 평균 R-multiple (모든 거래의 R 평균값) */
        private BigDecimal expectancy;

        /** R-multiple 분포 */
        private List<RMultipleDistribution> distribution;
    }

    /**
     * R-Multiple 분포
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RMultipleDistribution {
        /** 범위 (예: "-3R ~ -2R", "2R ~ 3R") */
        private String range;

        /** 거래 수 */
        private Integer count;

        /** 비율 (%) */
        private BigDecimal percentage;
    }

    /**
     * 포지션 리스크 요약
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PositionRiskSummary {
        /** 종목 심볼 */
        private String stockSymbol;

        /** 종목명 */
        private String stockName;

        /** 보유 수량 */
        private BigDecimal quantity;

        /** 진입 평균가 */
        private BigDecimal entryPrice;

        /** 현재가 */
        private BigDecimal currentPrice;

        /** 손절가 */
        private BigDecimal stopLossPrice;

        /** 익절가 */
        private BigDecimal takeProfitPrice;

        /** 미실현 손익 */
        private BigDecimal unrealizedPnl;

        /** 미실현 손익 % */
        private BigDecimal unrealizedPnlPercent;

        /** 리스크 금액 (손절 시 손실) */
        private BigDecimal riskAmount;

        /** 포지션 리스크 % (포트폴리오 대비) */
        private BigDecimal positionRiskPercent;

        /** 현재 R (현재 손익을 R 단위로) */
        private BigDecimal currentR;
    }

    /**
     * 섹터 노출
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectorExposure {
        /** 섹터 */
        private Sector sector;

        /** 섹터 라벨 */
        private String sectorLabel;

        /** 노출 금액 */
        private BigDecimal value;

        /** 노출 비율 (%) */
        private BigDecimal percentage;

        /** 한도 (%) */
        private BigDecimal limit;

        /** 한도 초과 여부 */
        private Boolean exceedsLimit;
    }
}
