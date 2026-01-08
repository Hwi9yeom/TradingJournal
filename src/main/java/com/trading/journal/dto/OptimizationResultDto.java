package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 전략 최적화 결과 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationResultDto {

    /** 최적 파라미터 */
    private Map<String, Object> bestParameters;

    /** 최적 파라미터로 실행한 백테스트 결과 */
    private BacktestResultDto bestResult;

    /** 모든 파라미터 조합 결과 */
    private List<ParameterResult> allResults;

    /** 총 테스트한 조합 수 */
    private int totalCombinations;

    /** 최적화 실행 시간 (ms) */
    private long executionTimeMs;

    /** 최적화 목표 */
    private String targetType;

    /**
     * 개별 파라미터 조합 결과
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParameterResult {
        /** 파라미터 조합 */
        private Map<String, Object> parameters;

        /** 목표값 (수익률/샤프/손익비 등) */
        private BigDecimal targetValue;

        /** 총 수익률 (%) */
        private BigDecimal totalReturn;

        /** 최대 낙폭 (%) */
        private BigDecimal maxDrawdown;

        /** 샤프 비율 */
        private BigDecimal sharpeRatio;

        /** 손익비 */
        private BigDecimal profitFactor;

        /** 총 거래 횟수 */
        private Integer totalTrades;

        /** 승률 (%) */
        private BigDecimal winRate;
    }
}
