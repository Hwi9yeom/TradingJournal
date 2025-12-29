package com.trading.journal.repository;

import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 기존 메서드 (하위 호환성)
    List<Transaction> findByStockIdOrderByTransactionDateDesc(Long stockId);
    List<Transaction> findByTypeOrderByTransactionDateDesc(TransactionType type);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock ORDER BY t.transactionDate DESC")
    List<Transaction> findAllWithStock();

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findBySymbolWithStock(@Param("symbol") String symbol);

    @Query("SELECT t FROM Transaction t WHERE t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findByStockSymbol(@Param("symbol") String symbol);

    // Account 기반 새로운 메서드
    List<Transaction> findByAccountIdOrderByTransactionDateDesc(Long accountId);
    List<Transaction> findByAccountIdAndStockIdOrderByTransactionDateDesc(Long accountId, Long stockId);
    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.account.id = :accountId ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdWithStock(@Param("accountId") Long accountId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.account.id = :accountId AND t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.stock WHERE t.account.id = :accountId AND t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdAndSymbol(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    // account_id가 NULL인 거래 (마이그레이션용)
    @Query("SELECT t FROM Transaction t WHERE t.account IS NULL")
    List<Transaction> findByAccountIsNull();
}