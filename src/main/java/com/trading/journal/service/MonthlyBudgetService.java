package com.trading.journal.service;

import com.trading.journal.dto.BudgetSummaryDto;
import com.trading.journal.dto.MonthlyBudgetDto;
import com.trading.journal.entity.MonthlyBudget;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.MonthlyBudgetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 소득/지출 점검 서비스.
 *
 * <p>월 단위로 소득·지출·저축을 기록하고 저축 여력과 총자산 추이를 계산한다. 금액이 암호화 저장되므로 모든 합계/평균은 애플리케이션에서 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBudgetService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final SavingsRecordService savingsRecordService;

    /** 월 기록 생성 또는 갱신 (같은 계좌+월이면 덮어쓴다) */
    @Transactional
    public MonthlyBudgetDto upsert(MonthlyBudgetDto dto) {
        if (dto.getBudgetMonth() == null) {
            throw new IllegalArgumentException("대상 월(budgetMonth)은 필수입니다");
        }
        LocalDate month = dto.getBudgetMonth().withDayOfMonth(1);

        MonthlyBudget budget =
                monthlyBudgetRepository
                        .findByAccountIdOrNullAndBudgetMonth(dto.getAccountId(), month)
                        .orElseGet(
                                () ->
                                        MonthlyBudget.builder()
                                                .accountId(dto.getAccountId())
                                                .budgetMonth(month)
                                                .build());

        budget.setFixedIncome(dto.getFixedIncome());
        budget.setVariableIncome(dto.getVariableIncome());
        budget.setFixedExpense(dto.getFixedExpense());
        budget.setVariableExpense(dto.getVariableExpense());
        budget.setPlannedSavings(dto.getPlannedSavings());
        budget.setActualSavings(
                resolveActualSavings(dto.getAccountId(), month, dto.getActualSavings()));
        budget.setNetWorth(dto.getNetWorth());
        budget.setNotes(dto.getNotes());

        MonthlyBudget saved = monthlyBudgetRepository.save(budget);
        log.info("월 가계 기록 저장: {} (계좌 {})", saved.getBudgetMonth(), saved.getAccountId());
        return toDto(saved);
    }

    /** 단건 조회 */
    public MonthlyBudgetDto get(Long id) {
        return toDto(
                monthlyBudgetRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("월 가계 기록을 찾을 수 없습니다: " + id)));
    }

    /** 특정 월 조회 (없으면 빈 Optional) */
    public Optional<MonthlyBudgetDto> getByMonth(Long accountId, LocalDate month) {
        return monthlyBudgetRepository
                .findByAccountIdOrNullAndBudgetMonth(accountId, month.withDayOfMonth(1))
                .map(this::toDto);
    }

    /** 전체 기록 최신순 조회 */
    public List<MonthlyBudgetDto> getAll() {
        return monthlyBudgetRepository.findAllByOrderByBudgetMonthDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 기간 조회 (최신순) */
    public List<MonthlyBudgetDto> getRange(
            Long accountId, LocalDate startMonth, LocalDate endMonth) {
        return monthlyBudgetRepository
                .findByAccountIdAndMonthRange(
                        accountId, startMonth.withDayOfMonth(1), endMonth.withDayOfMonth(1))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 삭제 */
    @Transactional
    public void delete(Long id) {
        if (!monthlyBudgetRepository.existsById(id)) {
            throw new ResourceNotFoundException("월 가계 기록을 찾을 수 없습니다: " + id);
        }
        monthlyBudgetRepository.deleteById(id);
        log.info("월 가계 기록 삭제: {}", id);
    }

    /**
     * 최근 {@code months}개월 요약.
     *
     * @param months 집계 대상 개월 수 (1 이상)
     */
    public BudgetSummaryDto getSummary(Long accountId, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("집계 개월 수는 1 이상이어야 합니다: " + months);
        }
        LocalDate end = LocalDate.now().withDayOfMonth(1);
        LocalDate start = end.minusMonths(months - 1L);

        List<MonthlyBudget> records =
                monthlyBudgetRepository.findByAccountIdAndMonthRange(accountId, start, end);

        if (records.isEmpty()) {
            return BudgetSummaryDto.builder()
                    .monthsCounted(0)
                    .averageIncome(BigDecimal.ZERO)
                    .averageExpense(BigDecimal.ZERO)
                    .averageSavingsCapacity(BigDecimal.ZERO)
                    .averageSavingsRatePercent(BigDecimal.ZERO)
                    .totalActualSavings(BigDecimal.ZERO)
                    .totalPlannedSavings(BigDecimal.ZERO)
                    .trend(List.of())
                    .build();
        }

        List<MonthlyBudget> ascending = new ArrayList<>(records);
        ascending.sort(Comparator.comparing(MonthlyBudget::getBudgetMonth));

        int count = ascending.size();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalCapacity = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalPlanned = BigDecimal.ZERO;

        for (MonthlyBudget b : ascending) {
            totalIncome = totalIncome.add(b.getTotalIncome());
            totalExpense = totalExpense.add(b.getTotalExpense());
            totalCapacity = totalCapacity.add(b.getSavingsCapacity());
            totalActual = totalActual.add(nullSafe(b.getActualSavings()));
            totalPlanned = totalPlanned.add(nullSafe(b.getPlannedSavings()));
        }

        BigDecimal divisor = BigDecimal.valueOf(count);
        BigDecimal averageIncome = totalIncome.divide(divisor, 0, RoundingMode.HALF_UP);
        BigDecimal averageExpense = totalExpense.divide(divisor, 0, RoundingMode.HALF_UP);
        BigDecimal averageCapacity = totalCapacity.divide(divisor, 0, RoundingMode.HALF_UP);

        BigDecimal averageRate =
                totalIncome.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : totalCapacity
                                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal achievement =
                totalPlanned.compareTo(BigDecimal.ZERO) == 0
                        ? null
                        : totalActual
                                .divide(totalPlanned, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);

        List<MonthlyBudget> withNetWorth =
                ascending.stream().filter(b -> b.getNetWorth() != null).toList();
        BigDecimal netWorthChange =
                withNetWorth.size() >= 2
                        ? withNetWorth
                                .get(withNetWorth.size() - 1)
                                .getNetWorth()
                                .subtract(withNetWorth.get(0).getNetWorth())
                        : null;

        return BudgetSummaryDto.builder()
                .monthsCounted(count)
                .latest(toDto(ascending.get(count - 1)))
                .averageIncome(averageIncome)
                .averageExpense(averageExpense)
                .averageSavingsCapacity(averageCapacity)
                .averageSavingsRatePercent(averageRate)
                .totalActualSavings(totalActual)
                .totalPlannedSavings(totalPlanned)
                .savingsAchievementPercent(achievement)
                .netWorthChange(netWorthChange)
                .trend(ascending.stream().map(this::toDto).collect(Collectors.toList()))
                .build();
    }

    /**
     * 저축 목표 추적용 누적 실제 저축액.
     *
     * <p>{@code GoalType.SAVINGS_AMOUNT} 목표가 참조한다. 기록이 없으면 0.
     */
    public BigDecimal getCumulativeActualSavings() {
        return monthlyBudgetRepository.findAll().stream()
                .map(b -> nullSafe(b.getActualSavings()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 해당 월의 실제 저축액을 결정한다.
     *
     * <p>저축 일지에 그 달의 기록이 하나라도 있으면 기록 합계가 이긴다. 개별 기록을 남기기 시작한 뒤에도 월 합계를 손으로 관리하게 두면 두 값이 어긋나기 때문이다.
     * 기록이 없으면 사용자가 입력한 값을 그대로 쓴다.
     */
    private BigDecimal resolveActualSavings(
            Long accountId, LocalDate month, BigDecimal manualValue) {
        BigDecimal fromRecords = savingsRecordService.getMonthlyTotal(accountId, month);
        return fromRecords.compareTo(BigDecimal.ZERO) > 0 ? fromRecords : manualValue;
    }

    private MonthlyBudgetDto toDto(MonthlyBudget b) {
        return MonthlyBudgetDto.builder()
                .id(b.getId())
                .accountId(b.getAccountId())
                .budgetMonth(b.getBudgetMonth())
                .fixedIncome(b.getFixedIncome())
                .variableIncome(b.getVariableIncome())
                .fixedExpense(b.getFixedExpense())
                .variableExpense(b.getVariableExpense())
                .plannedSavings(b.getPlannedSavings())
                .actualSavings(b.getActualSavings())
                .netWorth(b.getNetWorth())
                .notes(b.getNotes())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .totalIncome(b.getTotalIncome())
                .totalExpense(b.getTotalExpense())
                .savingsCapacity(b.getSavingsCapacity())
                .savingsRatePercent(b.getSavingsRatePercent())
                .savingsAchievementPercent(b.getSavingsAchievementPercent())
                .monthLabel(
                        b.getBudgetMonth() != null ? b.getBudgetMonth().format(MONTH_LABEL) : null)
                .build();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
