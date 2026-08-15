package com.trading.journal.service;

import com.trading.journal.dto.BudgetSummaryDto;
import com.trading.journal.dto.MonthlyBudgetDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.MonthlyBudget;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.MonthlyBudgetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>모든 조회/수정은 현재 인증 사용자로 격리된다.
 *
 * <p>실제 저축액은 저축 일지({@link SavingsRecordService})가 단일 원본이다. 그 달에 저축 기록이 하나라도 있으면 조회 시점에 기록 합계를 쓰고,
 * 없을 때만 사용자가 직접 입력한 값을 쓴다. 저장 시점에 복사하지 않으므로 기록을 추가/수정/삭제하면 예산·요약·목표 진행률에 즉시 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBudgetService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final SavingsRecordService savingsRecordService;
    private final AccountRepository accountRepository;
    private final SecurityContextService securityContextService;

    /** 월 기록 생성 또는 갱신 (같은 사용자+계좌+월이면 덮어쓴다) */
    @Transactional
    public MonthlyBudgetDto upsert(MonthlyBudgetDto dto) {
        if (dto.getBudgetMonth() == null) {
            throw new IllegalArgumentException("대상 월(budgetMonth)은 필수입니다");
        }
        LocalDate month = dto.getBudgetMonth().withDayOfMonth(1);
        Long userId = currentUserId();
        validateAccountOwnership(dto.getAccountId(), userId);

        MonthlyBudget budget =
                monthlyBudgetRepository
                        .findByUserAndAccountAndMonth(userId, dto.getAccountId(), month)
                        .orElseGet(
                                () ->
                                        MonthlyBudget.builder()
                                                .userId(userId)
                                                .accountId(dto.getAccountId())
                                                .budgetMonth(month)
                                                .build());

        budget.setFixedIncome(dto.getFixedIncome());
        budget.setVariableIncome(dto.getVariableIncome());
        budget.setFixedExpense(dto.getFixedExpense());
        budget.setVariableExpense(dto.getVariableExpense());
        budget.setPlannedSavings(dto.getPlannedSavings());
        // 직접 입력값만 저장한다. 저축 기록 합계는 저장하지 않고 조회 시점마다 계산한다.
        budget.setActualSavings(dto.getActualSavings());
        budget.setNetWorth(dto.getNetWorth());
        budget.setNotes(dto.getNotes());

        MonthlyBudget saved = monthlyBudgetRepository.save(budget);
        log.info("월 가계 기록 저장: {} (계좌 {})", saved.getBudgetMonth(), saved.getAccountId());
        return toDto(saved);
    }

    /** 단건 조회 (본인 소유만) */
    public MonthlyBudgetDto get(Long id) {
        return toDto(findOwned(id, currentUserId()));
    }

    /** 특정 월 조회 (없으면 빈 Optional) */
    public Optional<MonthlyBudgetDto> getByMonth(Long accountId, LocalDate month) {
        return monthlyBudgetRepository
                .findByUserAndAccountAndMonth(currentUserId(), accountId, month.withDayOfMonth(1))
                .map(this::toDto);
    }

    /** 현재 사용자의 전체 기록 최신순 조회 */
    public List<MonthlyBudgetDto> getAll() {
        return monthlyBudgetRepository.findAllByUserOrderByBudgetMonthDesc(currentUserId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 현재 사용자의 기간 조회 (최신순) */
    public List<MonthlyBudgetDto> getRange(
            Long accountId, LocalDate startMonth, LocalDate endMonth) {
        return monthlyBudgetRepository
                .findByUserAndMonthRange(
                        currentUserId(),
                        accountId,
                        startMonth.withDayOfMonth(1),
                        endMonth.withDayOfMonth(1))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 삭제 (본인 소유만) */
    @Transactional
    public void delete(Long id) {
        MonthlyBudget budget = findOwned(id, currentUserId());
        monthlyBudgetRepository.delete(budget);
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
                monthlyBudgetRepository.findByUserAndMonthRange(
                        currentUserId(), accountId, start, end);

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
        BigDecimal totalRate = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalPlanned = BigDecimal.ZERO;

        for (MonthlyBudget b : ascending) {
            totalIncome = totalIncome.add(b.getTotalIncome());
            totalExpense = totalExpense.add(b.getTotalExpense());
            totalCapacity = totalCapacity.add(b.getSavingsCapacity());
            totalRate = totalRate.add(b.getSavingsRatePercent());
            totalActual = totalActual.add(effectiveActualSavings(b));
            totalPlanned = totalPlanned.add(nullSafe(b.getPlannedSavings()));
        }

        BigDecimal divisor = BigDecimal.valueOf(count);
        BigDecimal averageIncome = totalIncome.divide(divisor, 0, RoundingMode.HALF_UP);
        BigDecimal averageExpense = totalExpense.divide(divisor, 0, RoundingMode.HALF_UP);
        BigDecimal averageCapacity = totalCapacity.divide(divisor, 0, RoundingMode.HALF_UP);

        // 소득 가중 비율이 아니라 월별 저축률의 단순 평균. 소득이 큰 달이 평균을 지배하지 않도록 한다.
        BigDecimal averageRate = totalRate.divide(divisor, 2, RoundingMode.HALF_UP);

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
     * 저축 목표 추적용 누적 실제 저축액 (현재 사용자 기준).
     *
     * <p>{@code GoalType.SAVINGS_AMOUNT} 목표가 참조한다. 월 단위로 저축 기록 합계를 우선하고, 기록이 없는 달만 가계 기록의 직접 입력값을
     * 더한다. 인증 사용자가 없는 컨텍스트(스케줄러 등)에서는 소유자를 특정할 수 없으므로 빈 Optional을 돌려준다.
     */
    public Optional<BigDecimal> getCumulativeActualSavings() {
        if (securityContextService.getCurrentUser().isEmpty()) {
            return Optional.empty();
        }
        Long userId = currentUserId();

        // 전 기간 월별 저축 기록 합계 (모든 계좌)
        Map<String, BigDecimal> recordTotals =
                savingsRecordService.getMonthlyTotals(null, LocalDate.EPOCH, LocalDate.now());

        BigDecimal total = recordTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // 저축 기록이 전혀 없는 달만 가계 기록의 직접 입력값을 더한다.
        for (MonthlyBudget b :
                monthlyBudgetRepository.findAllByUserOrderByBudgetMonthDesc(userId)) {
            String label = b.getBudgetMonth().format(MONTH_LABEL);
            if (!recordTotals.containsKey(label)) {
                total = total.add(nullSafe(b.getActualSavings()));
            }
        }
        return Optional.of(total);
    }

    private Long currentUserId() {
        return securityContextService.getCurrentUserId().orElse(null);
    }

    /** ID로 조회하되 현재 사용자 소유가 아니면 존재를 숨기고 404를 던진다. */
    private MonthlyBudget findOwned(Long id, Long userId) {
        MonthlyBudget budget =
                monthlyBudgetRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("월 가계 기록을 찾을 수 없습니다: " + id));
        if (!Objects.equals(budget.getUserId(), userId)) {
            throw new ResourceNotFoundException("월 가계 기록을 찾을 수 없습니다: " + id);
        }
        return budget;
    }

    /** 계좌를 지정했다면 존재하고 현재 사용자 소유인지 확인한다. */
    private void validateAccountOwnership(Long accountId, Long userId) {
        if (accountId == null) {
            return;
        }
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + accountId));
        if (account.getUserId() != null && !account.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("계좌를 찾을 수 없습니다: " + accountId);
        }
    }

    /** 유효 실제 저축액: 그 달 저축 기록 합계가 있으면 기록이 이기고, 없으면 직접 입력값. */
    private BigDecimal effectiveActualSavings(MonthlyBudget b) {
        BigDecimal fromRecords =
                savingsRecordService.getMonthlyTotal(b.getAccountId(), b.getBudgetMonth());
        return fromRecords.compareTo(BigDecimal.ZERO) > 0
                ? fromRecords
                : nullSafe(b.getActualSavings());
    }

    private MonthlyBudgetDto toDto(MonthlyBudget b) {
        BigDecimal effective = effectiveActualSavings(b);
        BigDecimal achievement = null;
        if (b.getPlannedSavings() != null
                && b.getPlannedSavings().compareTo(BigDecimal.ZERO) != 0) {
            achievement =
                    effective
                            .divide(b.getPlannedSavings(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
        }

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
                .effectiveActualSavings(effective)
                .netWorth(b.getNetWorth())
                .notes(b.getNotes())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .totalIncome(b.getTotalIncome())
                .totalExpense(b.getTotalExpense())
                .savingsCapacity(b.getSavingsCapacity())
                .savingsRatePercent(b.getSavingsRatePercent())
                .savingsAchievementPercent(achievement)
                .monthLabel(
                        b.getBudgetMonth() != null ? b.getBudgetMonth().format(MONTH_LABEL) : null)
                .build();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
