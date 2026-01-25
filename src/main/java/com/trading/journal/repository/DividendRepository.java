package com.trading.journal.repository;

import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Stock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    // 기존 메서드 (하위 호환성)
    List<Dividend> findByStockOrderByPaymentDateDesc(Stock stock);

    List<Dividend> findByPaymentDateBetweenOrderByPaymentDateDesc(
            LocalDate startDate, LocalDate endDate);

    @Query("SELECT d FROM Dividend d WHERE d.stock.symbol = :symbol ORDER BY d.paymentDate DESC")
    List<Dividend> findByStockSymbol(@Param("symbol") String symbol);

    @Query(
            "SELECT SUM(d.netAmount) FROM Dividend d WHERE d.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDividendsByPeriod(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(
            "SELECT SUM(d.netAmount) FROM Dividend d WHERE d.stock = :stock AND d.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDividendsByStockAndPeriod(
            @Param("stock") Stock stock,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(
            "SELECT d.stock, SUM(d.netAmount) as totalDividend FROM Dividend d "
                    + "WHERE d.paymentDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY d.stock "
                    + "ORDER BY totalDividend DESC")
    List<Object[]> getTopDividendStocks(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(
            "SELECT YEAR(d.paymentDate) as year, MONTH(d.paymentDate) as month, SUM(d.netAmount) as totalDividend "
                    + "FROM Dividend d "
                    + "GROUP BY YEAR(d.paymentDate), MONTH(d.paymentDate) "
                    + "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyDividends();

    @Query(
            "SELECT YEAR(d.paymentDate) as year, SUM(d.netAmount) as totalDividend "
                    + "FROM Dividend d "
                    + "GROUP BY YEAR(d.paymentDate) "
                    + "ORDER BY year DESC")
    List<Object[]> getYearlyDividends();

    // Account 기반 새로운 메서드
    List<Dividend> findByAccountIdOrderByPaymentDateDesc(Long accountId);

    List<Dividend> findByAccountIdAndPaymentDateBetweenOrderByPaymentDateDesc(
            Long accountId, LocalDate startDate, LocalDate endDate);

    @Query(
            "SELECT d FROM Dividend d WHERE d.account.id = :accountId AND d.stock.symbol = :symbol ORDER BY d.paymentDate DESC")
    List<Dividend> findByAccountIdAndStockSymbol(
            @Param("accountId") Long accountId, @Param("symbol") String symbol);

    @Query(
            "SELECT SUM(d.netAmount) FROM Dividend d WHERE d.account.id = :accountId AND d.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDividendsByAccountAndPeriod(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(
            "SELECT d.stock, SUM(d.netAmount) as totalDividend FROM Dividend d "
                    + "WHERE d.account.id = :accountId AND d.paymentDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY d.stock "
                    + "ORDER BY totalDividend DESC")
    List<Object[]> getTopDividendStocksByAccount(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(
            "SELECT YEAR(d.paymentDate) as year, MONTH(d.paymentDate) as month, SUM(d.netAmount) as totalDividend "
                    + "FROM Dividend d "
                    + "WHERE d.account.id = :accountId "
                    + "GROUP BY YEAR(d.paymentDate), MONTH(d.paymentDate) "
                    + "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyDividendsByAccount(@Param("accountId") Long accountId);

    // account_id가 NULL인 배당금 (마이그레이션용)
    @Query("SELECT d FROM Dividend d WHERE d.account IS NULL")
    List<Dividend> findByAccountIsNull();

    // ===== 집계 쿼리 (N+1 방지) =====

    /** 전체 순배당금 합계 */
    @Query("SELECT COALESCE(SUM(d.netAmount), 0) FROM Dividend d")
    BigDecimal sumTotalNetAmount();

    /** 전체 세금 합계 */
    @Query("SELECT COALESCE(SUM(d.taxAmount), 0) FROM Dividend d")
    BigDecimal sumTotalTaxAmount();

    /** FETCH JOIN으로 Stock 함께 로딩 */
    @Query("SELECT d FROM Dividend d JOIN FETCH d.stock LEFT JOIN FETCH d.account")
    List<Dividend> findAllWithStockAndAccount();
}
