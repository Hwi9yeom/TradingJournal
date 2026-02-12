package com.trading.journal.service;

import com.trading.journal.entity.StockFundamentals;
import com.trading.journal.repository.StockFundamentalsRepository;
import com.trading.journal.repository.StockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing fundamental data of stocks.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Retrieving fundamental data by symbol
 *   <li>Updating fundamentals from external sources
 *   <li>Batch updating fundamentals for multiple stocks
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FundamentalDataService {

    private final StockFundamentalsRepository fundamentalsRepository;
    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;

    /**
     * Get fundamental data by symbol.
     *
     * @param symbol Stock symbol
     * @return Optional containing fundamental data if found
     */
    public Optional<StockFundamentals> getFundamentalsBySymbol(String symbol) {
        log.debug("Retrieving fundamentals for symbol: {}", symbol);
        return fundamentalsRepository.findBySymbol(symbol);
    }

    /**
     * Get fundamental data by ID.
     *
     * @param id Fundamental data ID
     * @return Optional containing fundamental data if found
     */
    public Optional<StockFundamentals> getFundamentalsById(Long id) {
        log.debug("Retrieving fundamentals for ID: {}", id);
        return fundamentalsRepository.findById(id);
    }

    /**
     * Get fundamental data for multiple symbols.
     *
     * @param symbols List of stock symbols
     * @return List of fundamental data
     */
    public List<StockFundamentals> getFundamentalsBySymbols(List<String> symbols) {
        log.debug("Retrieving fundamentals for {} symbols", symbols.size());
        return fundamentalsRepository.findBySymbolIn(symbols);
    }

    /**
     * Update fundamental data for a stock.
     *
     * @param symbol Stock symbol
     * @param fundamentals Fundamental data to update
     * @return Updated fundamental data
     */
    @Transactional
    public StockFundamentals updateFundamentals(String symbol, StockFundamentals fundamentals) {
        log.info("Updating fundamentals for symbol: {}", symbol);

        Optional<StockFundamentals> existing = fundamentalsRepository.findBySymbol(symbol);

        StockFundamentals toSave;
        if (existing.isPresent()) {
            toSave = existing.get();
            copyFundamentalData(fundamentals, toSave);
        } else {
            toSave = fundamentals;
            toSave.setSymbol(symbol);
        }

        toSave.setLastUpdated(LocalDate.now());
        return fundamentalsRepository.save(toSave);
    }

    /**
     * Batch update fundamentals from external source.
     *
     * <p>This method would typically integrate with external APIs (e.g., Yahoo Finance, Alpha
     * Vantage) to fetch and update fundamental data for multiple stocks.
     *
     * @param symbols List of symbols to update
     * @return Number of successfully updated stocks
     */
    @Transactional
    public int batchUpdateFundamentals(List<String> symbols) {
        log.info("Batch updating fundamentals for {} symbols", symbols.size());

        int successCount = 0;
        for (String symbol : symbols) {
            try {
                Optional<StockFundamentals> existing = fundamentalsRepository.findBySymbol(symbol);
                StockFundamentals fundamentals = existing.orElseGet(StockFundamentals::new);

                fundamentals.setSymbol(symbol);
                stockRepository
                        .findBySymbol(symbol)
                        .ifPresent(
                                stock -> {
                                    fundamentals.setCompanyName(stock.getName());
                                    fundamentals.setSector(stock.getSector());
                                    fundamentals.setIndustry(stock.getIndustry());
                                });

                try {
                    BigDecimal currentPrice = stockPriceService.getCurrentPrice(symbol);
                    if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                        fundamentals.setCurrentPrice(currentPrice);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch current price for symbol: {}", symbol, e);
                }

                fundamentals.setLastUpdated(LocalDate.now());
                fundamentalsRepository.save(fundamentals);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to update fundamentals for symbol: {}", symbol, e);
            }
        }

        log.info("Successfully updated {} out of {} symbols", successCount, symbols.size());
        return successCount;
    }

    /**
     * Create or update fundamental data.
     *
     * @param fundamentals Fundamental data
     * @return Saved fundamental data
     */
    @Transactional
    public StockFundamentals saveFundamentals(StockFundamentals fundamentals) {
        log.info("Saving fundamentals for symbol: {}", fundamentals.getSymbol());

        fundamentals.setLastUpdated(LocalDate.now());
        return fundamentalsRepository.save(fundamentals);
    }

    /**
     * Delete fundamental data by symbol.
     *
     * @param symbol Stock symbol
     */
    @Transactional
    public void deleteFundamentals(String symbol) {
        log.info("Deleting fundamentals for symbol: {}", symbol);

        Optional<StockFundamentals> existing = fundamentalsRepository.findBySymbol(symbol);
        existing.ifPresent(fundamentalsRepository::delete);
    }

    /**
     * Check if fundamental data exists for a symbol.
     *
     * @param symbol Stock symbol
     * @return true if exists, false otherwise
     */
    public boolean existsBySymbol(String symbol) {
        return fundamentalsRepository.existsBySymbol(symbol);
    }

    /**
     * Get all fundamental data.
     *
     * @return List of all fundamental data
     */
    public List<StockFundamentals> getAllFundamentals() {
        log.debug("Retrieving all fundamental data");
        return fundamentalsRepository.findAll();
    }

    /**
     * Copy fundamental data from source to target.
     *
     * @param source Source fundamental data
     * @param target Target fundamental data to update
     */
    private void copyFundamentalData(StockFundamentals source, StockFundamentals target) {
        if (source.getCompanyName() != null) {
            target.setCompanyName(source.getCompanyName());
        }
        if (source.getSector() != null) {
            target.setSector(source.getSector());
        }
        if (source.getIndustry() != null) {
            target.setIndustry(source.getIndustry());
        }

        // Valuation Metrics
        copyIfNotNull(source.getMarketCap(), target::setMarketCap);
        copyIfNotNull(source.getPeRatio(), target::setPeRatio);
        copyIfNotNull(source.getPbRatio(), target::setPbRatio);
        copyIfNotNull(source.getPsRatio(), target::setPsRatio);
        copyIfNotNull(source.getPegRatio(), target::setPegRatio);
        copyIfNotNull(source.getEvToEbitda(), target::setEvToEbitda);

        // Profitability Metrics
        copyIfNotNull(source.getReturnOnEquity(), target::setReturnOnEquity);
        copyIfNotNull(source.getReturnOnAssets(), target::setReturnOnAssets);
        copyIfNotNull(source.getProfitMargin(), target::setProfitMargin);
        copyIfNotNull(source.getOperatingMargin(), target::setOperatingMargin);
        copyIfNotNull(source.getGrossMargin(), target::setGrossMargin);

        // Dividend Metrics
        copyIfNotNull(source.getDividendYield(), target::setDividendYield);
        copyIfNotNull(source.getPayoutRatio(), target::setPayoutRatio);
        copyIfNotNull(source.getDividendPerShare(), target::setDividendPerShare);

        // Growth Metrics
        copyIfNotNull(source.getRevenueGrowth(), target::setRevenueGrowth);
        copyIfNotNull(source.getEpsGrowth(), target::setEpsGrowth);
        copyIfNotNull(source.getBookValueGrowth(), target::setBookValueGrowth);

        // Financial Health
        copyIfNotNull(source.getDebtToEquity(), target::setDebtToEquity);
        copyIfNotNull(source.getCurrentRatio(), target::setCurrentRatio);
        copyIfNotNull(source.getQuickRatio(), target::setQuickRatio);

        // Per Share Metrics
        copyIfNotNull(source.getEarningsPerShare(), target::setEarningsPerShare);
        copyIfNotNull(source.getBookValuePerShare(), target::setBookValuePerShare);
        copyIfNotNull(source.getRevenuePerShare(), target::setRevenuePerShare);

        // Market Metrics
        copyIfNotNull(source.getCurrentPrice(), target::setCurrentPrice);
        copyIfNotNull(source.getFiftyTwoWeekHigh(), target::setFiftyTwoWeekHigh);
        copyIfNotNull(source.getFiftyTwoWeekLow(), target::setFiftyTwoWeekLow);
        copyIfNotNull(source.getAverageVolume(), target::setAverageVolume);
        copyIfNotNull(source.getBeta(), target::setBeta);

        // Company Size
        copyIfNotNull(source.getTotalRevenue(), target::setTotalRevenue);
        copyIfNotNull(source.getTotalAssets(), target::setTotalAssets);
        copyIfNotNull(source.getTotalDebt(), target::setTotalDebt);
        copyIfNotNull(source.getTotalCash(), target::setTotalCash);

        if (source.getFiscalYearEnd() != null) {
            target.setFiscalYearEnd(source.getFiscalYearEnd());
        }
    }

    /**
     * Helper method to copy value if not null.
     *
     * @param value Value to copy
     * @param setter Setter function to call if value is not null
     */
    private void copyIfNotNull(BigDecimal value, java.util.function.Consumer<BigDecimal> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
