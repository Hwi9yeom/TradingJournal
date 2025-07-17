package com.trading.journal.repository;

import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStockIdOrderByTransactionDateDesc(Long stockId);
    
    List<Transaction> findByTypeOrderByTransactionDateDesc(TransactionType type);
    
    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.stock.symbol = :symbol ORDER BY t.transactionDate DESC")
    List<Transaction> findByStockSymbol(@Param("symbol") String symbol);
}