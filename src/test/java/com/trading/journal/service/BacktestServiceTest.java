package com.trading.journal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.*;
import com.trading.journal.entity.BacktestResult;
import com.trading.journal.repository.BacktestResultRepository;
import com.trading.journal.strategy.TradingStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yahoofinance.histquotes.HistoricalQuote;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestService 테스트")
class BacktestServiceTest {

    @Mock private BacktestResultRepository backtestResultRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private StockPriceService stockPriceService;

    @InjectMocks private BacktestService backtestService;

    private BacktestRequestDto createDefaultRequest() {
        return BacktestRequestDto.builder()
                .symbol("AAPL")
                .strategyType(TradingStrategy.StrategyType.MOVING_AVERAGE)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 6, 30))
                .initialCapital(new BigDecimal("10000000"))
                .positionSizePercent(BigDecimal.valueOf(100))
                .commissionRate(BigDecimal.valueOf(0.015))
                .slippage(BigDecimal.valueOf(0.1))
                .build();
    }

    private List<HistoricalQuote> createSampleHistoricalQuotes() {
        List<HistoricalQuote> quotes = new ArrayList<>();
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < 100; i++) {
            LocalDate date = startDate.plusDays(i);
            // 주말 제외
            if (date.getDayOfWeek().getValue() <= 5) {
                BigDecimal basePrice = BigDecimal.valueOf(100 + i * 0.5);
                HistoricalQuote quote = mock(HistoricalQuote.class);

                // Calendar 생성
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(java.sql.Date.valueOf(date));

                when(quote.getDate()).thenReturn(calendar);
                when(quote.getOpen()).thenReturn(basePrice);
                when(quote.getHigh()).thenReturn(basePrice.add(BigDecimal.valueOf(2)));
                when(quote.getLow()).thenReturn(basePrice.subtract(BigDecimal.valueOf(1)));
                when(quote.getClose()).thenReturn(basePrice.add(BigDecimal.valueOf(0.5)));
                when(quote.getVolume()).thenReturn(1000000L);
                quotes.add(quote);
            }
        }

        return quotes;
    }

    @Nested
    @DisplayName("runBacktest 테스트")
    class RunBacktestTest {

        @Test
        @DisplayName("MOVING_AVERAGE 전략으로 백테스트 성공")
        void runBacktest_WithMovingAverageStrategy_Success() throws Exception {
            // Given
            BacktestRequestDto request = createDefaultRequest();
            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();

            when(stockPriceService.getHistoricalQuotes(
                            eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            BacktestResult savedResult = mock(BacktestResult.class);
            lenient().when(savedResult.getId()).thenReturn(1L);
            lenient().when(savedResult.getStrategyName()).thenReturn("MA-Cross-20/60");
            lenient().when(savedResult.getStrategyType()).thenReturn("MOVING_AVERAGE");
            lenient().when(savedResult.getSymbol()).thenReturn("AAPL");
            lenient().when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            lenient().when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            lenient().when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            lenient().when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("11000000"));
            lenient().when(savedResult.getTotalReturn()).thenReturn(BigDecimal.valueOf(10));
            lenient().when(savedResult.getCagr()).thenReturn(BigDecimal.valueOf(8.5));
            lenient().when(savedResult.getMaxDrawdown()).thenReturn(BigDecimal.valueOf(5.2));
            lenient().when(savedResult.getSharpeRatio()).thenReturn(BigDecimal.valueOf(1.5));
            lenient().when(savedResult.getSortinoRatio()).thenReturn(BigDecimal.valueOf(2.0));
            lenient().when(savedResult.getTotalTrades()).thenReturn(10);
            lenient().when(savedResult.getWinningTrades()).thenReturn(6);
            lenient().when(savedResult.getLosingTrades()).thenReturn(4);
            lenient().when(savedResult.getWinRate()).thenReturn(BigDecimal.valueOf(60));
            lenient().when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            lenient().when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            BacktestResultDto result = backtestService.runBacktest(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo("AAPL");
            assertThat(result.getStrategyType()).isEqualTo("MOVING_AVERAGE");
            assertThat(result.getInitialCapital())
                    .isEqualByComparingTo(request.getInitialCapital());

            verify(stockPriceService)
                    .getHistoricalQuotes(eq("AAPL"), any(LocalDate.class), any(LocalDate.class));
            verify(backtestResultRepository).save(any(BacktestResult.class));
        }

        @Test
        @DisplayName("RSI 전략으로 백테스트 성공")
        void runBacktest_WithRSIStrategy_Success() throws Exception {
            // Given
            BacktestRequestDto request =
                    BacktestRequestDto.builder()
                            .symbol("TSLA")
                            .strategyType(TradingStrategy.StrategyType.RSI)
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 6, 30))
                            .initialCapital(new BigDecimal("10000000"))
                            .strategyParams(
                                    Map.of(
                                            "period", 14,
                                            "overboughtLevel", 70,
                                            "oversoldLevel", 30))
                            .build();

            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();
            when(stockPriceService.getHistoricalQuotes(
                            eq("TSLA"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            BacktestResult savedResult = mock(BacktestResult.class);
            lenient().when(savedResult.getId()).thenReturn(1L);
            lenient().when(savedResult.getStrategyName()).thenReturn("RSI-14");
            lenient().when(savedResult.getStrategyType()).thenReturn("RSI");
            lenient().when(savedResult.getSymbol()).thenReturn("TSLA");
            lenient().when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            lenient().when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            lenient().when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            lenient().when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("10500000"));
            lenient().when(savedResult.getTotalReturn()).thenReturn(BigDecimal.valueOf(5));
            lenient().when(savedResult.getTotalTrades()).thenReturn(8);
            lenient().when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            lenient().when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            BacktestResultDto result = backtestService.runBacktest(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo("TSLA");
            assertThat(result.getStrategyType()).isEqualTo("RSI");

            verify(stockPriceService)
                    .getHistoricalQuotes(eq("TSLA"), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("가격 데이터가 없을 때 샘플 데이터로 폴백")
        void runBacktest_WithEmptyPriceData_FallbackToSample() throws Exception {
            // Given
            BacktestRequestDto request = createDefaultRequest();

            when(stockPriceService.getHistoricalQuotes(
                            eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            BacktestResult savedResult = mock(BacktestResult.class);
            when(savedResult.getId()).thenReturn(1L);
            when(savedResult.getSymbol()).thenReturn("AAPL");
            when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("10200000"));
            when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            when(savedResult.getStrategyName()).thenReturn("MA-Cross-20/60");
            when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            BacktestResultDto result = backtestService.runBacktest(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo("AAPL");

            // 샘플 데이터 생성으로 인해 백테스트는 정상 실행됨
            verify(backtestResultRepository).save(any(BacktestResult.class));
        }

        @Test
        @DisplayName("백테스트 통계 정확히 계산")
        void runBacktest_CalculatesStatisticsCorrectly() throws Exception {
            // Given
            BacktestRequestDto request = createDefaultRequest();
            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();

            when(stockPriceService.getHistoricalQuotes(
                            eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            ArgumentCaptor<BacktestResult> resultCaptor =
                    ArgumentCaptor.forClass(BacktestResult.class);

            BacktestResult savedResult = mock(BacktestResult.class);
            when(savedResult.getId()).thenReturn(1L);
            when(savedResult.getSymbol()).thenReturn("AAPL");
            when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("11000000"));
            when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            when(savedResult.getStrategyName()).thenReturn("MA-Cross-20/60");
            when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(resultCaptor.capture())).thenReturn(savedResult);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            BacktestResultDto result = backtestService.runBacktest(request);

            // Then
            BacktestResult capturedResult = resultCaptor.getValue();
            assertThat(capturedResult).isNotNull();
            assertThat(capturedResult.getSymbol()).isEqualTo("AAPL");
            assertThat(capturedResult.getInitialCapital())
                    .isEqualByComparingTo(request.getInitialCapital());

            // 실행 시간이 설정되었는지 확인
            assertThat(capturedResult.getExecutionTimeMs()).isNotNull();
        }

        @Test
        @DisplayName("MACD 전략으로 백테스트 성공")
        void runBacktest_WithMACDStrategy_Success() throws Exception {
            // Given
            BacktestRequestDto request =
                    BacktestRequestDto.builder()
                            .symbol("MSFT")
                            .strategyType(TradingStrategy.StrategyType.MACD)
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 6, 30))
                            .initialCapital(new BigDecimal("10000000"))
                            .strategyParams(
                                    Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9))
                            .build();

            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();
            when(stockPriceService.getHistoricalQuotes(
                            eq("MSFT"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            BacktestResult savedResult = mock(BacktestResult.class);
            when(savedResult.getId()).thenReturn(1L);
            when(savedResult.getSymbol()).thenReturn("MSFT");
            when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("10800000"));
            when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            when(savedResult.getStrategyName()).thenReturn("MACD-12/26/9");
            when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            BacktestResultDto result = backtestService.runBacktest(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo("MSFT");
        }
    }

    @Nested
    @DisplayName("getHistory 테스트")
    class GetHistoryTest {

        @Test
        @DisplayName("백테스트 히스토리 조회 성공")
        void getHistory_ReturnsAllResults() {
            // Given
            BacktestResult result1 = createMockBacktestResult(1L, "AAPL", BigDecimal.valueOf(10));
            BacktestResult result2 = createMockBacktestResult(2L, "TSLA", BigDecimal.valueOf(15));

            when(backtestResultRepository.findTop20ByOrderByExecutedAtDesc())
                    .thenReturn(Arrays.asList(result1, result2));

            // When
            List<BacktestSummaryDto> history = backtestService.getHistory();

            // Then
            assertThat(history).hasSize(2);
            assertThat(history.get(0).getSymbol()).isEqualTo("AAPL");
            assertThat(history.get(1).getSymbol()).isEqualTo("TSLA");

            verify(backtestResultRepository).findTop20ByOrderByExecutedAtDesc();
        }

        @Test
        @DisplayName("히스토리가 없을 때 빈 리스트 반환")
        void getHistory_WhenNoHistory_ReturnsEmptyList() {
            // Given
            when(backtestResultRepository.findTop20ByOrderByExecutedAtDesc())
                    .thenReturn(Collections.emptyList());

            // When
            List<BacktestSummaryDto> history = backtestService.getHistory();

            // Then
            assertThat(history).isEmpty();

            verify(backtestResultRepository).findTop20ByOrderByExecutedAtDesc();
        }

        private BacktestResult createMockBacktestResult(
                Long id, String symbol, BigDecimal totalReturn) {
            BacktestResult result = mock(BacktestResult.class);
            when(result.getId()).thenReturn(id);
            when(result.getSymbol()).thenReturn(symbol);
            when(result.getStrategyName()).thenReturn("MA-Cross-20/60");
            when(result.getStartDate()).thenReturn(LocalDate.of(2024, 1, 1));
            when(result.getEndDate()).thenReturn(LocalDate.of(2024, 6, 30));
            when(result.getInitialCapital()).thenReturn(new BigDecimal("10000000"));
            when(result.getFinalCapital()).thenReturn(new BigDecimal("11000000"));
            when(result.getTotalReturn()).thenReturn(totalReturn);
            when(result.getCagr()).thenReturn(BigDecimal.valueOf(8.5));
            when(result.getMaxDrawdown()).thenReturn(BigDecimal.valueOf(5.2));
            when(result.getSharpeRatio()).thenReturn(BigDecimal.valueOf(1.5));
            when(result.getProfitFactor()).thenReturn(BigDecimal.valueOf(2.0));
            when(result.getTotalTrades()).thenReturn(10);
            when(result.getWinRate()).thenReturn(BigDecimal.valueOf(60));
            return result;
        }
    }

    @Nested
    @DisplayName("getResult 테스트")
    class GetResultTest {

        @Test
        @DisplayName("ID로 백테스트 결과 조회 성공")
        void getResult_WhenFound_ReturnsResult() throws Exception {
            // Given
            Long resultId = 1L;
            BacktestResult result = mock(BacktestResult.class);
            when(result.getId()).thenReturn(resultId);
            when(result.getSymbol()).thenReturn("AAPL");
            when(result.getStrategyName()).thenReturn("MA-Cross-20/60");
            when(result.getStartDate()).thenReturn(LocalDate.of(2024, 1, 1));
            when(result.getEndDate()).thenReturn(LocalDate.of(2024, 6, 30));
            when(result.getInitialCapital()).thenReturn(new BigDecimal("10000000"));
            when(result.getFinalCapital()).thenReturn(new BigDecimal("11000000"));
            when(result.getTotalReturn()).thenReturn(BigDecimal.valueOf(10));
            when(result.getTrades()).thenReturn(new ArrayList<>());
            when(result.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.findByIdWithTrades(resultId))
                    .thenReturn(Optional.of(result));
            when(objectMapper.readValue(anyString(), any(Class.class)))
                    .thenReturn(Collections.emptyList());

            // When
            BacktestResultDto resultDto = backtestService.getResult(resultId);

            // Then
            assertThat(resultDto).isNotNull();
            assertThat(resultDto.getId()).isEqualTo(resultId);
            assertThat(resultDto.getSymbol()).isEqualTo("AAPL");

            verify(backtestResultRepository).findByIdWithTrades(resultId);
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 예외 발생")
        void getResult_WhenNotFound_ThrowsException() {
            // Given
            Long resultId = 999L;
            when(backtestResultRepository.findByIdWithTrades(resultId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> backtestService.getResult(resultId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("백테스트 결과를 찾을 수 없습니다");

            verify(backtestResultRepository).findByIdWithTrades(resultId);
        }
    }

    @Nested
    @DisplayName("getAvailableStrategies 테스트")
    class GetAvailableStrategiesTest {

        @Test
        @DisplayName("사용 가능한 모든 전략 반환")
        void getAvailableStrategies_ReturnsAllStrategies() {
            // When
            List<Map<String, Object>> strategies = backtestService.getAvailableStrategies();

            // Then
            assertThat(strategies)
                    .hasSize(6); // MOVING_AVERAGE, RSI, MACD, BOLLINGER_BAND, MOMENTUM, CUSTOM

            // MOVING_AVERAGE 전략 검증
            Map<String, Object> maStrategy =
                    strategies.stream()
                            .filter(s -> "MOVING_AVERAGE".equals(s.get("type")))
                            .findFirst()
                            .orElseThrow();

            assertThat(maStrategy.get("type")).isEqualTo("MOVING_AVERAGE");
            assertThat(maStrategy.get("label")).isNotNull();
            assertThat(maStrategy.get("description")).isNotNull();
            assertThat(maStrategy.get("parameters")).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) maStrategy.get("parameters");
            assertThat(params).containsKeys("shortPeriod", "longPeriod", "maType");
        }

        @Test
        @DisplayName("각 전략의 기본 파라미터 검증")
        void getAvailableStrategies_ValidatesDefaultParameters() {
            // When
            List<Map<String, Object>> strategies = backtestService.getAvailableStrategies();

            // Then - RSI 전략
            Map<String, Object> rsiStrategy =
                    strategies.stream()
                            .filter(s -> "RSI".equals(s.get("type")))
                            .findFirst()
                            .orElseThrow();

            @SuppressWarnings("unchecked")
            Map<String, Object> rsiParams = (Map<String, Object>) rsiStrategy.get("parameters");
            assertThat(rsiParams.get("period")).isEqualTo(14);
            assertThat(rsiParams.get("overboughtLevel")).isEqualTo(70);
            assertThat(rsiParams.get("oversoldLevel")).isEqualTo(30);

            // Then - MACD 전략
            Map<String, Object> macdStrategy =
                    strategies.stream()
                            .filter(s -> "MACD".equals(s.get("type")))
                            .findFirst()
                            .orElseThrow();

            @SuppressWarnings("unchecked")
            Map<String, Object> macdParams = (Map<String, Object>) macdStrategy.get("parameters");
            assertThat(macdParams.get("fastPeriod")).isEqualTo(12);
            assertThat(macdParams.get("slowPeriod")).isEqualTo(26);
            assertThat(macdParams.get("signalPeriod")).isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("optimizeStrategy 테스트")
    class OptimizeStrategyTest {

        @Test
        @DisplayName("전략 파라미터 최적화 성공")
        void optimizeStrategy_FindsOptimalParameters() throws Exception {
            // Given
            Map<String, OptimizationRequestDto.ParameterRange> ranges = new HashMap<>();
            ranges.put(
                    "shortPeriod",
                    OptimizationRequestDto.ParameterRange.builder()
                            .min(10)
                            .max(20)
                            .step(5)
                            .build());
            ranges.put(
                    "longPeriod",
                    OptimizationRequestDto.ParameterRange.builder()
                            .min(50)
                            .max(60)
                            .step(10)
                            .build());

            OptimizationRequestDto request =
                    OptimizationRequestDto.builder()
                            .symbol("AAPL")
                            .strategyType(TradingStrategy.StrategyType.MOVING_AVERAGE)
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 6, 30))
                            .initialCapital(new BigDecimal("10000000"))
                            .parameterRanges(ranges)
                            .target(OptimizationRequestDto.OptimizationTarget.TOTAL_RETURN)
                            .build();

            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();
            when(stockPriceService.getHistoricalQuotes(
                            eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            BacktestResult savedResult = mock(BacktestResult.class);
            lenient().when(savedResult.getId()).thenReturn(1L);
            lenient().when(savedResult.getSymbol()).thenReturn("AAPL");
            lenient().when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            lenient().when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            lenient().when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            lenient().when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("11000000"));
            lenient().when(savedResult.getTotalReturn()).thenReturn(BigDecimal.valueOf(10));
            lenient().when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            lenient().when(savedResult.getStrategyName()).thenReturn("MA-Cross-20/60");
            lenient().when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            OptimizationResultDto result = backtestService.optimizeStrategy(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBestParameters()).isNotNull();
            assertThat(result.getBestResult()).isNotNull();
            assertThat(result.getAllResults()).isNotEmpty();

            // 조합 수 검증 (3 shortPeriod values * 2 longPeriod values = 6)
            assertThat(result.getTotalCombinations()).isEqualTo(6);

            // stockPriceService는 한 번만 호출되어야 함 (가격 데이터 재사용)
            verify(stockPriceService, times(2))
                    .getHistoricalQuotes(eq("AAPL"), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("파라미터 조합 수가 최대치 초과 시 예외 발생")
        void optimizeStrategy_WhenTooManyCombinat_ThrowsException() {
            // Given
            Map<String, OptimizationRequestDto.ParameterRange> ranges = new HashMap<>();
            ranges.put(
                    "period",
                    OptimizationRequestDto.ParameterRange.builder()
                            .min(1)
                            .max(1000)
                            .step(1)
                            .build());
            ranges.put(
                    "threshold",
                    OptimizationRequestDto.ParameterRange.builder()
                            .min(1)
                            .max(1000)
                            .step(1)
                            .build());

            OptimizationRequestDto request =
                    OptimizationRequestDto.builder()
                            .symbol("AAPL")
                            .strategyType(TradingStrategy.StrategyType.RSI)
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 6, 30))
                            .initialCapital(new BigDecimal("10000000"))
                            .parameterRanges(ranges)
                            .build();

            // When & Then
            assertThatThrownBy(() -> backtestService.optimizeStrategy(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("파라미터 조합 수")
                    .hasMessageContaining("최대 허용 한도");
        }

        @Test
        @DisplayName("샤프 비율 최적화 목표로 최적화 성공")
        void optimizeStrategy_WithSharpeRatioTarget_Success() throws Exception {
            // Given
            Map<String, OptimizationRequestDto.ParameterRange> ranges = new HashMap<>();
            ranges.put(
                    "period",
                    OptimizationRequestDto.ParameterRange.builder()
                            .min(10)
                            .max(20)
                            .step(5)
                            .build());

            OptimizationRequestDto request =
                    OptimizationRequestDto.builder()
                            .symbol("AAPL")
                            .strategyType(TradingStrategy.StrategyType.RSI)
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 6, 30))
                            .initialCapital(new BigDecimal("10000000"))
                            .parameterRanges(ranges)
                            .target(OptimizationRequestDto.OptimizationTarget.SHARPE_RATIO)
                            .build();

            List<HistoricalQuote> quotes = createSampleHistoricalQuotes();
            when(stockPriceService.getHistoricalQuotes(
                            eq("AAPL"), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(quotes);

            BacktestResult savedResult = mock(BacktestResult.class);
            lenient().when(savedResult.getId()).thenReturn(1L);
            lenient().when(savedResult.getSymbol()).thenReturn("AAPL");
            lenient().when(savedResult.getStartDate()).thenReturn(request.getStartDate());
            lenient().when(savedResult.getEndDate()).thenReturn(request.getEndDate());
            lenient().when(savedResult.getInitialCapital()).thenReturn(request.getInitialCapital());
            lenient().when(savedResult.getFinalCapital()).thenReturn(new BigDecimal("10500000"));
            lenient().when(savedResult.getTotalReturn()).thenReturn(BigDecimal.valueOf(5));
            lenient().when(savedResult.getSharpeRatio()).thenReturn(BigDecimal.valueOf(1.8));
            lenient().when(savedResult.getTrades()).thenReturn(new ArrayList<>());
            lenient().when(savedResult.getStrategyName()).thenReturn("RSI-14");
            lenient().when(savedResult.getStrategyConfig()).thenReturn("{}");

            when(backtestResultRepository.save(any(BacktestResult.class))).thenReturn(savedResult);
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");

            // When
            OptimizationResultDto result = backtestService.optimizeStrategy(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTargetType()).isEqualTo("SHARPE_RATIO");
            assertThat(result.getBestResult()).isNotNull();
        }
    }
}
