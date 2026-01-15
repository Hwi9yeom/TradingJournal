package com.trading.journal.repository;

import com.trading.journal.entity.HistoricalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, Long> {

    /**
     * 특정 심볼의 특정 날짜 가격 조회
     */
    Optional<HistoricalPrice> findBySymbolAndPriceDate(String symbol, LocalDate priceDate);

    /**
     * 특정 심볼의 기간별 가격 조회
     */
    List<HistoricalPrice> findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 심볼의 모든 가격 조회 (날짜순)
     */
    List<HistoricalPrice> findBySymbolOrderByPriceDateAsc(String symbol);

    /**
     * 특정 심볼의 최신 가격 조회
     */
    Optional<HistoricalPrice> findFirstBySymbolOrderByPriceDateDesc(String symbol);

    /**
     * 특정 심볼의 가장 오래된 가격 조회
     */
    Optional<HistoricalPrice> findFirstBySymbolOrderByPriceDateAsc(String symbol);

    /**
     * 특정 날짜 이전의 가장 최근 가격 조회
     */
    Optional<HistoricalPrice> findFirstBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(
            String symbol, LocalDate date);

    /**
     * 특정 심볼의 데이터 존재 여부
     */
    boolean existsBySymbol(String symbol);

    /**
     * 특정 심볼의 데이터 개수
     */
    long countBySymbol(String symbol);

    /**
     * 특정 심볼의 기간별 데이터 개수
     */
    long countBySymbolAndPriceDateBetween(String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 심볼의 특정 기간 데이터 삭제
     */
    @Modifying
    @Query("DELETE FROM HistoricalPrice h WHERE h.symbol = :symbol AND h.priceDate BETWEEN :startDate AND :endDate")
    void deleteBySymbolAndPriceDateBetween(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 특정 심볼의 모든 데이터 삭제
     */
    void deleteBySymbol(String symbol);

    /**
     * 여러 심볼의 기간별 가격 조회
     */
    @Query("SELECT h FROM HistoricalPrice h " +
           "WHERE h.symbol IN :symbols " +
           "AND h.priceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY h.symbol, h.priceDate ASC")
    List<HistoricalPrice> findBySymbolsAndPriceDateBetween(
            @Param("symbols") List<String> symbols,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 캐시에 있는 최신 날짜 조회
     */
    @Query("SELECT MAX(h.priceDate) FROM HistoricalPrice h WHERE h.symbol = :symbol")
    Optional<LocalDate> findLatestPriceDateBySymbol(@Param("symbol") String symbol);

    /**
     * 캐시에 있는 가장 오래된 날짜 조회
     */
    @Query("SELECT MIN(h.priceDate) FROM HistoricalPrice h WHERE h.symbol = :symbol")
    Optional<LocalDate> findOldestPriceDateBySymbol(@Param("symbol") String symbol);
}
