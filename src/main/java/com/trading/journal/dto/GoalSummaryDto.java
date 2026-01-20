package com.trading.journal.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 목표 요약 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalSummaryDto {
    /** 전체 목표 수 */
    private long totalGoals;

    /** 활성 목표 수 */
    private long activeGoals;

    /** 달성된 목표 수 */
    private long completedGoals;

    /** 실패한 목표 수 */
    private long failedGoals;

    /** 전체 달성률 */
    private BigDecimal overallCompletionRate;

    /** 평균 진행률 */
    private BigDecimal averageProgress;

    /** 곧 마감되는 목표 수 (7일 이내) */
    private long upcomingDeadlines;

    /** 기한 초과 목표 수 */
    private long overdueGoals;

    /** 유형별 목표 수 */
    private Map<String, Long> goalsByType;

    /** 유형별 평균 진행률 */
    private Map<String, BigDecimal> averageProgressByType;

    /** 최근 달성한 목표 목록 */
    private List<GoalDto> recentlyCompleted;

    /** 우선 처리해야 할 목표 (마감 임박 + 진행률 낮음) */
    private List<GoalDto> priorityGoals;
}
