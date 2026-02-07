package com.trading.journal.service;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.dto.PortfolioTreemapDto;
import com.trading.journal.entity.HistoricalPrice;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.repository.HistoricalPriceRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioAnalysisService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final StockPriceService stockPriceService;
    private final HistoricalPriceRepository historicalPriceRepository;

    private static final Set<String> VALID_PERIODS =
            Set.of("1D", "1W", "1M", "MTD", "3M", "6M", "1Y");

    @Cacheable(value = "portfolio", key = "'summary'")
    public PortfolioSummaryDto getPortfolioSummary() {
        // FETCH JOIN으로 Stock과 Account를 함께 로딩하여 N+1 쿼리 방지
        List<Portfolio> portfolios = portfolioRepository.findAllWithStockAndAccount();

        if (portfolios.isEmpty()) {
            return buildEmptySummary();
        }

        // 병렬로 모든 가격 데이터 조회 (성능 최적화)
        Map<String, BigDecimal[]> priceMap = fetchPricesInParallel(portfolios);

        List<PortfolioDto> holdings = new ArrayList<>();
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalDayChange = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            String symbol = portfolio.getStock().getSymbol();
            BigDecimal[] prices =
                    priceMap.getOrDefault(
                            symbol, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal currentPrice = prices[0];
            BigDecimal previousClose = prices[1];

            PortfolioDto dto =
                    calculatePortfolioMetricsWithPrices(portfolio, currentPrice, previousClose);
            holdings.add(dto);

            totalInvestment = totalInvestment.add(portfolio.getTotalInvestment());
            totalCurrentValue = totalCurrentValue.add(dto.getCurrentValue());
            totalDayChange = totalDayChange.add(dto.getDayChange());
        }

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment);
        BigDecimal totalProfitLossPercent = BigDecimal.ZERO;
        BigDecimal totalDayChangePercent = BigDecimal.ZERO;

        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitLossPercent =
                    totalProfitLoss
                            .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
        }

        if (totalCurrentValue.subtract(totalDayChange).compareTo(BigDecimal.ZERO) > 0) {
            totalDayChangePercent =
                    totalDayChange
                            .divide(
                                    totalCurrentValue.subtract(totalDayChange),
                                    4,
                                    RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
        }

        // 실현 손익 계산 - DB 집계 쿼리 사용 (인메모리 연산 대신 성능 최적화)
        BigDecimal totalRealizedPnl = transactionRepository.sumTotalRealizedPnl();

        return PortfolioSummaryDto.builder()
                .totalInvestment(totalInvestment)
                .totalCurrentValue(totalCurrentValue)
                .totalProfitLoss(totalProfitLoss)
                .totalProfitLossPercent(totalProfitLossPercent)
                .totalDayChange(totalDayChange)
                .totalDayChangePercent(totalDayChangePercent)
                .totalRealizedPnl(totalRealizedPnl)
                .holdings(holdings)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /** 모든 포트폴리오의 가격을 병렬로 조회 (성능 최적화) */
    private Map<String, BigDecimal[]> fetchPricesInParallel(List<Portfolio> portfolios) {
        Map<String, BigDecimal[]> priceMap = new java.util.concurrent.ConcurrentHashMap<>();

        List<java.util.concurrent.CompletableFuture<Void>> futures =
                portfolios.stream()
                        .map(
                                portfolio -> {
                                    String symbol = portfolio.getStock().getSymbol();
                                    return java.util.concurrent.CompletableFuture.runAsync(
                                            () -> {
                                                try {
                                                    BigDecimal currentPrice =
                                                            stockPriceService.getCurrentPrice(
                                                                    symbol);
                                                    BigDecimal previousClose =
                                                            stockPriceService.getPreviousClose(
                                                                    symbol);
                                                    priceMap.put(
                                                            symbol,
                                                            new BigDecimal[] {
                                                                currentPrice, previousClose
                                                            });
                                                } catch (Exception e) {
                                                    log.warn(
                                                            "Failed to fetch prices for {}: {}",
                                                            symbol,
                                                            e.getMessage());
                                                    priceMap.put(
                                                            symbol,
                                                            new BigDecimal[] {
                                                                BigDecimal.ZERO, BigDecimal.ZERO
                                                            });
                                                }
                                            });
                                })
                        .collect(java.util.stream.Collectors.toList());

        // 모든 가격 조회 완료 대기
        java.util.concurrent.CompletableFuture.allOf(
                        futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .join();

        return priceMap;
    }

    private PortfolioSummaryDto buildEmptySummary() {
        return PortfolioSummaryDto.builder()
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalProfitLossPercent(BigDecimal.ZERO)
                .totalDayChange(BigDecimal.ZERO)
                .totalDayChangePercent(BigDecimal.ZERO)
                .totalRealizedPnl(BigDecimal.ZERO)
                .holdings(new ArrayList<>())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Cacheable(value = "portfolio", key = "#symbol")
    public PortfolioDto getPortfolioBySymbol(String symbol) {
        Portfolio portfolio =
                portfolioRepository
                        .findByStockSymbol(symbol)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Portfolio not found for symbol: " + symbol));
        return calculatePortfolioMetrics(portfolio);
    }

    private PortfolioDto calculatePortfolioMetrics(Portfolio portfolio) {
        try {
            BigDecimal currentPrice =
                    stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            BigDecimal previousClose =
                    stockPriceService.getPreviousClose(portfolio.getStock().getSymbol());
            return calculatePortfolioMetricsWithPrices(portfolio, currentPrice, previousClose);
        } catch (Exception e) {
            log.error(
                    "Failed to calculate portfolio metrics for symbol: {}",
                    portfolio.getStock().getSymbol(),
                    e);
            return buildFallbackPortfolioDto(portfolio);
        }
    }

    /** 미리 조회된 가격으로 포트폴리오 메트릭 계산 (병렬 조회 시 사용) */
    private PortfolioDto calculatePortfolioMetricsWithPrices(
            Portfolio portfolio, BigDecimal currentPrice, BigDecimal previousClose) {
        BigDecimal currentValue = currentPrice.multiply(portfolio.getQuantity());
        BigDecimal profitLoss = currentValue.subtract(portfolio.getTotalInvestment());
        BigDecimal profitLossPercent = BigDecimal.ZERO;

        if (portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0) {
            profitLossPercent =
                    profitLoss
                            .divide(portfolio.getTotalInvestment(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
        }

        BigDecimal dayChange =
                currentPrice.subtract(previousClose).multiply(portfolio.getQuantity());
        BigDecimal dayChangePercent = BigDecimal.ZERO;

        if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
            dayChangePercent =
                    currentPrice
                            .subtract(previousClose)
                            .divide(previousClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
        }

        return PortfolioDto.builder()
                .id(portfolio.getId())
                .stockSymbol(portfolio.getStock().getSymbol())
                .stockName(portfolio.getStock().getName())
                .quantity(portfolio.getQuantity())
                .averagePrice(portfolio.getAveragePrice())
                .totalInvestment(portfolio.getTotalInvestment())
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .dayChange(dayChange)
                .dayChangePercent(dayChangePercent)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /** 가격 조회 실패 시 사용할 기본 PortfolioDto */
    private PortfolioDto buildFallbackPortfolioDto(Portfolio portfolio) {
        return PortfolioDto.builder()
                .id(portfolio.getId())
                .stockSymbol(portfolio.getStock().getSymbol())
                .stockName(portfolio.getStock().getName())
                .quantity(portfolio.getQuantity())
                .averagePrice(portfolio.getAveragePrice())
                .totalInvestment(portfolio.getTotalInvestment())
                .currentPrice(BigDecimal.ZERO)
                .currentValue(BigDecimal.ZERO)
                .profitLoss(BigDecimal.ZERO)
                .profitLossPercent(BigDecimal.ZERO)
                .dayChange(BigDecimal.ZERO)
                .dayChangePercent(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // ==================== Portfolio Treemap ====================

    /** 포트폴리오 트리맵 데이터 조회 기간별 성과를 계산하여 트리맵 시각화용 데이터 반환 */
    @Cacheable(value = "treemap", key = "#period")
    public PortfolioTreemapDto getPortfolioTreemap(String period) {
        log.info("Getting portfolio treemap for period: {}", period);

        if (!VALID_PERIODS.contains(period)) {
            throw new IllegalArgumentException(
                    "Invalid period: " + period + ". Valid periods: " + VALID_PERIODS);
        }

        // FETCH JOIN으로 Stock과 Account를 함께 로딩하여 N+1 쿼리 방지
        List<Portfolio> portfolios = portfolioRepository.findAllWithStockAndAccount();

        // 빈 포지션 필터링
        List<Portfolio> activePortfolios =
                portfolios.stream()
                        .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .collect(java.util.stream.Collectors.toList());

        if (activePortfolios.isEmpty()) {
            return PortfolioTreemapDto.builder()
                    .cells(new ArrayList<>())
                    .period(period)
                    .lastUpdated(LocalDateTime.now())
                    .totalInvestment(BigDecimal.ZERO)
                    .totalPerformance(BigDecimal.ZERO)
                    .build();
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(period, endDate);

        // 병렬로 가격 데이터 조회 (성능 최적화)
        Map<String, BigDecimal[]> priceMap = fetchPricesInParallel(activePortfolios);

        List<PortfolioTreemapDto.TreemapCell> cells = new ArrayList<>();
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal weightedPerformanceSum = BigDecimal.ZERO;

        for (Portfolio portfolio : activePortfolios) {
            String symbol = portfolio.getStock().getSymbol();
            BigDecimal performance = calculatePeriodPerformance(symbol, startDate, endDate);
            boolean hasData = performance != null;

            BigDecimal[] prices =
                    priceMap.getOrDefault(
                            symbol, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal currentPrice = prices[0];
            BigDecimal previousClose = prices[1];
            BigDecimal priceChange = currentPrice.subtract(previousClose);

            String sector =
                    portfolio.getStock().getSector() != null
                            ? portfolio.getStock().getSector().name()
                            : "UNKNOWN";

            PortfolioTreemapDto.TreemapCell cell =
                    PortfolioTreemapDto.TreemapCell.builder()
                            .symbol(symbol)
                            .name(portfolio.getStock().getName())
                            .investmentAmount(portfolio.getTotalInvestment())
                            .performancePercent(hasData ? performance : BigDecimal.ZERO)
                            .currentPrice(currentPrice)
                            .priceChange(priceChange)
                            .sector(sector)
                            .hasData(hasData)
                            .build();

            cells.add(cell);

            totalInvestment = totalInvestment.add(portfolio.getTotalInvestment());
            if (hasData && portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0) {
                weightedPerformanceSum =
                        weightedPerformanceSum.add(
                                performance.multiply(portfolio.getTotalInvestment()));
            }
        }

        // Calculate weighted average performance
        BigDecimal totalPerformance = BigDecimal.ZERO;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalPerformance =
                    weightedPerformanceSum.divide(totalInvestment, 4, RoundingMode.HALF_UP);
        }

        return PortfolioTreemapDto.builder()
                .cells(cells)
                .period(period)
                .lastUpdated(LocalDateTime.now())
                .totalInvestment(totalInvestment)
                .totalPerformance(totalPerformance)
                .build();
    }

    /** 기간 문자열에서 시작일 계산 */
    private LocalDate calculateStartDate(String period, LocalDate endDate) {
        return switch (period) {
            case "1D" -> endDate.minusDays(1);
            case "1W" -> endDate.minusWeeks(1);
            case "1M" -> endDate.minusMonths(1);
            case "MTD" -> YearMonth.from(endDate).atDay(1);
            case "3M" -> endDate.minusMonths(3);
            case "6M" -> endDate.minusMonths(6);
            case "1Y" -> endDate.minusYears(1);
            default -> endDate.minusDays(1);
        };
    }

    /** 특정 기간의 수익률 계산 시작일과 종료일의 종가를 비교하여 백분율 수익률 반환 */
    private BigDecimal calculatePeriodPerformance(
            String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            List<HistoricalPrice> prices =
                    historicalPriceRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                            symbol, startDate, endDate);

            if (prices.isEmpty()) {
                log.debug(
                        "No historical price data for {} between {} and {}",
                        symbol,
                        startDate,
                        endDate);
                return null;
            }

            // Get start price (first available price in range or closest before startDate)
            BigDecimal startPrice = prices.get(0).getClosePrice();

            // Try to get price closer to startDate if first price is much later
            if (prices.get(0).getPriceDate().isAfter(startDate.plusDays(3))) {
                // If first available price is more than 3 days after startDate, try to find earlier
                // price
                historicalPriceRepository
                        .findFirstBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(
                                symbol, startDate)
                        .ifPresent(
                                p ->
                                        log.debug(
                                                "Found earlier price for {} on {}",
                                                symbol,
                                                p.getPriceDate()));
            }

            // Get end price (last available price in range)
            BigDecimal endPrice = prices.get(prices.size() - 1).getClosePrice();

            if (startPrice == null
                    || endPrice == null
                    || startPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            // Calculate percentage return: ((endPrice - startPrice) / startPrice) * 100
            return endPrice.subtract(startPrice)
                    .divide(startPrice, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.warn("Failed to calculate period performance for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
}
