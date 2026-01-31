package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 백테스트 결과 비교 DTO
 *
 * <p>여러 백테스트 결과를 비교하여 성과 순위, 차트 데이터, 통계 요약 제공
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestComparisonDto {

    /** 비교 대상 백테스트 목록 */
    private List<ComparedBacktest> backtests;

    /** 지표별 순위 */
    private MetricRankings rankings;

    /** 통합 차트 데이터 (오버레이용) */
    private ChartData chartData;

    /** 비교 통계 요약 */
    private ComparisonSummary summary;

    /** 비교 생성 시간 */
    private LocalDateTime generatedAt;

    // ============================================================
    // Inner Classes
    // ============================================================

    /** 비교 대상 개별 백테스트 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComparedBacktest {
        private Long id;
        private String strategyName;
        private String strategyType;
        private String symbol;
        private LocalDate startDate;
        private LocalDate endDate;

        /** 초기 자본금 */
        private BigDecimal initialCapital;

        /** 최종 자본금 */
        private BigDecimal finalCapital;

        /** 총 수익률 (%) */
        private BigDecimal totalReturn;

        /** 연평균 수익률 CAGR (%) */
        private BigDecimal cagr;

        /** 최대 낙폭 (%) */
        private BigDecimal maxDrawdown;

        /** 샤프 비율 */
        private BigDecimal sharpeRatio;

        /** 소르티노 비율 */
        private BigDecimal sortinoRatio;

        /** 손익비 */
        private BigDecimal profitFactor;

        /** 승률 (%) */
        private BigDecimal winRate;

        /** 총 거래 수 */
        private Integer totalTrades;

        /** 평균 보유 기간 (일) */
        private BigDecimal avgHoldingDays;

        /** 실행 시간 */
        private LocalDateTime executedAt;

        /** 차트 색상 (UI용) */
        private String color;
    }

    /** 지표별 순위 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetricRankings {
        /** 수익률 순위 (백테스트 ID 목록) */
        private List<RankEntry> byTotalReturn;

        /** 샤프 비율 순위 */
        private List<RankEntry> bySharpeRatio;

        /** 최대 낙폭 순위 (낮을수록 좋음) */
        private List<RankEntry> byMaxDrawdown;

        /** 승률 순위 */
        private List<RankEntry> byWinRate;

        /** 손익비 순위 */
        private List<RankEntry> byProfitFactor;

        /** CAGR 순위 */
        private List<RankEntry> byCagr;

        /** 종합 점수 순위 */
        private List<RankEntry> byOverallScore;
    }

    /** 순위 항목 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RankEntry {
        private int rank;
        private Long backtestId;
        private String strategyName;
        private BigDecimal value;
    }

    /** 통합 차트 데이터 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChartData {
        /** 공통 날짜 라벨 */
        private List<String> labels;

        /** 백테스트별 수익률 곡선 (정규화된 %) */
        private Map<Long, List<BigDecimal>> equityCurves;

        /** 백테스트별 낙폭 곡선 (%) */
        private Map<Long, List<BigDecimal>> drawdownCurves;

        /** 월별 수익률 비교 */
        private List<MonthlyComparison> monthlyReturns;
    }

    /** 월별 수익률 비교 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyComparison {
        private String month;

        /** 백테스트 ID별 월 수익률 */
        private Map<Long, BigDecimal> returns;
    }

    /** 비교 통계 요약 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComparisonSummary {
        /** 비교 대상 수 */
        private int totalBacktests;

        /** 최고 수익 전략 */
        private String bestReturnStrategy;

        private BigDecimal bestReturn;

        /** 최저 리스크 전략 */
        private String lowestRiskStrategy;

        private BigDecimal lowestDrawdown;

        /** 최고 샤프 비율 전략 */
        private String bestSharpeStrategy;

        private BigDecimal bestSharpe;

        /** 평균 수익률 */
        private BigDecimal avgReturn;

        /** 평균 샤프 비율 */
        private BigDecimal avgSharpe;

        /** 평균 최대 낙폭 */
        private BigDecimal avgMaxDrawdown;

        /** 수익 전략 비율 (%) */
        private BigDecimal profitableRatio;

        /** 분석 기간 (공통) */
        private LocalDate commonStartDate;

        private LocalDate commonEndDate;
    }
}
