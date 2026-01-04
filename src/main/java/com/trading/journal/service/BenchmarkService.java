package com.trading.journal.service;

import com.trading.journal.dto.BenchmarkComparisonDto;
import com.trading.journal.dto.BenchmarkComparisonDto.*;
import com.trading.journal.entity.BenchmarkPrice;
import com.trading.journal.entity.BenchmarkType;
import com.trading.journal.repository.BenchmarkPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BenchmarkService {

    private final BenchmarkPriceRepository benchmarkPriceRepository;
    private final AnalysisService analysisService;

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.03"); // 3% 연간 무위험 이자율

    /**
     * 포트폴리오와 벤치마크 비교 분석
     */
    @Cacheable(value = "benchmarkComparison", key = "#accountId + '_' + #benchmark + '_' + #startDate + '_' + #endDate")
    public BenchmarkComparisonDto compareToBenchmark(Long accountId, BenchmarkType benchmark,
                                                      LocalDate startDate, LocalDate endDate) {
        log.info("Comparing portfolio {} to benchmark {} from {} to {}", accountId, benchmark, startDate, endDate);

        // 포트폴리오 일간 수익률 가져오기
        List<BigDecimal> portfolioDailyReturns = getPortfolioDailyReturns(accountId, startDate, endDate);
        List<LocalDate> portfolioDates = getPortfolioDates(accountId, startDate, endDate);

        // 벤치마크 가격 데이터 가져오기
        List<BenchmarkPrice> benchmarkPrices = benchmarkPriceRepository
                .findByBenchmarkAndPriceDateBetweenOrderByPriceDateAsc(benchmark, startDate, endDate);

        if (benchmarkPrices.isEmpty()) {
            return buildEmptyComparison(benchmark, startDate, endDate);
        }

        // 날짜 매칭 - 포트폴리오와 벤치마크 모두 있는 날짜만 사용
        Map<LocalDate, BigDecimal> benchmarkReturnMap = new HashMap<>();
        Map<LocalDate, BigDecimal> benchmarkPriceMap = new HashMap<>();
        for (BenchmarkPrice bp : benchmarkPrices) {
            if (bp.getDailyReturn() != null) {
                benchmarkReturnMap.put(bp.getPriceDate(), bp.getDailyReturn());
            }
            benchmarkPriceMap.put(bp.getPriceDate(), bp.getClosePrice());
        }

        // 누적 수익률 계산
        List<String> labels = new ArrayList<>();
        List<BigDecimal> portfolioCumulativeReturns = new ArrayList<>();
        List<BigDecimal> benchmarkCumulativeReturns = new ArrayList<>();
        List<BigDecimal> excessReturns = new ArrayList<>();

        List<BigDecimal> matchedPortfolioReturns = new ArrayList<>();
        List<BigDecimal> matchedBenchmarkReturns = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        BigDecimal portfolioCumulative = BigDecimal.ZERO;
        BigDecimal benchmarkCumulative = BigDecimal.ZERO;

        for (int i = 0; i < portfolioDates.size() && i < portfolioDailyReturns.size(); i++) {
            LocalDate date = portfolioDates.get(i);
            BigDecimal portfolioReturn = portfolioDailyReturns.get(i);
            BigDecimal benchmarkReturn = benchmarkReturnMap.get(date);

            if (benchmarkReturn != null) {
                labels.add(date.format(formatter));

                // 누적 수익률 계산 (복리)
                portfolioCumulative = portfolioCumulative.add(portfolioReturn);
                benchmarkCumulative = benchmarkCumulative.add(benchmarkReturn);

                portfolioCumulativeReturns.add(portfolioCumulative);
                benchmarkCumulativeReturns.add(benchmarkCumulative);
                excessReturns.add(portfolioCumulative.subtract(benchmarkCumulative));

                matchedPortfolioReturns.add(portfolioReturn);
                matchedBenchmarkReturns.add(benchmarkReturn);
            }
        }

        // 성과 지표 계산
        BigDecimal portfolioTotalReturn = portfolioCumulative;
        BigDecimal benchmarkTotalReturn = benchmarkCumulative;
        BigDecimal excessReturn = portfolioTotalReturn.subtract(benchmarkTotalReturn);

        BigDecimal beta = calculateBeta(matchedPortfolioReturns, matchedBenchmarkReturns);
        BigDecimal alpha = calculateAlpha(matchedPortfolioReturns, matchedBenchmarkReturns, beta);
        BigDecimal correlation = calculateCorrelation(matchedPortfolioReturns, matchedBenchmarkReturns);
        BigDecimal rSquared = correlation.pow(2);
        BigDecimal trackingError = calculateTrackingError(matchedPortfolioReturns, matchedBenchmarkReturns);
        BigDecimal informationRatio = calculateInformationRatio(excessReturn, trackingError);
        BigDecimal treynorRatio = calculateTreynorRatio(portfolioTotalReturn, beta);

        // 변동성 계산
        BigDecimal portfolioVolatility = calculateVolatility(matchedPortfolioReturns);
        BigDecimal benchmarkVolatility = calculateVolatility(matchedBenchmarkReturns);

        // 샤프비율 계산
        BigDecimal portfolioSharpe = calculateSharpe(portfolioTotalReturn, portfolioVolatility, matchedPortfolioReturns.size());
        BigDecimal benchmarkSharpe = calculateSharpe(benchmarkTotalReturn, benchmarkVolatility, matchedBenchmarkReturns.size());

        // 최대 낙폭 계산
        BigDecimal portfolioMaxDrawdown = calculateMaxDrawdown(portfolioCumulativeReturns);
        BigDecimal benchmarkMaxDrawdown = calculateMaxDrawdown(benchmarkCumulativeReturns);

        // 월별 비교
        List<MonthlyComparison> monthlyComparisons = calculateMonthlyComparisons(
                portfolioDates, portfolioDailyReturns, benchmarkReturnMap);

        int portfolioWinMonths = (int) monthlyComparisons.stream()
                .filter(m -> Boolean.TRUE.equals(m.getPortfolioWin()))
                .count();
        int benchmarkWinMonths = monthlyComparisons.size() - portfolioWinMonths;

        return BenchmarkComparisonDto.builder()
                .benchmark(benchmark)
                .benchmarkLabel(benchmark.getLabel())
                .startDate(startDate)
                .endDate(endDate)
                .labels(labels)
                .portfolioReturns(portfolioCumulativeReturns)
                .benchmarkReturns(benchmarkCumulativeReturns)
                .excessReturns(excessReturns)
                .portfolioTotalReturn(portfolioTotalReturn.setScale(2, RoundingMode.HALF_UP))
                .benchmarkTotalReturn(benchmarkTotalReturn.setScale(2, RoundingMode.HALF_UP))
                .excessReturn(excessReturn.setScale(2, RoundingMode.HALF_UP))
                .alpha(alpha.setScale(4, RoundingMode.HALF_UP))
                .beta(beta.setScale(4, RoundingMode.HALF_UP))
                .correlation(correlation.setScale(4, RoundingMode.HALF_UP))
                .rSquared(rSquared.setScale(4, RoundingMode.HALF_UP))
                .informationRatio(informationRatio.setScale(4, RoundingMode.HALF_UP))
                .trackingError(trackingError.setScale(4, RoundingMode.HALF_UP))
                .treynorRatio(treynorRatio.setScale(4, RoundingMode.HALF_UP))
                .portfolioSharpe(portfolioSharpe.setScale(4, RoundingMode.HALF_UP))
                .benchmarkSharpe(benchmarkSharpe.setScale(4, RoundingMode.HALF_UP))
                .portfolioVolatility(portfolioVolatility.setScale(4, RoundingMode.HALF_UP))
                .benchmarkVolatility(benchmarkVolatility.setScale(4, RoundingMode.HALF_UP))
                .portfolioMaxDrawdown(portfolioMaxDrawdown.setScale(2, RoundingMode.HALF_UP))
                .benchmarkMaxDrawdown(benchmarkMaxDrawdown.setScale(2, RoundingMode.HALF_UP))
                .monthlyComparisons(monthlyComparisons)
                .portfolioWinMonths(portfolioWinMonths)
                .benchmarkWinMonths(benchmarkWinMonths)
                .build();
    }

    /**
     * 모든 벤치마크 요약 정보
     */
    public List<BenchmarkSummary> getBenchmarkSummaries() {
        List<BenchmarkSummary> summaries = new ArrayList<>();

        for (BenchmarkType type : BenchmarkType.values()) {
            Optional<BenchmarkPrice> latestOpt = benchmarkPriceRepository
                    .findFirstByBenchmarkOrderByPriceDateDesc(type);

            if (latestOpt.isPresent()) {
                BenchmarkPrice latest = latestOpt.get();

                // YTD 수익률 계산
                LocalDate yearStart = LocalDate.of(latest.getPriceDate().getYear(), 1, 1);
                BigDecimal ytdReturn = calculatePeriodReturn(type, yearStart, latest.getPriceDate());

                summaries.add(BenchmarkSummary.builder()
                        .benchmark(type)
                        .label(type.getLabel())
                        .symbol(type.getSymbol())
                        .description(type.getDescription())
                        .latestDate(latest.getPriceDate())
                        .latestPrice(latest.getClosePrice())
                        .dailyChange(latest.getDailyReturn())
                        .ytdReturn(ytdReturn)
                        .dataCount(benchmarkPriceRepository.countByBenchmark(type))
                        .build());
            } else {
                summaries.add(BenchmarkSummary.builder()
                        .benchmark(type)
                        .label(type.getLabel())
                        .symbol(type.getSymbol())
                        .description(type.getDescription())
                        .dataCount(0L)
                        .build());
            }
        }

        return summaries;
    }

    /**
     * 벤치마크 가격 데이터 저장 (동기화용)
     */
    @Transactional
    public BenchmarkPrice saveBenchmarkPrice(BenchmarkPrice price) {
        return benchmarkPriceRepository.save(price);
    }

    /**
     * 벤치마크 가격 데이터 일괄 저장
     */
    @Transactional
    public List<BenchmarkPrice> saveBenchmarkPrices(List<BenchmarkPrice> prices) {
        return benchmarkPriceRepository.saveAll(prices);
    }

    /**
     * 샘플 벤치마크 데이터 생성 (테스트/데모용)
     */
    @Transactional
    public void generateSampleBenchmarkData(BenchmarkType benchmark, LocalDate startDate, LocalDate endDate) {
        log.info("Generating sample benchmark data for {} from {} to {}", benchmark, startDate, endDate);

        List<BenchmarkPrice> prices = new ArrayList<>();
        Random random = new Random(benchmark.ordinal()); // 일관된 시드

        BigDecimal basePrice = switch (benchmark) {
            case SP500 -> new BigDecimal("4500");
            case NASDAQ -> new BigDecimal("14000");
            case KOSPI -> new BigDecimal("2500");
            case KOSDAQ -> new BigDecimal("800");
            case DOW -> new BigDecimal("35000");
        };

        BigDecimal currentPrice = basePrice;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // 주말 제외
            if (currentDate.getDayOfWeek().getValue() <= 5) {
                // 일간 수익률: -3% ~ +3% (정규분포 유사)
                double dailyReturn = (random.nextGaussian() * 1.5);
                BigDecimal dailyReturnBd = BigDecimal.valueOf(dailyReturn);

                BigDecimal change = currentPrice.multiply(dailyReturnBd.divide(BigDecimal.valueOf(100), MC));
                BigDecimal openPrice = currentPrice;
                currentPrice = currentPrice.add(change);

                BigDecimal high = currentPrice.max(openPrice).multiply(
                        BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble() * 0.01)));
                BigDecimal low = currentPrice.min(openPrice).multiply(
                        BigDecimal.ONE.subtract(BigDecimal.valueOf(random.nextDouble() * 0.01)));

                prices.add(BenchmarkPrice.builder()
                        .benchmark(benchmark)
                        .priceDate(currentDate)
                        .openPrice(openPrice.setScale(2, RoundingMode.HALF_UP))
                        .highPrice(high.setScale(2, RoundingMode.HALF_UP))
                        .lowPrice(low.setScale(2, RoundingMode.HALF_UP))
                        .closePrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
                        .dailyReturn(dailyReturnBd.setScale(4, RoundingMode.HALF_UP))
                        .volume((long) (random.nextDouble() * 100000000) + 50000000)
                        .build());
            }
            currentDate = currentDate.plusDays(1);
        }

        benchmarkPriceRepository.saveAll(prices);
        log.info("Generated {} sample benchmark prices for {}", prices.size(), benchmark);
    }

    // === Private Helper Methods ===

    private List<BigDecimal> getPortfolioDailyReturns(Long accountId, LocalDate startDate, LocalDate endDate) {
        // AnalysisService를 통해 포트폴리오 일간 수익률 가져오기
        try {
            var equityCurve = analysisService.getEquityCurve(accountId, startDate, endDate);
            if (equityCurve != null && equityCurve.getDailyReturns() != null) {
                return equityCurve.getDailyReturns();
            }
        } catch (Exception e) {
            log.warn("Failed to get portfolio daily returns: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<LocalDate> getPortfolioDates(Long accountId, LocalDate startDate, LocalDate endDate) {
        try {
            var equityCurve = analysisService.getEquityCurve(accountId, startDate, endDate);
            if (equityCurve != null && equityCurve.getLabels() != null) {
                return equityCurve.getLabels().stream()
                        .map(LocalDate::parse)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to get portfolio dates: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private BigDecimal calculateBeta(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        if (portfolioReturns.size() < 2 || benchmarkReturns.size() < 2) {
            return BigDecimal.ONE;
        }

        BigDecimal covariance = calculateCovariance(portfolioReturns, benchmarkReturns);
        BigDecimal variance = calculateVariance(benchmarkReturns);

        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        return covariance.divide(variance, MC);
    }

    private BigDecimal calculateAlpha(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns,
                                       BigDecimal beta) {
        if (portfolioReturns.isEmpty() || benchmarkReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal portfolioMean = calculateMean(portfolioReturns);
        BigDecimal benchmarkMean = calculateMean(benchmarkReturns);

        // Alpha = Rp - (Rf + Beta * (Rm - Rf))
        // 단순화: Alpha = Rp - Beta * Rm
        return portfolioMean.subtract(beta.multiply(benchmarkMean));
    }

    private BigDecimal calculateCorrelation(List<BigDecimal> list1, List<BigDecimal> list2) {
        if (list1.size() < 2 || list2.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal covariance = calculateCovariance(list1, list2);
        BigDecimal std1 = calculateStdDev(list1);
        BigDecimal std2 = calculateStdDev(list2);

        if (std1.compareTo(BigDecimal.ZERO) == 0 || std2.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return covariance.divide(std1.multiply(std2), MC);
    }

    private BigDecimal calculateTrackingError(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        if (portfolioReturns.size() != benchmarkReturns.size() || portfolioReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> excessReturns = new ArrayList<>();
        for (int i = 0; i < portfolioReturns.size(); i++) {
            excessReturns.add(portfolioReturns.get(i).subtract(benchmarkReturns.get(i)));
        }

        return calculateStdDev(excessReturns);
    }

    private BigDecimal calculateInformationRatio(BigDecimal excessReturn, BigDecimal trackingError) {
        if (trackingError.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return excessReturn.divide(trackingError, MC);
    }

    private BigDecimal calculateTreynorRatio(BigDecimal portfolioReturn, BigDecimal beta) {
        if (beta.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Treynor = (Rp - Rf) / Beta
        BigDecimal excessReturn = portfolioReturn.subtract(RISK_FREE_RATE.multiply(portfolioReturn.abs()));
        return excessReturn.divide(beta, MC);
    }

    private BigDecimal calculateVolatility(List<BigDecimal> returns) {
        return calculateStdDev(returns).multiply(BigDecimal.valueOf(Math.sqrt(252))); // 연환산
    }

    private BigDecimal calculateSharpe(BigDecimal totalReturn, BigDecimal volatility, int days) {
        if (volatility.compareTo(BigDecimal.ZERO) == 0 || days == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal annualizedReturn = totalReturn.multiply(BigDecimal.valueOf(252.0 / days));
        return annualizedReturn.subtract(RISK_FREE_RATE).divide(volatility, MC);
    }

    private BigDecimal calculateMaxDrawdown(List<BigDecimal> cumulativeReturns) {
        if (cumulativeReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = cumulativeReturns.get(0);

        for (BigDecimal value : cumulativeReturns) {
            if (value.compareTo(peak) > 0) {
                peak = value;
            }
            BigDecimal drawdown = peak.subtract(value);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    private BigDecimal calculatePeriodReturn(BenchmarkType benchmark, LocalDate startDate, LocalDate endDate) {
        Optional<BenchmarkPrice> startPriceOpt = benchmarkPriceRepository
                .findFirstByBenchmarkAndPriceDateGreaterThanEqualOrderByPriceDateAsc(benchmark, startDate);
        Optional<BenchmarkPrice> endPriceOpt = benchmarkPriceRepository
                .findFirstByBenchmarkAndPriceDateLessThanEqualOrderByPriceDateDesc(benchmark, endDate);

        if (startPriceOpt.isEmpty() || endPriceOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal startPrice = startPriceOpt.get().getClosePrice();
        BigDecimal endPrice = endPriceOpt.get().getClosePrice();

        if (startPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return endPrice.subtract(startPrice)
                .divide(startPrice, MC)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<MonthlyComparison> calculateMonthlyComparisons(
            List<LocalDate> dates, List<BigDecimal> portfolioReturns, Map<LocalDate, BigDecimal> benchmarkReturnMap) {

        Map<YearMonth, List<BigDecimal>> portfolioByMonth = new LinkedHashMap<>();
        Map<YearMonth, List<BigDecimal>> benchmarkByMonth = new LinkedHashMap<>();

        for (int i = 0; i < dates.size() && i < portfolioReturns.size(); i++) {
            LocalDate date = dates.get(i);
            YearMonth ym = YearMonth.from(date);

            portfolioByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(portfolioReturns.get(i));

            BigDecimal benchmarkReturn = benchmarkReturnMap.get(date);
            if (benchmarkReturn != null) {
                benchmarkByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(benchmarkReturn);
            }
        }

        List<MonthlyComparison> comparisons = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (YearMonth ym : portfolioByMonth.keySet()) {
            List<BigDecimal> pReturns = portfolioByMonth.get(ym);
            List<BigDecimal> bReturns = benchmarkByMonth.getOrDefault(ym, Collections.emptyList());

            BigDecimal pTotal = pReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bTotal = bReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            comparisons.add(MonthlyComparison.builder()
                    .month(ym.format(formatter))
                    .portfolioReturn(pTotal.setScale(2, RoundingMode.HALF_UP))
                    .benchmarkReturn(bTotal.setScale(2, RoundingMode.HALF_UP))
                    .excessReturn(pTotal.subtract(bTotal).setScale(2, RoundingMode.HALF_UP))
                    .portfolioWin(pTotal.compareTo(bTotal) > 0)
                    .build());
        }

        return comparisons;
    }

    private BenchmarkComparisonDto buildEmptyComparison(BenchmarkType benchmark, LocalDate startDate, LocalDate endDate) {
        return BenchmarkComparisonDto.builder()
                .benchmark(benchmark)
                .benchmarkLabel(benchmark.getLabel())
                .startDate(startDate)
                .endDate(endDate)
                .labels(Collections.emptyList())
                .portfolioReturns(Collections.emptyList())
                .benchmarkReturns(Collections.emptyList())
                .excessReturns(Collections.emptyList())
                .portfolioTotalReturn(BigDecimal.ZERO)
                .benchmarkTotalReturn(BigDecimal.ZERO)
                .excessReturn(BigDecimal.ZERO)
                .alpha(BigDecimal.ZERO)
                .beta(BigDecimal.ONE)
                .correlation(BigDecimal.ZERO)
                .build();
    }

    // === Statistical Helper Methods ===

    private BigDecimal calculateMean(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private BigDecimal calculateVariance(List<BigDecimal> values) {
        if (values.size() < 2) return BigDecimal.ZERO;
        BigDecimal mean = calculateMean(values);
        BigDecimal sumSquares = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumSquares.divide(BigDecimal.valueOf(values.size() - 1), MC);
    }

    private BigDecimal calculateStdDev(List<BigDecimal> values) {
        BigDecimal variance = calculateVariance(values);
        if (variance.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private BigDecimal calculateCovariance(List<BigDecimal> list1, List<BigDecimal> list2) {
        if (list1.size() != list2.size() || list1.size() < 2) return BigDecimal.ZERO;

        BigDecimal mean1 = calculateMean(list1);
        BigDecimal mean2 = calculateMean(list2);

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < list1.size(); i++) {
            sum = sum.add(list1.get(i).subtract(mean1).multiply(list2.get(i).subtract(mean2)));
        }

        return sum.divide(BigDecimal.valueOf(list1.size() - 1), MC);
    }
}
