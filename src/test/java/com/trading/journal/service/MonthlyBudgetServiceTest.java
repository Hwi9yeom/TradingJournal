package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.BudgetSummaryDto;
import com.trading.journal.dto.MonthlyBudgetDto;
import com.trading.journal.entity.MonthlyBudget;
import com.trading.journal.entity.User;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.MonthlyBudgetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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

    private static final Long USER_ID = 10L;

    @Mock private MonthlyBudgetRepository monthlyBudgetRepository;
    @Mock private SavingsRecordService savingsRecordService;
    @Mock private AccountRepository accountRepository;
    @Mock private SecurityContextService securityContextService;

    @InjectMocks private MonthlyBudgetService monthlyBudgetService;

    @BeforeEach
    void setUpCurrentUser() {
        lenient().when(securityContextService.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
        lenient()
                .when(savingsRecordService.getMonthlyTotal(any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    private static MonthlyBudget budget(
            LocalDate month, String income, String expense, String planned, String actual) {
        return MonthlyBudget.builder()
                .userId(USER_ID)
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
    }

    @Nested
    @DisplayName("월 기록 저장")
    class UpsertTests {

        @Test
        @DisplayName("대상 월은 항상 1일로 정규화되고 현재 사용자 소유로 저장된다")
        void upsert_NormalizesMonthAndStampsOwner() {
            when(monthlyBudgetRepository.findByUserAndAccountAndMonth(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MonthlyBudgetDto result =
                    monthlyBudgetService.upsert(
                            MonthlyBudgetDto.builder()
                                    .budgetMonth(LocalDate.of(2026, 7, 26))
                                    .fixedIncome(new BigDecimal("4000000"))
                                    .build());

            assertThat(result.getBudgetMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(result.getMonthLabel()).isEqualTo("2026-07");
            verify(monthlyBudgetRepository)
                    .findByUserAndAccountAndMonth(USER_ID, null, LocalDate.of(2026, 7, 1));

            ArgumentCaptor<MonthlyBudget> captor = ArgumentCaptor.forClass(MonthlyBudget.class);
            verify(monthlyBudgetRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("같은 사용자·계좌·월 기록이 있으면 새로 만들지 않고 덮어쓴다")
        void upsert_UpdatesExistingRecord() {
            MonthlyBudget existing =
                    budget(LocalDate.of(2026, 7, 1), "3000000", "1000000", null, null);
            existing.setId(7L);
            when(monthlyBudgetRepository.findByUserAndAccountAndMonth(eq(USER_ID), eq(null), any()))
                    .thenReturn(Optional.of(existing));
            when(monthlyBudgetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            monthlyBudgetService.upsert(
                    MonthlyBudgetDto.builder()
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
        @DisplayName("직접 입력값은 저축 기록 합계로 덮어쓰지 않고 그대로 저장된다")
        void upsert_StoresManualValueUnchanged() {
            when(monthlyBudgetRepository.findByUserAndAccountAndMonth(any(), any(), any()))
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

            // 저장된 원본값은 유지하되, 유효값은 기록 합계를 쓴다.
            assertThat(result.getActualSavings()).isEqualByComparingTo("500000");
            assertThat(result.getEffectiveActualSavings()).isEqualByComparingTo("1800000");
        }
    }

    @Nested
    @DisplayName("유효 실제 저축액 (읽기 시점 집계)")
    class EffectiveActualSavingsTests {

        @Test
        @DisplayName("그 달 저축 기록이 있으면 조회 시점에 기록 합계가 반영된다")
        void read_ReflectsSavingsRecordsAtReadTime() {
            MonthlyBudget b = budget(LocalDate.of(2026, 7, 1), "0", "0", "2000000", "500000");
            b.setId(1L);
            when(monthlyBudgetRepository.findById(1L)).thenReturn(Optional.of(b));
            when(savingsRecordService.getMonthlyTotal(null, LocalDate.of(2026, 7, 1)))
                    .thenReturn(new BigDecimal("1500000"));

            MonthlyBudgetDto dto = monthlyBudgetService.get(1L);

            assertThat(dto.getEffectiveActualSavings()).isEqualByComparingTo("1500000");
            // 달성률도 유효값 기준으로 계산된다.
            assertThat(dto.getSavingsAchievementPercent()).isEqualByComparingTo("75.00");
        }

        @Test
        @DisplayName("저축 기록이 없으면 직접 입력값이 유효값이 된다")
        void read_FallsBackToManualValue() {
            MonthlyBudget b = budget(LocalDate.of(2026, 7, 1), "0", "0", null, "500000");
            b.setId(1L);
            when(monthlyBudgetRepository.findById(1L)).thenReturn(Optional.of(b));

            assertThat(monthlyBudgetService.get(1L).getEffectiveActualSavings())
                    .isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("요약 집계")
    class SummaryTests {

        @Test
        @DisplayName("기록이 없으면 0으로 채운 빈 요약을 돌려준다")
        void summary_EmptyWhenNoRecords() {
            when(monthlyBudgetRepository.findByUserAndMonthRange(any(), any(), any(), any()))
                    .thenReturn(List.of());

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getMonthsCounted()).isZero();
            assertThat(summary.getLatest()).isNull();
            assertThat(summary.getAverageIncome()).isEqualByComparingTo("0");
            assertThat(summary.getTrend()).isEmpty();
        }

        @Test
        @DisplayName("평균 저축률은 소득 가중이 아니라 월별 저축률의 단순 평균이다")
        void summary_AveragesMonthlyRatesNotIncomeWeighted() {
            // 6월: 소득 400만/지출 200만 -> 50.00%, 5월: 소득 600만/지출 200만 -> 66.67%
            // 단순 평균 = 58.34%. (소득 가중이면 (200+400)/(400+600)=60.00%)
            when(monthlyBudgetRepository.findByUserAndMonthRange(any(), any(), any(), any()))
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
            assertThat(summary.getAverageSavingsRatePercent()).isEqualByComparingTo("58.34");
            assertThat(summary.getTotalActualSavings()).isEqualByComparingTo("4000000");
            assertThat(summary.getSavingsAchievementPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("요약 누계에도 저축 기록 합계가 우선 반영된다")
        void summary_UsesEffectiveActualSavings() {
            when(monthlyBudgetRepository.findByUserAndMonthRange(any(), any(), any(), any()))
                    .thenReturn(
                            List.of(budget(LocalDate.of(2026, 6, 1), "0", "0", null, "500000")));
            when(savingsRecordService.getMonthlyTotal(null, LocalDate.of(2026, 6, 1)))
                    .thenReturn(new BigDecimal("2000000"));

            assertThat(monthlyBudgetService.getSummary(null, 12).getTotalActualSavings())
                    .isEqualByComparingTo("2000000");
        }

        @Test
        @DisplayName("추이는 오래된 달부터 정렬되고 최신 기록이 latest가 된다")
        void summary_TrendIsChronological() {
            when(monthlyBudgetRepository.findByUserAndMonthRange(any(), any(), any(), any()))
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
            when(monthlyBudgetRepository.findByUserAndMonthRange(any(), any(), any(), any()))
                    .thenReturn(List.of(newer, older));

            BudgetSummaryDto summary = monthlyBudgetService.getSummary(null, 12);

            assertThat(summary.getNetWorthChange()).isEqualByComparingTo("12000000");
        }

        @Test
        @DisplayName("집계 개월 수가 0 이하면 예외")
        void summary_RejectsNonPositiveMonths() {
            assertThatThrownBy(() -> monthlyBudgetService.getSummary(null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("조회/삭제/격리")
    class LookupTests {

        @Test
        @DisplayName("없는 기록 조회 시 예외")
        void get_NotFound() {
            when(monthlyBudgetRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> monthlyBudgetService.get(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("다른 사용자의 기록은 조회/삭제할 수 없다 (존재를 숨기고 404)")
        void getAndDelete_HideForeignRecord() {
            MonthlyBudget foreign = budget(LocalDate.of(2026, 6, 1), "0", "0", null, null);
            foreign.setUserId(999L);
            foreign.setId(5L);
            when(monthlyBudgetRepository.findById(5L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> monthlyBudgetService.get(5L))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> monthlyBudgetService.delete(5L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(monthlyBudgetRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("실제 저축 누계 (저축 목표 추적)")
    class CumulativeTests {

        @Test
        @DisplayName("기록이 있는 달은 기록 합계, 없는 달만 직접 입력값을 더한다")
        void cumulative_RecordsFirstThenManualFallback() {
            when(securityContextService.getCurrentUser())
                    .thenReturn(Optional.of(User.builder().id(USER_ID).build()));
            Map<String, BigDecimal> recordTotals = new LinkedHashMap<>();
            recordTotals.put("2026-05", new BigDecimal("1000000"));
            recordTotals.put("2026-06", new BigDecimal("1200000"));
            when(savingsRecordService.getMonthlyTotals(any(), any(), any()))
                    .thenReturn(recordTotals);
            when(monthlyBudgetRepository.findAllByUserOrderByBudgetMonthDesc(USER_ID))
                    .thenReturn(
                            List.of(
                                    // 5월: 기록이 있으므로 직접 입력값 999만은 무시
                                    budget(LocalDate.of(2026, 5, 1), "0", "0", null, "9990000"),
                                    // 7월: 기록이 없으므로 직접 입력값 250만 합산
                                    budget(LocalDate.of(2026, 7, 1), "0", "0", null, "2500000")));

            Optional<BigDecimal> total = monthlyBudgetService.getCumulativeActualSavings();

            assertThat(total).isPresent();
            assertThat(total.get()).isEqualByComparingTo("4700000");
        }

        @Test
        @DisplayName("인증 사용자가 없으면(스케줄러 등) 빈 Optional을 돌려준다")
        void cumulative_EmptyWithoutAuthenticatedUser() {
            when(securityContextService.getCurrentUser()).thenReturn(Optional.empty());

            assertThat(monthlyBudgetService.getCumulativeActualSavings()).isEmpty();
        }
    }
}
