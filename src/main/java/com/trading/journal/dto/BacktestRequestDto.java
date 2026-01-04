package com.trading.journal.dto;

import com.trading.journal.strategy.TradingStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 백테스트 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRequestDto {

    /** 종목 심볼 */
    @NotBlank(message = "심볼은 필수입니다")
    private String symbol;

    /** 전략 유형 */
    @NotNull(message = "전략 유형은 필수입니다")
    private TradingStrategy.StrategyType strategyType;

    /** 전략 파라미터 */
    private Map<String, Object> strategyParams;

    /** 시작일 */
    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    /** 종료일 */
    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;

    /** 초기 자본금 */
    @NotNull(message = "초기 자본금은 필수입니다")
    @Positive(message = "초기 자본금은 양수여야 합니다")
    private BigDecimal initialCapital;

    /** 거래당 투자 비율 (%) */
    @Builder.Default
    private BigDecimal positionSizePercent = BigDecimal.valueOf(100);

    /** 최대 동시 포지션 수 */
    @Builder.Default
    private Integer maxPositions = 1;

    /** 수수료율 (%) */
    @Builder.Default
    private BigDecimal commissionRate = BigDecimal.valueOf(0.015);

    /** 슬리피지 (%) */
    @Builder.Default
    private BigDecimal slippage = BigDecimal.valueOf(0.1);

    /** 손절 비율 (%) - null이면 손절 없음 */
    private BigDecimal stopLossPercent;

    /** 익절 비율 (%) - null이면 익절 없음 */
    private BigDecimal takeProfitPercent;

    /** 후행 손절 비율 (%) - null이면 후행손절 없음 */
    private BigDecimal trailingStopPercent;
}
