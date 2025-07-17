package com.trading.journal.service;

import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private TransactionService transactionService;

    private Stock mockStock;
    private Transaction mockTransaction;
    private TransactionDto mockTransactionDto;

    @BeforeEach
    void setUp() {
        mockStock = Stock.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .exchange("NASDAQ")
                .build();

        mockTransaction = Transaction.builder()
                .id(1L)
                .stock(mockStock)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.00"))
                .commission(new BigDecimal("5.00"))
                .transactionDate(LocalDateTime.now())
                .notes("Test transaction")
                .build();

        mockTransactionDto = TransactionDto.builder()
                .stockSymbol("AAPL")
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.00"))
                .commission(new BigDecimal("5.00"))
                .transactionDate(LocalDateTime.now())
                .notes("Test transaction")
                .build();
    }

    @Test
    @DisplayName("매수 거래 생성 - 기존 종목")
    void createTransaction_ExistingStock_Buy() {
        // Given
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mockStock));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockTransaction);

        // When
        TransactionDto result = transactionService.createTransaction(mockTransactionDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        assertThat(result.getType()).isEqualTo(TransactionType.BUY);
        assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1505.00"));

        verify(stockRepository).findBySymbol("AAPL");
        verify(transactionRepository).save(any(Transaction.class));
        verify(portfolioService).updatePortfolio(any(Transaction.class));
    }

    @Test
    @DisplayName("매도 거래 생성 - 새로운 종목")
    void createTransaction_NewStock_Sell() {
        // Given
        TransactionDto sellDto = TransactionDto.builder()
                .stockSymbol("TSLA")
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("200.00"))
                .commission(new BigDecimal("3.00"))
                .transactionDate(LocalDateTime.now())
                .build();

        Stock newStock = Stock.builder()
                .id(2L)
                .symbol("TSLA")
                .name("Tesla Inc.")
                .build();

        Transaction sellTransaction = Transaction.builder()
                .id(2L)
                .stock(newStock)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("200.00"))
                .commission(new BigDecimal("3.00"))
                .transactionDate(sellDto.getTransactionDate())
                .build();

        when(stockRepository.findBySymbol("TSLA")).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenReturn(newStock);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(sellTransaction);

        // When
        TransactionDto result = transactionService.createTransaction(sellDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("TSLA");
        assertThat(result.getType()).isEqualTo(TransactionType.SELL);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("997.00"));

        verify(stockRepository).findBySymbol("TSLA");
        verify(stockRepository).save(any(Stock.class));
        verify(transactionRepository).save(any(Transaction.class));
        verify(portfolioService).updatePortfolio(any(Transaction.class));
    }

    @Test
    @DisplayName("모든 거래 조회")
    void getAllTransactions() {
        // Given
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findAll()).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getAllTransactions();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findAll();
    }

    @Test
    @DisplayName("종목별 거래 조회")
    void getTransactionsBySymbol() {
        // Given
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findByStockSymbol("AAPL")).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getTransactionsBySymbol("AAPL");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findByStockSymbol("AAPL");
    }

    @Test
    @DisplayName("거래 ID로 조회 - 성공")
    void getTransactionById_Found() {
        // Given
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mockTransaction));

        // When
        TransactionDto result = transactionService.getTransactionById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findById(1L);
    }

    @Test
    @DisplayName("거래 ID로 조회 - 실패")
    void getTransactionById_NotFound() {
        // Given
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> transactionService.getTransactionById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transaction not found with id: 999");
    }

    @Test
    @DisplayName("거래 수정")
    void updateTransaction() {
        // Given
        TransactionDto updateDto = TransactionDto.builder()
                .quantity(new BigDecimal("20"))
                .price(new BigDecimal("160.00"))
                .commission(new BigDecimal("10.00"))
                .transactionDate(LocalDateTime.now())
                .notes("Updated transaction")
                .build();

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mockTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockTransaction);

        // When
        TransactionDto result = transactionService.updateTransaction(1L, updateDto);

        // Then
        assertThat(result).isNotNull();
        verify(transactionRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(portfolioService).recalculatePortfolio(1L);
    }

    @Test
    @DisplayName("거래 삭제")
    void deleteTransaction() {
        // Given
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mockTransaction));

        // When
        transactionService.deleteTransaction(1L);

        // Then
        verify(transactionRepository).findById(1L);
        verify(transactionRepository).delete(mockTransaction);
        verify(portfolioService).recalculatePortfolio(1L);
    }

    @Test
    @DisplayName("날짜 범위로 거래 조회")
    void getTransactionsByDateRange() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findByDateRange(startDate, endDate)).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getTransactionsByDateRange(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findByDateRange(startDate, endDate);
    }
}