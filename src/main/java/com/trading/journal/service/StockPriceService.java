package com.trading.journal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StockPriceService {
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second
    
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
    
    public List<HistoricalQuote> getHistoricalQuotes(String symbol, LocalDate from, LocalDate to) {
        try {
            Calendar fromCal = Calendar.getInstance();
            fromCal.setTime(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            
            Calendar toCal = Calendar.getInstance();
            toCal.setTime(Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            
            Stock stock = YahooFinance.get(symbol);
            return stock.getHistory(fromCal, toCal, Interval.DAILY);
        } catch (IOException e) {
            log.error("Failed to fetch historical quotes for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch historical quotes", e);
        }
    }
    
    public BigDecimal getPreviousClose(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol);
            return stock.getQuote().getPreviousClose();
        } catch (IOException e) {
            log.error("Failed to fetch previous close for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch previous close", e);
        }
    }
}