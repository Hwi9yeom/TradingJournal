package com.trading.journal.service;

import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAnalysisServiceTest {
    
    @Mock
    private StockRepository stockRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private PortfolioRepository portfolioRepository;
    
    @Mock
    private StockPriceService stockPriceService;
    
    @InjectMocks
    private StockAnalysisService stockAnalysisService;
    
    private Stock stock;
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("005930");
        stock.setName("삼성전자");
        
        portfolio = new Portfolio();
        portfolio.setStock(stock);
        portfolio.setQuantity(new BigDecimal("50"));
        portfolio.setAveragePrice(new BigDecimal("50000"));
        portfolio.setTotalInvestment(new BigDecimal("2500000"));
    }
    
    @Test
    void analyzeStock_ShouldReturnCompleteAnalysis() {
        String symbol = "005930";
        LocalDateTime baseDate = LocalDateTime.now().minusDays(30);
        
        when(stockRepository.findBySymbol(symbol)).thenReturn(Optional.of(stock));
        when(transactionRepository.findByStockSymbol(symbol)).thenReturn(Arrays.asList(
            createTransaction(1L, stock, TransactionType.BUY, 
                new BigDecimal("100"), new BigDecimal("50000"), baseDate),
            createTransaction(2L, stock, TransactionType.SELL, 
                new BigDecimal("50"), new BigDecimal("55000"), baseDate.plusDays(10)),
            createTransaction(3L, stock, TransactionType.BUY, 
                new BigDecimal("30"), new BigDecimal("52000"), baseDate.plusDays(15)),
            createTransaction(4L, stock, TransactionType.SELL, 
                new BigDecimal("30"), new BigDecimal("58000"), baseDate.plusDays(20))
        ));
        when(portfolioRepository.findByStockId(stock.getId()))
            .thenReturn(Optional.of(portfolio));
        when(stockPriceService.getCurrentPrice(symbol))
            .thenReturn(new BigDecimal("60000"));
        
        StockAnalysisDto result = stockAnalysisService.analyzeStock(symbol);
        
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo(symbol);
        assertThat(result.getStockName()).isEqualTo("삼성전자");
        assertThat(result.getTotalBuyCount()).isEqualTo(2);
        assertThat(result.getTotalSellCount()).isEqualTo(2);
        assertThat(result.getTotalBuyQuantity())
            .isEqualByComparingTo(new BigDecimal("130"));
        assertThat(result.getTotalSellQuantity())
            .isEqualByComparingTo(new BigDecimal("80"));
        
        // 평균 가격 검증
        assertThat(result.getAverageBuyPrice()).isNotNull();
        assertThat(result.getAverageSellPrice()).isNotNull();
        
        // 실현손익 검증
        assertThat(result.getRealizedProfit()).isNotNull();
        assertThat(result.getRealizedProfitRate()).isNotNull();
        
        // 미실현손익 검증
        assertThat(result.getCurrentHolding())
            .isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.getUnrealizedProfit()).isNotNull();
        
        // 거래 패턴 검증
        assertThat(result.getTradingPatterns()).isNotEmpty();
        assertThat(result.getTradingPatterns()).hasSize(4);
        
        verify(stockRepository).findBySymbol(symbol);
        verify(transactionRepository).findByStockSymbol(symbol);
        verify(portfolioRepository).findByStockId(stock.getId());
        verify(stockPriceService).getCurrentPrice(symbol);
    }
    
    @Test
    void analyzeStock_WithNoTransactions_ShouldReturnEmptyAnalysis() {
        String symbol = "005930";
        
        when(stockRepository.findBySymbol(symbol)).thenReturn(Optional.of(stock));
        when(transactionRepository.findByStockSymbol(symbol))
            .thenReturn(Arrays.asList());
        
        StockAnalysisDto result = stockAnalysisService.analyzeStock(symbol);
        
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo(symbol);
        assertThat(result.getTotalBuyCount()).isEqualTo(0);
        assertThat(result.getTotalSellCount()).isEqualTo(0);
        assertThat(result.getRealizedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getUnrealizedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTradingPatterns()).isEmpty();
    }
    
    @Test
    void analyzeStock_StockNotFound_ShouldThrowException() {
        String symbol = "INVALID";
        
        when(stockRepository.findBySymbol(symbol)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> stockAnalysisService.analyzeStock(symbol))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Stock not found");
    }
    
    @Test
    void analyzeTradingPatterns_ShouldCalculateCorrectPatterns() {
        String symbol = "005930";
        LocalDateTime baseDate = LocalDateTime.now().minusDays(60);
        
        when(stockRepository.findBySymbol(symbol)).thenReturn(Optional.of(stock));
        when(transactionRepository.findByStockSymbol(symbol)).thenReturn(Arrays.asList(
            createTransaction(1L, stock, TransactionType.BUY, 
                new BigDecimal("100"), new BigDecimal("50000"), baseDate),
            createTransaction(2L, stock, TransactionType.SELL, 
                new BigDecimal("100"), new BigDecimal("55000"), baseDate.plusDays(30)),
            createTransaction(3L, stock, TransactionType.BUY, 
                new BigDecimal("50"), new BigDecimal("52000"), baseDate.plusDays(35)),
            createTransaction(4L, stock, TransactionType.SELL, 
                new BigDecimal("50"), new BigDecimal("48000"), baseDate.plusDays(45))
        ));
        when(portfolioRepository.findByStockId(stock.getId()))
            .thenReturn(Optional.empty());
        
        StockAnalysisDto result = stockAnalysisService.analyzeStock(symbol);
        
        assertThat(result.getTradingPatterns()).isNotEmpty();
        
        // 평균 보유 기간 패턴 검증
        StockAnalysisDto.TradingPatternDto holdingPattern = result.getTradingPatterns()
            .stream()
            .filter(p -> p.getPattern().equals("평균 보유 기간"))
            .findFirst()
            .orElse(null);
        assertThat(holdingPattern).isNotNull();
        assertThat(holdingPattern.getValue()).contains("일");
        
        // 승률 패턴 검증
        StockAnalysisDto.TradingPatternDto winRatePattern = result.getTradingPatterns()
            .stream()
            .filter(p -> p.getPattern().equals("승률"))
            .findFirst()
            .orElse(null);
        assertThat(winRatePattern).isNotNull();
        assertThat(winRatePattern.getValue()).contains("%");
        assertThat(winRatePattern.getValue()).contains("1승 1패");
        
        // 거래 빈도 패턴 검증
        StockAnalysisDto.TradingPatternDto frequencyPattern = result.getTradingPatterns()
            .stream()
            .filter(p -> p.getPattern().equals("거래 빈도"))
            .findFirst()
            .orElse(null);
        assertThat(frequencyPattern).isNotNull();
        assertThat(frequencyPattern.getValue()).contains("월 평균");
        
        // 평균 거래 규모 패턴 검증
        StockAnalysisDto.TradingPatternDto sizePattern = result.getTradingPatterns()
            .stream()
            .filter(p -> p.getPattern().equals("평균 거래 규모"))
            .findFirst()
            .orElse(null);
        assertThat(sizePattern).isNotNull();
        assertThat(sizePattern.getValue()).contains("₩");
    }
    
    private Transaction createTransaction(Long id, Stock stock, TransactionType type,
                                        BigDecimal quantity, BigDecimal price, 
                                        LocalDateTime transactionDate) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setStock(stock);
        transaction.setType(type);
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        // TotalAmount is calculated automatically by getTotalAmount() method
        transaction.setTransactionDate(transactionDate);
        return transaction;
    }
}