package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 거래 패턴 분석 DTO */
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
    private int totalTrades;

    // ========== 내부 DTO 클래스들 ==========

    /** 스트릭 분석 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StreakAnalysis {
        private int currentStreak; // +면 연승, -면 연패
        private int maxWinStreak; // 최대 연승
        private int maxLossStreak; // 최대 연패
        private int avgWinStreak; // 평균 연승
        private int avgLossStreak; // 평균 연패
        private List<StreakEvent> recentStreaks; // 최근 스트릭 이력
    }

    /** 스트릭 이벤트 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StreakEvent {
        private LocalDate startDate;
        private LocalDate endDate;
        private int length; // 스트릭 길이
        private boolean isWinStreak; // 연승 여부
        private BigDecimal totalPnl; // 해당 기간 총 손익
    }

    /** 요일별 성과 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayOfWeekPerformance {
        private DayOfWeek dayOfWeek;
        private String dayOfWeekLabel; // 한글 요일명
        private int tradeCount; // 거래 횟수
        private int winCount; // 수익 거래 수
        private BigDecimal winRate; // 승률
        private BigDecimal avgReturn; // 평균 수익률
        private BigDecimal totalPnl; // 총 손익
        private BigDecimal avgPnl; // 평균 손익
    }

    /** 월별 계절성 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlySeasonality {
        private int month; // 1-12
        private String monthLabel; // 한글 월명
        private int tradeCount;
        private int winCount;
        private BigDecimal winRate;
        private BigDecimal avgReturn;
        private BigDecimal totalPnl;
        private int yearCount; // 데이터가 있는 연도 수
    }

    /** 거래 규모 분석 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeSizeAnalysis {
        private BigDecimal avgTradeAmount; // 평균 거래 금액
        private BigDecimal maxTradeAmount; // 최대 거래 금액
        private BigDecimal minTradeAmount; // 최소 거래 금액
        private BigDecimal medianTradeAmount; // 중간값
        private BigDecimal stdDeviation; // 표준편차

        // 거래 규모별 분포
        private List<TradeSizeBucket> distribution;
    }

    /** 거래 규모 구간 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeSizeBucket {
        private String label; // "100만원 미만", "100-500만원" 등
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private int count;
        private BigDecimal percentage;
        private BigDecimal avgReturn; // 해당 구간 평균 수익률
        private BigDecimal winRate; // 해당 구간 승률
    }

    /** 보유 기간 분석 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingPeriodAnalysis {
        private BigDecimal avgHoldingDays; // 전체 평균 보유 기간
        private BigDecimal avgWinHoldingDays; // 수익 거래 평균 보유 기간
        private BigDecimal avgLossHoldingDays; // 손실 거래 평균 보유 기간
        private int maxHoldingDays; // 최대 보유 기간
        private int minHoldingDays; // 최소 보유 기간

        // 보유 기간별 분포
        private List<HoldingPeriodBucket> distribution;
    }

    /** 보유 기간 구간 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingPeriodBucket {
        private String label; // "당일", "1-3일", "1주일 이내" 등
        private int minDays;
        private int maxDays;
        private int count;
        private BigDecimal percentage;
        private BigDecimal avgReturn;
        private BigDecimal winRate;
    }
}
