package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.FifoResult;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.exception.TransactionNotFoundException;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @Mock private StockRepository stockRepository;

    @Mock private PortfolioService portfolioService;

    @Mock private StockPriceService stockPriceService;

    @Mock private AccountService accountService;

    @Mock private FifoCalculationService fifoCalculationService;

    @Mock private SecurityContextService securityContextService;

    @InjectMocks private TransactionService transactionService;

    private Account mockAccount;
    private Stock mockStock;
    private Transaction mockTransaction;
    private TransactionDto mockTransactionDto;

    @BeforeEach
    void setUp() {
        mockAccount =
                Account.builder()
                        .id(1L)
                        .name("기본 계좌")
                        .accountType(AccountType.GENERAL)
                        .isDefault(true)
                        .userId(100L)
                        .build();

        // Set up security context mock to return the same user ID as the account owner
        lenient()
                .when(securityContextService.getCurrentUserId())
                .thenReturn(java.util.Optional.of(100L));
        lenient()
                .when(securityContextService.getCurrentUsername())
                .thenReturn(java.util.Optional.of("testuser"));

        mockStock =
                Stock.builder().id(1L).symbol("AAPL").name("Apple Inc.").exchange("NASDAQ").build();

        mockTransaction =
                Transaction.builder()
                        .id(1L)
                        .account(mockAccount)
                        .stock(mockStock)
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal("150.00"))
                        .commission(new BigDecimal("5.00"))
                        .transactionDate(LocalDateTime.now())
                        .notes("Test transaction")
                        .build();

        mockTransactionDto =
                TransactionDto.builder()
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
        when(accountService.getDefaultAccount()).thenReturn(mockAccount);
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

        verify(accountService).getDefaultAccount();
        verify(stockRepository).findBySymbol("AAPL");
        verify(transactionRepository).save(any(Transaction.class));
        verify(portfolioService).updatePortfolio(any(Transaction.class));
    }

    @Test
    @DisplayName("매도 거래 생성 - 새로운 종목")
    void createTransaction_NewStock_Sell() {
        // Given
        TransactionDto sellDto =
                TransactionDto.builder()
                        .stockSymbol("TSLA")
                        .type(TransactionType.SELL)
                        .quantity(new BigDecimal("5"))
                        .price(new BigDecimal("200.00"))
                        .commission(new BigDecimal("3.00"))
                        .transactionDate(LocalDateTime.now())
                        .build();

        Stock newStock = Stock.builder().id(2L).symbol("TSLA").name("Tesla Inc.").build();

        Transaction sellTransaction =
                Transaction.builder()
                        .id(2L)
                        .account(mockAccount)
                        .stock(newStock)
                        .type(TransactionType.SELL)
                        .quantity(new BigDecimal("5"))
                        .price(new BigDecimal("200.00"))
                        .commission(new BigDecimal("3.00"))
                        .transactionDate(sellDto.getTransactionDate())
                        .realizedPnl(new BigDecimal("100.00"))
                        .costBasis(new BigDecimal("897.00"))
                        .build();

        FifoResult fifoResult =
                FifoResult.builder()
                        .realizedPnl(new BigDecimal("100.00"))
                        .costBasis(new BigDecimal("897.00"))
                        .consumptions(Collections.emptyList())
                        .build();

        when(accountService.getDefaultAccount()).thenReturn(mockAccount);
        when(stockRepository.findBySymbol("TSLA")).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenReturn(newStock);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(sellTransaction);
        when(fifoCalculationService.calculateFifoProfit(any(Transaction.class)))
                .thenReturn(fifoResult);

        // When
        TransactionDto result = transactionService.createTransaction(sellDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("TSLA");
        assertThat(result.getType()).isEqualTo(TransactionType.SELL);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("997.00"));
        assertThat(result.getRealizedPnl()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getCostBasis()).isEqualByComparingTo(new BigDecimal("897.00"));

        verify(accountService).getDefaultAccount();
        verify(stockRepository).findBySymbol("TSLA");
        verify(stockRepository).save(any(Stock.class));
        verify(transactionRepository).save(any(Transaction.class));
        verify(fifoCalculationService).calculateFifoProfit(any(Transaction.class));
        verify(fifoCalculationService)
                .applyFifoResult(any(Transaction.class), any(FifoResult.class));
        verify(portfolioService).updatePortfolio(any(Transaction.class));
    }

    @Test
    @DisplayName("모든 거래 조회")
    void getAllTransactions() {
        // Given
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findAllWithStock()).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getAllTransactions();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findAllWithStock();
    }

    @Test
    @DisplayName("종목별 거래 조회")
    void getTransactionsBySymbol() {
        // Given
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findBySymbolWithStock("AAPL")).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getTransactionsBySymbol("AAPL");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findBySymbolWithStock("AAPL");
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
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("거래를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("거래 수정")
    void updateTransaction() {
        // Given
        TransactionDto updateDto =
                TransactionDto.builder()
                        .quantity(new BigDecimal("20"))
                        .price(new BigDecimal("160.00"))
                        .commission(new BigDecimal("10.00"))
                        .transactionDate(LocalDateTime.now())
                        .notes("Updated transaction")
                        .build();

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mockTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockTransaction);
        doNothing()
                .when(fifoCalculationService)
                .recalculateFifoForAccountStock(anyLong(), anyLong());

        // When
        TransactionDto result = transactionService.updateTransaction(1L, updateDto);

        // Then
        assertThat(result).isNotNull();
        verify(transactionRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(fifoCalculationService).recalculateFifoForAccountStock(1L, 1L);
        verify(portfolioService).recalculatePortfolio(1L, 1L); // accountId, stockId
    }

    @Test
    @DisplayName("거래 삭제")
    void deleteTransaction() {
        // Given
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mockTransaction));
        doNothing()
                .when(fifoCalculationService)
                .recalculateFifoForAccountStock(anyLong(), anyLong());

        // When
        transactionService.deleteTransaction(1L);

        // Then
        verify(transactionRepository).findById(1L);
        verify(transactionRepository).delete(mockTransaction);
        verify(fifoCalculationService).recalculateFifoForAccountStock(1L, 1L);
        verify(portfolioService).recalculatePortfolio(1L, 1L); // accountId, stockId
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
        List<TransactionDto> result =
                transactionService.getTransactionsByDateRange(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(transactionRepository).findByDateRange(startDate, endDate);
    }

    @Test
    @DisplayName("계좌별 거래 조회")
    void getTransactionsByAccount() {
        // Given
        List<Transaction> transactions = Arrays.asList(mockTransaction);
        when(transactionRepository.findByAccountIdWithStock(1L)).thenReturn(transactions);

        // When
        List<TransactionDto> result = transactionService.getTransactionsByAccount(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getAccountId()).isEqualTo(1L);
        verify(transactionRepository).findByAccountIdWithStock(1L);
    }
}
