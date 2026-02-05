package com.trading.journal.provider;

import com.trading.journal.entity.BenchmarkPrice;
import com.trading.journal.entity.BenchmarkType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

@Component
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceBenchmarkProvider implements BenchmarkDataProvider {

    private static final Map<BenchmarkType, String> SYMBOL_MAP =
            Map.of(
                    BenchmarkType.SP500, "^GSPC",
                    BenchmarkType.NASDAQ, "^IXIC",
                    BenchmarkType.DOW, "^DJI",
                    BenchmarkType.KOSPI, "^KS11",
                    BenchmarkType.KOSDAQ, "^KQ11");

    @Override
    @CircuitBreaker(name = "yahooFinance", fallbackMethod = "fallbackFetchPrices")
    public List<BenchmarkPrice> fetchPrices(
            BenchmarkType benchmark, LocalDate startDate, LocalDate endDate) {
        String symbol = SYMBOL_MAP.get(benchmark);
        if (symbol == null) {
            log.warn("No Yahoo Finance symbol mapping for {}", benchmark);
            return Collections.emptyList();
        }

        try {
            Calendar from = toCalendar(startDate);
            Calendar to = toCalendar(endDate);

            Stock stock = YahooFinance.get(symbol, from, to, Interval.DAILY);
            if (stock == null || stock.getHistory() == null) {
                log.warn("No data returned from Yahoo Finance for {}", symbol);
                return Collections.emptyList();
            }

            List<BenchmarkPrice> prices = new ArrayList<>();
            List<HistoricalQuote> history = stock.getHistory();

            BigDecimal previousClose = null;
            for (HistoricalQuote quote : history) {
                if (quote.getClose() == null) continue;

                LocalDate priceDate =
                        quote.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                BigDecimal dailyReturn = BigDecimal.ZERO;
                if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
                    dailyReturn =
                            quote.getClose()
                                    .subtract(previousClose)
                                    .divide(previousClose, 6, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                }

                prices.add(
                        BenchmarkPrice.builder()
                                .benchmark(benchmark)
                                .priceDate(priceDate)
                                .openPrice(quote.getOpen())
                                .highPrice(quote.getHigh())
                                .lowPrice(quote.getLow())
                                .closePrice(quote.getClose())
                                .volume(quote.getVolume())
                                .dailyReturn(dailyReturn.setScale(4, RoundingMode.HALF_UP))
                                .build());

                previousClose = quote.getClose();
            }

            log.info("Fetched {} prices for {} from Yahoo Finance", prices.size(), benchmark);
            return prices;

        } catch (IOException e) {
            log.error(
                    "Error fetching data from Yahoo Finance for {}: {}", benchmark, e.getMessage());
            throw new RuntimeException("Failed to fetch benchmark data", e);
        }
    }

    public List<BenchmarkPrice> fallbackFetchPrices(
            BenchmarkType benchmark, LocalDate startDate, LocalDate endDate, Throwable t) {
        log.warn("Circuit breaker fallback for Yahoo Finance: {}", t.getMessage());
        return Collections.emptyList();
    }

    @Override
    public boolean supports(BenchmarkType benchmark) {
        return SYMBOL_MAP.containsKey(benchmark);
    }

    @Override
    public String getProviderName() {
        return "Yahoo Finance";
    }

    private Calendar toCalendar(LocalDate date) {
        Calendar cal = Calendar.getInstance();
        cal.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return cal;
    }
}
