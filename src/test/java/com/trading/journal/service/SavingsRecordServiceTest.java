package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.SavingsRecordDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.SavingsCategory;
import com.trading.journal.entity.SavingsRecord;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.SavingsRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@DisplayName("저축 기록 서비스 테스트")
class SavingsRecordServiceTest {

    private static final Long USER_ID = 10L;

    @Mock private SavingsRecordRepository savingsRecordRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private SecurityContextService securityContextService;

    @InjectMocks private SavingsRecordService savingsRecordService;

    @BeforeEach
    void setUpCurrentUser() {
        lenient().when(securityContextService.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
    }

    private static SavingsRecord record(LocalDate date, String amount, SavingsCategory category) {
        return SavingsRecord.builder()
                .userId(USER_ID)
                .savedDate(date)
                .amount(new BigDecimal(amount))
                .category(category)
                .build();
    }

    @Nested
    @DisplayName("생성/검증")
    class CreateTests {

        @Test
        @DisplayName("분류 미지정 시 정기 저축으로 저장된다")
        void create_DefaultsToRegularSavings() {
            when(savingsRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SavingsRecordDto result =
                    savingsRecordService.create(
                            SavingsRecordDto.builder()
                                    .savedDate(LocalDate.of(2026, 7, 10))
                                    .amount(new BigDecimal("1000000"))
                                    .build());

            assertThat(result.getCategory()).isEqualTo(SavingsCategory.REGULAR_SAVINGS);
            assertThat(result.getCategoryLabel()).isEqualTo("정기 저축");
            assertThat(result.getMonthLabel()).isEqualTo("2026-07");
        }

        @Test
        @DisplayName("생성 시 현재 사용자를 소유자로 저장한다")
        void create_StampsCurrentUserAsOwner() {
            when(savingsRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            savingsRecordService.create(
                    SavingsRecordDto.builder()
                            .savedDate(LocalDate.of(2026, 7, 10))
                            .amount(new BigDecimal("1000000"))
                            .build());

            ArgumentCaptor<SavingsRecord> captor = ArgumentCaptor.forClass(SavingsRecord.class);
            verify(savingsRecordRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("다른 사용자의 계좌를 지정하면 거부한다")
        void create_RejectsForeignAccount() {
            Account foreign = new Account();
            foreign.setUserId(999L);
            when(accountRepository.findById(5L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(
                            () ->
                                    savingsRecordService.create(
                                            SavingsRecordDto.builder()
                                                    .accountId(5L)
                                                    .savedDate(LocalDate.of(2026, 7, 1))
                                                    .amount(BigDecimal.TEN)
                                                    .build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(savingsRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("저축일이 없으면 예외")
        void create_RequiresDate() {
            assertThatThrownBy(
                            () ->
                                    savingsRecordService.create(
                                            SavingsRecordDto.builder()
                                                    .amount(BigDecimal.TEN)
                                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("savedDate");
        }

        @Test
        @DisplayName("금액이 0 이하이면 예외")
        void create_RejectsNonPositiveAmount() {
            assertThatThrownBy(
                            () ->
                                    savingsRecordService.create(
                                            SavingsRecordDto.builder()
                                                    .savedDate(LocalDate.of(2026, 7, 1))
                                                    .amount(BigDecimal.ZERO)
                                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0보다");
            verify(savingsRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("수정 시 분류를 비워 보내면 기존 분류를 유지한다")
        void update_KeepsCategoryWhenNull() {
            SavingsRecord existing =
                    record(LocalDate.of(2026, 7, 1), "500000", SavingsCategory.PENSION);
            existing.setId(3L);
            when(savingsRecordRepository.findById(3L)).thenReturn(Optional.of(existing));
            when(savingsRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            savingsRecordService.update(
                    3L,
                    SavingsRecordDto.builder()
                            .savedDate(LocalDate.of(2026, 7, 5))
                            .amount(new BigDecimal("700000"))
                            .build());

            ArgumentCaptor<SavingsRecord> captor = ArgumentCaptor.forClass(SavingsRecord.class);
            verify(savingsRecordRepository).save(captor.capture());
            assertThat(captor.getValue().getCategory()).isEqualTo(SavingsCategory.PENSION);
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo("700000");
        }

        @Test
        @DisplayName("없는 기록 삭제 시 예외")
        void delete_NotFound() {
            when(savingsRecordRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> savingsRecordService.delete(9L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("사용자 격리")
    class TenancyTests {

        @Test
        @DisplayName("다른 사용자의 기록은 조회할 수 없다 (존재를 숨기고 404)")
        void get_HidesForeignRecord() {
            SavingsRecord foreign =
                    record(LocalDate.of(2026, 7, 1), "500000", SavingsCategory.PENSION);
            foreign.setUserId(999L);
            foreign.setId(3L);
            when(savingsRecordRepository.findById(3L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> savingsRecordService.get(3L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("다른 사용자의 기록은 수정/삭제할 수 없다")
        void updateAndDelete_RejectForeignRecord() {
            SavingsRecord foreign =
                    record(LocalDate.of(2026, 7, 1), "500000", SavingsCategory.PENSION);
            foreign.setUserId(999L);
            foreign.setId(3L);
            when(savingsRecordRepository.findById(3L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(
                            () ->
                                    savingsRecordService.update(
                                            3L,
                                            SavingsRecordDto.builder()
                                                    .savedDate(LocalDate.of(2026, 7, 5))
                                                    .amount(BigDecimal.TEN)
                                                    .build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> savingsRecordService.delete(3L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(savingsRecordRepository, never()).save(any());
            verify(savingsRecordRepository, never()).delete(any());
        }

        @Test
        @DisplayName("목록/집계 조회는 현재 사용자로 스코프된다")
        void listQueries_ScopedToCurrentUser() {
            when(savingsRecordRepository.findAllByUserOrderBySavedDateDesc(USER_ID))
                    .thenReturn(List.of());

            savingsRecordService.getAll();
            savingsRecordService.getTotalsByCategory();

            verify(savingsRecordRepository, times(2)).findAllByUserOrderBySavedDateDesc(USER_ID);
        }
    }

    @Nested
    @DisplayName("집계")
    class AggregationTests {

        @Test
        @DisplayName("월 합계는 해당 월 범위로 조회해 합산한다")
        void monthlyTotal_SumsWithinMonth() {
            when(savingsRecordRepository.findByUserAndExactAccountAndDateRange(
                            eq(USER_ID),
                            eq(null),
                            eq(LocalDate.of(2026, 7, 1)),
                            eq(LocalDate.of(2026, 7, 31))))
                    .thenReturn(
                            List.of(
                                    record(
                                            LocalDate.of(2026, 7, 5),
                                            "1000000",
                                            SavingsCategory.REGULAR_SAVINGS),
                                    record(
                                            LocalDate.of(2026, 7, 25),
                                            "500000",
                                            SavingsCategory.EMERGENCY_FUND)));

            BigDecimal total =
                    savingsRecordService.getMonthlyTotal(null, LocalDate.of(2026, 7, 26));

            assertThat(total).isEqualByComparingTo("1500000");
        }

        @Test
        @DisplayName("기록이 없으면 월 합계는 0")
        void monthlyTotal_ZeroWhenEmpty() {
            when(savingsRecordRepository.findByUserAndExactAccountAndDateRange(
                            any(), any(), any(), any()))
                    .thenReturn(List.of());

            assertThat(savingsRecordService.getMonthlyTotal(null, LocalDate.of(2026, 7, 1)))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("월별 합계는 오래된 달부터 정렬된다")
        void monthlyTotals_OrderedChronologically() {
            when(savingsRecordRepository.findByUserAndDateRange(any(), any(), any(), any()))
                    .thenReturn(
                            List.of(
                                    record(
                                            LocalDate.of(2026, 7, 5),
                                            "1000000",
                                            SavingsCategory.REGULAR_SAVINGS),
                                    record(
                                            LocalDate.of(2026, 5, 5),
                                            "300000",
                                            SavingsCategory.REGULAR_SAVINGS),
                                    record(
                                            LocalDate.of(2026, 7, 20),
                                            "200000",
                                            SavingsCategory.REGULAR_SAVINGS)));

            var totals =
                    savingsRecordService.getMonthlyTotals(
                            null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            assertThat(totals)
                    .containsExactly(
                            org.assertj.core.api.Assertions.entry(
                                    "2026-05", new BigDecimal("300000")),
                            org.assertj.core.api.Assertions.entry(
                                    "2026-07", new BigDecimal("1200000")));
        }

        @Test
        @DisplayName("분류별 합계를 계산한다")
        void categoryTotals_SumsPerCategory() {
            when(savingsRecordRepository.findAllByUserOrderBySavedDateDesc(USER_ID))
                    .thenReturn(
                            List.of(
                                    record(
                                            LocalDate.of(2026, 7, 5),
                                            "1000000",
                                            SavingsCategory.INVESTMENT_TRANSFER),
                                    record(
                                            LocalDate.of(2026, 7, 6),
                                            "400000",
                                            SavingsCategory.INVESTMENT_TRANSFER),
                                    record(
                                            LocalDate.of(2026, 7, 7),
                                            "250000",
                                            SavingsCategory.EMERGENCY_FUND)));

            assertThat(savingsRecordService.getTotalsByCategory())
                    .containsEntry("INVESTMENT_TRANSFER", new BigDecimal("1400000"))
                    .containsEntry("EMERGENCY_FUND", new BigDecimal("250000"));
        }
    }
}
