package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 월별 소득/지출 점검 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBudgetDto {
    private Long id;
    private Long accountId;

    /** 대상 월 (일자는 무시되고 해당 월 1일로 정규화된다) */
    private LocalDate budgetMonth;

    private BigDecimal fixedIncome;
    private BigDecimal variableIncome;
    private BigDecimal fixedExpense;
    private BigDecimal variableExpense;
    private BigDecimal plannedSavings;
    private BigDecimal actualSavings;
    private BigDecimal netWorth;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 계산 필드 (서버에서 채워 내려준다) ----

    /** 총 소득 */
    private BigDecimal totalIncome;

    /** 총 지출 */
    private BigDecimal totalExpense;

    /** 월 저축 가능 금액 (총 소득 - 총 지출) */
    private BigDecimal savingsCapacity;

    /** 저축률 (%) */
    private BigDecimal savingsRatePercent;

    /** 저축 계획 달성률 (%). 계획 금액이 없으면 null */
    private BigDecimal savingsAchievementPercent;

    /** 표시용 월 라벨 (예: 2026-07) */
    private String monthLabel;
}
