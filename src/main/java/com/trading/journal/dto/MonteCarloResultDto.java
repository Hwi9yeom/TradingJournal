package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 몬테카를로 시뮬레이션 결과 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloResultDto {

    /** 결과 ID */
    private Long id;

    /** 실행 시간 */
    private LocalDateTime executedAt;

    /** 시뮬레이션 횟수 */
    private Integer numSimulations;

    /** 예측 기간 (거래일) */
    private Integer projectionDays;

    // === 백분위수 결과 ===

    /** 백분위수별 최종 자산 가치 (예: "5%": 85000, "95%": 145000) */
    private Map<String, BigDecimal> percentileValues;

    // === 분포 통계 ===

    /** 최종 자산의 평균값 */
    private BigDecimal meanFinalValue;

    /** 최종 자산의 중앙값 */
    private BigDecimal medianFinalValue;

    /** 표준편차 */
    private BigDecimal standardDeviation;

    /** 왜도 (Skewness) */
    private BigDecimal skewness;

    /** 첨도 (Kurtosis) */
    private BigDecimal kurtosis;

    // === 위험 지표 ===

    /** 손실 확률 */
    private BigDecimal probabilityOfLoss;

    /** 95% VaR (Value at Risk) */
    private BigDecimal valueAtRisk95;

    /** 99% VaR (Value at Risk) */
    private BigDecimal valueAtRisk99;

    /** Expected Shortfall (조건부 VaR) */
    private BigDecimal expectedShortfall;

    /** 95 백분위수에서의 최대 낙폭 */
    private BigDecimal maxDrawdownAt95Percentile;

    // === 차트 데이터 ===

    /** 차트 레이블 (거래일) */
    private List<String> labels;

    /** 평균 경로 */
    private List<BigDecimal> meanPath;

    /** 상단 경계 (예: 95% 신뢰도) */
    private List<BigDecimal> upperBound;

    /** 하단 경계 (예: 5% 신뢰도) */
    private List<BigDecimal> lowerBound;

    // === 히스토그램 데이터 ===

    /** 히스토그램 구간 */
    private List<BigDecimal> histogramBins;

    /** 히스토그램 도수 */
    private List<Integer> histogramCounts;

    /** 시뮬레이션 경로 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationPath {
        /** 경로별 자산 가치 */
        private List<BigDecimal> values;
    }
}
