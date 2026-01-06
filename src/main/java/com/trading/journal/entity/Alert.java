package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 알림/경고 엔티티
 */
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_type", columnList = "alertType"),
        @Index(name = "idx_alert_created", columnList = "createdAt"),
        @Index(name = "idx_alert_account", columnList = "accountId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 알림 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    /**
     * 알림 우선순위
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertPriority priority = AlertPriority.MEDIUM;

    /**
     * 알림 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.UNREAD;

    /**
     * 알림 제목
     */
    @Column(nullable = false)
    private String title;

    /**
     * 알림 메시지
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * 관련 값 (예: 달성률, 손실 금액 등)
     */
    private BigDecimal relatedValue;

    /**
     * 임계값 (경고 기준)
     */
    private BigDecimal thresholdValue;

    /**
     * 관련 엔티티 ID (목표, 거래 등)
     */
    private Long relatedEntityId;

    /**
     * 관련 엔티티 타입
     */
    private String relatedEntityType;

    /**
     * 계좌 ID (특정 계좌 관련 알림)
     */
    private Long accountId;

    /**
     * 액션 URL (클릭 시 이동할 페이지)
     */
    private String actionUrl;

    /**
     * 읽은 시간
     */
    private LocalDateTime readAt;

    /**
     * 만료 시간 (일정 시간 후 자동 삭제용)
     */
    private LocalDateTime expiresAt;

    /**
     * 생성 시간
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 알림 읽음 처리
     */
    public void markAsRead() {
        this.status = AlertStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    /**
     * 알림 무시 처리
     */
    public void dismiss() {
        this.status = AlertStatus.DISMISSED;
    }

    /**
     * 알림 보관 처리
     */
    public void archive() {
        this.status = AlertStatus.ARCHIVED;
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
