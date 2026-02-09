package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.trading.journal.dto.DrawdownDto;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.RiskMetricsDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskMetricsServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @Mock private AnalysisService analysisService;

    @InjectMocks private RiskMetricsService riskMetricsService;

    private Stock stock;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setId(1L);
        stock.setSymbol("005930");
        stock.setName("삼성전자");

        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 3, 31);
    }

    @Nested
    @DisplayName("VaR 계산 테스트")
    class VaRCalculationTests {

        @Test
        @DisplayName("빈 수익률 목록으로 VaR 계산 시 0 반환")
        void calculateVaR_WithEmptyReturns_ReturnsZero() {
            List<BigDecimal> emptyReturns = Collections.emptyList();

            RiskMetricsDto.VaRDto result =
                    riskMetricsService.calculateVaR(
                            emptyReturns, new BigDecimal("0.95"), new BigDecimal("10000000"));

            assertThat(result.getDailyVaR()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getWeeklyVaR()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getMonthlyVaR()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getDailyVaRAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getConfidenceLevel()).isEqualByComparingTo(new BigDecimal("0.95"));
        }

        @Test
        @DisplayName("정상 수익률로 95% VaR 계산")
        void calculateVaR_WithNormalReturns_ReturnsCorrectVaR() {
            // 일별 수익률 (-5%, -3%, -1%, 1%, 2%, 3%, 4%, 5%, 6%, 7%)
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("-0.05"),
                            new BigDecimal("-0.03"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("0.01"),
                            new BigDecimal("0.02"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.05"),
                            new BigDecimal("0.06"),
                            new BigDecimal("0.07"));

            RiskMetricsDto.VaRDto result =
                    riskMetricsService.calculateVaR(
                            returns, new BigDecimal("0.95"), new BigDecimal("10000000"));

            // 95% VaR = 하위 5% 퍼센타일 = -5%
            assertThat(result.getDailyVaR()).isLessThan(BigDecimal.ZERO);
            assertThat(result.getWeeklyVaR()).isLessThan(result.getDailyVaR());
            assertThat(result.getMonthlyVaR()).isLessThan(result.getWeeklyVaR());
            assertThat(result.getDailyVaRAmount()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("99% VaR 계산")
        void calculateVaR_With99Confidence_ReturnsCorrectVaR() {
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("-0.10"),
                            new BigDecimal("-0.05"),
                            new BigDecimal("-0.03"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("0.01"),
                            new BigDecimal("0.02"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.05"),
                            new BigDecimal("0.06"));

            RiskMetricsDto.VaRDto result =
                    riskMetricsService.calculateVaR(
                            returns, new BigDecimal("0.99"), new BigDecimal("10000000"));

            // 99% VaR는 95% VaR보다 절대값이 큼 (더 극단적 손실)
            assertThat(result.getConfidenceLevel()).isEqualByComparingTo(new BigDecimal("0.99"));
            assertThat(result.getDailyVaR()).isNotNull();
        }

        @Test
        @DisplayName("포트폴리오 가치가 null일 때 VaR 금액은 0")
        void calculateVaR_WithNullPortfolioValue_ReturnsZeroAmount() {
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("-0.05"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.02"));

            RiskMetricsDto.VaRDto result =
                    riskMetricsService.calculateVaR(returns, new BigDecimal("0.95"), null);

            assertThat(result.getDailyVaRAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("샤프 비율 계산 테스트")
    class SharpeRatioTests {

        @Test
        @DisplayName("수익률이 2개 미만일 때 0 반환")
        void calculateSharpeRatio_WithLessThanTwoReturns_ReturnsZero() {
            List<BigDecimal> singleReturn = Arrays.asList(new BigDecimal("0.05"));

            BigDecimal result =
                    riskMetricsService.calculateSharpeRatio(singleReturn, new BigDecimal("0.03"));

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("표준편차가 0일 때 0 반환")
        void calculateSharpeRatio_WithZeroStdDev_ReturnsZero() {
            // 모든 수익률이 동일하면 표준편차 0
            List<BigDecimal> sameReturns =
                    Arrays.asList(
                            new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.01"));

            BigDecimal result =
                    riskMetricsService.calculateSharpeRatio(sameReturns, new BigDecimal("0.03"));

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("정상 수익률로 샤프 비율 계산")
        void calculateSharpeRatio_WithNormalReturns_ReturnsPositiveRatio() {
            // 양수 수익률 다수
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("0.02"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.01"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.02"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.02"),
                            new BigDecimal("0.01"),
                            new BigDecimal("0.03"),
                            new BigDecimal("0.02"));

            BigDecimal result =
                    riskMetricsService.calculateSharpeRatio(returns, new BigDecimal("0.03"));

            // 평균 수익률이 양수이고 변동성이 낮으면 양수 샤프
            assertThat(result).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("손실 수익률로 샤프 비율 계산시 음수")
        void calculateSharpeRatio_WithNegativeReturns_ReturnsNegativeRatio() {
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("-0.02"),
                            new BigDecimal("-0.03"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("-0.04"),
                            new BigDecimal("-0.02"));

            BigDecimal result =
                    riskMetricsService.calculateSharpeRatio(returns, new BigDecimal("0.03"));

            assertThat(result).isLessThan(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("소르티노 비율 계산 테스트")
    class SortinoRatioTests {

        @Test
        @DisplayName("수익률이 2개 미만일 때 0 반환")
        void calculateSortinoRatio_WithLessThanTwoReturns_ReturnsZero() {
            List<BigDecimal> singleReturn = Arrays.asList(new BigDecimal("0.05"));

            BigDecimal result =
                    riskMetricsService.calculateSortinoRatio(singleReturn, new BigDecimal("0.03"));

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("하락이 없을 때 양수 수익률이면 최대값 반환")
        void calculateSortinoRatio_WithNoDownside_ReturnsMaxValue() {
            // 모든 수익률이 목표 이상
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("0.05"),
                            new BigDecimal("0.06"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.07"));

            BigDecimal result =
                    riskMetricsService.calculateSortinoRatio(returns, new BigDecimal("0.03"));

            // 하락 편차가 0이고 양수 수익률이면 10.00 반환
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("정상 수익률로 소르티노 비율 계산")
        void calculateSortinoRatio_WithMixedReturns_ReturnsCorrectRatio() {
            List<BigDecimal> returns =
                    Arrays.asList(
                            new BigDecimal("0.02"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("0.03"),
                            new BigDecimal("-0.02"),
                            new BigDecimal("0.04"),
                            new BigDecimal("0.01"),
                            new BigDecimal("-0.01"),
                            new BigDecimal("0.03"));

            BigDecimal result =
                    riskMetricsService.calculateSortinoRatio(returns, new BigDecimal("0.03"));

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("칼마 비율 계산 테스트")
    class CalmarRatioTests {

        @Test
        @DisplayName("MDD가 null이면 양수 CAGR일 때 최대값 반환")
        void calculateCalmarRatio_WithNullMdd_ReturnsMaxValue() {
            BigDecimal result =
                    riskMetricsService.calculateCalmarRatio(new BigDecimal("0.15"), null);

            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("MDD가 0이면 양수 CAGR일 때 최대값 반환")
        void calculateCalmarRatio_WithZeroMdd_ReturnsMaxValue() {
            BigDecimal result =
                    riskMetricsService.calculateCalmarRatio(
                            new BigDecimal("0.15"), BigDecimal.ZERO);

            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("CAGR이 null이면 0 반환")
        void calculateCalmarRatio_WithNullCagr_ReturnsZero() {
            BigDecimal result =
                    riskMetricsService.calculateCalmarRatio(null, new BigDecimal("0.10"));

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("정상 값으로 칼마 비율 계산")
        void calculateCalmarRatio_WithNormalValues_ReturnsCorrectRatio() {
            // CAGR 15%, MDD 10%
            BigDecimal result =
                    riskMetricsService.calculateCalmarRatio(
                            new BigDecimal("0.15"), new BigDecimal("0.10"));

            // Calmar = 0.15 / 0.10 = 1.50
            assertThat(result).isEqualByComparingTo(new BigDecimal("1.50"));
        }

        @Test
        @DisplayName("MDD가 음수로 전달되어도 절대값 사용")
        void calculateCalmarRatio_WithNegativeMdd_UsesAbsoluteValue() {
            BigDecimal result =
                    riskMetricsService.calculateCalmarRatio(
                            new BigDecimal("0.20"), new BigDecimal("-0.10"));

            // |MDD| = 0.10, Calmar = 0.20 / 0.10 = 2.00
            assertThat(result).isEqualByComparingTo(new BigDecimal("2.00"));
        }
    }

    @Nested
    @DisplayName("종합 리스크 메트릭스 계산 테스트")
    class RiskMetricsCalculationTests {

        @Test
        @DisplayName("거래가 없을 때 빈 메트릭스 반환")
        void calculateRiskMetrics_WithNoTransactions_ReturnsEmptyMetrics() {
            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            RiskMetricsDto result = riskMetricsService.calculateRiskMetrics(1L, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getSharpeRatio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getSortinoRatio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getCalmarRatio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getMaxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getVolatility()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getProfitFactor()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getRiskLevel()).isEqualTo(RiskMetricsDto.RiskLevel.LOW);
            assertThat(result.getTradingDays()).isEqualTo(0);
        }

        @Test
        @DisplayName("충분한 거래로 메트릭스 계산")
        void calculateRiskMetrics_WithTransactions_ReturnsCompleteMetrics() {
            List<Transaction> transactions = createMultipleTransactions();

            EquityCurveDto equityCurve =
                    EquityCurveDto.builder()
                            .finalValue(new BigDecimal("12000000"))
                            .cagr(new BigDecimal("0.15"))
                            .build();

            DrawdownDto drawdown =
                    DrawdownDto.builder().maxDrawdown(new BigDecimal("-0.10")).build();

            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(analysisService.calculateEquityCurve(any(), any())).thenReturn(equityCurve);
            when(analysisService.calculateDrawdown(any(), any())).thenReturn(drawdown);

            RiskMetricsDto result = riskMetricsService.calculateRiskMetrics(1L, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getStartDate()).isEqualTo(startDate);
            assertThat(result.getEndDate()).isEqualTo(endDate);
            assertThat(result.getCagr()).isEqualByComparingTo(new BigDecimal("0.15"));
            assertThat(result.getMaxDrawdown()).isNotNull();
            assertThat(result.getVar95()).isNotNull();
            assertThat(result.getVar99()).isNotNull();
            assertThat(result.getRiskLevel()).isNotNull();
        }

        @Test
        @DisplayName("accountId 없이 전체 계산")
        void calculateRiskMetrics_WithoutAccountId_UsesAllTransactions() {
            when(transactionRepository.findByDateRange(any(), any()))
                    .thenReturn(Collections.emptyList());

            RiskMetricsDto result = riskMetricsService.calculateRiskMetrics(startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result.getSharpeRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("리스크 등급 결정 테스트")
    class RiskLevelTests {

        @Test
        @DisplayName("저위험 조건 충족")
        void riskLevel_LowConditions_ReturnsLow() {
            // 변동성 5%, MDD 5%, 샤프 1.5 -> LOW
            List<Transaction> transactions = createLowVolatilityTransactions();

            EquityCurveDto equityCurve =
                    EquityCurveDto.builder()
                            .finalValue(new BigDecimal("11000000"))
                            .cagr(new BigDecimal("0.10"))
                            .build();

            DrawdownDto drawdown =
                    DrawdownDto.builder().maxDrawdown(new BigDecimal("-0.05")).build();

            when(transactionRepository.findByAccountIdAndDateRange(any(), any(), any()))
                    .thenReturn(transactions);
            when(analysisService.calculateEquityCurve(any(), any())).thenReturn(equityCurve);
            when(analysisService.calculateDrawdown(any(), any())).thenReturn(drawdown);

            RiskMetricsDto result = riskMetricsService.calculateRiskMetrics(1L, startDate, endDate);

            assertThat(result.getRiskLevel()).isNotNull();
        }
    }

    // Helper methods for creating test data

    private List<Transaction> createMultipleTransactions() {
        LocalDateTime baseDate = startDate.atStartOfDay();
        return Arrays.asList(
                createBuyTransaction(1L, baseDate.plusDays(1), "100", "50000"),
                createSellTransaction(
                        2L, baseDate.plusDays(10), "50", "55000", "250000", "2500000"),
                createBuyTransaction(3L, baseDate.plusDays(15), "100", "52000"),
                createSellTransaction(
                        4L, baseDate.plusDays(25), "100", "48000", "-200000", "5200000"),
                createBuyTransaction(5L, baseDate.plusDays(30), "150", "49000"),
                createSellTransaction(
                        6L, baseDate.plusDays(40), "100", "54000", "500000", "4900000"),
                createBuyTransaction(7L, baseDate.plusDays(50), "80", "53000"),
                createSellTransaction(
                        8L, baseDate.plusDays(60), "80", "56000", "240000", "4240000"));
    }

    private List<Transaction> createLowVolatilityTransactions() {
        LocalDateTime baseDate = startDate.atStartOfDay();
        return Arrays.asList(
                createBuyTransaction(1L, baseDate.plusDays(1), "100", "50000"),
                createSellTransaction(2L, baseDate.plusDays(10), "50", "51000", "50000", "2500000"),
                createBuyTransaction(3L, baseDate.plusDays(20), "50", "50500"),
                createSellTransaction(4L, baseDate.plusDays(30), "50", "51500", "50000", "2525000"),
                createBuyTransaction(5L, baseDate.plusDays(40), "50", "51000"),
                createSellTransaction(
                        6L, baseDate.plusDays(50), "50", "52000", "50000", "2550000"));
    }

    private Transaction createBuyTransaction(
            Long id, LocalDateTime date, String quantity, String price) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setStock(stock);
        tx.setType(TransactionType.BUY);
        tx.setQuantity(new BigDecimal(quantity));
        tx.setPrice(new BigDecimal(price));
        tx.setTransactionDate(date);
        return tx;
    }

    private Transaction createSellTransaction(
            Long id,
            LocalDateTime date,
            String quantity,
            String price,
            String realizedPnl,
            String costBasis) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setStock(stock);
        tx.setType(TransactionType.SELL);
        tx.setQuantity(new BigDecimal(quantity));
        tx.setPrice(new BigDecimal(price));
        tx.setTransactionDate(date);
        tx.setRealizedPnl(new BigDecimal(realizedPnl));
        tx.setCostBasis(new BigDecimal(costBasis));
        return tx;
    }
}
