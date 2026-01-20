package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @Mock private StockRepository stockRepository;

    @InjectMocks private TaxCalculationService taxCalculationService;

    private Stock stock1;
    private Stock stock2;

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
    }

    @Test
    void calculateTax_WithProfitOverDeduction_ShouldCalculateTax() {
        Integer year = 2024;

        // FIFO 계산:
        // 삼성전자: 100주@80000 매도, 원가 100주@50000 -> 이익 = 100*(80000-50000) = 3,000,000
        // SK하이닉스: 50주@110000 매도, 원가 50주@120000 -> 손실 = 50*(110000-120000) = -500,000
        List<Transaction> transactions =
                Arrays.asList(
                        // 삼성전자 매수/매도 - 이익 300만원
                        createTransaction(
                                1L,
                                stock1,
                                TransactionType.BUY,
                                new BigDecimal("100"),
                                new BigDecimal("50000"),
                                LocalDateTime.of(2024, 1, 10, 9, 0)),
                        createTransaction(
                                2L,
                                stock1,
                                TransactionType.SELL,
                                new BigDecimal("100"),
                                new BigDecimal("80000"),
                                LocalDateTime.of(2024, 3, 15, 14, 30),
                                new BigDecimal("3000000"),
                                new BigDecimal("5000000")), // realizedPnl, costBasis

                        // SK하이닉스 매수/매도 - 손실 50만원
                        createTransaction(
                                3L,
                                stock2,
                                TransactionType.BUY,
                                new BigDecimal("50"),
                                new BigDecimal("120000"),
                                LocalDateTime.of(2024, 2, 5, 10, 0)),
                        createTransaction(
                                4L,
                                stock2,
                                TransactionType.SELL,
                                new BigDecimal("50"),
                                new BigDecimal("110000"),
                                LocalDateTime.of(2024, 4, 20, 15, 0),
                                new BigDecimal("-500000"),
                                new BigDecimal("6000000")) // realizedPnl, costBasis
                        );

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

        TaxCalculationDto result = taxCalculationService.calculateTax(year);

        assertThat(result).isNotNull();
        assertThat(result.getTaxYear()).isEqualTo(year);

        // 총 매도금액: 8,000,000 + 5,500,000 = 13,500,000
        assertThat(result.getTotalSellAmount()).isEqualByComparingTo(new BigDecimal("13500000"));

        // 총 이익: 3,000,000
        assertThat(result.getTotalProfit()).isEqualByComparingTo(new BigDecimal("3000000"));

        // 총 손실: 500,000
        assertThat(result.getTotalLoss()).isEqualByComparingTo(new BigDecimal("500000"));

        // 순이익: 3,000,000 - 500,000 = 2,500,000
        assertThat(result.getNetProfit()).isEqualByComparingTo(new BigDecimal("2500000"));

        // 과세표준: 2,500,000 - 2,500,000(기본공제) = 0
        assertThat(result.getTaxableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // 예상 세금: 0 * 22% = 0
        assertThat(result.getEstimatedTax()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.getTaxRate()).isEqualByComparingTo(new BigDecimal("22"));

        verify(transactionRepository).findByDateRange(any(), any());
    }

    @Test
    void calculateTax_WithHighProfit_ShouldCalculateTax() {
        Integer year = 2024;

        // FIFO 계산:
        // 1000주@60000 매도, 원가 1000주@50000 -> 이익 = 1000*(60000-50000) = 10,000,000
        List<Transaction> transactions =
                Arrays.asList(
                        // 큰 이익 발생 - 1000만원
                        createTransaction(
                                1L,
                                stock1,
                                TransactionType.BUY,
                                new BigDecimal("1000"),
                                new BigDecimal("50000"),
                                LocalDateTime.of(2024, 1, 10, 9, 0)),
                        createTransaction(
                                2L,
                                stock1,
                                TransactionType.SELL,
                                new BigDecimal("1000"),
                                new BigDecimal("60000"),
                                LocalDateTime.of(2024, 6, 15, 14, 30),
                                new BigDecimal("10000000"),
                                new BigDecimal("50000000")) // realizedPnl, costBasis
                        );

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

        TaxCalculationDto result = taxCalculationService.calculateTax(year);

        // 순이익: 10,000,000
        assertThat(result.getNetProfit()).isEqualByComparingTo(new BigDecimal("10000000"));

        // 과세표준: 10,000,000 - 2,500,000 = 7,500,000
        assertThat(result.getTaxableAmount()).isEqualByComparingTo(new BigDecimal("7500000"));

        // 예상 세금: 7,500,000 * 22% = 1,650,000
        assertThat(result.getEstimatedTax()).isEqualByComparingTo(new BigDecimal("1650000"));
    }

    @Test
    void calculateTax_WithNoSellTransactions_ShouldReturnEmptyTax() {
        Integer year = 2024;

        List<Transaction> transactions =
                Arrays.asList(
                        // 매수만 있고 매도 없음
                        createTransaction(
                                1L,
                                stock1,
                                TransactionType.BUY,
                                new BigDecimal("100"),
                                new BigDecimal("50000"),
                                LocalDateTime.of(2024, 1, 10, 9, 0)));

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

        TaxCalculationDto result = taxCalculationService.calculateTax(year);

        assertThat(result).isNotNull();
        assertThat(result.getTotalSellAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getEstimatedTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTaxDetails()).isEmpty();
    }

    @Test
    void calculateTax_WithLoss_ShouldNotCalculateTax() {
        Integer year = 2024;

        // FIFO 계산:
        // 100주@50000 매도, 원가 100주@60000 -> 손실 = 100*(50000-60000) = -1,000,000
        List<Transaction> transactions =
                Arrays.asList(
                        // 손실만 발생
                        createTransaction(
                                1L,
                                stock1,
                                TransactionType.BUY,
                                new BigDecimal("100"),
                                new BigDecimal("60000"),
                                LocalDateTime.of(2024, 1, 10, 9, 0)),
                        createTransaction(
                                2L,
                                stock1,
                                TransactionType.SELL,
                                new BigDecimal("100"),
                                new BigDecimal("50000"),
                                LocalDateTime.of(2024, 3, 15, 14, 30),
                                new BigDecimal("-1000000"),
                                new BigDecimal("6000000")) // realizedPnl, costBasis
                        );

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

        TaxCalculationDto result = taxCalculationService.calculateTax(year);

        // 순손실: -1,000,000
        assertThat(result.getNetProfit()).isEqualByComparingTo(new BigDecimal("-1000000"));

        // 과세표준: 0 (손실인 경우)
        assertThat(result.getTaxableAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // 예상 세금: 0
        assertThat(result.getEstimatedTax()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateTaxDetail_ShouldDetermineIsLongTerm() {
        Integer year = 2024;

        // FIFO 계산:
        // 삼성전자: 100주@60000 매도, 원가 100주@50000 -> 이익 = 100*(60000-50000) = 1,000,000
        // SK하이닉스: 50주@110000 매도, 원가 50주@100000 -> 이익 = 50*(110000-100000) = 500,000
        List<Transaction> transactions =
                Arrays.asList(
                        // 1년 이상 보유
                        createTransaction(
                                1L,
                                stock1,
                                TransactionType.BUY,
                                new BigDecimal("100"),
                                new BigDecimal("50000"),
                                LocalDateTime.of(2023, 1, 10, 9, 0)),
                        createTransaction(
                                2L,
                                stock1,
                                TransactionType.SELL,
                                new BigDecimal("100"),
                                new BigDecimal("60000"),
                                LocalDateTime.of(2024, 3, 15, 14, 30),
                                new BigDecimal("1000000"),
                                new BigDecimal("5000000")), // realizedPnl, costBasis

                        // 1년 미만 보유
                        createTransaction(
                                3L,
                                stock2,
                                TransactionType.BUY,
                                new BigDecimal("50"),
                                new BigDecimal("100000"),
                                LocalDateTime.of(2024, 1, 5, 10, 0)),
                        createTransaction(
                                4L,
                                stock2,
                                TransactionType.SELL,
                                new BigDecimal("50"),
                                new BigDecimal("110000"),
                                LocalDateTime.of(2024, 6, 20, 15, 0),
                                new BigDecimal("500000"),
                                new BigDecimal("5000000")) // realizedPnl, costBasis
                        );

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

        TaxCalculationDto result = taxCalculationService.calculateTax(year);

        assertThat(result.getTaxDetails()).hasSize(2);

        // 첫 번째 거래 - 장기보유
        TaxCalculationDto.TaxDetailDto detail1 = result.getTaxDetails().get(0);
        assertThat(detail1.getStockSymbol()).isEqualTo("005930");
        assertThat(detail1.getIsLongTerm()).isTrue();

        // 두 번째 거래 - 단기보유
        TaxCalculationDto.TaxDetailDto detail2 = result.getTaxDetails().get(1);
        assertThat(detail2.getStockSymbol()).isEqualTo("000660");
        assertThat(detail2.getIsLongTerm()).isFalse();
    }

    private Transaction createTransaction(
            Long id,
            Stock stock,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime transactionDate) {
        return createTransaction(id, stock, type, quantity, price, transactionDate, null, null);
    }

    private Transaction createTransaction(
            Long id,
            Stock stock,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime transactionDate,
            BigDecimal realizedPnl,
            BigDecimal costBasis) {
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
}
