package com.trading.journal.entity;

import com.trading.journal.security.converter.EncryptedBigDecimalConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 월별 소득/지출 점검 및 저축 여력 기록.
 *
 * <p>매월 1일 갱신하는 가계 스냅샷이다. 투자 실적(포트폴리오)과 달리 "얼마를 벌어서 얼마를 쓰고 얼마를 투자에 넣을 수 있는가"를 추적하며, 저축 목표({@link
 * GoalType#SAVINGS_AMOUNT})의 실적 근거가 된다.
 *
 * <p>금액 컬럼은 다른 자산 금액과 동일하게 {@link EncryptedBigDecimalConverter}로 암호화 저장되므로 DB 레벨 집계(SUM/AVG)를 걸 수
 * 없다. 월 단위 레코드라 건수가 적어 애플리케이션에서 집계한다.
 */
@Entity
@Table(
        name = "monthly_budgets",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"user_id", "account_id", "budget_month"}),
        indexes = {
            @Index(name = "idx_budget_month", columnList = "budget_month"),
            @Index(name = "idx_budget_account", columnList = "account_id"),
            @Index(name = "idx_budget_user", columnList = "user_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 ID. 모든 조회/수정은 이 값으로 격리된다. */
    @Column(name = "user_id")
    private Long userId;

    /** 계좌 ID (null이면 전체 계좌 통합 가계) */
    @Column(name = "account_id")
    private Long accountId;

    /** 대상 월 (항상 해당 월 1일로 정규화해 저장한다) */
    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    /** 월 고정 소득 (급여 등) */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal fixedIncome;

    /** 월 변동 소득 (상여, 부수입 등) */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal variableIncome;

    /** 월 고정 지출 (주거비, 보험료 등) */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal fixedExpense;

    /** 월 변동 지출 (생활비 등) */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal variableExpense;

    /** 계획한 저축/투자 금액 */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal plannedSavings;

    /** 실제 저축/투자한 금액 */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal actualSavings;

    /** 월말 기준 총 자산 스냅샷 (현금 + 투자자산 등 사용자가 기입) */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(columnDefinition = "TEXT")
    private BigDecimal netWorth;

    /** 메모 */
    @Column(length = 2000)
    private String notes;

    /** 생성 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        normalizeMonth();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeMonth();
        updatedAt = LocalDateTime.now();
    }

    private void normalizeMonth() {
        if (budgetMonth != null) {
            budgetMonth = budgetMonth.withDayOfMonth(1);
        }
    }

    /** 총 소득 (고정 + 변동) */
    public BigDecimal getTotalIncome() {
        return nullSafe(fixedIncome).add(nullSafe(variableIncome));
    }

    /** 총 지출 (고정 + 변동) */
    public BigDecimal getTotalExpense() {
        return nullSafe(fixedExpense).add(nullSafe(variableExpense));
    }

    /** 월 저축 가능 금액 = 총 소득 - 총 지출. 음수면 적자를 그대로 노출한다. */
    public BigDecimal getSavingsCapacity() {
        return getTotalIncome().subtract(getTotalExpense());
    }

    /** 저축률 (%) = 저축 가능 금액 / 총 소득 * 100. 소득이 0이면 0을 돌려준다. */
    public BigDecimal getSavingsRatePercent() {
        BigDecimal income = getTotalIncome();
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getSavingsCapacity()
                .divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 저축 계획 달성률 (%) = 실제 저축 / 계획 저축 * 100. 계획이 없으면 null. */
    public BigDecimal getSavingsAchievementPercent() {
        if (plannedSavings == null || plannedSavings.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return nullSafe(actualSavings)
                .divide(plannedSavings, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
