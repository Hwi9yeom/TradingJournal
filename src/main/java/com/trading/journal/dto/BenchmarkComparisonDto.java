package com.trading.journal.dto;

import com.trading.journal.entity.BenchmarkType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 벤치마크 비교 분석 결과 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkComparisonDto {

    /** 벤치마크 유형 */
    private BenchmarkType benchmark;

    /** 벤치마크 라벨 */
    private String benchmarkLabel;

    /** 분석 시작일 */
    private LocalDate startDate;

    /** 분석 종료일 */
    private LocalDate endDate;

    /** 차트 레이블 (날짜) */
    private List<String> labels;

    /** 포트폴리오 누적 수익률 (%) */
    private List<BigDecimal> portfolioReturns;

    /** 벤치마크 누적 수익률 (%) */
    private List<BigDecimal> benchmarkReturns;

    /** 초과 수익률 (%) - 포트폴리오 - 벤치마크 */
    private List<BigDecimal> excessReturns;

    // === 성과 지표 ===

    /** 포트폴리오 총 수익률 (%) */
    private BigDecimal portfolioTotalReturn;

    /** 벤치마크 총 수익률 (%) */
    private BigDecimal benchmarkTotalReturn;

    /** 초과 수익률 (%) */
    private BigDecimal excessReturn;

    /** 알파 (Jensen's Alpha) - 벤치마크 대비 초과 수익 */
    private BigDecimal alpha;

    /** 베타 - 시장 민감도 */
    private BigDecimal beta;

    /** 상관계수 (-1 ~ 1) */
    private BigDecimal correlation;

    /** 결정계수 R² (0 ~ 1) */
    private BigDecimal rSquared;

    /** Information Ratio - 초과수익 / 추적오차 */
    private BigDecimal informationRatio;

    /** 추적오차 (Tracking Error) */
    private BigDecimal trackingError;

    /** 트레이너 비율 (Treynor Ratio) */
    private BigDecimal treynorRatio;

    // === 위험 조정 성과 ===

    /** 포트폴리오 샤프비율 */
    private BigDecimal portfolioSharpe;

    /** 벤치마크 샤프비율 */
    private BigDecimal benchmarkSharpe;

    /** 포트폴리오 변동성 */
    private BigDecimal portfolioVolatility;

    /** 벤치마크 변동성 */
    private BigDecimal benchmarkVolatility;

    /** 포트폴리오 최대 낙폭 */
    private BigDecimal portfolioMaxDrawdown;

    /** 벤치마크 최대 낙폭 */
    private BigDecimal benchmarkMaxDrawdown;

    // === 월별 비교 ===

    /** 월별 비교 데이터 */
    private List<MonthlyComparison> monthlyComparisons;

    /** 포트폴리오 승리 개월 수 */
    private Integer portfolioWinMonths;

    /** 벤치마크 승리 개월 수 */
    private Integer benchmarkWinMonths;

    // === 연간 비교 ===

    /** 연간 비교 데이터 */
    private List<YearlyComparison> yearlyComparisons;

    /** 월별 비교 데이터 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyComparison {
        private String month; // YYYY-MM
        private BigDecimal portfolioReturn;
        private BigDecimal benchmarkReturn;
        private BigDecimal excessReturn;
        private Boolean portfolioWin;
    }

    /** 연간 비교 데이터 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearlyComparison {
        private Integer year;
        private BigDecimal portfolioReturn;
        private BigDecimal benchmarkReturn;
        private BigDecimal excessReturn;
        private Boolean portfolioWin;
    }

    /** 벤치마크 요약 정보 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkSummary {
        private BenchmarkType benchmark;
        private String label;
        private String symbol;
        private String description;
        private LocalDate latestDate;
        private BigDecimal latestPrice;
        private BigDecimal dailyChange;
        private BigDecimal ytdReturn;
        private Long dataCount;
    }

    /** 다중 벤치마크 비교 결과 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiBenchmarkComparison {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> labels;
        private List<BigDecimal> portfolioReturns;
        private List<BenchmarkData> benchmarks;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BenchmarkData {
            private BenchmarkType type;
            private String label;
            private List<BigDecimal> returns;
            private BigDecimal totalReturn;
            private BigDecimal correlation;
            private BigDecimal beta;
        }
    }
}
