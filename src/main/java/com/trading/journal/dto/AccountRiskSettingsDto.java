package com.trading.journal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 계정별 리스크 설정 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRiskSettingsDto {
    private Long id;

    private Long accountId;
    private String accountName;

    // 리스크 한도 설정
    @DecimalMin(value = "0.1", message = "Max risk per trade must be at least 0.1%")
    @DecimalMax(value = "10.0", message = "Max risk per trade cannot exceed 10%")
    @Builder.Default
    private BigDecimal maxRiskPerTradePercent = new BigDecimal("2.00");

    @DecimalMin(value = "1.0", message = "Max daily loss must be at least 1%")
    @DecimalMax(value = "20.0", message = "Max daily loss cannot exceed 20%")
    @Builder.Default
    private BigDecimal maxDailyLossPercent = new BigDecimal("6.00");

    @DecimalMin(value = "2.0", message = "Max weekly loss must be at least 2%")
    @DecimalMax(value = "30.0", message = "Max weekly loss cannot exceed 30%")
    @Builder.Default
    private BigDecimal maxWeeklyLossPercent = new BigDecimal("10.00");

    @Min(value = 1, message = "Max open positions must be at least 1")
    @Max(value = 100, message = "Max open positions cannot exceed 100")
    @Builder.Default
    private Integer maxOpenPositions = 10;

    @DecimalMin(value = "5.0", message = "Max position size must be at least 5%")
    @DecimalMax(value = "100.0", message = "Max position size cannot exceed 100%")
    @Builder.Default
    private BigDecimal maxPositionSizePercent = new BigDecimal("20.00");

    @DecimalMin(value = "10.0", message = "Max sector concentration must be at least 10%")
    @DecimalMax(value = "100.0", message = "Max sector concentration cannot exceed 100%")
    @Builder.Default
    private BigDecimal maxSectorConcentrationPercent = new BigDecimal("30.00");

    @DecimalMin(value = "5.0", message = "Max stock concentration must be at least 5%")
    @DecimalMax(value = "100.0", message = "Max stock concentration cannot exceed 100%")
    @Builder.Default
    private BigDecimal maxStockConcentrationPercent = new BigDecimal("15.00");

    // 자본금 관리
    @PositiveOrZero(message = "Account capital cannot be negative")
    private BigDecimal accountCapital;

    @DecimalMin(value = "0.1", message = "Kelly fraction must be at least 0.1")
    @DecimalMax(value = "1.0", message = "Kelly fraction cannot exceed 1.0")
    @Builder.Default
    private BigDecimal kellyFraction = new BigDecimal("0.50");

    // 보유 기간 설정
    @Min(value = 1, message = "Max holding days must be at least 1")
    @Max(value = 365, message = "Max holding days cannot exceed 365")
    @Builder.Default
    private Integer maxHoldingDays = 30;

    // 알림 설정
    @Builder.Default private Boolean dailyLossAlertEnabled = true;

    @Builder.Default private Boolean concentrationAlertEnabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 현재 상태 (조회 시에만 사용)
    /** 오늘 실현 손익 */
    private BigDecimal currentDayPnl;

    /** 이번 주 실현 손익 */
    private BigDecimal currentWeekPnl;

    /** 현재 오픈 포지션 수 */
    private Integer currentOpenPositions;

    /** 일일 한도 초과 여부 */
    private Boolean isDailyLimitBreached;

    /** 주간 한도 초과 여부 */
    private Boolean isWeeklyLimitBreached;

    /** 포지션 수 한도 초과 여부 */
    private Boolean isPositionLimitBreached;
}
