package com.trading.journal.performance;

import com.trading.journal.entity.*;
import com.trading.journal.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test to measure the impact of database index optimizations
 */
@DataJpaTest
@ActiveProfiles("test")
public class DatabaseIndexPerformanceTest {

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private DividendRepository dividendRepository;
    
    @Autowired
    private DisclosureRepository disclosureRepository;
    
    @Autowired
    private StockRepository stockRepository;

    @Test
    public void testTransactionQueryPerformance() {
        // Setup test data
        Stock stock = createTestStock("AAPL", "Apple Inc.");
        stockRepository.save(stock);
        
        // Create 1000 test transactions
        for (int i = 0; i < 1000; i++) {
            Transaction transaction = Transaction.builder()
                .stock(stock)
                .type(i % 2 == 0 ? TransactionType.BUY : TransactionType.SELL)
                .quantity(BigDecimal.valueOf(10))
                .price(BigDecimal.valueOf(150 + i))
                .transactionDate(LocalDateTime.now().minusDays(i))
                .build();
            transactionRepository.save(transaction);
        }
        
        // Test query performance
        long startTime = System.nanoTime();
        List<Transaction> transactions = transactionRepository.findByStockIdOrderByTransactionDateDesc(stock.getId());
        long endTime = System.nanoTime();
        
        long executionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        assertFalse(transactions.isEmpty());
        assertTrue(executionTime < 100, "Query should complete in under 100ms with indexes");
        System.out.println("Transaction query execution time: " + executionTime + "ms");
    }
    
    @Test
    public void testDividendDateRangeQueryPerformance() {
        Stock stock = createTestStock("MSFT", "Microsoft Corp.");
        stockRepository.save(stock);
        
        // Create 500 test dividends
        for (int i = 0; i < 500; i++) {
            Dividend dividend = Dividend.builder()
                .stock(stock)
                .exDividendDate(LocalDate.now().minusDays(i * 30))
                .paymentDate(LocalDate.now().minusDays(i * 30 - 7))
                .dividendPerShare(BigDecimal.valueOf(2.5))
                .quantity(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(250))
                .netAmount(BigDecimal.valueOf(212.5))
                .build();
            dividendRepository.save(dividend);
        }
        
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate endDate = LocalDate.now();
        
        long startTime = System.nanoTime();
        List<Dividend> dividends = dividendRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(startDate, endDate);
        long endTime = System.nanoTime();
        
        long executionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        assertFalse(dividends.isEmpty());
        assertTrue(executionTime < 50, "Dividend date range query should complete in under 50ms with indexes");
        System.out.println("Dividend date range query execution time: " + executionTime + "ms");
    }
    
    @Test
    public void testDisclosureComplexQueryPerformance() {
        Stock stock = createTestStock("GOOGL", "Alphabet Inc.");
        stockRepository.save(stock);
        
        // Create portfolio entry
        Portfolio portfolio = Portfolio.builder()
            .stock(stock)
            .quantity(BigDecimal.valueOf(50))
            .averagePrice(BigDecimal.valueOf(2500))
            .totalInvestment(BigDecimal.valueOf(125000))
            .build();
        portfolioRepository.save(portfolio);
        
        // Create 300 test disclosures
        for (int i = 0; i < 300; i++) {
            Disclosure disclosure = Disclosure.builder()
                .stock(stock)
                .reportNumber("REP" + String.format("%06d", i))
                .corpCode("CORP123")
                .corpName("Test Corporation")
                .reportName("Test Report " + i)
                .receivedDate(LocalDateTime.now().minusDays(i))
                .submitter("Test Submitter")
                .isImportant(i % 10 == 0)  // 10% important
                .isRead(i % 3 == 0)        // 33% read
                .build();
            disclosureRepository.save(disclosure);
        }
        
        // Test complex query performance
        long startTime = System.nanoTime();
        List<Disclosure> importantDisclosures = disclosureRepository.findImportantDisclosuresForPortfolio();
        long endTime = System.nanoTime();
        
        long executionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        assertFalse(importantDisclosures.isEmpty());
        assertTrue(executionTime < 75, "Complex disclosure query should complete in under 75ms with indexes");
        System.out.println("Complex disclosure query execution time: " + executionTime + "ms");
    }
    
    @Test
    public void testStockSearchPerformance() {
        // Create 1000 test stocks
        for (int i = 0; i < 1000; i++) {
            Stock stock = createTestStock("STOCK" + i, "Company " + i + " Inc.");
            stockRepository.save(stock);
        }
        
        long startTime = System.nanoTime();
        var result = stockRepository.findBySymbol("STOCK500");
        long endTime = System.nanoTime();
        
        long executionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        assertTrue(result.isPresent());
        assertTrue(executionTime < 25, "Stock symbol lookup should complete in under 25ms with indexes");
        System.out.println("Stock symbol lookup execution time: " + executionTime + "ms");
    }
    
    private Stock createTestStock(String symbol, String name) {
        return Stock.builder()
            .symbol(symbol)
            .name(name)
            .exchange("NASDAQ")
            .build();
    }
}