package com.trading.journal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 몬테카를로 시뮬레이션 요청 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloRequestDto {

    /** 계좌 ID */
    @NotNull(message = "Account ID is required")
    private Long accountId;

    /** 분석 시작일 */
    private LocalDate startDate;

    /** 분석 종료일 */
    private LocalDate endDate;

    /** 시뮬레이션 횟수 */
    @Builder.Default
    @Min(value = 100, message = "Minimum 100 simulations")
    @Max(value = 100000, message = "Maximum 100,000 simulations")
    private Integer numSimulations = 10000;

    /** 예측 기간 (거래일 기준) */
    @Builder.Default
    @Min(value = 1, message = "Minimum 1 day projection")
    @Max(value = 1260, message = "Maximum 5 years projection")
    private Integer projectionDays = 252;

    /** 초기 자산 가치 */
    private BigDecimal initialValue;

    /** 신뢰도 수준 (0~1) */
    @Builder.Default
    private List<BigDecimal> confidenceLevels =
            List.of(
                    new BigDecimal("0.05"),
                    new BigDecimal("0.25"),
                    new BigDecimal("0.50"),
                    new BigDecimal("0.75"),
                    new BigDecimal("0.95"));
}
