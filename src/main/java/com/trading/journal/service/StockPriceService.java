package com.trading.journal.service;

import com.trading.journal.entity.HistoricalPrice;
import com.trading.journal.repository.HistoricalPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockPriceService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second
    private static final int MIN_COVERAGE_PERCENT = 80; // 최소 데이터 커버리지 비율

    private final HistoricalPriceRepository historicalPriceRepository;

    @Cacheable(value = "stockPrice", key = "#symbol")
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol);
            return stock.getQuote().getPrice();
        } catch (IOException e) {
            log.error("Failed to fetch current price for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch stock price", e);
        }
    }

    @Cacheable(value = "stockInfo", key = "#symbol")
    public Stock getStockInfo(String symbol) {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("Fetching stock info for symbol: {} (attempt {}/{})", symbol, attempt, MAX_RETRIES);
                Stock stock = YahooFinance.get(symbol);

                // Yahoo Finance API에서 null을 반환하는 경우도 있음
                if (stock == null || stock.getName() == null) {
                    log.warn("Stock info not found for symbol: {}", symbol);
                    // 기본 Stock 객체 생성 - Yahoo Finance API가 실패해도 작동하도록
                    return createDefaultStock(symbol);
                }

                return stock;
            } catch (IOException e) {
                lastException = e;

                // 429 오류인 경우 더 긴 대기
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    log.warn("Rate limit hit for symbol: {}. Waiting before retry...", symbol);
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Failed to fetch stock info for symbol: {} (attempt {}/{})",
                            symbol, attempt, MAX_RETRIES, e);
                }

                if (attempt < MAX_RETRIES) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to fetch stock info after {} attempts for symbol: {}", MAX_RETRIES, symbol);
        // 모든 시도가 실패해도 기본 Stock 객체 반환
        return createDefaultStock(symbol);
    }

    private Stock createDefaultStock(String symbol) {
        Stock stock = new Stock(symbol);
        // 한국 주식의 경우 기본 Exchange 설정
        if (symbol.endsWith(".KS") || symbol.endsWith(".KQ")) {
            stock.setStockExchange("KRX");
        }
        stock.setName(symbol); // 이름은 심볼로 설정
        return stock;
    }

    /**
     * 과거 가격 데이터 조회 (로컬 DB 우선, API 폴백)
     * 1. 먼저 로컬 DB에서 데이터를 조회
     * 2. 데이터가 충분하면 DB 데이터 반환
     * 3. 데이터가 부족하면 Yahoo Finance API 호출 후 DB 저장
     */
    @Cacheable(value = "historicalQuotes", key = "#symbol + '_' + #from + '_' + #to")
    public List<HistoricalQuote> getHistoricalQuotes(String symbol, LocalDate from, LocalDate to) {
        log.debug("Fetching historical quotes for {} from {} to {}", symbol, from, to);

        // 1. 로컬 DB에서 데이터 조회
        List<HistoricalPrice> cachedPrices = historicalPriceRepository
                .findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(symbol, from, to);

        // 2. 데이터 커버리지 확인
        long expectedDays = calculateTradingDays(from, to);
        long actualDays = cachedPrices.size();
        double coverage = expectedDays > 0 ? (double) actualDays / expectedDays * 100 : 0;

        log.debug("Cache coverage for {}: {}/{} days ({}%)", symbol, actualDays, expectedDays,
                String.format("%.1f", coverage));

        // 3. 충분한 데이터가 있으면 DB 데이터 반환
        if (coverage >= MIN_COVERAGE_PERCENT && actualDays > 0) {
            log.info("Using cached data for {} ({} records)", symbol, actualDays);
            return convertToHistoricalQuotes(cachedPrices);
        }

        // 4. API에서 데이터 조회
        log.info("Fetching from Yahoo Finance API for {} (coverage {}%)", symbol, String.format("%.1f", coverage));
        List<HistoricalQuote> quotes = fetchFromYahooFinance(symbol, from, to);

        // 5. DB에 저장 (비동기로 처리해도 됨)
        if (!quotes.isEmpty()) {
            saveToDatabase(symbol, quotes);
        }

        return quotes;
    }

    /**
     * 예상 거래일 수 계산 (주말 제외, 대략적인 추정)
     */
    private long calculateTradingDays(LocalDate from, LocalDate to) {
        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        // 대략 주 5일 거래로 추정 (공휴일 미고려)
        return (long) (totalDays * 5.0 / 7.0);
    }

    /**
     * Yahoo Finance API에서 데이터 조회
     */
    private List<HistoricalQuote> fetchFromYahooFinance(String symbol, LocalDate from, LocalDate to) {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Calendar fromCal = Calendar.getInstance();
                fromCal.setTime(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));

                Calendar toCal = Calendar.getInstance();
                toCal.setTime(Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));

                Stock stock = YahooFinance.get(symbol);
                List<HistoricalQuote> quotes = stock.getHistory(fromCal, toCal, Interval.DAILY);

                log.info("Successfully fetched {} quotes from Yahoo Finance for {}",
                        quotes != null ? quotes.size() : 0, symbol);
                return quotes != null ? quotes : Collections.emptyList();

            } catch (IOException e) {
                lastException = e;
                log.warn("Yahoo Finance API call failed for {} (attempt {}/{}): {}",
                        symbol, attempt, MAX_RETRIES, e.getMessage());

                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt * 2);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (attempt < MAX_RETRIES) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to fetch historical quotes after {} attempts for symbol: {}", MAX_RETRIES, symbol);
        throw new RuntimeException("Failed to fetch historical quotes from Yahoo Finance", lastException);
    }

    /**
     * 가격 데이터를 로컬 DB에 저장
     */
    @Transactional
    public void saveToDatabase(String symbol, List<HistoricalQuote> quotes) {
        log.debug("Saving {} quotes to database for {}", quotes.size(), symbol);

        List<HistoricalPrice> pricesToSave = new ArrayList<>();

        for (HistoricalQuote quote : quotes) {
            if (quote.getClose() == null) continue;

            LocalDate priceDate = quote.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            // 이미 존재하는 데이터는 건너뛰기
            if (historicalPriceRepository.findBySymbolAndPriceDate(symbol, priceDate).isPresent()) {
                continue;
            }

            HistoricalPrice price = HistoricalPrice.builder()
                    .symbol(symbol)
                    .priceDate(priceDate)
                    .openPrice(quote.getOpen())
                    .highPrice(quote.getHigh())
                    .lowPrice(quote.getLow())
                    .closePrice(quote.getClose())
                    .adjClose(quote.getAdjClose())
                    .volume(quote.getVolume())
                    .build();

            pricesToSave.add(price);
        }

        if (!pricesToSave.isEmpty()) {
            historicalPriceRepository.saveAll(pricesToSave);
            log.info("Saved {} new price records for {}", pricesToSave.size(), symbol);
        }
    }

    /**
     * 로컬 DB 데이터를 HistoricalQuote 객체로 변환
     */
    private List<HistoricalQuote> convertToHistoricalQuotes(List<HistoricalPrice> cachedPrices) {
        return cachedPrices.stream()
                .map(this::convertToHistoricalQuote)
                .collect(Collectors.toList());
    }

    private HistoricalQuote convertToHistoricalQuote(HistoricalPrice price) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.from(price.getPriceDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));

        // HistoricalQuote(String symbol, Calendar date, BigDecimal open, BigDecimal low,
        //                 BigDecimal high, BigDecimal close, BigDecimal adjClose, Long volume)
        return new HistoricalQuote(
                price.getSymbol(),
                cal,
                price.getOpenPrice(),
                price.getLowPrice(),
                price.getHighPrice(),
                price.getClosePrice(),
                price.getAdjClose(),
                price.getVolume()
        );
    }

    @Cacheable(value = "stockPrice", key = "#symbol + '_prev'")
    public BigDecimal getPreviousClose(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol);
            return stock.getQuote().getPreviousClose();
        } catch (IOException e) {
            log.error("Failed to fetch previous close for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch previous close", e);
        }
    }

    /**
     * 캐시 상태 조회 (디버깅용)
     */
    public Map<String, Object> getCacheStatus(String symbol) {
        Map<String, Object> status = new HashMap<>();

        long count = historicalPriceRepository.countBySymbol(symbol);
        Optional<LocalDate> oldest = historicalPriceRepository.findOldestPriceDateBySymbol(symbol);
        Optional<LocalDate> latest = historicalPriceRepository.findLatestPriceDateBySymbol(symbol);

        status.put("symbol", symbol);
        status.put("cachedRecords", count);
        status.put("oldestDate", oldest.orElse(null));
        status.put("latestDate", latest.orElse(null));

        return status;
    }

    /**
     * 특정 심볼의 캐시 강제 갱신
     */
    @Transactional
    public void refreshCache(String symbol, LocalDate from, LocalDate to) {
        log.info("Refreshing cache for {} from {} to {}", symbol, from, to);

        // 기존 데이터 삭제
        historicalPriceRepository.deleteBySymbolAndPriceDateBetween(symbol, from, to);

        // 새로 조회 및 저장
        List<HistoricalQuote> quotes = fetchFromYahooFinance(symbol, from, to);
        if (!quotes.isEmpty()) {
            saveToDatabase(symbol, quotes);
        }
    }
}
