package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.BudgetSummaryDto;
import com.trading.journal.dto.MonthlyBudgetDto;
import com.trading.journal.entity.MonthlyBudget;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.MonthlyBudgetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("월별 가계 점검 서비스 테스트")
class MonthlyBudgetServiceTest {

    @Mock private MonthlyBudgetRepository monthlyBudgetRepository;
    @Mock private SavingsRecordService savingsRecordService;

    @InjectMocks private MonthlyBudgetService monthlyBudgetService;

    private static MonthlyBudget budget(
            LocalDate month, String income, String expense, String planned, String actual) {
        return MonthlyBudget.builder()
                .budgetMonth(month)
                .fixedIncome(new BigDecimal(income))
                .fixedExpense(new BigDecimal(expense))
                .plannedSavings(planned != null ? new BigDecimal(planned) : null)
                .actualSavings(actual != null ? new BigDecimal(actual) : null)
                .build();
    }

    @Nested
    @DisplayName("저축 여력 계산")
    class SavingsCapacityTests {

        @Test
        @DisplayName("저축 가능 금액 = 총 소득 - 총 지출")
        void savingsCapacity_SubtractsExpenseFromIncome() {
            MonthlyBudget b =
                    MonthlyBudget.builder()
                            .fixedIncome(new BigDecimal("4000000"))
                            .variableIncome(new BigDecimal("500000"))
                            .fixedExpense(new BigDecimal("1500000"))
                            .variableExpense(new BigDecimal("1000000"))
                            .build();

            assertThat(b.getTotalIncome()).isEqualByComparingTo("4500000");
            assertThat(b.getTotalExpense()).isEqualByComparingTo("2500000");
            assertThat(b.getSavingsCapacity()).isEqualByComparingTo("2000000");
            assertThat(b.getSavingsRatePercent()).isEqualByComparingTo("44.44");
        }

        @Test
        @DisplayName("지출이 소득을 넘으면 적자를 음수로 노출한다")
        void savingsCapacity_AllowsDeficit() {
            MonthlyBudget b =
                    MonthlyBudget.builder()
                            .fixedIncome(new BigDecimal("3000000"))
                            .fixedExpense(new BigDecimal("3500000"))
                            .build();

            assertThat(b.getSavingsCapacity()).isEqualByComparingTo("-500000");
            assertThat(b.getSavingsRatePercent()).isNegative();
        }

        @Test
        @DisplayName("소득이 0이면 저축률은 0으로 처리한다")
        void savingsRate_ZeroIncomeIsZero() {
            MonthlyBudget b = MonthlyBudget.builder().fixedExpense(new BigDecimal("100")).build();

            assertThat(b.getSavingsRatePercent()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("계획 저축이 없으면 달성률은 null")
        void achievement_NullWithoutPlan() {
            MonthlyBudget b =
                    MonthlyBudget.builder().actualSavings(new BigDecimal("1000000")).build();

            assertThat(b.getSavingsAchievementPercent()).isNull();
        }

        @Test
        @DisplayName("계획 대비 실제 저축 달성률 계산")
        void achievement_ComputesPercent() {
            MonthlyBudget b =
                    MonthlyBudget.builder()
                            .plannedSavings(new BigDecimal("2000000"))
                            .actualSavings(new BigDecimal("1500000"))
                            .build();

            assertThat(b.getSavingsAchievementPercent()).isEqualByComparingTo("75.00");
        }
    }

    @Nested
    @DisplayName("월 기록 저장")
    class UpsertTests {

        @Test
        @DisplayName("대상 월은 항상 1일로 정규화된다")
        void upsert_NormalizesMonthToFirstDay() {
            when(savingsRecordService.getMonthlyTotal(any(), any())).thenReturn(BigDecimal.ZERO);
            when(monthlyBudgetRepository.findByAccountIdOrNullAndBudgetMonth(any(), any()))
                    .thenReturn(Optional.empty());
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MonthlyBudgetDto dto =
                    MonthlyBudgetDto.builder()
                            .budgetMonth(LocalDate.of(2026, 7, 26))
                            .fixedIncome(new BigDecimal("4000000"))
                            .build();

            MonthlyBudgetDto result = monthlyBudgetService.upsert(dto);

            assertThat(result.getBudgetMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(result.getMonthLabel()).isEqualTo("2026-07");
            verify(monthlyBudgetRepository)
                    .findByAccountIdOrNullAndBudgetMonth(null, LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("같은 계좌·월 기록이 있으면 새로 만들지 않고 덮어쓴다")
        void upsert_UpdatesExistingRecord() {
            MonthlyBudget existing =
                    budget(LocalDate.of(2026, 7, 1), "3000000", "1000000", null, null);
            existing.setId(7L);
            when(savingsRecordService.getMonthlyTotal(any(), any())).thenReturn(BigDecimal.ZERO);
            when(monthlyBudgetRepository.findByAccountIdOrNullAndBudgetMonth(eq(1L), any()))
                    .thenReturn(Optional.of(existing));
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            monthlyBudgetService.upsert(
                    MonthlyBudgetDto.builder()
                            .accountId(1L)
                            .budgetMonth(LocalDate.of(2026, 7, 1))
                            .fixedIncome(new BigDecimal("5000000"))
                            .fixedExpense(new BigDecimal("2000000"))
                            .build());

            ArgumentCaptor<MonthlyBudget> captor = ArgumentCaptor.forClass(MonthlyBudget.class);
            verify(monthlyBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(7L);
            assertThat(captor.getValue().getFixedIncome()).isEqualByComparingTo("5000000");
            assertThat(captor.getValue().getSavingsCapacity()).isEqualByComparingTo("3000000");
        }

        @Test
        @DisplayName("대상 월이 없으면 예외")
        void upsert_RequiresMonth() {
            assertThatThrownBy(
                            () ->
                                    monthlyBudgetService.upsert(
                                            MonthlyBudgetDto.builder()
                                                    .fixedIncome(BigDecimal.ONE)
                                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("budgetMonth");
        }

        @Test
        @DisplayName("저축 일지에 기록이 있으면 그 합계가 수동 입력값을 대체한다")
        void upsert_SavingsRecordsOverrideManualValue() {
            when(monthlyBudgetRepository.findByAccountIdOrNullAndBudgetMonth(any(), any()))
                    .thenReturn(Optional.empty());
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(savingsRecordService.getMonthlyTotal(null, LocalDate.of(2026, 7, 1)))
                    .thenReturn(new BigDecimal("1800000"));

            MonthlyBudgetDto result =
                    monthlyBudgetService.upsert(
                            MonthlyBudgetDto.builder()
                                    .budgetMonth(LocalDate.of(2026, 7, 1))
                                    .actualSavings(new BigDecimal("500000"))
                                    .build());

            assertThat(result.getActualSavings()).isEqualByComparingTo("1800000");
        }

        @Test
        @DisplayName("저축 일지가 비어 있으면 수동 입력값을 그대로 쓴다")
        void upsert_KeepsManualValueWithoutRecords() {
            when(monthlyBudgetRepository.findByAccountIdOrNullAndBudgetMonth(any(), any()))
                    .thenReturn(Optional.empty());
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(savingsRecordService.getMonthlyTotal(any(), any())).thenReturn(BigDecimal.ZERO);

            MonthlyBudgetDto result =
                    monthlyBudgetService.upsert(
                            MonthlyBudgetDto.builder()
                                    .budgetMonth(LocalDate.of(2026, 7, 1))
                                    .actualSavings(new BigDecimal("500000"))
                                    .build());

            assertThat(result.getActualSavings()).isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("요약 집계")
    class SummaryTests {

        @Test
        @DisplayName("기록이 없으면 0으로 채운 빈 요약을 돌려준다")
        void summary_EmptyWhenNoRecords() {
            when(monthlyBudgetRepository.findByAccountIdAndMonthRange(any(), any(), any()))
                    .thenReturn(List.of());

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getMonthsCounted()).isZero();
            assertThat(summary.getLatest()).isNull();
            assertThat(summary.getAverageIncome()).isEqualByComparingTo("0");
            assertThat(summary.getTrend()).isEmpty();
        }

        @Test
        @DisplayName("평균 소득/지출/저축률과 계획 달성률을 집계한다")
        void summary_AggregatesAverages() {
            when(monthlyBudgetRepository.findByAccountIdAndMonthRange(any(), any(), any()))
                    .thenReturn(
                            List.of(
                                    budget(
                                            LocalDate.of(2026, 6, 1),
                                            "4000000",
                                            "2000000",
                                            "2000000",
                                            "1000000"),
                                    budget(
                                            LocalDate.of(2026, 5, 1),
                                            "6000000",
                                            "2000000",
                                            "2000000",
                                            "3000000")));

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getMonthsCounted()).isEqualTo(2);
            assertThat(summary.getAverageIncome()).isEqualByComparingTo("5000000");
            assertThat(summary.getAverageExpense()).isEqualByComparingTo("2000000");
            assertThat(summary.getAverageSavingsCapacity()).isEqualByComparingTo("3000000");
            assertThat(summary.getAverageSavingsRatePercent()).isEqualByComparingTo("60.00");
            assertThat(summary.getTotalActualSavings()).isEqualByComparingTo("4000000");
            assertThat(summary.getSavingsAchievementPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("추이는 오래된 달부터 정렬되고 최신 기록이 latest가 된다")
        void summary_TrendIsChronological() {
            when(monthlyBudgetRepository.findByAccountIdAndMonthRange(any(), any(), any()))
                    .thenReturn(
                            List.of(
                                    budget(LocalDate.of(2026, 6, 1), "1", "0", null, null),
                                    budget(LocalDate.of(2026, 4, 1), "2", "0", null, null),
                                    budget(LocalDate.of(2026, 5, 1), "3", "0", null, null)));

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getTrend())
                    .extracting(MonthlyBudgetDto::getMonthLabel)
                    .containsExactly("2026-04", "2026-05", "2026-06");
            assertThat(summary.getLatest().getMonthLabel()).isEqualTo("2026-06");
        }

        @Test
        @DisplayName("총자산 스냅샷이 2개 이상이면 변화량을 계산한다")
        void summary_NetWorthChange() {
            MonthlyBudget older = budget(LocalDate.of(2026, 4, 1), "0", "0", null, null);
            older.setNetWorth(new BigDecimal("50000000"));
            MonthlyBudget newer = budget(LocalDate.of(2026, 6, 1), "0", "0", null, null);
            newer.setNetWorth(new BigDecimal("62000000"));
            when(monthlyBudgetRepository.findByAccountIdAndMonthRange(any(), any(), any()))
                    .thenReturn(List.of(newer, older));

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getNetWorthChange()).isEqualByComparingTo("12000000");
        }

        @Test
        @DisplayName("총자산 스냅샷이 하나뿐이면 변화량은 null")
        void summary_NetWorthChangeNullWithSingleSnapshot() {
            MonthlyBudget only = budget(LocalDate.of(2026, 6, 1), "0", "0", null, null);
            only.setNetWorth(new BigDecimal("50000000"));
            when(monthlyBudgetRepository.findByAccountIdAndMonthRange(any(), any(), any()))
                    .thenReturn(List.of(only));

            assertThat(monthlyBudgetService.getSummary(null, 12).getNetWorthChange()).isNull();
        }

        @Test
        @DisplayName("집계 개월 수가 0 이하면 예외")
        void summary_RejectsNonPositiveMonths() {
            assertThatThrownBy(() -> monthlyBudgetService.getSummary(null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("조회/삭제")
    class LookupTests {

        @Test
        @DisplayName("없는 기록 조회 시 예외")
        void get_NotFound() {
            when(monthlyBudgetRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> monthlyBudgetService.get(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("없는 기록 삭제 시 예외")
        void delete_NotFound() {
            when(monthlyBudgetRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> monthlyBudgetService.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(monthlyBudgetRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("실제 저축 누계는 전체 기록을 합산한다")
        void cumulativeActualSavings_SumsAllRecords() {
            when(monthlyBudgetRepository.findAll())
                    .thenReturn(
                            List.of(
                                    budget(LocalDate.of(2026, 5, 1), "0", "0", null, "1000000"),
                                    budget(LocalDate.of(2026, 6, 1), "0", "0", null, null),
                                    budget(LocalDate.of(2026, 7, 1), "0", "0", null, "2500000")));

            assertThat(monthlyBudgetService.getCumulativeActualSavings())
                    .isEqualByComparingTo("3500000");
        }
    }
}
