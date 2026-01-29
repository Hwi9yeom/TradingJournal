package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 복합 지표 대시보드 DTO
 *
 * <p>포트폴리오 현황, 리스크 지표, 심리 점수, 거래 통계를 한 화면에서 볼 수 있는 통합 대시보드
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompositeDashboardDto {

    /** 포트폴리오 현황 요약 */
    private PortfolioOverview portfolioOverview;

    /** 리스크 지표 요약 */
    private RiskMetricsSummary riskMetrics;

    /** 심리 점수 상세 */
    private PsychologyScoreDetail psychologyScore;

    /** 거래 통계 요약 */
    private TradingStatisticsSummary tradingStatistics;

    /** 대시보드 생성 시간 */
    private LocalDateTime generatedAt;

    /** 계좌 ID (null이면 전체) */
    private Long accountId;

    // ============================================================
    // Inner Classes
    // ============================================================

    /** 포트폴리오 현황 요약 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PortfolioOverview {
        /** 총 투자금액 */
        private BigDecimal totalInvestment;

        /** 총 현재가치 */
        private BigDecimal totalCurrentValue;

        /** 총 평가손익 */
        private BigDecimal totalProfitLoss;

        /** 총 평가손익률 (%) */
        private BigDecimal totalProfitLossPercent;

        /** 당일 변동 금액 */
        private BigDecimal totalDayChange;

        /** 당일 변동률 (%) */
        private BigDecimal totalDayChangePercent;

        /** 총 실현손익 */
        private BigDecimal totalRealizedPnl;

        /** 보유 종목 수 */
        private int holdingsCount;
    }

    /** 리스크 지표 요약 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskMetricsSummary {
        /** 샤프 비율 */
        private BigDecimal sharpeRatio;

        /** 소르티노 비율 */
        private BigDecimal sortinoRatio;

        /** 최대 낙폭 (%) */
        private BigDecimal maxDrawdown;

        /** 변동성 (%) */
        private BigDecimal volatility;

        /** 승률 (%) */
        private BigDecimal winRate;

        /** 손익비 (Profit Factor) */
        private BigDecimal profitFactor;

        /** 리스크 등급 (LOW, MEDIUM, HIGH) */
        private String riskLevel;

        /** 95% 신뢰수준 VaR 요약 */
        private VaRSummary var95;
    }

    /** VaR 요약 (간소화된 버전) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VaRSummary {
        /** 일간 VaR (%) */
        private BigDecimal dailyVaR;

        /** 금액 기준 일간 VaR */
        private BigDecimal dailyVaRAmount;
    }

    /** 심리 점수 상세 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PsychologyScoreDetail {
        /** 종합 점수 (0-100) */
        private BigDecimal overallScore;

        /** 등급 (A-F) */
        private String grade;

        /** 집중력 점수 */
        private BigDecimal focusScore;

        /** 규율 점수 */
        private BigDecimal disciplineScore;

        /** 감정 안정성 점수 */
        private BigDecimal emotionalStabilityScore;

        /** 회복력 점수 */
        private BigDecimal resilienceScore;

        /** 틸트 상태 */
        private TiltStatus tiltStatus;
    }

    /** 틸트 상태 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TiltStatus {
        /** 틸트 점수 (0-100) */
        private int tiltScore;

        /** 틸트 수준 (SEVERE, MODERATE, MILD, NONE) */
        private String tiltLevel;

        /** 틸트 수준 한글 라벨 */
        private String tiltLevelLabel;

        /** 추천사항 */
        private List<String> recommendations;
    }

    /** 거래 통계 요약 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradingStatisticsSummary {
        /** 총 거래 수 */
        private int totalTrades;

        /** 거래한 고유 종목 수 */
        private int uniqueStocks;

        /** 승률 (%) */
        private double winRate;

        /** 평균 수익률 (%) */
        private double avgReturn;

        /** 샤프 비율 */
        private double sharpeRatio;

        /** 최대 낙폭 (%) */
        private double maxDrawdown;

        /** 평균 보유 기간 (일) */
        private double avgHoldingPeriod;
    }
}
