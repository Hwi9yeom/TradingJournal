package com.trading.journal.repository;

import com.trading.journal.entity.TradePlan;
import com.trading.journal.entity.TradePlanStatus;
import com.trading.journal.entity.TradeStrategy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradePlanRepository extends JpaRepository<TradePlan, Long> {

    // 상태별 조회
    List<TradePlan> findByStatusOrderByCreatedAtDesc(TradePlanStatus status);

    Page<TradePlan> findByStatusOrderByCreatedAtDesc(TradePlanStatus status, Pageable pageable);

    // 계좌별 조회
    List<TradePlan> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<TradePlan> findByAccountIdAndStatusOrderByCreatedAtDesc(
            Long accountId, TradePlanStatus status);

    // 종목별 조회
    List<TradePlan> findByStockIdOrderByCreatedAtDesc(Long stockId);

    @Query("SELECT p FROM TradePlan p WHERE p.stock.symbol = :symbol ORDER BY p.createdAt DESC")
    List<TradePlan> findByStockSymbolOrderByCreatedAtDesc(@Param("symbol") String symbol);

    // 전략별 조회
    List<TradePlan> findByStrategyOrderByCreatedAtDesc(TradeStrategy strategy);

    // 대기 중인 플랜 (만료 임박 순)
    @Query(
            "SELECT p FROM TradePlan p WHERE p.status = 'PLANNED' "
                    + "AND (p.validUntil IS NULL OR p.validUntil > :now) "
                    + "ORDER BY p.validUntil ASC NULLS LAST, p.createdAt DESC")
    List<TradePlan> findPendingPlans(@Param("now") LocalDateTime now);

    // 만료된 플랜 조회 (스케줄러용)
    @Query(
            "SELECT p FROM TradePlan p WHERE p.status = 'PLANNED' "
                    + "AND p.validUntil IS NOT NULL AND p.validUntil < :now")
    List<TradePlan> findExpiredPlans(@Param("now") LocalDateTime now);

    // 만료 플랜 일괄 업데이트
    @Modifying
    @Query(
            "UPDATE TradePlan p SET p.status = 'EXPIRED', p.updatedAt = :now "
                    + "WHERE p.status = 'PLANNED' AND p.validUntil IS NOT NULL AND p.validUntil < :now")
    int expirePlans(@Param("now") LocalDateTime now);

    // 통계 쿼리
    long countByStatus(TradePlanStatus status);

    @Query(
            "SELECT p.strategy, COUNT(p), "
                    + "SUM(CASE WHEN p.status = 'EXECUTED' THEN 1 ELSE 0 END), "
                    + "AVG(p.actualRMultiple) "
                    + "FROM TradePlan p WHERE p.strategy IS NOT NULL GROUP BY p.strategy")
    List<Object[]> getStrategyStatistics();

    // 실행된 플랜 중 결과가 있는 것들
    @Query(
            "SELECT p FROM TradePlan p WHERE p.status = 'EXECUTED' "
                    + "AND p.resultTransactionId IS NOT NULL "
                    + "AND p.followedPlan IS NOT NULL")
    List<TradePlan> findExecutedPlansWithResults();

    // 계획 준수율 계산
    @Query(
            "SELECT "
                    + "COUNT(CASE WHEN p.followedPlan = true THEN 1 END) * 100.0 / NULLIF(COUNT(p), 0) "
                    + "FROM TradePlan p WHERE p.status = 'EXECUTED' AND p.followedPlan IS NOT NULL")
    Double getPlanAdherenceRate();

    // 평균 R-multiple (실행된 것들)
    @Query(
            "SELECT AVG(p.actualRMultiple) FROM TradePlan p "
                    + "WHERE p.status = 'EXECUTED' AND p.actualRMultiple IS NOT NULL")
    Double getAverageRMultiple();

    // 기간별 조회
    @Query(
            "SELECT p FROM TradePlan p WHERE p.createdAt BETWEEN :start AND :end ORDER BY p.createdAt DESC")
    List<TradePlan> findByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 최근 N개 조회
    Page<TradePlan> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 거래 ID로 연결된 플랜 조회
    Optional<TradePlan> findByExecutedTransactionId(Long transactionId);
}
