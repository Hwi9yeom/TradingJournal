package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.trading.journal.dto.TradingStatisticsDto;
import com.trading.journal.dto.TradingStatisticsDto.*;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.AccountRiskSettingsRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingStatisticsServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRiskSettingsRepository accountRiskSettingsRepository;

    @InjectMocks private TradingStatisticsService tradingStatisticsService;

    private Stock stock1;
    private Stock stock2;
    private Account account;
    private LocalDate startDate;
    private LocalDate endDate;

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

        account = new Account();
        account.setId(1L);

        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 3, 31);
    }

    @Nested
    @DisplayName("시간대별 성과 분석 테스트")
    class TimeOfDayPerformanceTests {

        @Test
        @DisplayName("거래 데이터 없을 때 빈 리스트 반환")
        void getTimeOfDayPerformance_NoData_ReturnsEmptyList() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<TimeOfDayStats> result =
                    tradingStatisticsService.getTimeOfDayPerformance(1L, startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("시간대별 성과 분석 성공")
        void getTimeOfDayPerformance_WithData_ReturnsStats() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);

            List<TimeOfDayStats> result =
                    tradingStatisticsService.getTimeOfDayPerformance(1L, startDate, endDate);

            assertThat(result).isNotEmpty();
            result.forEach(
                    stats -> {
                        assertThat(stats.getHour()).isBetween(8, 16);
                        assertThat(stats.getTotalTrades()).isGreaterThanOrEqualTo(0);
                        assertThat(stats.getTimePeriod()).isNotBlank();
                    });
        }
    }

    @Nested
    @DisplayName("요일별 성과 분석 테스트")
    class WeekdayPerformanceTests {

        @Test
        @DisplayName("거래 데이터 없을 때 빈 요일 리스트 반환")
        void getWeekdayPerformance_NoData_ReturnsAllDays() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<WeekdayStats> result =
                    tradingStatisticsService.getWeekdayPerformance(1L, startDate, endDate);

            // 모든 요일 포함 (7일)
            assertThat(result).hasSize(7);
            result.forEach(
                    stats -> {
                        assertThat(stats.getDayOfWeek()).isBetween(1, 7);
                        assertThat(stats.getTotalTrades()).isEqualTo(0);
                    });
        }

        @Test
        @DisplayName("요일별 성과 분석 성공")
        void getWeekdayPerformance_WithData_ReturnsStats() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);

            List<WeekdayStats> result =
                    tradingStatisticsService.getWeekdayPerformance(1L, startDate, endDate);

            assertThat(result).hasSize(7);
            assertThat(result.stream().anyMatch(s -> s.getTotalTrades() > 0)).isTrue();
        }
    }

    @Nested
    @DisplayName("종목별 성과 분석 테스트")
    class SymbolPerformanceTests {

        @Test
        @DisplayName("거래 데이터 없을 때 빈 리스트 반환")
        void getSymbolPerformance_NoData_ReturnsEmptyList() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<SymbolStats> result =
                    tradingStatisticsService.getSymbolPerformance(1L, startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("종목별 성과 분석 및 순위 부여")
        void getSymbolPerformance_WithData_ReturnsRankedStats() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(createSampleBuyTransactions());

            List<SymbolStats> result =
                    tradingStatisticsService.getSymbolPerformance(1L, startDate, endDate);

            assertThat(result).isNotEmpty();
            // 순위 정렬 확인
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).getTotalProfit())
                        .isGreaterThanOrEqualTo(result.get(i + 1).getTotalProfit());
            }
            // 순위 부여 확인
            for (int i = 0; i < result.size(); i++) {
                assertThat(result.get(i).getRank()).isEqualTo(i + 1);
            }
        }
    }

    @Nested
    @DisplayName("실수 패턴 분석 테스트")
    class MistakePatternsTests {

        @Test
        @DisplayName("거래 데이터 없을 때 빈 패턴 리스트 반환")
        void getMistakePatterns_NoData_ReturnsEmptyList() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<MistakePattern> result =
                    tradingStatisticsService.getMistakePatterns(1L, startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("손절가 미설정 패턴 감지")
        void getMistakePatterns_NoStopLoss_DetectsPattern() {
            List<Transaction> transactions = createTransactionsWithoutStopLoss();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(createSampleBuyTransactions());

            List<MistakePattern> result =
                    tradingStatisticsService.getMistakePatterns(1L, startDate, endDate);

            assertThat(result).anyMatch(p -> p.getType().equals("NO_STOP_LOSS"));
        }

        @Test
        @DisplayName("과도한 거래 패턴 감지")
        void getMistakePatterns_Overtrading_DetectsPattern() {
            List<Transaction> transactions = createOvertradingTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<MistakePattern> result =
                    tradingStatisticsService.getMistakePatterns(1L, startDate, endDate);

            assertThat(result).anyMatch(p -> p.getType().equals("OVERTRADING"));
        }
    }

    @Nested
    @DisplayName("개선 제안 테스트")
    class ImprovementSuggestionsTests {

        @Test
        @DisplayName("거래 데이터 없을 때 빈 제안 리스트")
        void getImprovementSuggestions_NoData_ReturnsEmpty() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());

            List<ImprovementSuggestion> result =
                    tradingStatisticsService.getImprovementSuggestions(1L, startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("개선 제안 우선순위 정렬")
        void getImprovementSuggestions_SortedByPriority() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(createSampleBuyTransactions());
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());

            List<ImprovementSuggestion> result =
                    tradingStatisticsService.getImprovementSuggestions(1L, startDate, endDate);

            // HIGH 우선순위가 먼저 오는지 확인
            if (result.size() > 1) {
                for (int i = 0; i < result.size() - 1; i++) {
                    int currentPriority = getPriorityValue(result.get(i).getPriority());
                    int nextPriority = getPriorityValue(result.get(i + 1).getPriority());
                    assertThat(currentPriority).isGreaterThanOrEqualTo(nextPriority);
                }
            }
        }
    }

    @Nested
    @DisplayName("전체 통계 요약 테스트")
    class FullStatisticsTests {

        @Test
        @DisplayName("전체 통계 조회")
        void getFullStatistics_ReturnsCompleteDto() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(createSampleBuyTransactions());
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());

            TradingStatisticsDto result =
                    tradingStatisticsService.getFullStatistics(1L, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getTimeOfDayStats()).isNotNull();
            assertThat(result.getWeekdayStats()).isNotNull();
            assertThat(result.getSymbolStats()).isNotNull();
            assertThat(result.getMistakePatterns()).isNotNull();
            assertThat(result.getSuggestions()).isNotNull();
            assertThat(result.getOverallSummary()).isNotNull();
        }

        @Test
        @DisplayName("전체 요약에 베스트 요일/시간 포함")
        void getFullStatistics_ContainsOverallSummary() {
            List<Transaction> transactions = createSampleSellTransactions();
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            any(), any(), any()))
                    .thenReturn(createSampleBuyTransactions());
            when(accountRiskSettingsRepository.findByAccountId(any())).thenReturn(Optional.empty());

            TradingStatisticsDto result =
                    tradingStatisticsService.getFullStatistics(1L, startDate, endDate);

            OverallSummary summary = result.getOverallSummary();
            assertThat(summary.getTotalTrades()).isGreaterThanOrEqualTo(0);
            assertThat(summary.getOverallWinRate()).isNotNull();
            assertThat(summary.getConsistencyScore()).isNotNull();
        }
    }

    @Nested
    @DisplayName("전체 거래 통계 테스트")
    class OverallStatisticsTests {

        @Test
        @DisplayName("전체 거래 통계 조회")
        void getOverallStatistics_ReturnsStats() {
            List<Transaction> transactions = createAllTransactions();
            when(transactionRepository.findAllWithStock()).thenReturn(transactions);

            Map<String, Object> result = tradingStatisticsService.getOverallStatistics();

            assertThat(result).containsKeys("totalTrades", "uniqueStocks", "winRate", "avgReturn");
            assertThat(result.get("totalTrades")).isEqualTo(transactions.size());
        }

        @Test
        @DisplayName("거래 없을 때 기본값")
        void getOverallStatistics_NoData_ReturnsDefaults() {
            when(transactionRepository.findAllWithStock()).thenReturn(Collections.emptyList());

            Map<String, Object> result = tradingStatisticsService.getOverallStatistics();

            assertThat(result.get("totalTrades")).isEqualTo(0);
            assertThat((Number) result.get("winRate"))
                    .satisfies(n -> assertThat(n.doubleValue()).isEqualTo(0.0));
        }
    }

    @Nested
    @DisplayName("자산 히스토리 테스트")
    class AssetHistoryTests {

        @Test
        @DisplayName("자산 히스토리 조회")
        void getAssetHistory_ReturnsHistory() {
            List<Transaction> transactions = createAllTransactions();
            when(transactionRepository.findByDateRange(any(), any())).thenReturn(transactions);

            Map<String, Object> result =
                    tradingStatisticsService.getAssetHistory(startDate, endDate);

            assertThat(result).containsKeys("labels", "values");
            assertThat((List<?>) result.get("labels")).isNotEmpty();
            assertThat((List<?>) result.get("values")).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("월별 수익률 테스트")
    class MonthlyReturnsTests {

        @Test
        @DisplayName("월별 수익률 조회")
        void getMonthlyReturns_ReturnsSortedList() {
            List<Transaction> transactions = createAllTransactions();
            when(transactionRepository.findAllWithStock()).thenReturn(transactions);

            List<Map<String, Object>> result = tradingStatisticsService.getMonthlyReturns();

            assertThat(result).isNotEmpty();
            result.forEach(
                    monthly -> {
                        assertThat(monthly).containsKeys("month", "returnRate", "investment");
                    });
        }
    }

    // Helper methods
    private List<Transaction> createSampleSellTransactions() {
        List<Transaction> transactions = new ArrayList<>();

        // 월요일 오전 10시 거래 (수익)
        transactions.add(
                createSellTransaction(
                        1L, stock1, LocalDateTime.of(2024, 1, 8, 10, 0), "100000", "2500000"));

        // 화요일 오후 2시 거래 (손실)
        transactions.add(
                createSellTransaction(
                        2L, stock2, LocalDateTime.of(2024, 1, 9, 14, 0), "-50000", "5000000"));

        // 수요일 오전 9시 거래 (수익)
        transactions.add(
                createSellTransaction(
                        3L, stock1, LocalDateTime.of(2024, 1, 10, 9, 0), "80000", "3000000"));

        return transactions;
    }

    private List<Transaction> createSampleBuyTransactions() {
        List<Transaction> transactions = new ArrayList<>();

        transactions.add(createBuyTransaction(10L, stock1, LocalDateTime.of(2024, 1, 2, 10, 0)));
        transactions.add(createBuyTransaction(11L, stock2, LocalDateTime.of(2024, 1, 3, 14, 0)));

        return transactions;
    }

    private List<Transaction> createTransactionsWithoutStopLoss() {
        List<Transaction> transactions = new ArrayList<>();

        // 손절가 없이 손실 발생한 거래
        Transaction tx =
                createSellTransaction(
                        1L, stock1, LocalDateTime.of(2024, 1, 8, 10, 0), "-100000", "5000000");
        tx.setStopLossPrice(null);
        transactions.add(tx);

        return transactions;
    }

    private List<Transaction> createOvertradingTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 8, 9, 0);

        // 같은 날 5건 이상 거래
        for (int i = 0; i < 6; i++) {
            transactions.add(
                    createSellTransaction(
                            (long) (i + 1),
                            stock1,
                            baseTime.plusHours(i),
                            String.valueOf(10000 * (i % 2 == 0 ? 1 : -1)),
                            "1000000"));
        }

        return transactions;
    }

    private List<Transaction> createAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();

        // 매수 거래
        transactions.add(createBuyTransaction(1L, stock1, LocalDateTime.of(2024, 1, 2, 10, 0)));
        transactions.add(createBuyTransaction(2L, stock2, LocalDateTime.of(2024, 1, 5, 11, 0)));

        // 매도 거래
        transactions.addAll(createSampleSellTransactions());

        return transactions;
    }

    private Transaction createSellTransaction(
            Long id, Stock stock, LocalDateTime date, String realizedPnl, String costBasis) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setStock(stock);
        tx.setAccount(account);
        tx.setType(TransactionType.SELL);
        tx.setQuantity(new BigDecimal("10"));
        tx.setPrice(new BigDecimal("50000"));
        tx.setTransactionDate(date);
        tx.setRealizedPnl(new BigDecimal(realizedPnl));
        tx.setCostBasis(new BigDecimal(costBasis));
        return tx;
    }

    private Transaction createBuyTransaction(Long id, Stock stock, LocalDateTime date) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setStock(stock);
        tx.setAccount(account);
        tx.setType(TransactionType.BUY);
        tx.setQuantity(new BigDecimal("10"));
        tx.setPrice(new BigDecimal("48000"));
        tx.setTransactionDate(date);
        return tx;
    }

    private int getPriorityValue(String priority) {
        return switch (priority) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
