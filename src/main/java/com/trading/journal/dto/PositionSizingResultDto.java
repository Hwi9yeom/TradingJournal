package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포지션 사이징 계산 결과 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionSizingResultDto {

    // ===== 입력 요약 =====
    /** 계좌 자본금 */
    private BigDecimal accountCapital;

    /** 진입가 */
    private BigDecimal entryPrice;

    /** 손절가 */
    private BigDecimal stopLossPrice;

    /** 익절가 */
    private BigDecimal takeProfitPrice;

    /** 사용된 방법 */
    private PositionSizingRequestDto.PositionSizingMethod method;

    // ===== 핵심 계산 결과 =====
    /** 주당 리스크 = |진입가 - 손절가| */
    private BigDecimal riskPerShare;

    /** 리스크 % */
    private BigDecimal riskPercent;

    /** 최대 리스크 금액 = 자본금 × 리스크% */
    private BigDecimal maxRiskAmount;

    // ===== 추천 포지션 =====
    /** 추천 수량 */
    private BigDecimal recommendedQuantity;

    /** 추천 포지션 금액 */
    private BigDecimal recommendedPositionValue;

    /** 추천 포지션 비율 (% of capital) */
    private BigDecimal recommendedPositionPercent;

    // ===== Kelly Criterion =====
    /** Kelly % */
    private BigDecimal kellyPercentage;

    /** Full Kelly 수량 */
    private BigDecimal fullKellyQuantity;

    /** Half Kelly 수량 */
    private BigDecimal halfKellyQuantity;

    /** Quarter Kelly 수량 */
    private BigDecimal quarterKellyQuantity;

    // ===== 리스크/리워드 분석 =====
    /** 리스크:리워드 비율 */
    private BigDecimal riskRewardRatio;

    /** 예상 손실 (추천 수량 기준) */
    private BigDecimal potentialLoss;

    /** 예상 이익 (추천 수량 기준) */
    private BigDecimal potentialProfit;

    // ===== 한도 체크 =====
    /** 최대 포지션 크기 초과 여부 */
    private Boolean exceedsMaxPositionSize;

    /** 최대 종목 집중도 초과 여부 */
    private Boolean exceedsMaxStockConcentration;

    /** 한도 내 최대 허용 수량 */
    private BigDecimal maxAllowedQuantity;

    /** 경고 메시지 */
    private String warningMessage;

    // ===== 시나리오 비교 =====
    /** 다양한 리스크 수준별 시나리오 */
    private List<PositionScenario> scenarios;

    /**
     * 포지션 시나리오
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PositionScenario {
        /** 시나리오 이름 */
        private String name;

        /** 리스크 % */
        private BigDecimal riskPercent;

        /** 수량 */
        private BigDecimal quantity;

        /** 포지션 금액 */
        private BigDecimal positionValue;

        /** 예상 손실 */
        private BigDecimal potentialLoss;

        /** 예상 이익 */
        private BigDecimal potentialProfit;
    }
}
