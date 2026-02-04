package com.trading.journal.dto;

import com.trading.journal.entity.GoalStatus;
import com.trading.journal.entity.GoalType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 투자 목표 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalDto {
    private Long id;
    private String name;
    private String description;
    private GoalType goalType;
    private BigDecimal targetValue;
    private BigDecimal currentValue;
    private BigDecimal startValue;
    private LocalDate startDate;
    private LocalDate deadline;
    private GoalStatus status;
    private BigDecimal progressPercent;
    private LocalDateTime completedAt;
    private Boolean notificationEnabled;
    private Integer milestoneInterval;
    private Integer lastMilestone;
    private Long accountId;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 추가 계산 필드
    private Long daysRemaining;
    private Long daysElapsed;
    private Boolean isOverdue;
    private String statusLabel;
    private String goalTypeLabel;
    private LocalDate estimatedCompletionDate; // 예상 달성일
    private String estimatedCompletionMessage; // 예상 달성일 메시지
}
