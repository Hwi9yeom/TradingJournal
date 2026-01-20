package com.trading.journal.dto;

import com.trading.journal.strategy.TradingStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 전략 최적화 요청 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationRequestDto {

    /** 종목 심볼 */
    private String symbol;

    /** 전략 유형 */
    private TradingStrategy.StrategyType strategyType;

    /** 시작일 */
    private LocalDate startDate;

    /** 종료일 */
    private LocalDate endDate;

    /** 초기 자본금 */
    @Builder.Default private BigDecimal initialCapital = BigDecimal.valueOf(10_000_000);

    /** 포지션 크기 (%) */
    @Builder.Default private BigDecimal positionSizePercent = BigDecimal.valueOf(100);

    /** 수수료율 (%) */
    @Builder.Default private BigDecimal commissionRate = BigDecimal.valueOf(0.015);

    /** 슬리피지 (%) */
    @Builder.Default private BigDecimal slippage = BigDecimal.valueOf(0.1);

    /** 최적화할 파라미터 범위 */
    private Map<String, ParameterRange> parameterRanges;

    /** 최적화 목표 */
    @Builder.Default private OptimizationTarget target = OptimizationTarget.TOTAL_RETURN;

    /** 파라미터 범위 설정 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParameterRange {
        /** 최소값 */
        private Number min;

        /** 최대값 */
        private Number max;

        /** 증가 단위 */
        private Number step;
    }

    /** 최적화 목표 */
    public enum OptimizationTarget {
        /** 총 수익률 최대화 */
        TOTAL_RETURN("총 수익률"),

        /** 샤프 비율 최대화 */
        SHARPE_RATIO("샤프 비율"),

        /** 손익비 최대화 */
        PROFIT_FACTOR("손익비"),

        /** 최대낙폭 최소화 */
        MIN_DRAWDOWN("최대낙폭 최소화"),

        /** 칼마 비율 최대화 */
        CALMAR_RATIO("칼마 비율");

        private final String label;

        OptimizationTarget(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
