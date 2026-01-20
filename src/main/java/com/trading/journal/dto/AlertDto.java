package com.trading.journal.dto;

import com.trading.journal.entity.AlertPriority;
import com.trading.journal.entity.AlertStatus;
import com.trading.journal.entity.AlertType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 알림 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDto {
    private Long id;
    private AlertType alertType;
    private AlertPriority priority;
    private AlertStatus status;
    private String title;
    private String message;
    private BigDecimal relatedValue;
    private BigDecimal thresholdValue;
    private Long relatedEntityId;
    private String relatedEntityType;
    private Long accountId;
    private String actionUrl;
    private LocalDateTime readAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    // 추가 표시 필드
    private String alertTypeLabel;
    private String priorityLabel;
    private String statusLabel;
    private String timeAgo;
    private String iconClass;
    private String colorClass;
}
