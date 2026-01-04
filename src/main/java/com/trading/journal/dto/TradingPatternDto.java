package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 거래 패턴 분석 DTO
 * 연승/연패, 시간대별 성과, 보유 기간 분석 등
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingPatternDto {

    /** 스트릭(연승/연패) 분석 */
    private StreakAnalysis streakAnalysis;

    /** 요일별 성과 */
    private List<DayOfWeekPerformance> dayOfWeekPerformance;

    /** 월별 계절성 */
    private List<MonthlySeasonality> monthlySeasonality;

    /** 거래 규모 분석 */
    private TradeSizeAnalysis tradeSizeAnalysis;

    /** 보유 기간 분석 */
    private HoldingPeriodAnalysis holdingPeriodAnalysis;

    /** 분석 기간 */
    private LocalDate startDate;
    private LocalDate endDate;

    /** 총 거래 수 */
    private int totalTrades;

    /**
     * 스트릭(연승/연패) 분석
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StreakAnalysis {
        /** 현재 스트릭 (+면 연승, -면 연패) */
        private int currentStreak;

        /** 최대 연승 */
        private int maxWinStreak;

        /** 최대 연패 */
        private int maxLossStreak;

        /** 평균 연승 */
        private BigDecimal avgWinStreak;

        /** 평균 연패 */
        private BigDecimal avgLossStreak;

        /** 연승 횟수 */
        private int winStreakCount;

        /** 연패 횟수 */
        private int lossStreakCount;

        /** 최근 스트릭 이벤트 목록 */
        private List<StreakEvent> recentStreaks;
    }

    /**
     * 스트릭 이벤트
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StreakEvent {
        private LocalDate startDate;
        private LocalDate endDate;
        private int streakLength;
        private boolean isWinStreak;
        private BigDecimal totalProfit;
    }

    /**
     * 요일별 성과
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayOfWeekPerformance {
        private DayOfWeek dayOfWeek;
        private String dayOfWeekKorean;
        private int tradeCount;
        private BigDecimal winRate;
        private BigDecimal avgReturn;
        private BigDecimal totalProfit;
        private int winCount;
        private int lossCount;
    }

    /**
     * 월별 계절성
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlySeasonality {
        private int month;
        private String monthName;
        private int tradeCount;
        private BigDecimal winRate;
        private BigDecimal avgReturn;
        private BigDecimal totalProfit;
        private int winCount;
        private int lossCount;
    }

    /**
     * 거래 규모 분석
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeSizeAnalysis {
        /** 평균 거래 금액 */
        private BigDecimal avgTradeAmount;

        /** 최대 거래 금액 */
        private BigDecimal maxTradeAmount;

        /** 최소 거래 금액 */
        private BigDecimal minTradeAmount;

        /** 중앙값 거래 금액 */
        private BigDecimal medianTradeAmount;

        /** 거래 금액 표준편차 */
        private BigDecimal stdDevTradeAmount;

        /** 평균 수익 거래 금액 */
        private BigDecimal avgWinTradeAmount;

        /** 평균 손실 거래 금액 */
        private BigDecimal avgLossTradeAmount;
    }

    /**
     * 보유 기간 분석
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingPeriodAnalysis {
        /** 평균 보유 기간 (일) */
        private BigDecimal avgHoldingDays;

        /** 최대 보유 기간 (일) */
        private int maxHoldingDays;

        /** 최소 보유 기간 (일) */
        private int minHoldingDays;

        /** 중앙값 보유 기간 (일) */
        private int medianHoldingDays;

        /** 수익 거래 평균 보유 기간 */
        private BigDecimal avgWinHoldingDays;

        /** 손실 거래 평균 보유 기간 */
        private BigDecimal avgLossHoldingDays;

        /** 보유 기간별 성과 분포 */
        private List<HoldingPeriodBucket> holdingPeriodDistribution;
    }

    /**
     * 보유 기간 구간별 성과
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingPeriodBucket {
        private String label;  // e.g., "1-7일", "1-4주", "1-3개월"
        private int minDays;
        private int maxDays;
        private int tradeCount;
        private BigDecimal winRate;
        private BigDecimal avgReturn;
        private BigDecimal totalProfit;
    }
}
