package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 투자 목표 엔티티
 */
@Entity
@Table(name = "goals", indexes = {
    @Index(name = "idx_goal_type", columnList = "goalType"),
    @Index(name = "idx_goal_status", columnList = "status"),
    @Index(name = "idx_goal_deadline", columnList = "deadline")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 목표 이름 */
    @Column(nullable = false)
    private String name;

    /** 목표 설명 */
    @Column(length = 1000)
    private String description;

    /** 목표 유형 */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private GoalType goalType;

    /** 목표 값 (수익률 %, 금액 등) */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal targetValue;

    /** 현재 달성 값 */
    @Column(precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    /** 시작 기준값 (시작 시점의 자산/수익률 등) */
    @Column(precision = 19, scale = 4)
    private BigDecimal startValue;

    /** 목표 시작일 */
    @Column(nullable = false)
    private LocalDate startDate;

    /** 목표 마감일 */
    private LocalDate deadline;

    /** 목표 상태 */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    /** 달성률 (%) */
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal progressPercent = BigDecimal.ZERO;

    /** 달성 일시 */
    private LocalDateTime completedAt;

    /** 알림 활성화 여부 */
    @Builder.Default
    private Boolean notificationEnabled = true;

    /** 마일스톤 알림 간격 (%) - 예: 25면 25%, 50%, 75% 도달 시 알림 */
    @Builder.Default
    private Integer milestoneInterval = 25;

    /** 마지막 마일스톤 달성 (%) */
    @Builder.Default
    private Integer lastMilestone = 0;

    /** 연결된 계좌 ID (선택적) */
    private Long accountId;

    /** 메모 */
    @Column(length = 2000)
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (startDate == null) {
            startDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 진행률 계산 및 업데이트
     */
    public void updateProgress() {
        if (targetValue == null || targetValue.compareTo(BigDecimal.ZERO) == 0) {
            this.progressPercent = BigDecimal.ZERO;
            return;
        }

        BigDecimal progress;
        if (startValue != null) {
            // 시작값 기준 진행률 계산
            BigDecimal totalGap = targetValue.subtract(startValue);
            BigDecimal currentGap = currentValue.subtract(startValue);
            if (totalGap.compareTo(BigDecimal.ZERO) != 0) {
                progress = currentGap.divide(totalGap, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            } else {
                // startValue == targetValue: check if currentValue meets target
                if (currentValue.compareTo(targetValue) >= 0) {
                    progress = BigDecimal.valueOf(100);
                } else {
                    // Calculate progress as percentage of target reached
                    progress = currentValue.divide(targetValue, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                }
            }
        } else {
            // 단순 비율 계산
            progress = currentValue.divide(targetValue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        this.progressPercent = progress.min(BigDecimal.valueOf(100)).max(BigDecimal.ZERO);

        // 목표 달성 확인
        if (this.progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0 && this.status == GoalStatus.ACTIVE) {
            this.status = GoalStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        }
    }

    /**
     * 새로운 마일스톤 달성 여부 확인
     */
    public boolean checkNewMilestone() {
        if (milestoneInterval == null || milestoneInterval <= 0) {
            return false;
        }

        int currentMilestone = progressPercent.intValue() / milestoneInterval * milestoneInterval;
        if (currentMilestone > lastMilestone && currentMilestone < 100) {
            lastMilestone = currentMilestone;
            return true;
        }
        return false;
    }
}
