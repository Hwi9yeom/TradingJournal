package com.trading.journal.repository;

import com.trading.journal.entity.BenchmarkPrice;
import com.trading.journal.entity.BenchmarkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BenchmarkPriceRepository extends JpaRepository<BenchmarkPrice, Long> {

    /**
     * 특정 벤치마크의 특정 날짜 가격 조회
     */
    Optional<BenchmarkPrice> findByBenchmarkAndPriceDate(BenchmarkType benchmark, LocalDate priceDate);

    /**
     * 특정 벤치마크의 기간별 가격 조회
     */
    List<BenchmarkPrice> findByBenchmarkAndPriceDateBetweenOrderByPriceDateAsc(
            BenchmarkType benchmark, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 벤치마크의 모든 가격 조회 (날짜순)
     */
    List<BenchmarkPrice> findByBenchmarkOrderByPriceDateAsc(BenchmarkType benchmark);

    /**
     * 특정 벤치마크의 최신 가격 조회
     */
    Optional<BenchmarkPrice> findFirstByBenchmarkOrderByPriceDateDesc(BenchmarkType benchmark);

    /**
     * 특정 벤치마크의 가장 오래된 가격 조회
     */
    Optional<BenchmarkPrice> findFirstByBenchmarkOrderByPriceDateAsc(BenchmarkType benchmark);

    /**
     * 특정 날짜 이전의 가장 최근 가격 조회
     */
    Optional<BenchmarkPrice> findFirstByBenchmarkAndPriceDateLessThanEqualOrderByPriceDateDesc(
            BenchmarkType benchmark, LocalDate date);

    /**
     * 특정 날짜 이후의 가장 빠른 가격 조회
     */
    Optional<BenchmarkPrice> findFirstByBenchmarkAndPriceDateGreaterThanEqualOrderByPriceDateAsc(
            BenchmarkType benchmark, LocalDate date);

    /**
     * 특정 벤치마크의 데이터 존재 여부
     */
    boolean existsByBenchmark(BenchmarkType benchmark);

    /**
     * 특정 벤치마크의 데이터 개수
     */
    long countByBenchmark(BenchmarkType benchmark);

    /**
     * 일간 수익률 조회 (기간별)
     */
    @Query("SELECT b.dailyReturn FROM BenchmarkPrice b " +
           "WHERE b.benchmark = :benchmark " +
           "AND b.priceDate BETWEEN :startDate AND :endDate " +
           "AND b.dailyReturn IS NOT NULL " +
           "ORDER BY b.priceDate ASC")
    List<java.math.BigDecimal> findDailyReturnsByBenchmarkAndPeriod(
            @Param("benchmark") BenchmarkType benchmark,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 특정 날짜들의 가격 조회 (포트폴리오 매칭용)
     */
    @Query("SELECT b FROM BenchmarkPrice b " +
           "WHERE b.benchmark = :benchmark " +
           "AND b.priceDate IN :dates " +
           "ORDER BY b.priceDate ASC")
    List<BenchmarkPrice> findByBenchmarkAndPriceDates(
            @Param("benchmark") BenchmarkType benchmark,
            @Param("dates") List<LocalDate> dates);

    /**
     * 기간 내 종가 조회
     */
    @Query("SELECT b.priceDate, b.closePrice FROM BenchmarkPrice b " +
           "WHERE b.benchmark = :benchmark " +
           "AND b.priceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY b.priceDate ASC")
    List<Object[]> findClosePricesByBenchmarkAndPeriod(
            @Param("benchmark") BenchmarkType benchmark,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
