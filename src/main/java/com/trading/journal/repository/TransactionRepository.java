package com.trading.journal.repository;

import com.trading.journal.entity.Account;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 기존 메서드 (하위 호환성)
    List<Transaction> findByStockIdOrderByTransactionDateDesc(Long stockId);

    List<Transaction> findByTypeOrderByTransactionDateDesc(TransactionType type);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock LEFT JOIN FETCH t.account WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock LEFT JOIN FETCH t.account ORDER BY t.transactionDate DESC")
    List<Transaction> findAllWithStock();

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findBySymbolWithStock(@Param("symbol") String symbol);

    @Query(
            "SELECT t FROM Transaction t WHERE t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findByStockSymbol(@Param("symbol") String symbol);

    // Account 기반 새로운 메서드
    List<Transaction> findByAccountIdOrderByTransactionDateDesc(Long accountId);

    List<Transaction> findByAccountIdAndStockIdOrderByTransactionDateDesc(
            Long accountId, Long stockId);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock JOIN FETCH t.account WHERE t.account.id = :accountId ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdWithStock(@Param("accountId") Long accountId);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock JOIN FETCH t.account WHERE t.account.id = :accountId AND t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock JOIN FETCH t.account WHERE t.account.id = :accountId AND t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdAndSymbol(
            @Param("accountId") Long accountId, @Param("symbol") String symbol);

    // account_id가 NULL인 거래 (마이그레이션용)
    @Query("SELECT t FROM Transaction t WHERE t.account IS NULL")
    List<Transaction> findByAccountIsNull();

    // ===== FIFO 계산용 쿼리 =====

    /** FIFO용: 잔여 수량이 있는 매수 거래 조회 (날짜 오름차순) 매도 날짜 이전의 매수 거래만 조회 */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "((:accountId IS NULL AND t.account IS NULL) OR t.account.id = :accountId) "
                    + "AND t.stock.id = :stockId "
                    + "AND t.type = 'BUY' "
                    + "AND t.remainingQuantity > 0 "
                    + "AND t.transactionDate <= :beforeDate "
                    + "ORDER BY t.transactionDate ASC")
    List<Transaction> findAvailableBuyTransactionsForFifo(
            @Param("accountId") Long accountId,
            @Param("stockId") Long stockId,
            @Param("beforeDate") LocalDateTime beforeDate);

    /** 계좌-종목 쌍 조회 (FIFO 마이그레이션용) */
    @Query("SELECT DISTINCT t.account.id, t.stock.id FROM Transaction t")
    List<Object[]> findDistinctAccountStockPairs();

    /** 계좌/종목별 거래 조회 (날짜 오름차순) - FIFO 재계산용 */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "((:accountId IS NULL AND t.account IS NULL) OR t.account.id = :accountId) "
                    + "AND t.stock.id = :stockId "
                    + "ORDER BY t.transactionDate ASC")
    List<Transaction> findByAccountIdAndStockIdOrderByTransactionDateAsc(
            @Param("accountId") Long accountId, @Param("stockId") Long stockId);

    /** 실현 손익 합계 조회 (계좌별/기간별) */
    @Query(
            "SELECT COALESCE(SUM(t.realizedPnl), 0) FROM Transaction t WHERE "
                    + "t.type = 'SELL' "
                    + "AND (:accountId IS NULL OR t.account.id = :accountId) "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate")
    java.math.BigDecimal sumRealizedPnlByAccountAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 전체 실현 손익 합계 조회 (모든 SELL 거래) */
    @Query("SELECT COALESCE(SUM(t.realizedPnl), 0) FROM Transaction t WHERE t.type = 'SELL'")
    java.math.BigDecimal sumTotalRealizedPnl();

    // ===== 리스크 관리용 쿼리 =====

    /** 계좌/종목/타입별 거래 조회 (날짜 오름차순) - R-multiple 계산용 */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "((:accountId IS NULL AND t.account IS NULL) OR t.account.id = :accountId) "
                    + "AND t.stock.id = :stockId "
                    + "AND t.type = :type "
                    + "ORDER BY t.transactionDate ASC")
    List<Transaction> findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
            @Param("accountId") Long accountId,
            @Param("stockId") Long stockId,
            @Param("type") TransactionType type);

    /** R-multiple 계산용: 초기 리스크가 설정된 BUY 거래만 조회 (성능 최적화) */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "((:accountId IS NULL AND t.account IS NULL) OR t.account.id = :accountId) "
                    + "AND t.stock.id = :stockId "
                    + "AND t.type = 'BUY' "
                    + "AND t.initialRiskAmount IS NOT NULL "
                    + "AND t.initialRiskAmount > 0 "
                    + "ORDER BY t.transactionDate ASC")
    List<Transaction> findBuyTransactionsWithInitialRisk(
            @Param("accountId") Long accountId, @Param("stockId") Long stockId);

    /** R-multiple이 있는 SELL 거래 조회 (리스크 분석용) */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "(:accountId IS NULL OR t.account.id = :accountId) "
                    + "AND t.type = 'SELL' "
                    + "AND t.rMultiple IS NOT NULL "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY t.transactionDate DESC")
    List<Transaction> findSellTransactionsWithRMultiple(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** SELL 거래 조회 (계좌별/기간별) - Kelly Criterion 계산용 */
    @Query(
            "SELECT t FROM Transaction t WHERE "
                    + "(:accountId IS NULL OR t.account.id = :accountId) "
                    + "AND t.type = :type "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY t.transactionDate ASC")
    List<Transaction> findByAccountIdAndTypeAndDateRange(
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 일별 실현 손익 조회 (리스크 대시보드용) */
    @Query(
            "SELECT COALESCE(SUM(t.realizedPnl), 0) FROM Transaction t WHERE "
                    + "(:accountId IS NULL OR t.account.id = :accountId) "
                    + "AND t.type = 'SELL' "
                    + "AND CAST(t.transactionDate AS DATE) = :date")
    java.math.BigDecimal sumRealizedPnlByDate(
            @Param("accountId") Long accountId, @Param("date") java.time.LocalDate date);

    // ===== AI 거래 복기용 쿼리 =====

    /** 같은 종목의 가장 최근 매수 거래 조회 (AI 거래 복기 생성용) */
    Optional<Transaction>
            findFirstByAccountAndStockAndTypeAndTransactionDateBeforeOrderByTransactionDateDesc(
                    Account account, Stock stock, TransactionType type, LocalDateTime beforeDate);
}
