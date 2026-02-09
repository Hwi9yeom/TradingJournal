package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.BenchmarkComparisonDto;
import com.trading.journal.dto.BenchmarkComparisonDto.BenchmarkSummary;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.entity.BenchmarkPrice;
import com.trading.journal.entity.BenchmarkType;
import com.trading.journal.provider.BenchmarkDataProvider;
import com.trading.journal.repository.BenchmarkPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

    @Mock private BenchmarkPriceRepository benchmarkPriceRepository;
    @Mock private AnalysisService analysisService;
    @Mock private BenchmarkDataProvider dataProvider;

    private BenchmarkService benchmarkService;

    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        List<BenchmarkDataProvider> providers = Collections.singletonList(dataProvider);
        benchmarkService =
                new BenchmarkService(benchmarkPriceRepository, analysisService, providers);

        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 3, 31);
    }

    @Nested
    @DisplayName("벤치마크 비교 테스트")
    class CompareToBenchmarkTests {

        @Test
        @DisplayName("벤치마크 데이터가 없을 때 빈 비교 결과 반환")
        void compareToBenchmark_NoBenchmarkData_ReturnsEmptyComparison() {
            when(analysisService.getEquityCurve(any(), any(), any())).thenReturn(null);
            when(benchmarkPriceRepository.findByBenchmarkAndPriceDateBetweenOrderByPriceDateAsc(
                            any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            BenchmarkComparisonDto result =
                    benchmarkService.compareToBenchmark(
                            1L, BenchmarkType.SP500, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getBenchmark()).isEqualTo(BenchmarkType.SP500);
            assertThat(result.getLabels()).isEmpty();
            assertThat(result.getPortfolioTotalReturn()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getBenchmarkTotalReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("포트폴리오와 벤치마크 비교 성공")
        void compareToBenchmark_WithData_ReturnsComparison() {
            // 포트폴리오 데이터 준비
            List<String> dates =
                    Arrays.asList("2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05");
            List<BigDecimal> portfolioReturns =
                    Arrays.asList(
                            new BigDecimal("0.01"),
                            new BigDecimal("0.02"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("0.015"));

            EquityCurveDto equityCurve =
                    EquityCurveDto.builder().labels(dates).dailyReturns(portfolioReturns).build();

            when(analysisService.getEquityCurve(any(), any(), any())).thenReturn(equityCurve);

            // 벤치마크 데이터 준비
            List<BenchmarkPrice> benchmarkPrices = createBenchmarkPrices();
            when(benchmarkPriceRepository.findByBenchmarkAndPriceDateBetweenOrderByPriceDateAsc(
                            any(), any(), any()))
                    .thenReturn(benchmarkPrices);

            BenchmarkComparisonDto result =
                    benchmarkService.compareToBenchmark(
                            1L, BenchmarkType.SP500, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getBenchmark()).isEqualTo(BenchmarkType.SP500);
            assertThat(result.getBenchmarkLabel()).isEqualTo(BenchmarkType.SP500.getLabel());
            assertThat(result.getStartDate()).isEqualTo(startDate);
            assertThat(result.getEndDate()).isEqualTo(endDate);

            // 성과 지표가 계산되었는지 확인
            assertThat(result.getAlpha()).isNotNull();
            assertThat(result.getBeta()).isNotNull();
            assertThat(result.getCorrelation()).isNotNull();
        }

        @Test
        @DisplayName("포트폴리오 데이터 없이 벤치마크 데이터만 있을 때")
        void compareToBenchmark_NoPortfolioData_ReturnsEmptyComparison() {
            when(analysisService.getEquityCurve(any(), any(), any())).thenReturn(null);

            List<BenchmarkPrice> benchmarkPrices = createBenchmarkPrices();
            when(benchmarkPriceRepository.findByBenchmarkAndPriceDateBetweenOrderByPriceDateAsc(
                            any(), any(), any()))
                    .thenReturn(benchmarkPrices);

            BenchmarkComparisonDto result =
                    benchmarkService.compareToBenchmark(
                            1L, BenchmarkType.KOSPI, startDate, endDate);

            assertThat(result).isNotNull();
            // 매칭된 데이터가 없으므로 결과는 거의 비어있음
        }
    }

    @Nested
    @DisplayName("벤치마크 요약 테스트")
    class BenchmarkSummaryTests {

        @Test
        @DisplayName("모든 벤치마크 요약 조회")
        void getBenchmarkSummaries_ReturnsAllBenchmarks() {
            // 일부 벤치마크만 데이터 있음
            BenchmarkPrice sp500Price =
                    BenchmarkPrice.builder()
                            .benchmark(BenchmarkType.SP500)
                            .priceDate(LocalDate.now())
                            .closePrice(new BigDecimal("4500"))
                            .dailyReturn(new BigDecimal("0.5"))
                            .build();

            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(
                            BenchmarkType.SP500))
                    .thenReturn(Optional.of(sp500Price));
            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(
                            BenchmarkType.NASDAQ))
                    .thenReturn(Optional.empty());
            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(
                            BenchmarkType.KOSPI))
                    .thenReturn(Optional.empty());
            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(
                            BenchmarkType.KOSDAQ))
                    .thenReturn(Optional.empty());
            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(
                            BenchmarkType.DOW))
                    .thenReturn(Optional.empty());

            when(benchmarkPriceRepository
                            .findFirstByBenchmarkAndPriceDateGreaterThanEqualOrderByPriceDateAsc(
                                    any(), any()))
                    .thenReturn(Optional.of(sp500Price));
            when(benchmarkPriceRepository
                            .findFirstByBenchmarkAndPriceDateLessThanEqualOrderByPriceDateDesc(
                                    any(), any()))
                    .thenReturn(Optional.of(sp500Price));

            when(benchmarkPriceRepository.countByBenchmark(any())).thenReturn(100L);

            List<BenchmarkSummary> result = benchmarkService.getBenchmarkSummaries();

            assertThat(result).hasSize(BenchmarkType.values().length);

            // SP500은 데이터가 있음
            BenchmarkSummary sp500Summary =
                    result.stream()
                            .filter(s -> s.getBenchmark() == BenchmarkType.SP500)
                            .findFirst()
                            .orElse(null);

            assertThat(sp500Summary).isNotNull();
            assertThat(sp500Summary.getLatestPrice()).isEqualByComparingTo(new BigDecimal("4500"));
            assertThat(sp500Summary.getDailyChange()).isEqualByComparingTo(new BigDecimal("0.5"));
        }

        @Test
        @DisplayName("벤치마크 데이터가 없을 때")
        void getBenchmarkSummaries_NoData_ReturnsEmptySummaries() {
            when(benchmarkPriceRepository.findFirstByBenchmarkOrderByPriceDateDesc(any()))
                    .thenReturn(Optional.empty());

            List<BenchmarkSummary> result = benchmarkService.getBenchmarkSummaries();

            assertThat(result).hasSize(BenchmarkType.values().length);
            result.forEach(
                    summary -> {
                        assertThat(summary.getLatestPrice()).isNull();
                        assertThat(summary.getDataCount()).isEqualTo(0L);
                    });
        }
    }

    @Nested
    @DisplayName("벤치마크 데이터 저장 테스트")
    class SaveBenchmarkDataTests {

        @Test
        @DisplayName("단일 벤치마크 가격 저장")
        void saveBenchmarkPrice_Success() {
            BenchmarkPrice price =
                    BenchmarkPrice.builder()
                            .benchmark(BenchmarkType.SP500)
                            .priceDate(LocalDate.now())
                            .closePrice(new BigDecimal("4500"))
                            .build();

            when(benchmarkPriceRepository.save(any())).thenReturn(price);

            BenchmarkPrice result = benchmarkService.saveBenchmarkPrice(price);

            assertThat(result).isNotNull();
            verify(benchmarkPriceRepository).save(price);
        }

        @Test
        @DisplayName("벤치마크 가격 일괄 저장")
        void saveBenchmarkPrices_Success() {
            List<BenchmarkPrice> prices = createBenchmarkPrices();
            when(benchmarkPriceRepository.saveAll(any())).thenReturn(prices);

            List<BenchmarkPrice> result = benchmarkService.saveBenchmarkPrices(prices);

            assertThat(result).hasSize(prices.size());
            verify(benchmarkPriceRepository).saveAll(prices);
        }
    }

    @Nested
    @DisplayName("벤치마크 데이터 동기화 테스트")
    class SyncBenchmarkDataTests {

        @Test
        @DisplayName("데이터 제공자가 없을 때")
        void syncBenchmarkData_NoProvider_ReturnsFailure() {
            when(dataProvider.supports(BenchmarkType.SP500)).thenReturn(false);

            Map<String, Object> result =
                    benchmarkService.syncBenchmarkData(BenchmarkType.SP500, startDate, endDate);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("fetchedCount")).isEqualTo(0);
        }

        @Test
        @DisplayName("데이터 동기화 성공")
        void syncBenchmarkData_Success() {
            List<BenchmarkPrice> prices = createBenchmarkPrices();

            when(dataProvider.supports(BenchmarkType.SP500)).thenReturn(true);
            when(dataProvider.getProviderName()).thenReturn("TestProvider");
            when(dataProvider.fetchPrices(any(), any(), any())).thenReturn(prices);
            when(benchmarkPriceRepository.findByBenchmarkAndPriceDate(any(), any()))
                    .thenReturn(Optional.empty());
            when(benchmarkPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result =
                    benchmarkService.syncBenchmarkData(BenchmarkType.SP500, startDate, endDate);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("fetchedCount")).isEqualTo(prices.size());
            verify(benchmarkPriceRepository, times(prices.size())).save(any());
        }

        @Test
        @DisplayName("데이터 동기화 시 기존 데이터 업데이트")
        void syncBenchmarkData_UpdatesExisting() {
            List<BenchmarkPrice> prices = createBenchmarkPrices();
            BenchmarkPrice existingPrice = prices.get(0);

            when(dataProvider.supports(BenchmarkType.SP500)).thenReturn(true);
            when(dataProvider.getProviderName()).thenReturn("TestProvider");
            when(dataProvider.fetchPrices(any(), any(), any())).thenReturn(prices);
            when(benchmarkPriceRepository.findByBenchmarkAndPriceDate(any(), any()))
                    .thenReturn(Optional.of(existingPrice));
            when(benchmarkPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result =
                    benchmarkService.syncBenchmarkData(BenchmarkType.SP500, startDate, endDate);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("newCount")).isEqualTo(0); // 모두 업데이트
        }

        @Test
        @DisplayName("동기화 중 예외 발생")
        void syncBenchmarkData_ThrowsException() {
            when(dataProvider.supports(BenchmarkType.SP500)).thenReturn(true);
            when(dataProvider.fetchPrices(any(), any(), any()))
                    .thenThrow(new RuntimeException("API Error"));

            Map<String, Object> result =
                    benchmarkService.syncBenchmarkData(BenchmarkType.SP500, startDate, endDate);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("오류");
        }
    }

    @Nested
    @DisplayName("모든 벤치마크 동기화 테스트")
    class SyncAllBenchmarksTests {

        @Test
        @DisplayName("모든 벤치마크 동기화")
        void syncAllBenchmarks_Success() {
            when(dataProvider.supports(any())).thenReturn(false);

            List<Map<String, Object>> results = benchmarkService.syncAllBenchmarks();

            assertThat(results).hasSize(BenchmarkType.values().length);
        }
    }

    @Nested
    @DisplayName("샘플 데이터 생성 테스트")
    class GenerateSampleDataTests {

        @Test
        @DisplayName("샘플 벤치마크 데이터 생성")
        void generateSampleBenchmarkData_Success() {
            when(benchmarkPriceRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            benchmarkService.generateSampleBenchmarkData(BenchmarkType.SP500, startDate, endDate);

            verify(benchmarkPriceRepository).saveAll(any());
        }
    }

    // Helper methods
    private List<BenchmarkPrice> createBenchmarkPrices() {
        return Arrays.asList(
                BenchmarkPrice.builder()
                        .benchmark(BenchmarkType.SP500)
                        .priceDate(LocalDate.of(2024, 1, 2))
                        .openPrice(new BigDecimal("4480"))
                        .closePrice(new BigDecimal("4500"))
                        .dailyReturn(new BigDecimal("0.0045"))
                        .build(),
                BenchmarkPrice.builder()
                        .benchmark(BenchmarkType.SP500)
                        .priceDate(LocalDate.of(2024, 1, 3))
                        .openPrice(new BigDecimal("4500"))
                        .closePrice(new BigDecimal("4520"))
                        .dailyReturn(new BigDecimal("0.0044"))
                        .build(),
                BenchmarkPrice.builder()
                        .benchmark(BenchmarkType.SP500)
                        .priceDate(LocalDate.of(2024, 1, 4))
                        .openPrice(new BigDecimal("4520"))
                        .closePrice(new BigDecimal("4510"))
                        .dailyReturn(new BigDecimal("-0.0022"))
                        .build(),
                BenchmarkPrice.builder()
                        .benchmark(BenchmarkType.SP500)
                        .priceDate(LocalDate.of(2024, 1, 5))
                        .openPrice(new BigDecimal("4510"))
                        .closePrice(new BigDecimal("4530"))
                        .dailyReturn(new BigDecimal("0.0044"))
                        .build());
    }
}
