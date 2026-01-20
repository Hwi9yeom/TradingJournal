package com.trading.journal.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 알림 요약 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertSummaryDto {
    /** 읽지 않은 알림 수 */
    private long unreadCount;

    /** 긴급 알림 수 */
    private long criticalCount;

    /** 높은 우선순위 알림 수 */
    private long highPriorityCount;

    /** 오늘 생성된 알림 수 */
    private long todayCount;

    /** 유형별 알림 수 */
    private Map<String, Long> countByType;

    /** 최근 읽지 않은 알림 목록 */
    private List<AlertDto> recentUnread;

    /** 긴급 알림 목록 */
    private List<AlertDto> criticalAlerts;
}
