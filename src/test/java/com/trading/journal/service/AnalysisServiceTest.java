package com.trading.journal.service;

import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private PortfolioRepository portfolioRepository;
    
    @Mock
    private StockPriceService stockPriceService;
    
    @Mock
    private PortfolioAnalysisService portfolioAnalysisService;
    
    @InjectMocks
    private AnalysisService analysisService;
    
    private Stock stock1;
    private Stock stock2;
    private List<Transaction> transactions;
    private List<Portfolio> portfolios;
    
    @BeforeEach
    void setUp() {
        stock1 = new Stock();
        stock1.setId(1L);
        stock1.setSymbol("005930");
        stock1.setName("삼성전자");
        
        stock2 = new Stock();
        stock2.setId(2L);
        stock2.setSymbol("000660");
        stock2.setName("SK하이닉스");
        
        LocalDateTime startDate = LocalDateTime.of(2024, 1, 1, 0, 0);
        
        // FIFO 계산:
        // stock1: 매수 100주@50000, 매도 50주@55000 -> 손익 = 50 * (55000-50000) = 250,000
        // stock2: 매수 200주@100000, 매도 100주@110000 -> 손익 = 100 * (110000-100000) = 1,000,000
        // 총 실현손익 = 1,250,000
        transactions = Arrays.asList(
            createTransaction(1L, stock1, TransactionType.BUY,
                new BigDecimal("100"), new BigDecimal("50000"),
                startDate.plusDays(10)),
            createTransaction(2L, stock1, TransactionType.SELL,
                new BigDecimal("50"), new BigDecimal("55000"),
                startDate.plusDays(30),
                new BigDecimal("250000"), new BigDecimal("2500000")),  // realizedPnl, costBasis
            createTransaction(3L, stock2, TransactionType.BUY,
                new BigDecimal("200"), new BigDecimal("100000"),
                startDate.plusDays(15)),
            createTransaction(4L, stock2, TransactionType.SELL,
                new BigDecimal("100"), new BigDecimal("110000"),
                startDate.plusDays(45),
                new BigDecimal("1000000"), new BigDecimal("10000000"))  // realizedPnl, costBasis
        );
        
        portfolios = Arrays.asList(
            createPortfolio(stock1, new BigDecimal("50"), 
                new BigDecimal("50000"), new BigDecimal("2500000")),
            createPortfolio(stock2, new BigDecimal("100"), 
                new BigDecimal("100000"), new BigDecimal("10000000"))
        );
    }
    
    @Test
    void analyzePeriod_ShouldReturnCorrectAnalysis() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        
        PortfolioSummaryDto portfolioSummary = PortfolioSummaryDto.builder()
            .totalInvestment(new BigDecimal("12500000"))
            .totalCurrentValue(new BigDecimal("15000000"))
            .totalProfitLoss(new BigDecimal("2500000"))
            .totalProfitLossPercent(new BigDecimal("20"))
            .build();
        
        when(transactionRepository.findByDateRange(any(), any()))
            .thenReturn(transactions);
        when(portfolioAnalysisService.getPortfolioSummary())
            .thenReturn(portfolioSummary);
        
        PeriodAnalysisDto result = analysisService.analyzePeriod(startDate, endDate);
        
        assertThat(result).isNotNull();
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getEndDate()).isEqualTo(endDate);
        assertThat(result.getTotalBuyAmount())
            .isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(result.getTotalSellAmount())
            .isEqualByComparingTo(new BigDecimal("13750000"));
        assertThat(result.getTotalTransactions()).isEqualTo(4);
        assertThat(result.getBuyTransactions()).isEqualTo(2);
        assertThat(result.getSellTransactions()).isEqualTo(2);
        
        // 실현손익 검증 (FIFO 기반)
        assertThat(result.getRealizedProfit())
            .isEqualByComparingTo(new BigDecimal("1250000"));
        
        // 미실현손익 검증 (현재가 기준)
        assertThat(result.getUnrealizedProfit()).isNotNull();
        
        verify(transactionRepository).findByDateRange(any(), any());
        verify(portfolioAnalysisService).getPortfolioSummary();
    }
    
    @Test
    void analyzePeriod_WithNoTransactions_ShouldReturnEmptyAnalysis() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        
        PortfolioSummaryDto emptyPortfolioSummary = PortfolioSummaryDto.builder()
            .totalInvestment(BigDecimal.ZERO)
            .totalCurrentValue(BigDecimal.ZERO)
            .totalProfitLoss(BigDecimal.ZERO)
            .totalProfitLossPercent(BigDecimal.ZERO)
            .build();
        
        when(transactionRepository.findByDateRange(any(), any()))
            .thenReturn(Arrays.asList());
        when(portfolioAnalysisService.getPortfolioSummary())
            .thenReturn(emptyPortfolioSummary);
        
        PeriodAnalysisDto result = analysisService.analyzePeriod(startDate, endDate);
        
        assertThat(result).isNotNull();
        assertThat(result.getTotalBuyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalSellAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getRealizedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getUnrealizedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalTransactions()).isEqualTo(0);
    }
    
    @Test
    void analyzePeriod_ShouldGenerateMonthlyAnalysis() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        
        PortfolioSummaryDto portfolioSummary = PortfolioSummaryDto.builder()
            .totalInvestment(new BigDecimal("12500000"))
            .totalCurrentValue(new BigDecimal("15000000"))
            .totalProfitLoss(new BigDecimal("2500000"))
            .totalProfitLossPercent(new BigDecimal("20"))
            .build();
        
        when(transactionRepository.findByDateRange(any(), any()))
            .thenReturn(transactions);
        when(portfolioAnalysisService.getPortfolioSummary())
            .thenReturn(portfolioSummary);
        
        PeriodAnalysisDto result = analysisService.analyzePeriod(startDate, endDate);
        
        assertThat(result.getMonthlyAnalysis()).isNotEmpty();
        assertThat(result.getMonthlyAnalysis()).hasSize(3); // 1월, 2월, 3월
        
        PeriodAnalysisDto.MonthlyAnalysisDto january = result.getMonthlyAnalysis().get(0);
        assertThat(january.getYearMonth()).isEqualTo("2024-01");
        assertThat(january.getBuyAmount()).isEqualByComparingTo(new BigDecimal("25000000"));
        
        PeriodAnalysisDto.MonthlyAnalysisDto february = result.getMonthlyAnalysis().get(1);
        assertThat(february.getYearMonth()).isEqualTo("2024-02");
        assertThat(february.getSellAmount()).isEqualByComparingTo(new BigDecimal("11000000"));
    }
    
    private Transaction createTransaction(Long id, Stock stock, TransactionType type,
                                        BigDecimal quantity, BigDecimal price,
                                        LocalDateTime transactionDate) {
        return createTransaction(id, stock, type, quantity, price, transactionDate, null, null);
    }

    private Transaction createTransaction(Long id, Stock stock, TransactionType type,
                                        BigDecimal quantity, BigDecimal price,
                                        LocalDateTime transactionDate,
                                        BigDecimal realizedPnl, BigDecimal costBasis) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setStock(stock);
        transaction.setType(type);
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        // TotalAmount is calculated automatically by getTotalAmount() method
        transaction.setTransactionDate(transactionDate);
        if (type == TransactionType.SELL) {
            transaction.setRealizedPnl(realizedPnl);
            transaction.setCostBasis(costBasis);
        }
        return transaction;
    }
    
    private Portfolio createPortfolio(Stock stock, BigDecimal quantity, 
                                    BigDecimal averagePrice, BigDecimal totalInvestment) {
        Portfolio portfolio = new Portfolio();
        portfolio.setStock(stock);
        portfolio.setQuantity(quantity);
        portfolio.setAveragePrice(averagePrice);
        portfolio.setTotalInvestment(totalInvestment);
        return portfolio;
    }
}