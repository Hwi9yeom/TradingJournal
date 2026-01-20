package com.trading.journal.repository;

import com.trading.journal.entity.Goal;
import com.trading.journal.entity.GoalStatus;
import com.trading.journal.entity.GoalType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** 상태별 목표 조회 */
    List<Goal> findByStatusOrderByDeadlineAsc(GoalStatus status);

    /** 유형별 목표 조회 */
    List<Goal> findByGoalTypeOrderByCreatedAtDesc(GoalType goalType);

    /** 활성 목표 조회 */
    List<Goal> findByStatusInOrderByDeadlineAsc(List<GoalStatus> statuses);

    /** 마감일이 지난 활성 목표 조회 */
    @Query("SELECT g FROM Goal g WHERE g.status = 'ACTIVE' AND g.deadline < :today")
    List<Goal> findOverdueGoals(@Param("today") LocalDate today);

    /** 곧 마감되는 목표 조회 (N일 이내) */
    @Query(
            "SELECT g FROM Goal g WHERE g.status = 'ACTIVE' AND g.deadline BETWEEN :today AND :futureDate ORDER BY g.deadline ASC")
    List<Goal> findUpcomingDeadlines(
            @Param("today") LocalDate today, @Param("futureDate") LocalDate futureDate);

    /** 계좌별 목표 조회 */
    List<Goal> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /** 알림이 활성화된 목표 조회 */
    List<Goal> findByStatusAndNotificationEnabledTrue(GoalStatus status);

    /** 특정 기간 내 생성된 목표 수 */
    @Query("SELECT COUNT(g) FROM Goal g WHERE g.createdAt >= :startDate")
    long countGoalsCreatedSince(@Param("startDate") java.time.LocalDateTime startDate);

    /** 달성된 목표 수 */
    long countByStatus(GoalStatus status);

    /** 유형별 평균 달성률 */
    @Query("SELECT g.goalType, AVG(g.progressPercent) FROM Goal g GROUP BY g.goalType")
    List<Object[]> getAverageProgressByType();
}
