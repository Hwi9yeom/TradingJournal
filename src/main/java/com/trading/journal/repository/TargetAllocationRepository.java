package com.trading.journal.repository;

import com.trading.journal.entity.TargetAllocation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TargetAllocationRepository extends JpaRepository<TargetAllocation, Long> {

    /** 계정별 목표 배분 목록 조회 */
    List<TargetAllocation> findByAccountId(Long accountId);

    /** 계정별 활성 목표 배분 목록 조회 */
    List<TargetAllocation> findByAccountIdAndIsActiveTrue(Long accountId);

    /** 계정별 목표 배분 목록 조회 (우선순위, 목표 비율 순 정렬) */
    @Query(
            "SELECT ta FROM TargetAllocation ta "
                    + "WHERE ta.account.id = :accountId AND ta.isActive = true "
                    + "ORDER BY ta.priority ASC, ta.targetPercent DESC")
    List<TargetAllocation> findByAccountIdOrderByPriority(@Param("accountId") Long accountId);

    /** 계정별 목표 배분 목록 조회 (Stock 정보 포함) */
    @Query(
            "SELECT ta FROM TargetAllocation ta "
                    + "JOIN FETCH ta.stock "
                    + "WHERE ta.account.id = :accountId AND ta.isActive = true "
                    + "ORDER BY ta.priority ASC, ta.targetPercent DESC")
    List<TargetAllocation> findByAccountIdWithStock(@Param("accountId") Long accountId);

    /** 특정 계정 및 종목의 목표 배분 조회 */
    Optional<TargetAllocation> findByAccountIdAndStockId(Long accountId, Long stockId);

    /** 특정 계정 및 종목 심볼로 목표 배분 조회 */
    @Query(
            "SELECT ta FROM TargetAllocation ta "
                    + "JOIN ta.stock s "
                    + "WHERE ta.account.id = :accountId AND s.symbol = :symbol")
    Optional<TargetAllocation> findByAccountIdAndStockSymbol(
            @Param("accountId") Long accountId, @Param("symbol") String symbol);

    /** 특정 종목의 목표 배분 존재 여부 확인 */
    boolean existsByAccountIdAndStockId(Long accountId, Long stockId);

    /** 계정별 총 목표 배분율 합계 */
    @Query(
            "SELECT COALESCE(SUM(ta.targetPercent), 0) FROM TargetAllocation ta "
                    + "WHERE ta.account.id = :accountId AND ta.isActive = true")
    BigDecimal sumTargetPercentByAccountId(@Param("accountId") Long accountId);

    /** 특정 배분 제외한 총 목표 배분율 합계 */
    @Query(
            "SELECT COALESCE(SUM(ta.targetPercent), 0) FROM TargetAllocation ta "
                    + "WHERE ta.account.id = :accountId AND ta.isActive = true AND ta.id != :excludeId")
    BigDecimal sumTargetPercentExcluding(
            @Param("accountId") Long accountId, @Param("excludeId") Long excludeId);

    /** 계정별 활성 목표 배분 수 */
    int countByAccountIdAndIsActiveTrue(Long accountId);

    /** 계정별 목표 배분 삭제 */
    void deleteByAccountId(Long accountId);
}
