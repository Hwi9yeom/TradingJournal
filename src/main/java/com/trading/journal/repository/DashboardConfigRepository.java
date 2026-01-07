package com.trading.journal.repository;

import com.trading.journal.entity.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 대시보드 설정 Repository
 */
@Repository
public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {

    /**
     * 사용자의 활성 대시보드 설정 조회
     */
    Optional<DashboardConfig> findByUserIdAndActiveTrue(Long userId);

    /**
     * 사용자의 모든 대시보드 설정 조회
     */
    List<DashboardConfig> findByUserIdOrderByConfigNameAsc(Long userId);

    /**
     * 사용자의 특정 이름의 설정 조회
     */
    Optional<DashboardConfig> findByUserIdAndConfigName(Long userId, String configName);

    /**
     * 사용자의 설정 존재 여부 확인
     */
    boolean existsByUserIdAndConfigName(Long userId, String configName);

    /**
     * 활성 설정과 위젯을 함께 조회
     */
    @Query("SELECT DISTINCT d FROM DashboardConfig d LEFT JOIN FETCH d.widgets WHERE d.userId = :userId AND d.active = true")
    Optional<DashboardConfig> findActiveWithWidgets(@Param("userId") Long userId);
}
