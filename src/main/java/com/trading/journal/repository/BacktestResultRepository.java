package com.trading.journal.repository;

import com.trading.journal.entity.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백테스트 결과 리포지토리
 */
@Repository
public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {

    /**
     * 전략명으로 백테스트 결과 조회
     */
    List<BacktestResult> findByStrategyNameContainingIgnoreCaseOrderByExecutedAtDesc(String strategyName);

    /**
     * 최근 백테스트 결과 조회
     */
    List<BacktestResult> findTop20ByOrderByExecutedAtDesc();

    /**
     * 특정 심볼에 대한 백테스트 결과 조회
     */
    List<BacktestResult> findBySymbolOrderByExecutedAtDesc(String symbol);

    /**
     * 특정 기간 내 실행된 백테스트 조회
     */
    List<BacktestResult> findByExecutedAtBetweenOrderByExecutedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 수익률 기준 상위 백테스트 조회
     */
    @Query("SELECT b FROM BacktestResult b ORDER BY b.totalReturn DESC")
    List<BacktestResult> findTopPerformingBacktests();

    /**
     * 샤프비율 기준 상위 백테스트 조회
     */
    @Query("SELECT b FROM BacktestResult b WHERE b.sharpeRatio IS NOT NULL ORDER BY b.sharpeRatio DESC")
    List<BacktestResult> findByBestSharpeRatio();

    /**
     * 특정 전략 유형으로 조회
     */
    @Query("SELECT b FROM BacktestResult b WHERE b.strategyConfig LIKE %:strategyType% ORDER BY b.executedAt DESC")
    List<BacktestResult> findByStrategyType(@Param("strategyType") String strategyType);

    /**
     * 전략별 평균 성과 조회
     */
    @Query("SELECT b.strategyName, AVG(b.totalReturn), AVG(b.sharpeRatio), COUNT(b) " +
           "FROM BacktestResult b GROUP BY b.strategyName ORDER BY AVG(b.totalReturn) DESC")
    List<Object[]> findAveragePerformanceByStrategy();
}
