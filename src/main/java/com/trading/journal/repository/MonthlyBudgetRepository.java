package com.trading.journal.repository;

import com.trading.journal.entity.MonthlyBudget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 월별 가계 점검 Repository.
 *
 * <p>모든 조회는 소유 사용자({@code userId})로 격리된다. userId가 null인 행은 인증 사용자 정보가 없던 시점의 레코드로, null 사용자로만 조회된다.
 *
 * <p>금액 컬럼이 암호화 저장되므로 SUM/AVG 같은 DB 집계는 사용하지 않는다. 조회는 기간/계좌 필터까지만 담당하고 합계는 서비스에서 계산한다.
 */
@Repository
public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    /** 사용자 + 계좌(전체 가계는 null) + 월로 단건 조회 */
    @Query(
            "SELECT b FROM MonthlyBudget b WHERE "
                    + "((:userId IS NULL AND b.userId IS NULL) OR b.userId = :userId) "
                    + "AND ((:accountId IS NULL AND b.accountId IS NULL) OR b.accountId = :accountId) "
                    + "AND b.budgetMonth = :budgetMonth")
    Optional<MonthlyBudget> findByUserAndAccountAndMonth(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("budgetMonth") LocalDate budgetMonth);

    /** 사용자의 전체 월 최신순 조회 */
    @Query(
            "SELECT b FROM MonthlyBudget b WHERE "
                    + "((:userId IS NULL AND b.userId IS NULL) OR b.userId = :userId) "
                    + "ORDER BY b.budgetMonth DESC")
    List<MonthlyBudget> findAllByUserOrderByBudgetMonthDesc(@Param("userId") Long userId);

    /** 사용자의 기간 내 월 최신순 조회 (계좌 null이면 전체 계좌) */
    @Query(
            "SELECT b FROM MonthlyBudget b WHERE "
                    + "((:userId IS NULL AND b.userId IS NULL) OR b.userId = :userId) "
                    + "AND (:accountId IS NULL OR b.accountId = :accountId) "
                    + "AND b.budgetMonth BETWEEN :startMonth AND :endMonth "
                    + "ORDER BY b.budgetMonth DESC")
    List<MonthlyBudget> findByUserAndMonthRange(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("startMonth") LocalDate startMonth,
            @Param("endMonth") LocalDate endMonth);
}
