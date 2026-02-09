package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.dto.GoalDto;
import com.trading.journal.dto.GoalSummaryDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Goal;
import com.trading.journal.entity.GoalStatus;
import com.trading.journal.entity.GoalType;
import com.trading.journal.repository.GoalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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
class GoalServiceTest {

    @Mock private GoalRepository goalRepository;
    @Mock private PortfolioAnalysisService portfolioAnalysisService;
    @Mock private AnalysisService analysisService;
    @Mock private AlertService alertService;
    @Mock private DividendService dividendService;

    @InjectMocks private GoalService goalService;

    private Goal activeGoal;
    private Goal completedGoal;
    private GoalDto createGoalDto;

    @BeforeEach
    void setUp() {
        activeGoal =
                Goal.builder()
                        .id(1L)
                        .name("목표 수익률 20%")
                        .description("연간 수익률 20% 달성")
                        .goalType(GoalType.RETURN_RATE)
                        .targetValue(new BigDecimal("20"))
                        .startValue(BigDecimal.ZERO)
                        .currentValue(new BigDecimal("10"))
                        .startDate(LocalDate.of(2024, 1, 1))
                        .deadline(LocalDate.of(2024, 12, 31))
                        .status(GoalStatus.ACTIVE)
                        .progressPercent(new BigDecimal("50"))
                        .notificationEnabled(true)
                        .milestoneInterval(25)
                        .build();

        completedGoal =
                Goal.builder()
                        .id(2L)
                        .name("목표 자산 1억")
                        .goalType(GoalType.TARGET_AMOUNT)
                        .targetValue(new BigDecimal("100000000"))
                        .startValue(new BigDecimal("50000000"))
                        .currentValue(new BigDecimal("100000000"))
                        .startDate(LocalDate.of(2024, 1, 1))
                        .status(GoalStatus.COMPLETED)
                        .progressPercent(new BigDecimal("100"))
                        .completedAt(LocalDateTime.now())
                        .build();

        createGoalDto =
                GoalDto.builder()
                        .name("신규 목표")
                        .description("테스트 목표")
                        .goalType(GoalType.RETURN_RATE)
                        .targetValue(new BigDecimal("15"))
                        .startValue(BigDecimal.ZERO)
                        .startDate(LocalDate.now())
                        .deadline(LocalDate.now().plusMonths(6))
                        .notificationEnabled(true)
                        .milestoneInterval(25)
                        .build();
    }

    @Nested
    @DisplayName("목표 생성 테스트")
    class CreateGoalTests {

        @Test
        @DisplayName("목표 생성 성공")
        void createGoal_Success() {
            // createGoalDto has startValue already set, so no need for portfolioAnalysisService
            Goal savedGoal =
                    Goal.builder()
                            .id(3L)
                            .name("신규 목표")
                            .goalType(GoalType.RETURN_RATE)
                            .targetValue(new BigDecimal("15"))
                            .startValue(BigDecimal.ZERO)
                            .currentValue(BigDecimal.ZERO)
                            .startDate(LocalDate.now())
                            .status(GoalStatus.ACTIVE)
                            .build();
            when(goalRepository.save(any())).thenReturn(savedGoal);

            GoalDto result = goalService.createGoal(createGoalDto);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("신규 목표");
            assertThat(result.getGoalType()).isEqualTo(GoalType.RETURN_RATE);
            verify(goalRepository).save(any(Goal.class));
        }

        @Test
        @DisplayName("시작값 미지정시 자동 설정")
        void createGoal_AutoSetStartValue() {
            GoalDto dtoWithoutStartValue =
                    GoalDto.builder()
                            .name("자동 시작값")
                            .goalType(GoalType.TARGET_AMOUNT)
                            .targetValue(new BigDecimal("100000000"))
                            .build();

            PortfolioSummaryDto portfolioSummary =
                    PortfolioSummaryDto.builder()
                            .totalCurrentValue(new BigDecimal("50000000"))
                            .build();
            when(portfolioAnalysisService.getPortfolioSummary()).thenReturn(portfolioSummary);

            Goal savedGoal =
                    Goal.builder()
                            .id(4L)
                            .name("자동 시작값")
                            .goalType(GoalType.TARGET_AMOUNT)
                            .startValue(new BigDecimal("50000000"))
                            .currentValue(new BigDecimal("50000000"))
                            .startDate(LocalDate.now())
                            .status(GoalStatus.ACTIVE)
                            .build();
            when(goalRepository.save(any())).thenReturn(savedGoal);

            GoalDto result = goalService.createGoal(dtoWithoutStartValue);

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).save(captor.capture());
            Goal capturedGoal = captor.getValue();

            // 시작값이 현재 포트폴리오 값으로 설정되었는지 확인
            assertThat(capturedGoal.getStartValue()).isNotNull();
        }

        @Test
        @DisplayName("배당 수익 목표 생성")
        void createGoal_DividendIncome() {
            GoalDto dividendGoalDto =
                    GoalDto.builder()
                            .name("배당 수익 목표")
                            .goalType(GoalType.DIVIDEND_INCOME)
                            .targetValue(new BigDecimal("1000000"))
                            .build();

            DividendSummaryDto dividendSummary =
                    DividendSummaryDto.builder().totalDividends(new BigDecimal("500000")).build();
            when(dividendService.getDividendSummary()).thenReturn(dividendSummary);

            Goal savedGoal =
                    Goal.builder()
                            .id(5L)
                            .name("배당 수익 목표")
                            .goalType(GoalType.DIVIDEND_INCOME)
                            .startValue(new BigDecimal("500000"))
                            .currentValue(new BigDecimal("500000"))
                            .startDate(LocalDate.now())
                            .status(GoalStatus.ACTIVE)
                            .build();
            when(goalRepository.save(any())).thenReturn(savedGoal);

            GoalDto result = goalService.createGoal(dividendGoalDto);

            verify(dividendService).getDividendSummary();
        }
    }

    @Nested
    @DisplayName("목표 수정 테스트")
    class UpdateGoalTests {

        @Test
        @DisplayName("목표 수정 성공")
        void updateGoal_Success() {
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));
            when(goalRepository.save(any())).thenReturn(activeGoal);

            GoalDto updateDto =
                    GoalDto.builder()
                            .name("수정된 목표")
                            .targetValue(new BigDecimal("25"))
                            .deadline(LocalDate.of(2025, 6, 30))
                            .build();

            GoalDto result = goalService.updateGoal(1L, updateDto);

            assertThat(result).isNotNull();
            verify(goalRepository).findById(1L);
            verify(goalRepository).save(any(Goal.class));
        }

        @Test
        @DisplayName("목표 완료 상태로 변경시 완료 시간 설정")
        void updateGoal_SetCompletedAt() {
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));
            when(goalRepository.save(any())).thenReturn(activeGoal);

            GoalDto updateDto = GoalDto.builder().status(GoalStatus.COMPLETED).build();

            goalService.updateGoal(1L, updateDto);

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).save(captor.capture());
            Goal savedGoal = captor.getValue();

            assertThat(savedGoal.getStatus()).isEqualTo(GoalStatus.COMPLETED);
            assertThat(savedGoal.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 목표 수정 시 예외")
        void updateGoal_NotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            GoalDto updateDto = GoalDto.builder().name("수정").build();

            assertThatThrownBy(() -> goalService.updateGoal(999L, updateDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("목표를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("목표 삭제 테스트")
    class DeleteGoalTests {

        @Test
        @DisplayName("목표 삭제 성공")
        void deleteGoal_Success() {
            when(goalRepository.existsById(1L)).thenReturn(true);
            doNothing().when(goalRepository).deleteById(1L);

            goalService.deleteGoal(1L);

            verify(goalRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 목표 삭제 시 예외")
        void deleteGoal_NotFound() {
            when(goalRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> goalService.deleteGoal(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("목표를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("목표 조회 테스트")
    class GetGoalTests {

        @Test
        @DisplayName("ID로 목표 조회")
        void getGoal_Success() {
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));

            GoalDto result = goalService.getGoal(1L);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("목표 수익률 20%");
            assertThat(result.getGoalType()).isEqualTo(GoalType.RETURN_RATE);
        }

        @Test
        @DisplayName("존재하지 않는 목표 조회 시 예외")
        void getGoal_NotFound() {
            when(goalRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> goalService.getGoal(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("목표를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("전체 목표 조회")
        void getAllGoals_Success() {
            when(goalRepository.findAll()).thenReturn(Arrays.asList(activeGoal, completedGoal));

            List<GoalDto> result = goalService.getAllGoals();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("활성 목표 조회")
        void getActiveGoals_Success() {
            when(goalRepository.findByStatusInOrderByDeadlineAsc(any()))
                    .thenReturn(Collections.singletonList(activeGoal));

            List<GoalDto> result = goalService.getActiveGoals();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(GoalStatus.ACTIVE);
        }

        @Test
        @DisplayName("상태별 목표 조회")
        void getGoalsByStatus_Success() {
            when(goalRepository.findByStatusOrderByDeadlineAsc(GoalStatus.COMPLETED))
                    .thenReturn(Collections.singletonList(completedGoal));

            List<GoalDto> result = goalService.getGoalsByStatus(GoalStatus.COMPLETED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(GoalStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("목표 요약 테스트")
    class GoalSummaryTests {

        @Test
        @DisplayName("목표 요약 정보 조회")
        void getGoalSummary_Success() {
            when(goalRepository.findAll()).thenReturn(Arrays.asList(activeGoal, completedGoal));
            when(goalRepository.countByStatus(GoalStatus.COMPLETED)).thenReturn(1L);
            when(goalRepository.countByStatus(GoalStatus.FAILED)).thenReturn(0L);
            when(goalRepository.findUpcomingDeadlines(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(goalRepository.findOverdueGoals(any())).thenReturn(Collections.emptyList());

            GoalSummaryDto result = goalService.getGoalSummary();

            assertThat(result).isNotNull();
            assertThat(result.getTotalGoals()).isEqualTo(2);
            assertThat(result.getActiveGoals()).isEqualTo(1);
            assertThat(result.getCompletedGoals()).isEqualTo(1);
        }

        @Test
        @DisplayName("목표가 없을 때 요약")
        void getGoalSummary_NoGoals() {
            when(goalRepository.findAll()).thenReturn(Collections.emptyList());
            when(goalRepository.countByStatus(any())).thenReturn(0L);
            when(goalRepository.findUpcomingDeadlines(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(goalRepository.findOverdueGoals(any())).thenReturn(Collections.emptyList());

            GoalSummaryDto result = goalService.getGoalSummary();

            assertThat(result.getTotalGoals()).isEqualTo(0);
            assertThat(result.getActiveGoals()).isEqualTo(0);
            assertThat(result.getOverallCompletionRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("목표 진행률 갱신 테스트")
    class UpdateProgressTests {

        @Test
        @DisplayName("모든 활성 목표 진행률 갱신")
        void updateAllGoalsProgress_Success() {
            Goal goalWithMilestone =
                    Goal.builder()
                            .id(1L)
                            .name("진행 중 목표")
                            .goalType(GoalType.RETURN_RATE)
                            .targetValue(new BigDecimal("20"))
                            .startValue(BigDecimal.ZERO)
                            .currentValue(new BigDecimal("10"))
                            .status(GoalStatus.ACTIVE)
                            .notificationEnabled(true)
                            .milestoneInterval(25)
                            .lastMilestone(25)
                            .build();

            when(goalRepository.findByStatusOrderByDeadlineAsc(GoalStatus.ACTIVE))
                    .thenReturn(Collections.singletonList(goalWithMilestone));

            PortfolioSummaryDto portfolioSummary =
                    PortfolioSummaryDto.builder()
                            .totalProfitLossPercent(new BigDecimal("12"))
                            .build();
            when(portfolioAnalysisService.getPortfolioSummary()).thenReturn(portfolioSummary);
            when(goalRepository.save(any())).thenReturn(goalWithMilestone);
            when(goalRepository.findOverdueGoals(any())).thenReturn(Collections.emptyList());

            goalService.updateAllGoalsProgress();

            verify(goalRepository).findByStatusOrderByDeadlineAsc(GoalStatus.ACTIVE);
            verify(goalRepository, atLeastOnce()).save(any(Goal.class));
        }

        @Test
        @DisplayName("기한 초과 목표 실패 처리")
        void updateOverdueGoals_Success() {
            Goal overdueGoal =
                    Goal.builder()
                            .id(3L)
                            .name("기한 초과")
                            .goalType(GoalType.RETURN_RATE)
                            .deadline(LocalDate.now().minusDays(1))
                            .status(GoalStatus.ACTIVE)
                            .build();

            when(goalRepository.findOverdueGoals(any()))
                    .thenReturn(Collections.singletonList(overdueGoal));
            when(goalRepository.save(any())).thenReturn(overdueGoal);

            goalService.updateOverdueGoals();

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).save(captor.capture());

            assertThat(captor.getValue().getStatus()).isEqualTo(GoalStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("DTO 변환 테스트")
    class ConvertToDtoTests {

        @Test
        @DisplayName("DTO 변환 시 남은 일수 계산")
        void convertToDto_CalculatesDaysRemaining() {
            activeGoal.setDeadline(LocalDate.now().plusDays(30));
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));

            GoalDto result = goalService.getGoal(1L);

            assertThat(result.getDaysRemaining()).isEqualTo(30);
        }

        @Test
        @DisplayName("기한 초과 여부 확인")
        void convertToDto_DetectsOverdue() {
            activeGoal.setDeadline(LocalDate.now().minusDays(5));
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));

            GoalDto result = goalService.getGoal(1L);

            assertThat(result.getIsOverdue()).isTrue();
            assertThat(result.getDaysRemaining()).isEqualTo(-5);
        }

        @Test
        @DisplayName("완료된 목표는 기한 초과가 아님")
        void convertToDto_CompletedNotOverdue() {
            completedGoal.setDeadline(LocalDate.now().minusDays(5));
            when(goalRepository.findById(2L)).thenReturn(Optional.of(completedGoal));

            GoalDto result = goalService.getGoal(2L);

            assertThat(result.getIsOverdue()).isFalse();
        }

        @Test
        @DisplayName("상태 라벨 변환")
        void convertToDto_StatusLabel() {
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));
            when(goalRepository.findById(2L)).thenReturn(Optional.of(completedGoal));

            GoalDto activeResult = goalService.getGoal(1L);
            GoalDto completedResult = goalService.getGoal(2L);

            assertThat(activeResult.getStatusLabel()).isEqualTo("진행 중");
            assertThat(completedResult.getStatusLabel()).isEqualTo("달성");
        }

        @Test
        @DisplayName("목표 유형 라벨 변환")
        void convertToDto_GoalTypeLabel() {
            when(goalRepository.findById(1L)).thenReturn(Optional.of(activeGoal));

            GoalDto result = goalService.getGoal(1L);

            assertThat(result.getGoalTypeLabel()).isEqualTo("목표 수익률");
        }
    }
}
