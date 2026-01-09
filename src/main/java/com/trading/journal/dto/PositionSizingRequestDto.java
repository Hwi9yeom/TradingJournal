package com.trading.journal.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 포지션 사이징 계산 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionSizingRequestDto {

    /** 계정 ID (null이면 기본 계좌 사용) */
    private Long accountId;

    /** 진입가 */
    @NotNull(message = "Entry price is required")
    @Positive(message = "Entry price must be positive")
    private BigDecimal entryPrice;

    /** 손절가 */
    @NotNull(message = "Stop loss price is required")
    @Positive(message = "Stop loss price must be positive")
    private BigDecimal stopLossPrice;

    /** 익절가 (선택) */
    @Positive(message = "Take profit price must be positive")
    private BigDecimal takeProfitPrice;

    /** 계좌 자본금 오버라이드 (선택) */
    @Positive(message = "Account capital must be positive")
    private BigDecimal accountCapital;

    /** 리스크 % 오버라이드 (선택) */
    @DecimalMin(value = "0.1", message = "Risk percent must be at least 0.1%")
    @DecimalMax(value = "10.0", message = "Risk percent cannot exceed 10%")
    private BigDecimal riskPercent;

    /** 포지션 사이징 방법 */
    @Builder.Default
    private PositionSizingMethod method = PositionSizingMethod.FIXED_FRACTIONAL;

    /**
     * 포지션 사이징 방법
     */
    public enum PositionSizingMethod {
        /** 고정 비율법: 자본의 X%를 리스크로 */
        FIXED_FRACTIONAL,

        /** Kelly Criterion: 최적 비율 계산 */
        KELLY_CRITERION,

        /** 고정 금액: 매번 동일 금액 리스크 */
        FIXED_DOLLAR,

        /** 변동성 기반: ATR 등 활용 */
        VOLATILITY_BASED
    }
}
