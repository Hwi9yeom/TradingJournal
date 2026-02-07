package com.trading.journal.service;

import com.trading.journal.dto.AlertDto;
import com.trading.journal.dto.AlertSummaryDto;
import com.trading.journal.entity.*;
import com.trading.journal.repository.AlertRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    /** 알림 생성 */
    @Transactional
    public AlertDto createAlert(
            AlertType type, AlertPriority priority, String title, String message) {
        Alert alert =
                Alert.builder()
                        .alertType(type)
                        .priority(priority)
                        .status(AlertStatus.UNREAD)
                        .title(title)
                        .message(message)
                        .build();

        Alert saved = alertRepository.save(alert);
        log.info("알림 생성: {} - {}", type, title);
        return convertToDto(saved);
    }

    /** 상세 알림 생성 */
    @Transactional
    public AlertDto createAlert(
            AlertType type,
            AlertPriority priority,
            String title,
            String message,
            BigDecimal relatedValue,
            BigDecimal thresholdValue,
            Long relatedEntityId,
            String relatedEntityType,
            Long accountId,
            String actionUrl,
            LocalDateTime expiresAt) {
        Alert alert =
                Alert.builder()
                        .alertType(type)
                        .priority(priority)
                        .status(AlertStatus.UNREAD)
                        .title(title)
                        .message(message)
                        .relatedValue(relatedValue)
                        .thresholdValue(thresholdValue)
                        .relatedEntityId(relatedEntityId)
                        .relatedEntityType(relatedEntityType)
                        .accountId(accountId)
                        .actionUrl(actionUrl)
                        .expiresAt(expiresAt)
                        .build();

        Alert saved = alertRepository.save(alert);
        log.info("알림 생성: {} - {} (관련: {}:{})", type, title, relatedEntityType, relatedEntityId);
        return convertToDto(saved);
    }

    /** 목표 마일스톤 알림 */
    @Transactional
    public void createGoalMilestoneAlert(Long goalId, String goalName, int milestone) {
        String title = String.format("목표 %d%% 달성!", milestone);
        String message = String.format("'%s' 목표가 %d%% 진행되었습니다.", goalName, milestone);

        createAlert(
                AlertType.GOAL_MILESTONE,
                AlertPriority.MEDIUM,
                title,
                message,
                BigDecimal.valueOf(milestone),
                null,
                goalId,
                "Goal",
                null,
                "/goals.html",
                LocalDateTime.now().plusDays(7));
    }

    /** 목표 달성 알림 */
    @Transactional
    public void createGoalCompletedAlert(Long goalId, String goalName) {
        String title = "목표 달성!";
        String message = String.format("축하합니다! '%s' 목표를 달성했습니다.", goalName);

        createAlert(
                AlertType.GOAL_COMPLETED,
                AlertPriority.HIGH,
                title,
                message,
                BigDecimal.valueOf(100),
                null,
                goalId,
                "Goal",
                null,
                "/goals.html",
                null);
    }

    /** 목표 마감 임박 알림 */
    @Transactional
    public void createGoalDeadlineAlert(Long goalId, String goalName, long daysRemaining) {
        String title = String.format("목표 마감 D-%d", daysRemaining);
        String message = String.format("'%s' 목표의 마감이 %d일 남았습니다.", goalName, daysRemaining);

        createAlert(
                AlertType.GOAL_DEADLINE,
                AlertPriority.HIGH,
                title,
                message,
                BigDecimal.valueOf(daysRemaining),
                null,
                goalId,
                "Goal",
                null,
                "/goals.html",
                LocalDateTime.now().plusDays(daysRemaining));
    }

    /** 손실 한도 초과 알림 */
    @Transactional
    public void createLossLimitAlert(
            BigDecimal currentLoss, BigDecimal limitValue, Long accountId) {
        String title = "손실 한도 초과 경고";
        String message =
                String.format("현재 손실(%.2f%%)이 설정된 한도(%.2f%%)를 초과했습니다.", currentLoss, limitValue);

        createAlert(
                AlertType.LOSS_LIMIT,
                AlertPriority.CRITICAL,
                title,
                message,
                currentLoss,
                limitValue,
                null,
                null,
                accountId,
                "/dashboard.html",
                null);
    }

    /** 낙폭 경고 알림 */
    @Transactional
    public void createDrawdownAlert(BigDecimal currentDrawdown, BigDecimal threshold) {
        String title = "포트폴리오 낙폭 경고";
        String message =
                String.format(
                        "현재 낙폭(%.2f%%)이 경고 수준(%.2f%%)에 도달했습니다.", currentDrawdown.abs(), threshold);

        createAlert(
                AlertType.DRAWDOWN_WARNING,
                AlertPriority.HIGH,
                title,
                message,
                currentDrawdown,
                threshold,
                null,
                null,
                null,
                "/dashboard.html",
                LocalDateTime.now().plusDays(1));
    }

    /** 연승/연패 알림 */
    @Transactional
    public void createStreakAlert(int streak, boolean isWinning) {
        AlertType type = isWinning ? AlertType.WINNING_STREAK : AlertType.LOSING_STREAK;
        AlertPriority priority = isWinning ? AlertPriority.LOW : AlertPriority.HIGH;
        String title =
                isWinning
                        ? String.format("%d연승 달성!", streak)
                        : String.format("%d연패 주의", Math.abs(streak));
        String message =
                isWinning
                        ? String.format("축하합니다! %d연속 수익 거래를 기록했습니다.", streak)
                        : String.format("최근 %d연속 손실 거래입니다. 거래 전략을 점검해보세요.", Math.abs(streak));

        createAlert(
                type,
                priority,
                title,
                message,
                BigDecimal.valueOf(streak),
                null,
                null,
                null,
                null,
                "/patterns.html",
                LocalDateTime.now().plusDays(3));
    }

    /** 알림 조회 */
    @Transactional(readOnly = true)
    public AlertDto getAlert(Long id) {
        Alert alert =
                alertRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + id));
        return convertToDto(alert);
    }

    /** 읽지 않은 알림 조회 */
    @Transactional(readOnly = true)
    public List<AlertDto> getUnreadAlerts() {
        return alertRepository
                .findByStatusInOrderByPriorityDescCreatedAtDesc(List.of(AlertStatus.UNREAD))
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** 모든 알림 조회 (페이징) */
    @Transactional(readOnly = true)
    public Page<AlertDto> getAllAlerts(int page, int size) {
        return alertRepository
                .findByStatusInOrderByCreatedAtDesc(
                        List.of(AlertStatus.UNREAD, AlertStatus.READ), PageRequest.of(page, size))
                .map(this::convertToDto);
    }

    /** 알림 요약 조회 */
    @Transactional(readOnly = true)
    public AlertSummaryDto getAlertSummary() {
        long unreadCount = alertRepository.countByStatus(AlertStatus.UNREAD);
        long criticalCount =
                alertRepository.countByStatusAndPriority(
                        AlertStatus.UNREAD, AlertPriority.CRITICAL);
        long highPriorityCount =
                alertRepository.countByStatusAndPriority(AlertStatus.UNREAD, AlertPriority.HIGH);
        long todayCount =
                alertRepository.countTodayAlerts(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));

        List<Alert> unreadAlerts =
                alertRepository.findByStatusInOrderByPriorityDescCreatedAtDesc(
                        List.of(AlertStatus.UNREAD));

        // 유형별 카운트
        Map<String, Long> countByType =
                unreadAlerts.stream()
                        .collect(
                                Collectors.groupingBy(
                                        a -> a.getAlertType().name(), Collectors.counting()));

        // 최근 읽지 않은 알림 (상위 5개)
        List<AlertDto> recentUnread =
                unreadAlerts.stream().limit(5).map(this::convertToDto).collect(Collectors.toList());

        // 긴급 알림
        List<AlertDto> criticalAlerts =
                alertRepository.findHighPriorityUnreadAlerts().stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList());

        return AlertSummaryDto.builder()
                .unreadCount(unreadCount)
                .criticalCount(criticalCount)
                .highPriorityCount(highPriorityCount)
                .todayCount(todayCount)
                .countByType(countByType)
                .recentUnread(recentUnread)
                .criticalAlerts(criticalAlerts)
                .build();
    }

    /** 알림 읽음 처리 */
    @Transactional
    public AlertDto markAsRead(Long id) {
        Alert alert =
                alertRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + id));
        alert.markAsRead();
        return convertToDto(alertRepository.save(alert));
    }

    /** 모든 알림 읽음 처리 */
    @Transactional
    public int markAllAsRead() {
        return alertRepository.markAllAsRead(LocalDateTime.now());
    }

    /** 알림 무시 처리 */
    @Transactional
    public AlertDto dismissAlert(Long id) {
        Alert alert =
                alertRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + id));
        alert.dismiss();
        return convertToDto(alertRepository.save(alert));
    }

    /** 알림 삭제 */
    @Transactional
    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }

    /** 오래된 알림 정리 (30일 이상) */
    @Transactional
    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    public void cleanupOldAlerts() {
        LocalDateTime before = LocalDateTime.now().minusDays(30);
        int deleted = alertRepository.deleteOldAlerts(before);
        if (deleted > 0) {
            log.info("오래된 알림 {} 건 삭제", deleted);
        }

        // 만료된 알림 삭제
        int expiredDeleted = alertRepository.deleteExpiredAlerts(LocalDateTime.now());
        if (expiredDeleted > 0) {
            log.info("만료된 알림 {} 건 삭제", expiredDeleted);
        }
    }

    /** Entity를 DTO로 변환 */
    private AlertDto convertToDto(Alert alert) {
        return AlertDto.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType())
                .priority(alert.getPriority())
                .status(alert.getStatus())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .relatedValue(alert.getRelatedValue())
                .thresholdValue(alert.getThresholdValue())
                .relatedEntityId(alert.getRelatedEntityId())
                .relatedEntityType(alert.getRelatedEntityType())
                .accountId(alert.getAccountId())
                .actionUrl(alert.getActionUrl())
                .readAt(alert.getReadAt())
                .expiresAt(alert.getExpiresAt())
                .createdAt(alert.getCreatedAt())
                .alertTypeLabel(getAlertTypeLabel(alert.getAlertType()))
                .priorityLabel(getPriorityLabel(alert.getPriority()))
                .statusLabel(getStatusLabel(alert.getStatus()))
                .timeAgo(getTimeAgo(alert.getCreatedAt()))
                .iconClass(getIconClass(alert.getAlertType()))
                .colorClass(getColorClass(alert.getPriority()))
                .build();
    }

    private String getAlertTypeLabel(AlertType type) {
        return switch (type) {
            case GOAL_MILESTONE -> "목표 마일스톤";
            case GOAL_COMPLETED -> "목표 달성";
            case GOAL_DEADLINE -> "마감 임박";
            case GOAL_OVERDUE -> "기한 초과";
            case PROFIT_TARGET -> "목표 수익";
            case LOSS_LIMIT -> "손실 한도";
            case DAILY_LOSS_LIMIT -> "일일 손실";
            case DRAWDOWN_WARNING -> "낙폭 경고";
            case PORTFOLIO_CHANGE -> "포트폴리오 변동";
            case POSITION_SIZE -> "포지션 비중";
            case SECTOR_CONCENTRATION -> "섹터 집중";
            case TRADE_EXECUTED -> "거래 체결";
            case WINNING_STREAK -> "연승";
            case LOSING_STREAK -> "연패";
            case SYSTEM_INFO -> "시스템 정보";
            case SYSTEM_WARNING -> "시스템 경고";
            case CUSTOM -> "사용자 정의";
        };
    }

    private String getPriorityLabel(AlertPriority priority) {
        return switch (priority) {
            case LOW -> "낮음";
            case MEDIUM -> "보통";
            case HIGH -> "높음";
            case CRITICAL -> "긴급";
        };
    }

    private String getStatusLabel(AlertStatus status) {
        return switch (status) {
            case UNREAD -> "읽지 않음";
            case READ -> "읽음";
            case DISMISSED -> "무시됨";
            case ARCHIVED -> "보관됨";
        };
    }

    private String getTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "";

        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);

        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";

        long hours = ChronoUnit.HOURS.between(dateTime, now);
        if (hours < 24) return hours + "시간 전";

        long days = ChronoUnit.DAYS.between(dateTime, now);
        if (days < 7) return days + "일 전";
        if (days < 30) return (days / 7) + "주 전";

        return (days / 30) + "개월 전";
    }

    private String getIconClass(AlertType type) {
        return switch (type) {
            case GOAL_MILESTONE, GOAL_COMPLETED -> "bi-trophy";
            case GOAL_DEADLINE, GOAL_OVERDUE -> "bi-clock";
            case PROFIT_TARGET -> "bi-graph-up-arrow";
            case LOSS_LIMIT, DAILY_LOSS_LIMIT -> "bi-exclamation-triangle";
            case DRAWDOWN_WARNING -> "bi-graph-down-arrow";
            case PORTFOLIO_CHANGE -> "bi-pie-chart";
            case POSITION_SIZE, SECTOR_CONCENTRATION -> "bi-diagram-3";
            case TRADE_EXECUTED -> "bi-arrow-left-right";
            case WINNING_STREAK -> "bi-emoji-smile";
            case LOSING_STREAK -> "bi-emoji-frown";
            case SYSTEM_INFO -> "bi-info-circle";
            case SYSTEM_WARNING -> "bi-exclamation-circle";
            case CUSTOM -> "bi-bell";
        };
    }

    private String getColorClass(AlertPriority priority) {
        return switch (priority) {
            case LOW -> "text-secondary";
            case MEDIUM -> "text-info";
            case HIGH -> "text-warning";
            case CRITICAL -> "text-danger";
        };
    }
}
