package com.trading.journal.repository;

import com.trading.journal.entity.SavingsCategory;
import com.trading.journal.entity.SavingsRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 저축 기록 Repository.
 *
 * <p>금액이 암호화 저장되므로 SUM/AVG 집계는 서비스 계층에서 수행한다.
 */
@Repository
public interface SavingsRecordRepository extends JpaRepository<SavingsRecord, Long> {

    /** 전체 기록 최신순 */
    List<SavingsRecord> findAllByOrderBySavedDateDesc();

    /** 기간 조회 (계좌 null이면 전체) */
    @Query(
            "SELECT s FROM SavingsRecord s WHERE "
                    + "(:accountId IS NULL OR s.accountId = :accountId) "
                    + "AND s.savedDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY s.savedDate DESC")
    List<SavingsRecord> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 특정 가계(계좌 null 포함)의 기간 조회 */
    @Query(
            "SELECT s FROM SavingsRecord s WHERE "
                    + "((:accountId IS NULL AND s.accountId IS NULL) OR s.accountId = :accountId) "
                    + "AND s.savedDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY s.savedDate DESC")
    List<SavingsRecord> findByExactAccountAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 분류별 조회 */
    List<SavingsRecord> findByCategoryOrderBySavedDateDesc(SavingsCategory category);
}
