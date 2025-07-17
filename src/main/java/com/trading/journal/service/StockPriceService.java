package com.trading.journal.service;

import lombok.extern.slf4j.Slf4j;
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

@Service
@Slf4j
public class StockPriceService {
    
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol);
            return stock.getQuote().getPrice();
        } catch (IOException e) {
            log.error("Failed to fetch current price for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch stock price", e);
        }
    }
    
    public Stock getStockInfo(String symbol) {
        try {
            return YahooFinance.get(symbol);
        } catch (IOException e) {
            log.error("Failed to fetch stock info for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch stock info", e);
        }
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