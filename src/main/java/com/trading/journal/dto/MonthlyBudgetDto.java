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

    /** 직접 입력한 실제 저축액 (수정 폼 프리필용 원본값) */
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

    /** 저축 계획 달성률 (%). 계획 금액이 없으면 null. 유효 실제 저축액 기준. */
    private BigDecimal savingsAchievementPercent;

    /**
     * 유효 실제 저축액: 그 달에 저축 일지 기록이 있으면 기록 합계, 없으면 직접 입력값.
     *
     * <p>표시/집계는 항상 이 값을 쓴다. 저축 기록이 단일 원본이므로 기록을 추가/수정/삭제하면 조회 시점에 자동 반영된다.
     */
    private BigDecimal effectiveActualSavings;

    /** 표시용 월 라벨 (예: 2026-07) */
    private String monthLabel;
}
