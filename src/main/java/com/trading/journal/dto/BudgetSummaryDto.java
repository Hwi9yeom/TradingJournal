package com.trading.journal.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 가계 점검 요약 DTO (최근 N개월 기준) */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetSummaryDto {

    /** 집계에 사용된 개월 수 */
    private int monthsCounted;

    /** 가장 최근 기록 (없으면 null) */
    private MonthlyBudgetDto latest;

    /** 월평균 총 소득 */
    private BigDecimal averageIncome;

    /** 월평균 총 지출 */
    private BigDecimal averageExpense;

    /** 월평균 저축 가능 금액 */
    private BigDecimal averageSavingsCapacity;

    /** 월평균 저축률 (%) */
    private BigDecimal averageSavingsRatePercent;

    /** 실제 저축 누계 */
    private BigDecimal totalActualSavings;

    /** 계획 저축 누계 */
    private BigDecimal totalPlannedSavings;

    /** 계획 대비 실제 저축 달성률 (%). 계획 누계가 0이면 null */
    private BigDecimal savingsAchievementPercent;

    /** 총자산 변화량 (가장 오래된 스냅샷 → 최신 스냅샷). 스냅샷이 2개 미만이면 null */
    private BigDecimal netWorthChange;

    /** 월별 추이 (오래된 달 → 최신 달) */
    private List<MonthlyBudgetDto> trend;
}
