package com.trading.journal.service;

import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.dto.GoalDto;
import com.trading.journal.dto.GoalSummaryDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Goal;
import com.trading.journal.entity.GoalStatus;
import com.trading.journal.entity.GoalType;
import com.trading.journal.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 투자 목표 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final AnalysisService analysisService;
    private final AlertService alertService;
    private final DividendService dividendService;

    /**
     * 새 목표 생성
     */
    @Transactional
    public GoalDto createGoal(GoalDto dto) {
        Goal goal = Goal.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .goalType(dto.getGoalType())
                .targetValue(dto.getTargetValue())
                .startValue(dto.getStartValue())
                .startDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now())
                .deadline(dto.getDeadline())
                .status(GoalStatus.ACTIVE)
                .notificationEnabled(dto.getNotificationEnabled() != null ? dto.getNotificationEnabled() : true)
                .milestoneInterval(dto.getMilestoneInterval() != null ? dto.getMilestoneInterval() : 25)
                .accountId(dto.getAccountId())
                .notes(dto.getNotes())
                .build();

        // 시작값 자동 설정 (현재 포트폴리오 상태 기반)
        if (goal.getStartValue() == null) {
            goal.setStartValue(getCurrentValueForGoalType(goal.getGoalType()));
        }

        // 현재값 설정 및 진행률 계산
        goal.setCurrentValue(goal.getStartValue());
        goal.updateProgress();

        Goal savedGoal = goalRepository.save(goal);
        log.info("새 목표 생성: {} (유형: {})", savedGoal.getName(), savedGoal.getGoalType());

        return convertToDto(savedGoal);
    }

    /**
     * 목표 수정
     */
    @Transactional
    public GoalDto updateGoal(Long id, GoalDto dto) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("목표를 찾을 수 없습니다: " + id));

        goal.setName(dto.getName());
        goal.setDescription(dto.getDescription());
        goal.setTargetValue(dto.getTargetValue());
        goal.setDeadline(dto.getDeadline());
        goal.setNotificationEnabled(dto.getNotificationEnabled());
        goal.setMilestoneInterval(dto.getMilestoneInterval());
        goal.setNotes(dto.getNotes());

        if (dto.getStatus() != null) {
            goal.setStatus(dto.getStatus());
            if (dto.getStatus() == GoalStatus.COMPLETED && goal.getCompletedAt() == null) {
                goal.setCompletedAt(LocalDateTime.now());
            }
        }

        goal.updateProgress();
        Goal savedGoal = goalRepository.save(goal);

        return convertToDto(savedGoal);
    }

    /**
     * 목표 삭제
     */
    @Transactional
    public void deleteGoal(Long id) {
        if (!goalRepository.existsById(id)) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다: " + id);
        }
        goalRepository.deleteById(id);
        log.info("목표 삭제: {}", id);
    }

    /**
     * 목표 조회
     */
    public GoalDto getGoal(Long id) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("목표를 찾을 수 없습니다: " + id));
        return convertToDto(goal);
    }

    /**
     * 전체 목표 조회
     */
    public List<GoalDto> getAllGoals() {
        return goalRepository.findAll().stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(GoalDto::getStatus)
                        .thenComparing(g -> g.getDeadline() != null ? g.getDeadline() : LocalDate.MAX))
                .collect(Collectors.toList());
    }

    /**
     * 활성 목표 조회
     */
    public List<GoalDto> getActiveGoals() {
        return goalRepository.findByStatusInOrderByDeadlineAsc(
                        List.of(GoalStatus.ACTIVE, GoalStatus.PAUSED))
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 상태별 목표 조회
     */
    public List<GoalDto> getGoalsByStatus(GoalStatus status) {
        return goalRepository.findByStatusOrderByDeadlineAsc(status)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 목표 요약 정보 조회
     */
    public GoalSummaryDto getGoalSummary() {
        List<Goal> allGoals = goalRepository.findAll();
        LocalDate today = LocalDate.now();

        long activeGoals = allGoals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).count();
        long completedGoals = goalRepository.countByStatus(GoalStatus.COMPLETED);
        long failedGoals = goalRepository.countByStatus(GoalStatus.FAILED);

        // 평균 진행률 계산
        BigDecimal averageProgress = allGoals.stream()
                .filter(g -> g.getStatus() == GoalStatus.ACTIVE)
                .map(Goal::getProgressPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (activeGoals > 0) {
            averageProgress = averageProgress.divide(BigDecimal.valueOf(activeGoals), 2, RoundingMode.HALF_UP);
        }

        // 달성률 계산
        long totalCompleteOrFail = completedGoals + failedGoals;
        BigDecimal completionRate = totalCompleteOrFail > 0
                ? BigDecimal.valueOf(completedGoals * 100.0 / totalCompleteOrFail).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 기한 임박 및 초과 목표
        List<Goal> upcoming = goalRepository.findUpcomingDeadlines(today, today.plusDays(7));
        List<Goal> overdue = goalRepository.findOverdueGoals(today);

        // 유형별 통계
        Map<String, Long> goalsByType = allGoals.stream()
                .collect(Collectors.groupingBy(g -> g.getGoalType().name(), Collectors.counting()));

        Map<String, BigDecimal> avgProgressByType = allGoals.stream()
                .filter(g -> g.getStatus() == GoalStatus.ACTIVE)
                .collect(Collectors.groupingBy(
                        g -> g.getGoalType().name(),
                        Collectors.collectingAndThen(
                                Collectors.averagingDouble(g -> g.getProgressPercent() != null ? g.getProgressPercent().doubleValue() : 0),
                                avg -> BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP)
                        )
                ));

        // 최근 달성 목표
        List<GoalDto> recentlyCompleted = allGoals.stream()
                .filter(g -> g.getStatus() == GoalStatus.COMPLETED)
                .sorted(Comparator.comparing(Goal::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        // 우선순위 목표 (마감 임박 + 진행률 낮음)
        List<GoalDto> priorityGoals = allGoals.stream()
                .filter(g -> g.getStatus() == GoalStatus.ACTIVE)
                .filter(g -> g.getDeadline() != null && g.getDeadline().isBefore(today.plusDays(30)))
                .sorted(Comparator.comparing(Goal::getDeadline)
                        .thenComparing(Goal::getProgressPercent))
                .limit(5)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return GoalSummaryDto.builder()
                .totalGoals(allGoals.size())
                .activeGoals(activeGoals)
                .completedGoals(completedGoals)
                .failedGoals(failedGoals)
                .overallCompletionRate(completionRate)
                .averageProgress(averageProgress)
                .upcomingDeadlines(upcoming.size())
                .overdueGoals(overdue.size())
                .goalsByType(goalsByType)
                .averageProgressByType(avgProgressByType)
                .recentlyCompleted(recentlyCompleted)
                .priorityGoals(priorityGoals)
                .build();
    }

    /**
     * 모든 활성 목표 진행률 갱신
     */
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // 매시간 실행
    public void updateAllGoalsProgress() {
        log.debug("목표 진행률 갱신 시작");

        List<Goal> activeGoals = goalRepository.findByStatusOrderByDeadlineAsc(GoalStatus.ACTIVE);

        for (Goal goal : activeGoals) {
            try {
                BigDecimal newValue = getCurrentValueForGoalType(goal.getGoalType());
                goal.setCurrentValue(newValue);
                goal.updateProgress();

                // 마일스톤 달성 확인
                if (goal.checkNewMilestone()) {
                    log.info("목표 마일스톤 달성: {} - {}% 도달", goal.getName(), goal.getLastMilestone());
                    // 알림 발송
                    if (goal.getNotificationEnabled() != null && goal.getNotificationEnabled()) {
                        if (goal.getLastMilestone() >= 100) {
                            alertService.createGoalCompletedAlert(goal.getId(), goal.getName());
                        } else {
                            alertService.createGoalMilestoneAlert(goal.getId(), goal.getName(), goal.getLastMilestone());
                        }
                    }
                }

                goalRepository.save(goal);
            } catch (Exception e) {
                log.warn("목표 진행률 갱신 실패: {} - {}", goal.getId(), e.getMessage());
            }
        }

        // 기한 초과 목표 상태 업데이트
        updateOverdueGoals();

        log.debug("목표 진행률 갱신 완료: {} 개", activeGoals.size());
    }

    /**
     * 기한 초과 목표 상태 업데이트
     */
    @Transactional
    public void updateOverdueGoals() {
        List<Goal> overdueGoals = goalRepository.findOverdueGoals(LocalDate.now());
        for (Goal goal : overdueGoals) {
            goal.setStatus(GoalStatus.FAILED);
            goalRepository.save(goal);
            log.info("목표 기한 초과로 실패 처리: {}", goal.getName());
        }
    }

    /**
     * 목표 유형에 따른 현재 값 조회
     */
    private BigDecimal getCurrentValueForGoalType(GoalType goalType) {
        try {
            PortfolioSummaryDto portfolio = portfolioAnalysisService.getPortfolioSummary();

            return switch (goalType) {
                case RETURN_RATE -> portfolio.getTotalProfitLossPercent() != null
                        ? portfolio.getTotalProfitLossPercent() : BigDecimal.ZERO;
                case TARGET_AMOUNT -> portfolio.getTotalCurrentValue() != null
                        ? portfolio.getTotalCurrentValue() : BigDecimal.ZERO;
                case SAVINGS_AMOUNT -> portfolio.getTotalInvestment() != null
                        ? portfolio.getTotalInvestment() : BigDecimal.ZERO;
                case DIVIDEND_INCOME -> {
                    DividendSummaryDto dividendSummary = dividendService.getDividendSummary();
                    yield dividendSummary.getTotalDividends() != null
                            ? dividendSummary.getTotalDividends() : BigDecimal.ZERO;
                }
                case WIN_RATE, TRADE_COUNT, MAX_DRAWDOWN_LIMIT, SHARPE_RATIO -> {
                    log.warn("GoalType {} 자동 추적 미지원 - 수동 업데이트 필요", goalType);
                    yield BigDecimal.ZERO;
                }
                case CUSTOM -> BigDecimal.ZERO; // CUSTOM은 수동 관리
            };
        } catch (Exception e) {
            log.warn("현재 값 조회 실패 (goalType={}): {}", goalType, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Entity를 DTO로 변환
     */
    private GoalDto convertToDto(Goal goal) {
        LocalDate today = LocalDate.now();

        Long daysRemaining = goal.getDeadline() != null
                ? ChronoUnit.DAYS.between(today, goal.getDeadline()) : null;

        Long daysElapsed = ChronoUnit.DAYS.between(goal.getStartDate(), today);

        Boolean isOverdue = goal.getDeadline() != null && goal.getDeadline().isBefore(today)
                && goal.getStatus() == GoalStatus.ACTIVE;

        return GoalDto.builder()
                .id(goal.getId())
                .name(goal.getName())
                .description(goal.getDescription())
                .goalType(goal.getGoalType())
                .targetValue(goal.getTargetValue())
                .currentValue(goal.getCurrentValue())
                .startValue(goal.getStartValue())
                .startDate(goal.getStartDate())
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .progressPercent(goal.getProgressPercent())
                .completedAt(goal.getCompletedAt())
                .notificationEnabled(goal.getNotificationEnabled())
                .milestoneInterval(goal.getMilestoneInterval())
                .lastMilestone(goal.getLastMilestone())
                .accountId(goal.getAccountId())
                .notes(goal.getNotes())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .daysRemaining(daysRemaining)
                .daysElapsed(daysElapsed)
                .isOverdue(isOverdue)
                .statusLabel(getStatusLabel(goal.getStatus()))
                .goalTypeLabel(getGoalTypeLabel(goal.getGoalType()))
                .build();
    }

    private String getStatusLabel(GoalStatus status) {
        return switch (status) {
            case ACTIVE -> "진행 중";
            case COMPLETED -> "달성";
            case FAILED -> "미달성";
            case PAUSED -> "일시중지";
            case CANCELLED -> "취소";
        };
    }

    private String getGoalTypeLabel(GoalType type) {
        return switch (type) {
            case RETURN_RATE -> "목표 수익률";
            case TARGET_AMOUNT -> "목표 자산";
            case SAVINGS_AMOUNT -> "목표 저축";
            case WIN_RATE -> "목표 승률";
            case TRADE_COUNT -> "거래 횟수";
            case DIVIDEND_INCOME -> "배당 수익";
            case MAX_DRAWDOWN_LIMIT -> "최대 낙폭 제한";
            case SHARPE_RATIO -> "샤프 비율";
            case CUSTOM -> "사용자 정의";
        };
    }
}
