package com.trading.journal.repository;

import com.trading.journal.entity.Alert;
import com.trading.journal.entity.AlertPriority;
import com.trading.journal.entity.AlertStatus;
import com.trading.journal.entity.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 Repository
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 상태별 알림 조회 (최신순)
     */
    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    /**
     * 읽지 않은 알림 조회
     */
    List<Alert> findByStatusInOrderByPriorityDescCreatedAtDesc(List<AlertStatus> statuses);

    /**
     * 읽지 않은 알림 개수
     */
    long countByStatus(AlertStatus status);

    /**
     * 우선순위별 읽지 않은 알림 개수
     */
    long countByStatusAndPriority(AlertStatus status, AlertPriority priority);

    /**
     * 유형별 알림 조회
     */
    List<Alert> findByAlertTypeOrderByCreatedAtDesc(AlertType alertType);

    /**
     * 계좌별 알림 조회
     */
    List<Alert> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * 최근 알림 조회 (페이징)
     */
    Page<Alert> findByStatusInOrderByCreatedAtDesc(List<AlertStatus> statuses, Pageable pageable);

    /**
     * 특정 기간 내 알림 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<Alert> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 만료된 알림 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<Alert> findExpiredAlerts(@Param("now") LocalDateTime now);

    /**
     * 관련 엔티티별 알림 조회
     */
    List<Alert> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);

    /**
     * 모든 알림 읽음 처리
     */
    @Modifying
    @Query("UPDATE Alert a SET a.status = 'READ', a.readAt = :now WHERE a.status = 'UNREAD'")
    int markAllAsRead(@Param("now") LocalDateTime now);

    /**
     * 오래된 알림 삭제 (보관함 제외)
     */
    @Modifying
    @Query("DELETE FROM Alert a WHERE a.status != 'ARCHIVED' AND a.createdAt < :before")
    int deleteOldAlerts(@Param("before") LocalDateTime before);

    /**
     * 만료된 알림 삭제
     */
    @Modifying
    @Query("DELETE FROM Alert a WHERE a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    int deleteExpiredAlerts(@Param("now") LocalDateTime now);

    /**
     * 우선순위 높은 읽지 않은 알림 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.status = 'UNREAD' AND a.priority IN ('HIGH', 'CRITICAL') ORDER BY a.priority DESC, a.createdAt DESC")
    List<Alert> findHighPriorityUnreadAlerts();

    /**
     * 오늘 생성된 알림 개수
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt >= :today")
    long countTodayAlerts(@Param("today") LocalDateTime today);
}
