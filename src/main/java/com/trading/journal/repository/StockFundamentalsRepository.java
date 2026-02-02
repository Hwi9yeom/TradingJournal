package com.trading.journal.repository;

import com.trading.journal.entity.Sector;
import com.trading.journal.entity.StockFundamentals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockFundamentalsRepository
        extends JpaRepository<StockFundamentals, Long>,
                JpaSpecificationExecutor<StockFundamentals> {

    /** 심볼로 펀더멘털 데이터 조회 */
    Optional<StockFundamentals> findBySymbol(String symbol);

    /** 심볼 존재 여부 확인 */
    boolean existsBySymbol(String symbol);

    /** PE Ratio 범위로 조회 */
    @Query(
            "SELECT f FROM StockFundamentals f "
                    + "WHERE f.peRatio BETWEEN :minPeRatio AND :maxPeRatio "
                    + "ORDER BY f.peRatio ASC")
    List<StockFundamentals> findByPeRatioBetween(
            @Param("minPeRatio") BigDecimal minPeRatio, @Param("maxPeRatio") BigDecimal maxPeRatio);

    /** 섹터별 조회 */
    @Query(
            "SELECT f FROM StockFundamentals f "
                    + "WHERE f.sector IN :sectors "
                    + "ORDER BY f.marketCap DESC")
    List<StockFundamentals> findBySectorIn(@Param("sectors") List<Sector> sectors);

    /** 배당 수익률 범위로 조회 */
    @Query(
            "SELECT f FROM StockFundamentals f "
                    + "WHERE f.dividendYield >= :minYield "
                    + "ORDER BY f.dividendYield DESC")
    List<StockFundamentals> findByDividendYieldGreaterThanEqual(
            @Param("minYield") BigDecimal minYield);

    /** 시가총액 범위로 조회 */
    @Query(
            "SELECT f FROM StockFundamentals f "
                    + "WHERE f.marketCap BETWEEN :minMarketCap AND :maxMarketCap "
                    + "ORDER BY f.marketCap DESC")
    List<StockFundamentals> findByMarketCapBetween(
            @Param("minMarketCap") BigDecimal minMarketCap,
            @Param("maxMarketCap") BigDecimal maxMarketCap);

    /** ROE 기준 상위 종목 조회 */
    @Query(
            "SELECT f FROM StockFundamentals f "
                    + "WHERE f.returnOnEquity IS NOT NULL "
                    + "ORDER BY f.returnOnEquity DESC "
                    + "LIMIT :limit")
    List<StockFundamentals> findTopByReturnOnEquity(@Param("limit") int limit);

    /** 섹터별 평균 PE Ratio */
    @Query(
            "SELECT f.sector, AVG(f.peRatio) "
                    + "FROM StockFundamentals f "
                    + "WHERE f.peRatio IS NOT NULL "
                    + "GROUP BY f.sector")
    List<Object[]> findAveragePeRatioBySector();

    /** 심볼 목록으로 조회 */
    List<StockFundamentals> findBySymbolIn(List<String> symbols);
}
