package com.trading.journal.provider;

import com.trading.journal.entity.BenchmarkPrice;
import com.trading.journal.entity.BenchmarkType;
import java.time.LocalDate;
import java.util.List;

/**
 * Provider interface for fetching benchmark price data from external sources. Implementations
 * should handle rate limiting and error recovery.
 */
public interface BenchmarkDataProvider {

    /**
     * Fetch historical prices for a benchmark.
     *
     * @param benchmark the benchmark type
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of benchmark prices ordered by date ascending
     */
    List<BenchmarkPrice> fetchPrices(
            BenchmarkType benchmark, LocalDate startDate, LocalDate endDate);

    /**
     * Check if this provider supports the given benchmark type.
     *
     * @param benchmark the benchmark type to check
     * @return true if this provider can fetch data for this benchmark
     */
    boolean supports(BenchmarkType benchmark);

    /**
     * Get the provider name for logging purposes.
     *
     * @return provider name
     */
    String getProviderName();
}
