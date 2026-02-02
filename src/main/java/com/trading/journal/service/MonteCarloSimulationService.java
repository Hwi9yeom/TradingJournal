package com.trading.journal.service;

import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.MonteCarloRequestDto;
import com.trading.journal.dto.MonteCarloResultDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

/**
 * Monte Carlo Simulation Service
 *
 * <p>Uses Geometric Brownian Motion (GBM) to simulate future portfolio values: S(t+1) = S(t) *
 * exp((mu - sigma^2/2)*dt + sigma*sqrt(dt)*Z) where Z is a standard normal random variable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonteCarloSimulationService {

    private final AnalysisService analysisService;

    private static final int MAX_SIMULATIONS = 100000;
    private static final int MAX_PROJECTION_DAYS = 1260;
    private static final int FINANCIAL_SCALE = 8;
    private static final int DISPLAY_SCALE = 2;
    private static final int HISTOGRAM_BINS = 50;
    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * Run Monte Carlo simulation using Geometric Brownian Motion.
     *
     * @param request simulation parameters
     * @return simulation results including VaR, CVaR, percentiles, and chart data
     */
    public MonteCarloResultDto runSimulation(MonteCarloRequestDto request) {
        log.info(
                "Starting Monte Carlo simulation: {} simulations, {} days projection",
                request.getNumSimulations(),
                request.getProjectionDays());

        // Validate request parameters
        validateRequest(request);

        // Get historical returns
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        LocalDate startDate =
                request.getStartDate() != null ? request.getStartDate() : endDate.minusYears(1);

        List<BigDecimal> historicalReturns =
                getHistoricalReturns(request.getAccountId(), startDate, endDate);

        if (historicalReturns.size() < 20) {
            log.warn("Insufficient historical data: {} data points", historicalReturns.size());
            return buildInsufficientDataResult(request);
        }

        // Calculate GBM parameters (mu and sigma)
        DescriptiveStatistics stats = new DescriptiveStatistics();
        historicalReturns.forEach(r -> stats.addValue(r.doubleValue()));

        double mu = stats.getMean(); // Daily mean return
        double sigma = stats.getStandardDeviation(); // Daily volatility

        log.debug("GBM parameters - mu: {}, sigma: {}", mu, sigma);

        // Determine initial value
        BigDecimal initialValue = request.getInitialValue();
        if (initialValue == null || initialValue.compareTo(BigDecimal.ZERO) <= 0) {
            EquityCurveDto equityCurve =
                    analysisService.getEquityCurve(request.getAccountId(), startDate, endDate);
            initialValue = equityCurve.getFinalValue();
            if (initialValue == null || initialValue.compareTo(BigDecimal.ZERO) <= 0) {
                initialValue = new BigDecimal("100000"); // Default starting value
            }
        }

        // Run parallel simulations
        int numSimulations = Math.min(request.getNumSimulations(), MAX_SIMULATIONS);
        int projectionDays = Math.min(request.getProjectionDays(), MAX_PROJECTION_DAYS);

        NormalDistribution normalDist = new NormalDistribution(0, 1);
        BigDecimal finalInitialValue = initialValue;

        // Use parallelStream for performance
        List<SimulationResult> simulationResults =
                IntStream.range(0, numSimulations)
                        .parallel()
                        .mapToObj(
                                i ->
                                        simulatePath(
                                                finalInitialValue,
                                                mu,
                                                sigma,
                                                projectionDays,
                                                normalDist))
                        .collect(Collectors.toList());

        // Extract final values for statistics
        List<BigDecimal> finalValues =
                simulationResults.stream()
                        .map(SimulationResult::finalValue)
                        .collect(Collectors.toList());

        // Calculate statistics
        DescriptiveStatistics finalStats = new DescriptiveStatistics();
        finalValues.forEach(v -> finalStats.addValue(v.doubleValue()));

        // Calculate VaR and CVaR
        BigDecimal var95 = calculateVaR(finalValues, 0.95, finalInitialValue);
        BigDecimal var99 = calculateVaR(finalValues, 0.99, finalInitialValue);
        BigDecimal cvar95 = calculateCVaR(finalValues, var95, finalInitialValue);

        // Calculate percentile values
        Map<String, BigDecimal> percentileValues =
                calculatePercentileValues(finalStats, request.getConfidenceLevels());

        // Calculate probability of loss
        long lossCount =
                finalValues.stream().filter(v -> v.compareTo(finalInitialValue) < 0).count();
        BigDecimal probabilityOfLoss =
                new BigDecimal(lossCount)
                        .divide(
                                new BigDecimal(numSimulations),
                                FINANCIAL_SCALE,
                                RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);

        // Calculate max drawdown at 95th percentile (worst 5% of paths)
        BigDecimal maxDrawdownAt95 = calculateMaxDrawdownAtPercentile(simulationResults, 0.05);

        // Generate chart data (mean path and confidence bands)
        ChartData chartData =
                generateChartData(simulationResults, projectionDays, request.getConfidenceLevels());

        // Generate histogram data
        HistogramData histogramData = generateHistogramData(finalValues);

        log.info(
                "Monte Carlo simulation completed: mean={}, VaR95={}, VaR99={}",
                finalStats.getMean(),
                var95,
                var99);

        return MonteCarloResultDto.builder()
                .executedAt(LocalDateTime.now())
                .numSimulations(numSimulations)
                .projectionDays(projectionDays)
                .percentileValues(percentileValues)
                .meanFinalValue(
                        BigDecimal.valueOf(finalStats.getMean())
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .medianFinalValue(
                        BigDecimal.valueOf(finalStats.getPercentile(50))
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .standardDeviation(
                        BigDecimal.valueOf(finalStats.getStandardDeviation())
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .skewness(
                        BigDecimal.valueOf(finalStats.getSkewness())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .kurtosis(
                        BigDecimal.valueOf(finalStats.getKurtosis())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .probabilityOfLoss(probabilityOfLoss)
                .valueAtRisk95(var95)
                .valueAtRisk99(var99)
                .expectedShortfall(cvar95)
                .maxDrawdownAt95Percentile(maxDrawdownAt95)
                .labels(chartData.labels)
                .meanPath(chartData.meanPath)
                .upperBound(chartData.upperBound)
                .lowerBound(chartData.lowerBound)
                .histogramBins(histogramData.bins)
                .histogramCounts(histogramData.counts)
                .build();
    }

    /** Validate request parameters. */
    private void validateRequest(MonteCarloRequestDto request) {
        if (request.getAccountId() == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        if (request.getNumSimulations() != null && request.getNumSimulations() > MAX_SIMULATIONS) {
            log.warn(
                    "Requested simulations {} exceeds max {}, capping",
                    request.getNumSimulations(),
                    MAX_SIMULATIONS);
        }
        if (request.getProjectionDays() != null
                && request.getProjectionDays() > MAX_PROJECTION_DAYS) {
            log.warn(
                    "Requested projection days {} exceeds max {}, capping",
                    request.getProjectionDays(),
                    MAX_PROJECTION_DAYS);
        }
    }

    /** Get historical daily returns from equity curve. */
    private List<BigDecimal> getHistoricalReturns(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        EquityCurveDto equityCurve = analysisService.getEquityCurve(accountId, startDate, endDate);

        List<BigDecimal> dailyReturns = equityCurve.getDailyReturns();
        if (dailyReturns == null || dailyReturns.isEmpty()) {
            return new ArrayList<>();
        }

        // Convert percentage returns to decimal (divide by 100)
        return dailyReturns.stream()
                .filter(r -> r != null)
                .map(r -> r.divide(new BigDecimal("100"), FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
    }

    /**
     * Simulate a single price path using Geometric Brownian Motion.
     *
     * <p>Formula: S(t+1) = S(t) * exp((mu - sigma^2/2)*dt + sigma*sqrt(dt)*Z)
     */
    private SimulationResult simulatePath(
            BigDecimal initialValue,
            double mu,
            double sigma,
            int days,
            NormalDistribution normalDist) {

        List<BigDecimal> path = new ArrayList<>(days + 1);
        path.add(initialValue);

        double dt = 1.0 / TRADING_DAYS_PER_YEAR; // Daily time step
        double drift = (mu - 0.5 * sigma * sigma) * dt;
        double diffusion = sigma * Math.sqrt(dt);

        BigDecimal currentValue = initialValue;
        BigDecimal maxValue = initialValue;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (int i = 0; i < days; i++) {
            // Generate random normal variable using thread-local random for thread safety
            double z =
                    normalDist.inverseCumulativeProbability(
                            ThreadLocalRandom.current().nextDouble());
            double logReturn = drift + diffusion * z;
            double multiplier = Math.exp(logReturn);

            currentValue = currentValue.multiply(BigDecimal.valueOf(multiplier));
            path.add(currentValue.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));

            // Track max drawdown
            if (currentValue.compareTo(maxValue) > 0) {
                maxValue = currentValue;
            } else {
                BigDecimal drawdown =
                        maxValue.subtract(currentValue)
                                .divide(maxValue, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return new SimulationResult(
                path, currentValue.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP), maxDrawdown);
    }

    /**
     * Calculate Value at Risk (VaR) at given confidence level. VaR represents the potential loss at
     * the confidence percentile.
     */
    private BigDecimal calculateVaR(
            List<BigDecimal> finalValues, double confidence, BigDecimal initialValue) {
        List<BigDecimal> sortedReturns =
                finalValues.stream()
                        .map(v -> v.subtract(initialValue))
                        .sorted()
                        .collect(Collectors.toList());

        int varIndex = (int) Math.floor((1 - confidence) * sortedReturns.size());
        varIndex = Math.max(0, Math.min(varIndex, sortedReturns.size() - 1));

        BigDecimal varValue = sortedReturns.get(varIndex);

        // Return as positive percentage loss
        if (initialValue.compareTo(BigDecimal.ZERO) > 0) {
            return varValue.abs()
                    .divide(initialValue, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate Conditional VaR (CVaR) / Expected Shortfall. CVaR is the expected loss given that
     * the loss exceeds VaR.
     */
    private BigDecimal calculateCVaR(
            List<BigDecimal> finalValues, BigDecimal varPercentage, BigDecimal initialValue) {
        BigDecimal varThreshold =
                initialValue.subtract(
                        initialValue.multiply(
                                varPercentage.divide(
                                        new BigDecimal("100"),
                                        FINANCIAL_SCALE,
                                        RoundingMode.HALF_UP)));

        List<BigDecimal> tailLosses =
                finalValues.stream()
                        .filter(v -> v.compareTo(varThreshold) < 0)
                        .map(v -> initialValue.subtract(v))
                        .collect(Collectors.toList());

        if (tailLosses.isEmpty()) {
            return varPercentage;
        }

        BigDecimal sumLosses = tailLosses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgLoss =
                sumLosses.divide(
                        new BigDecimal(tailLosses.size()), FINANCIAL_SCALE, RoundingMode.HALF_UP);

        if (initialValue.compareTo(BigDecimal.ZERO) > 0) {
            return avgLoss.divide(initialValue, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** Calculate percentile values from simulation results. */
    private Map<String, BigDecimal> calculatePercentileValues(
            DescriptiveStatistics stats, List<BigDecimal> confidenceLevels) {

        Map<String, BigDecimal> percentileValues = new LinkedHashMap<>();

        for (BigDecimal level : confidenceLevels) {
            double percentile = level.multiply(new BigDecimal("100")).doubleValue();
            String key = level.multiply(new BigDecimal("100")).intValue() + "%";
            BigDecimal value =
                    BigDecimal.valueOf(stats.getPercentile(percentile))
                            .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
            percentileValues.put(key, value);
        }

        return percentileValues;
    }

    /** Calculate max drawdown at a specific percentile of paths. */
    private BigDecimal calculateMaxDrawdownAtPercentile(
            List<SimulationResult> results, double percentile) {

        List<BigDecimal> maxDrawdowns =
                results.stream()
                        .map(SimulationResult::maxDrawdown)
                        .sorted((a, b) -> b.compareTo(a)) // Descending order
                        .collect(Collectors.toList());

        int index = (int) Math.floor(percentile * maxDrawdowns.size());
        index = Math.max(0, Math.min(index, maxDrawdowns.size() - 1));

        return maxDrawdowns.get(index).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** Generate chart data (mean path and confidence bands). */
    private ChartData generateChartData(
            List<SimulationResult> results, int projectionDays, List<BigDecimal> confidenceLevels) {

        List<String> labels = new ArrayList<>();
        List<BigDecimal> meanPath = new ArrayList<>();
        List<BigDecimal> upperBound = new ArrayList<>();
        List<BigDecimal> lowerBound = new ArrayList<>();

        // Find upper and lower confidence levels
        double upperConfidence = 0.95;
        double lowerConfidence = 0.05;

        for (BigDecimal level : confidenceLevels) {
            double l = level.doubleValue();
            if (l > 0.9 && l <= 1.0) {
                upperConfidence = l;
            } else if (l < 0.1 && l >= 0) {
                lowerConfidence = l;
            }
        }

        // Calculate statistics for each day
        for (int day = 0; day <= projectionDays; day++) {
            labels.add("Day " + day);

            final int dayIndex = day;
            DescriptiveStatistics dayStats = new DescriptiveStatistics();

            results.stream()
                    .filter(r -> r.path().size() > dayIndex)
                    .forEach(r -> dayStats.addValue(r.path().get(dayIndex).doubleValue()));

            if (dayStats.getN() > 0) {
                meanPath.add(
                        BigDecimal.valueOf(dayStats.getMean())
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
                upperBound.add(
                        BigDecimal.valueOf(dayStats.getPercentile(upperConfidence * 100))
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
                lowerBound.add(
                        BigDecimal.valueOf(dayStats.getPercentile(lowerConfidence * 100))
                                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
            }
        }

        return new ChartData(labels, meanPath, upperBound, lowerBound);
    }

    /** Generate histogram data for distribution visualization. */
    private HistogramData generateHistogramData(List<BigDecimal> finalValues) {
        if (finalValues.isEmpty()) {
            return new HistogramData(new ArrayList<>(), new ArrayList<>());
        }

        double min = finalValues.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double max = finalValues.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double range = max - min;

        if (range <= 0) {
            return new HistogramData(
                    List.of(BigDecimal.valueOf(min).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP)),
                    List.of(finalValues.size()));
        }

        double binWidth = range / HISTOGRAM_BINS;
        int[] counts = new int[HISTOGRAM_BINS];

        for (BigDecimal value : finalValues) {
            int binIndex = (int) ((value.doubleValue() - min) / binWidth);
            binIndex = Math.min(binIndex, HISTOGRAM_BINS - 1);
            counts[binIndex]++;
        }

        List<BigDecimal> bins = new ArrayList<>();
        List<Integer> countList = new ArrayList<>();

        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            double binStart = min + i * binWidth;
            bins.add(BigDecimal.valueOf(binStart).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
            countList.add(counts[i]);
        }

        return new HistogramData(bins, countList);
    }

    /** Build result for insufficient data scenario. */
    private MonteCarloResultDto buildInsufficientDataResult(MonteCarloRequestDto request) {
        return MonteCarloResultDto.builder()
                .executedAt(LocalDateTime.now())
                .numSimulations(0)
                .projectionDays(request.getProjectionDays())
                .percentileValues(new LinkedHashMap<>())
                .meanFinalValue(BigDecimal.ZERO)
                .medianFinalValue(BigDecimal.ZERO)
                .standardDeviation(BigDecimal.ZERO)
                .skewness(BigDecimal.ZERO)
                .kurtosis(BigDecimal.ZERO)
                .probabilityOfLoss(BigDecimal.ZERO)
                .valueAtRisk95(BigDecimal.ZERO)
                .valueAtRisk99(BigDecimal.ZERO)
                .expectedShortfall(BigDecimal.ZERO)
                .maxDrawdownAt95Percentile(BigDecimal.ZERO)
                .labels(new ArrayList<>())
                .meanPath(new ArrayList<>())
                .upperBound(new ArrayList<>())
                .lowerBound(new ArrayList<>())
                .histogramBins(new ArrayList<>())
                .histogramCounts(new ArrayList<>())
                .build();
    }

    /** Record to hold simulation path results. */
    private record SimulationResult(
            List<BigDecimal> path, BigDecimal finalValue, BigDecimal maxDrawdown) {}

    /** Record to hold chart data. */
    private record ChartData(
            List<String> labels,
            List<BigDecimal> meanPath,
            List<BigDecimal> upperBound,
            List<BigDecimal> lowerBound) {}

    /** Record to hold histogram data. */
    private record HistogramData(List<BigDecimal> bins, List<Integer> counts) {}
}
