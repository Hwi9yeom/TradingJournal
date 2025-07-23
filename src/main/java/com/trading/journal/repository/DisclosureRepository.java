package com.trading.journal.repository;

import com.trading.journal.entity.Disclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DisclosureRepository extends JpaRepository<Disclosure, Long> {
    
    List<Disclosure> findByStockIdOrderByReceivedDateDesc(Long stockId);
    
    List<Disclosure> findByStockSymbolOrderByReceivedDateDesc(String stockSymbol);
    
    @Query("SELECT d FROM Disclosure d WHERE d.stock.symbol = :symbol " +
           "AND d.receivedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY d.receivedDate DESC")
    List<Disclosure> findByStockSymbolAndDateRange(
        @Param("symbol") String symbol,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT d FROM Disclosure d WHERE d.isImportant = true " +
           "AND d.stock.id IN (SELECT p.stock.id FROM Portfolio p) " +
           "ORDER BY d.receivedDate DESC")
    List<Disclosure> findImportantDisclosuresForPortfolio();
    
    @Query("SELECT d FROM Disclosure d WHERE d.isRead = false " +
           "AND d.stock.id IN (SELECT p.stock.id FROM Portfolio p) " +
           "ORDER BY d.receivedDate DESC")
    List<Disclosure> findUnreadDisclosuresForPortfolio();
    
    Optional<Disclosure> findByReportNumber(String reportNumber);
    
    @Query("SELECT COUNT(d) FROM Disclosure d WHERE d.isRead = false " +
           "AND d.stock.id IN (SELECT p.stock.id FROM Portfolio p)")
    Long countUnreadDisclosuresForPortfolio();
    
    @Query("SELECT d FROM Disclosure d WHERE " +
           "d.receivedDate >= :since AND " +
           "d.stock.id IN (SELECT p.stock.id FROM Portfolio p) " +
           "ORDER BY d.receivedDate DESC")
    List<Disclosure> findRecentDisclosuresForPortfolio(@Param("since") LocalDateTime since);
}