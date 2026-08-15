package com.trading.journal.repository;

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
 * <p>모든 조회는 소유 사용자({@code userId})로 격리된다. userId가 null인 행은 인증 사용자 정보가 없던 시점의 레코드로, null 사용자로만 조회된다.
 *
 * <p>금액이 암호화 저장되므로 SUM/AVG 집계는 서비스 계층에서 수행한다.
 */
@Repository
public interface SavingsRecordRepository extends JpaRepository<SavingsRecord, Long> {

    /** 사용자의 전체 기록 최신순 */
    @Query(
            "SELECT s FROM SavingsRecord s WHERE "
                    + "((:userId IS NULL AND s.userId IS NULL) OR s.userId = :userId) "
                    + "ORDER BY s.savedDate DESC")
    List<SavingsRecord> findAllByUserOrderBySavedDateDesc(@Param("userId") Long userId);

    /** 사용자의 기간 조회 (계좌 null이면 전체 계좌) */
    @Query(
            "SELECT s FROM SavingsRecord s WHERE "
                    + "((:userId IS NULL AND s.userId IS NULL) OR s.userId = :userId) "
                    + "AND (:accountId IS NULL OR s.accountId = :accountId) "
                    + "AND s.savedDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY s.savedDate DESC")
    List<SavingsRecord> findByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 사용자의 특정 가계(계좌 null 포함) 기간 조회 */
    @Query(
            "SELECT s FROM SavingsRecord s WHERE "
                    + "((:userId IS NULL AND s.userId IS NULL) OR s.userId = :userId) "
                    + "AND ((:accountId IS NULL AND s.accountId IS NULL) OR s.accountId = :accountId) "
                    + "AND s.savedDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY s.savedDate DESC")
    List<SavingsRecord> findByUserAndExactAccountAndDateRange(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
