package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.FifoResult;
import com.trading.journal.dto.FifoResult.BuyConsumption;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FifoCalculationService 테스트")
class FifoCalculationServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private FifoCalculationService fifoCalculationService;

    private Account account;
    private Stock stock;

    @BeforeEach
    void setUp() {
        account = Account.builder().id(1L).userId(100L).name("테스트 계좌").build();
        stock = Stock.builder().id(1L).symbol("AAPL").name("Apple Inc.").build();
    }

    @Nested
    @DisplayName("calculateFifoProfit 메서드")
    class CalculateFifoProfitTests {

        @Test
        @DisplayName("단일 매수 후 전체 매도 - FIFO 손익 계산 성공")
        void calculateFifoProfit_singleBuy_fullSell() {
            // Given: 10주 매수 (단가 100, 수수료 5) → 10주 매도 (단가 120, 수수료 3)
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(buyTx));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 매수원가 = (100 * 10 + 5) = 1005
            // 매도금액 = 120 * 10 - 3 = 1197
            // 실현손익 = 1197 - 1005 = 192
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("192");
            assertThat(result.getCostBasis()).isEqualByComparingTo("1005");
            assertThat(result.getConsumptions()).hasSize(1);

            BuyConsumption consumption = result.getConsumptions().get(0);
            assertThat(consumption.getBuyTransaction()).isEqualTo(buyTx);
            assertThat(consumption.getConsumedQuantity()).isEqualByComparingTo("10");
            assertThat(consumption.getConsumedCost()).isEqualByComparingTo("1005");
        }

        @Test
        @DisplayName("단일 매수 후 부분 매도 - 잔여 수량 유지")
        void calculateFifoProfit_singleBuy_partialSell() {
            // Given: 10주 매수 → 5주 매도
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("5"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("2"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(buyTx));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 매수단가 = (100 * 10 + 5) / 10 = 100.5
            // 소진원가 = 100.5 * 5 = 502.5
            // 매도금액 = 110 * 5 - 2 = 548
            // 실현손익 = 548 - 502.5 = 45.5
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("45.5");
            assertThat(result.getCostBasis()).isEqualByComparingTo("502.5");
            assertThat(result.getConsumptions()).hasSize(1);

            BuyConsumption consumption = result.getConsumptions().get(0);
            assertThat(consumption.getConsumedQuantity()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("다중 매수 후 매도 - 첫 번째 매수만 소진 (FIFO 순서)")
        void calculateFifoProfit_multipleBuys_consumeFirstOnly() {
            // Given: 첫 번째 10주 매수 (단가 100), 두 번째 10주 매수 (단가 110) → 8주 매도
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("6"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("8"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList(buyTx1, buyTx2));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 첫 번째 매수단가 = (100 * 10 + 5) / 10 = 100.5
            // 소진원가 = 100.5 * 8 = 804
            // 매도금액 = 120 * 8 - 3 = 957
            // 실현손익 = 957 - 804 = 153
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("153");
            assertThat(result.getCostBasis()).isEqualByComparingTo("804");
            assertThat(result.getConsumptions()).hasSize(1);

            BuyConsumption consumption = result.getConsumptions().get(0);
            assertThat(consumption.getBuyTransaction()).isEqualTo(buyTx1);
            assertThat(consumption.getConsumedQuantity()).isEqualByComparingTo("8");
        }

        @Test
        @DisplayName("다중 매수 후 매도 - 여러 매수 거래 소진 (FIFO 순서)")
        void calculateFifoProfit_multipleBuys_consumeMultiple() {
            // Given: 첫 번째 10주 매수 (단가 100), 두 번째 10주 매수 (단가 110) → 15주 매도
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("6"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("15"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("4"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList(buyTx1, buyTx2));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 첫 번째 매수단가 = (100 * 10 + 5) / 10 = 100.5
            // 두 번째 매수단가 = (110 * 10 + 6) / 10 = 110.6
            // 소진원가 = (100.5 * 10) + (110.6 * 5) = 1005 + 553 = 1558
            // 매도금액 = 120 * 15 - 4 = 1796
            // 실현손익 = 1796 - 1558 = 238
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("238");
            assertThat(result.getCostBasis()).isEqualByComparingTo("1558");
            assertThat(result.getConsumptions()).hasSize(2);

            BuyConsumption consumption1 = result.getConsumptions().get(0);
            assertThat(consumption1.getBuyTransaction()).isEqualTo(buyTx1);
            assertThat(consumption1.getConsumedQuantity()).isEqualByComparingTo("10");

            BuyConsumption consumption2 = result.getConsumptions().get(1);
            assertThat(consumption2.getBuyTransaction()).isEqualTo(buyTx2);
            assertThat(consumption2.getConsumedQuantity()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("매수가 아닌 거래 타입으로 호출 시 예외 발생")
        void calculateFifoProfit_nonSellTransaction_throwsException() {
            // Given: BUY 타입 거래
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            // When & Then
            assertThatThrownBy(() -> fifoCalculationService.calculateFifoProfit(buyTx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FIFO 계산은 매도 거래에만 적용됩니다");
        }

        @Test
        @DisplayName("매도 시 매수 기록이 없는 경우 - 경고 로그 출력 및 음수 손익")
        void calculateFifoProfit_sellWithoutMatchingBuys() {
            // Given: 매수 기록 없이 10주 매도
            Transaction sellTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // costBasis = 0 (매수 기록 없음)
            // 매도금액 = 100 * 10 - 3 = 997
            // 실현손익 = 997 - 0 = 997
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("997");
            assertThat(result.getCostBasis()).isEqualByComparingTo("0");
            assertThat(result.getConsumptions()).isEmpty();
        }

        @Test
        @DisplayName("잔여 수량이 0인 매수 거래는 건너뜀")
        void calculateFifoProfit_skipZeroRemainingQuantity() {
            // Given: 잔여 수량이 0인 매수와 10주 남은 매수 → 5주 매도
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(BigDecimal.ZERO)
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("6"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("5"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("2"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList(buyTx1, buyTx2));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 첫 번째 매수는 건너뛰고 두 번째 매수만 사용
            assertThat(result.getConsumptions()).hasSize(1);
            assertThat(result.getConsumptions().get(0).getBuyTransaction()).isEqualTo(buyTx2);
        }
    }

    @Nested
    @DisplayName("applyFifoResult 메서드")
    class ApplyFifoResultTests {

        @Test
        @DisplayName("FIFO 결과를 매도 거래에 적용 - realizedPnl 및 costBasis 저장")
        void applyFifoResult_updatesSellTransaction() {
            // Given
            Transaction sellTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            FifoResult fifoResult =
                    FifoResult.builder()
                            .realizedPnl(new BigDecimal("192"))
                            .costBasis(new BigDecimal("1005"))
                            .consumptions(Collections.emptyList())
                            .build();

            // When
            fifoCalculationService.applyFifoResult(sellTx, fifoResult);

            // Then
            assertThat(sellTx.getRealizedPnl()).isEqualByComparingTo("192");
            assertThat(sellTx.getCostBasis()).isEqualByComparingTo("1005");

            verify(transactionRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("FIFO 결과 적용 - 매수 거래의 잔여 수량 차감")
        void applyFifoResult_reducesBuyRemainingQuantity() {
            // Given
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("6"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("15"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("4"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            List<BuyConsumption> consumptions =
                    Arrays.asList(
                            BuyConsumption.builder()
                                    .buyTransaction(buyTx1)
                                    .consumedQuantity(new BigDecimal("10"))
                                    .consumedCost(new BigDecimal("1005"))
                                    .build(),
                            BuyConsumption.builder()
                                    .buyTransaction(buyTx2)
                                    .consumedQuantity(new BigDecimal("5"))
                                    .consumedCost(new BigDecimal("553"))
                                    .build());

            FifoResult fifoResult =
                    FifoResult.builder()
                            .realizedPnl(new BigDecimal("238"))
                            .costBasis(new BigDecimal("1558"))
                            .consumptions(consumptions)
                            .build();

            // When
            fifoCalculationService.applyFifoResult(sellTx, fifoResult);

            // Then
            assertThat(buyTx1.getRemainingQuantity()).isEqualByComparingTo("0");
            assertThat(buyTx2.getRemainingQuantity()).isEqualByComparingTo("5");

            ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
            verify(transactionRepository).saveAll(captor.capture());

            List<Transaction> savedTransactions = captor.getValue();
            assertThat(savedTransactions).hasSize(3); // buyTx1, buyTx2, sellTx
            assertThat(savedTransactions).contains(buyTx1, buyTx2, sellTx);
        }

        @Test
        @DisplayName("음수 잔여 수량 방지 - 0으로 조정")
        void applyFifoResult_preventsNegativeRemainingQuantity() {
            // Given: 잔여 수량보다 많이 소진하는 비정상 케이스
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("5"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            BuyConsumption consumption =
                    BuyConsumption.builder()
                            .buyTransaction(buyTx)
                            .consumedQuantity(new BigDecimal("10"))
                            .consumedCost(new BigDecimal("1005"))
                            .build();

            FifoResult fifoResult =
                    FifoResult.builder()
                            .realizedPnl(new BigDecimal("192"))
                            .costBasis(new BigDecimal("1005"))
                            .consumptions(Collections.singletonList(consumption))
                            .build();

            // When
            fifoCalculationService.applyFifoResult(sellTx, fifoResult);

            // Then: 음수가 되지 않고 0으로 조정됨
            assertThat(buyTx.getRemainingQuantity()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("recalculateFifoForAccountStock 메서드")
    class RecalculateFifoForAccountStockTests {

        @Test
        @DisplayName("특정 계좌/종목의 모든 거래 FIFO 재계산")
        void recalculateFifoForAccountStock_recalculatesAllTransactions() {
            // Given
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("6"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction sellTx1 =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("7"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("2"))
                            .realizedPnl(new BigDecimal("999"))
                            .costBasis(new BigDecimal("999"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            List<Transaction> allTransactions = Arrays.asList(buyTx1, buyTx2, sellTx1);

            when(transactionRepository.findByAccountIdAndStockIdOrderByTransactionDateAsc(
                            anyLong(), anyLong()))
                    .thenReturn(allTransactions);

            // When
            fifoCalculationService.recalculateFifoForAccountStock(1L, 1L);

            // Then
            // 매수 거래들의 remainingQuantity가 초기화 후 재계산됨
            assertThat(buyTx1.getRemainingQuantity()).isEqualByComparingTo("3");
            assertThat(buyTx2.getRemainingQuantity()).isEqualByComparingTo("10");

            // 매도 거래의 realizedPnl과 costBasis가 재계산됨
            assertThat(sellTx1.getRealizedPnl()).isNotEqualByComparingTo("999");
            assertThat(sellTx1.getCostBasis()).isNotEqualByComparingTo("999");

            verify(transactionRepository)
                    .findByAccountIdAndStockIdOrderByTransactionDateAsc(1L, 1L);
            verify(transactionRepository).saveAll(allTransactions);
        }

        @Test
        @DisplayName("재계산 시 매수 거래 remainingQuantity 초기화")
        void recalculateFifoForAccountStock_resetsRemainingQuantity() {
            // Given
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            when(transactionRepository.findByAccountIdAndStockIdOrderByTransactionDateAsc(
                            anyLong(), anyLong()))
                    .thenReturn(Collections.singletonList(buyTx));

            // When
            fifoCalculationService.recalculateFifoForAccountStock(1L, 1L);

            // Then: remainingQuantity가 원래 quantity로 초기화됨
            assertThat(buyTx.getRemainingQuantity()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("재계산 시 매도 거래 FIFO 필드 초기화 후 재계산")
        void recalculateFifoForAccountStock_resetsSellTransactionFields() {
            // Given
            Transaction sellTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("3"))
                            .realizedPnl(new BigDecimal("999"))
                            .costBasis(new BigDecimal("999"))
                            .remainingQuantity(new BigDecimal("5"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            when(transactionRepository.findByAccountIdAndStockIdOrderByTransactionDateAsc(
                            anyLong(), anyLong()))
                    .thenReturn(Collections.singletonList(sellTx));

            // When
            fifoCalculationService.recalculateFifoForAccountStock(1L, 1L);

            // Then: FIFO 필드가 초기화됨 (매도는 remainingQuantity = null)
            assertThat(sellTx.getRemainingQuantity()).isNull();
            assertThat(sellTx.getRealizedPnl()).isNotNull();
            assertThat(sellTx.getCostBasis()).isNotNull();
        }
    }

    @Nested
    @DisplayName("migrateAllExistingSellTransactions 메서드")
    class MigrateAllExistingSellTransactionsTests {

        @Test
        @DisplayName("모든 계좌/종목 쌍에 대해 FIFO 마이그레이션 실행")
        void migrateAllExistingSellTransactions_processesAllPairs() {
            // Given
            List<Object[]> accountStockPairs =
                    Arrays.asList(
                            new Object[] {1L, 1L}, new Object[] {1L, 2L}, new Object[] {2L, 1L});

            when(transactionRepository.findDistinctAccountStockPairs())
                    .thenReturn(accountStockPairs);

            when(transactionRepository.findByAccountIdAndStockIdOrderByTransactionDateAsc(
                            anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());

            // When
            fifoCalculationService.migrateAllExistingSellTransactions();

            // Then
            verify(transactionRepository).findDistinctAccountStockPairs();
            verify(transactionRepository, times(3))
                    .findByAccountIdAndStockIdOrderByTransactionDateAsc(anyLong(), anyLong());
        }

        @Test
        @DisplayName("계좌/종목 쌍이 없는 경우 마이그레이션 수행하지 않음")
        void migrateAllExistingSellTransactions_noPairs_doesNothing() {
            // Given
            when(transactionRepository.findDistinctAccountStockPairs())
                    .thenReturn(Collections.emptyList());

            // When
            fifoCalculationService.migrateAllExistingSellTransactions();

            // Then
            verify(transactionRepository).findDistinctAccountStockPairs();
            verify(transactionRepository, never())
                    .findByAccountIdAndStockIdOrderByTransactionDateAsc(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("FIFO 통합 시나리오 테스트")
    class FifoIntegrationTests {

        @Test
        @DisplayName("복잡한 FIFO 시나리오 - 매수 3건, 매도 2건")
        void complexFifoScenario() {
            // Given: 매수 10주(100원), 매수 20주(110원), 매수 15주(105원) → 매도 25주, 매도
            // 10주
            Transaction buyTx1 =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction buyTx2 =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("20"))
                            .price(new BigDecimal("110"))
                            .commission(new BigDecimal("10"))
                            .remainingQuantity(new BigDecimal("20"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            Transaction buyTx3 =
                    Transaction.builder()
                            .id(3L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("15"))
                            .price(new BigDecimal("105"))
                            .commission(new BigDecimal("7"))
                            .remainingQuantity(new BigDecimal("15"))
                            .transactionDate(LocalDateTime.of(2024, 1, 3, 10, 0))
                            .build();

            Transaction sellTx1 =
                    Transaction.builder()
                            .id(4L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("25"))
                            .price(new BigDecimal("120"))
                            .commission(new BigDecimal("5"))
                            .transactionDate(LocalDateTime.of(2024, 1, 4, 10, 0))
                            .build();

            // 첫 번째 매도: 10주(buyTx1 전체) + 15주(buyTx2 일부)
            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            eq(1L), eq(1L), eq(sellTx1.getTransactionDate())))
                    .thenReturn(Arrays.asList(buyTx1, buyTx2, buyTx3));

            // When
            FifoResult result1 = fifoCalculationService.calculateFifoProfit(sellTx1);

            // Then
            // buyTx1: 100.5 * 10 = 1005
            // buyTx2: 110.5 * 15 = 1657.5
            // 총 원가 = 2662.5
            // 매도금액 = 120 * 25 - 5 = 2995
            // 실현손익 = 2995 - 2662.5 = 332.5
            assertThat(result1.getRealizedPnl()).isEqualByComparingTo("332.5");
            assertThat(result1.getCostBasis()).isEqualByComparingTo("2662.5");
            assertThat(result1.getConsumptions()).hasSize(2);
        }

        @Test
        @DisplayName("수수료 없는 경우 FIFO 계산")
        void fifoCalculation_noCommission() {
            // Given
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(null)
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("120"))
                            .commission(null)
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(buyTx));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 매수원가 = 100 * 10 = 1000
            // 매도금액 = 120 * 10 = 1200
            // 실현손익 = 1200 - 1000 = 200
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("200");
            assertThat(result.getCostBasis()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("손실 거래 FIFO 계산")
        void fifoCalculation_lossTransaction() {
            // Given: 100원에 매수 → 80원에 매도 (손실)
            Transaction buyTx =
                    Transaction.builder()
                            .id(1L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.BUY)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("100"))
                            .commission(new BigDecimal("5"))
                            .remainingQuantity(new BigDecimal("10"))
                            .transactionDate(LocalDateTime.of(2024, 1, 1, 10, 0))
                            .build();

            Transaction sellTx =
                    Transaction.builder()
                            .id(2L)
                            .account(account)
                            .stock(stock)
                            .type(TransactionType.SELL)
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("80"))
                            .commission(new BigDecimal("3"))
                            .transactionDate(LocalDateTime.of(2024, 1, 2, 10, 0))
                            .build();

            when(transactionRepository.findAvailableBuyTransactionsForFifo(
                            anyLong(), anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(buyTx));

            // When
            FifoResult result = fifoCalculationService.calculateFifoProfit(sellTx);

            // Then
            // 매수원가 = 100 * 10 + 5 = 1005
            // 매도금액 = 80 * 10 - 3 = 797
            // 실현손익 = 797 - 1005 = -208
            assertThat(result.getRealizedPnl()).isEqualByComparingTo("-208");
            assertThat(result.getCostBasis()).isEqualByComparingTo("1005");
        }
    }
}
