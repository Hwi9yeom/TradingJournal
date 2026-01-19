package com.trading.journal.service;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.dto.PortfolioTreemapDto;
import com.trading.journal.entity.HistoricalPrice;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.HistoricalPriceRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioAnalysisService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final StockPriceService stockPriceService;
    private final HistoricalPriceRepository historicalPriceRepository;

    private static final Set<String> VALID_PERIODS = Set.of("1D", "1W", "1M", "MTD", "3M", "6M", "1Y");
    
    @Cacheable(value = "portfolio", key = "'summary'")
    public PortfolioSummaryDto getPortfolioSummary() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<PortfolioDto> holdings = new ArrayList<>();
        
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalDayChange = BigDecimal.ZERO;
        
        for (Portfolio portfolio : portfolios) {
            PortfolioDto dto = calculatePortfolioMetrics(portfolio);
            holdings.add(dto);
            
            totalInvestment = totalInvestment.add(portfolio.getTotalInvestment());
            totalCurrentValue = totalCurrentValue.add(dto.getCurrentValue());
            totalDayChange = totalDayChange.add(dto.getDayChange());
        }
        
        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment);
        BigDecimal totalProfitLossPercent = BigDecimal.ZERO;
        BigDecimal totalDayChangePercent = BigDecimal.ZERO;
        
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitLossPercent = totalProfitLoss
                    .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        if (totalCurrentValue.subtract(totalDayChange).compareTo(BigDecimal.ZERO) > 0) {
            totalDayChangePercent = totalDayChange
                    .divide(totalCurrentValue.subtract(totalDayChange), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // 실현 손익 계산 (모든 매도 거래의 FIFO 기반 실현손익 합계)
        BigDecimal totalRealizedPnl = transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(t -> t.getRealizedPnl() != null ? t.getRealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
    
    @Cacheable(value = "portfolio", key = "#symbol")
    public PortfolioDto getPortfolioBySymbol(String symbol) {
        Portfolio portfolio = portfolioRepository.findByStockSymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Portfolio not found for symbol: " + symbol));
        return calculatePortfolioMetrics(portfolio);
    }
    
    private PortfolioDto calculatePortfolioMetrics(Portfolio portfolio) {
        try {
            BigDecimal currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            BigDecimal previousClose = stockPriceService.getPreviousClose(portfolio.getStock().getSymbol());
            
            BigDecimal currentValue = currentPrice.multiply(portfolio.getQuantity());
            BigDecimal profitLoss = currentValue.subtract(portfolio.getTotalInvestment());
            BigDecimal profitLossPercent = BigDecimal.ZERO;
            
            if (portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .divide(portfolio.getTotalInvestment(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            BigDecimal dayChange = currentPrice.subtract(previousClose).multiply(portfolio.getQuantity());
            BigDecimal dayChangePercent = BigDecimal.ZERO;
            
            if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
                dayChangePercent = currentPrice.subtract(previousClose)
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
                    
        } catch (Exception e) {
            log.error("Failed to calculate portfolio metrics for symbol: {}", 
                    portfolio.getStock().getSymbol(), e);
            
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
    }

    // ==================== Portfolio Treemap ====================

    /**
     * 포트폴리오 트리맵 데이터 조회
     * 기간별 성과를 계산하여 트리맵 시각화용 데이터 반환
     */
    @Cacheable(value = "treemap", key = "#period")
    public PortfolioTreemapDto getPortfolioTreemap(String period) {
        log.info("Getting portfolio treemap for period: {}", period);

        if (!VALID_PERIODS.contains(period)) {
            throw new IllegalArgumentException("Invalid period: " + period + ". Valid periods: " + VALID_PERIODS);
        }

        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<PortfolioTreemapDto.TreemapCell> cells = new ArrayList<>();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(period, endDate);

        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal weightedPerformanceSum = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip empty positions
            }

            String symbol = portfolio.getStock().getSymbol();
            BigDecimal performance = calculatePeriodPerformance(symbol, startDate, endDate);
            boolean hasData = performance != null;

            BigDecimal currentPrice = BigDecimal.ZERO;
            BigDecimal priceChange = BigDecimal.ZERO;

            try {
                currentPrice = stockPriceService.getCurrentPrice(symbol);
                BigDecimal previousClose = stockPriceService.getPreviousClose(symbol);
                priceChange = currentPrice.subtract(previousClose);
            } catch (Exception e) {
                log.warn("Failed to get current price for {}: {}", symbol, e.getMessage());
            }

            String sector = portfolio.getStock().getSector() != null
                    ? portfolio.getStock().getSector().name()
                    : "UNKNOWN";

            PortfolioTreemapDto.TreemapCell cell = PortfolioTreemapDto.TreemapCell.builder()
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
                weightedPerformanceSum = weightedPerformanceSum.add(
                        performance.multiply(portfolio.getTotalInvestment()));
            }
        }

        // Calculate weighted average performance
        BigDecimal totalPerformance = BigDecimal.ZERO;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalPerformance = weightedPerformanceSum.divide(totalInvestment, 4, RoundingMode.HALF_UP);
        }

        return PortfolioTreemapDto.builder()
                .cells(cells)
                .period(period)
                .lastUpdated(LocalDateTime.now())
                .totalInvestment(totalInvestment)
                .totalPerformance(totalPerformance)
                .build();
    }

    /**
     * 기간 문자열에서 시작일 계산
     */
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

    /**
     * 특정 기간의 수익률 계산
     * 시작일과 종료일의 종가를 비교하여 백분율 수익률 반환
     */
    private BigDecimal calculatePeriodPerformance(String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            List<HistoricalPrice> prices = historicalPriceRepository
                    .findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(symbol, startDate, endDate);

            if (prices.isEmpty()) {
                log.debug("No historical price data for {} between {} and {}", symbol, startDate, endDate);
                return null;
            }

            // Get start price (first available price in range or closest before startDate)
            BigDecimal startPrice = prices.get(0).getClosePrice();

            // Try to get price closer to startDate if first price is much later
            if (prices.get(0).getPriceDate().isAfter(startDate.plusDays(3))) {
                // If first available price is more than 3 days after startDate, try to find earlier price
                historicalPriceRepository.findFirstBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, startDate)
                        .ifPresent(p -> log.debug("Found earlier price for {} on {}", symbol, p.getPriceDate()));
            }

            // Get end price (last available price in range)
            BigDecimal endPrice = prices.get(prices.size() - 1).getClosePrice();

            if (startPrice == null || endPrice == null || startPrice.compareTo(BigDecimal.ZERO) <= 0) {
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