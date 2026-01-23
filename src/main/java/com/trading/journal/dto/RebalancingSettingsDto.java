package com.trading.journal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 리밸런싱 설정 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebalancingSettingsDto {

    /** 기본 드리프트 임계값 (%) */
    @DecimalMin(value = "0.1", message = "드리프트 임계값은 0.1% 이상이어야 합니다")
    @DecimalMax(value = "50", message = "드리프트 임계값은 50% 이하여야 합니다")
    @Builder.Default
    private BigDecimal defaultDriftThreshold = BigDecimal.valueOf(5);

    /** 최소 거래 금액 (이 이하면 리밸런싱 추천에서 제외) */
    @DecimalMin(value = "0", message = "최소 거래 금액은 0 이상이어야 합니다")
    @Builder.Default
    private BigDecimal minTradeAmount = BigDecimal.valueOf(10000);

    /** 현금 보유 목표 비율 (%) */
    @DecimalMin(value = "0", message = "현금 보유 비율은 0 이상이어야 합니다")
    @DecimalMax(value = "100", message = "현금 보유 비율은 100% 이하여야 합니다")
    @Builder.Default
    private BigDecimal cashReservePercent = BigDecimal.ZERO;

    /** 세금/수수료 고려 여부 */
    @Builder.Default private Boolean considerTaxAndFees = false;

    /** 리밸런싱 주기 (일) - 0이면 수동 */
    @Builder.Default private Integer rebalancingPeriodDays = 0;
}
