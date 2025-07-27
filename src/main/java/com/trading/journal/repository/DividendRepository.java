package com.trading.journal.repository;

import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    
    List<Dividend> findByStockOrderByPaymentDateDesc(Stock stock);
    
    List<Dividend> findByPaymentDateBetweenOrderByPaymentDateDesc(LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT d FROM Dividend d WHERE d.stock.symbol = :symbol ORDER BY d.paymentDate DESC")
    List<Dividend> findByStockSymbol(@Param("symbol") String symbol);
    
    @Query("SELECT SUM(d.netAmount) FROM Dividend d WHERE d.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDividendsByPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT SUM(d.netAmount) FROM Dividend d WHERE d.stock = :stock AND d.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDividendsByStockAndPeriod(@Param("stock") Stock stock, 
                                                 @Param("startDate") LocalDate startDate, 
                                                 @Param("endDate") LocalDate endDate);
    
    @Query("SELECT d.stock, SUM(d.netAmount) as totalDividend FROM Dividend d " +
           "WHERE d.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY d.stock " +
           "ORDER BY totalDividend DESC")
    List<Object[]> getTopDividendStocks(@Param("startDate") LocalDate startDate, 
                                       @Param("endDate") LocalDate endDate);
    
    @Query("SELECT YEAR(d.paymentDate) as year, MONTH(d.paymentDate) as month, SUM(d.netAmount) as totalDividend " +
           "FROM Dividend d " +
           "GROUP BY YEAR(d.paymentDate), MONTH(d.paymentDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyDividends();
    
    @Query("SELECT YEAR(d.paymentDate) as year, SUM(d.netAmount) as totalDividend " +
           "FROM Dividend d " +
           "GROUP BY YEAR(d.paymentDate) " +
           "ORDER BY year DESC")
    List<Object[]> getYearlyDividends();
}