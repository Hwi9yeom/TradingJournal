package com.trading.journal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.BacktestComparisonDto;
import com.trading.journal.dto.BacktestComparisonDto.*;
import com.trading.journal.entity.BacktestResult;
import com.trading.journal.repository.BacktestResultRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 백테스트 결과 비교 서비스
 *
 * <p>여러 백테스트 결과를 비교 분석하여 성과 순위, 차트 데이터, 통계 요약 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestComparisonService {

    private final BacktestResultRepository backtestResultRepository;
    private final ObjectMapper objectMapper;

    /** 차트 색상 팔레트 */
    private static final List<String> COLOR_PALETTE =
            List.of(
                    "#3B82F6", // Blue
                    "#EF4444", // Red
                    "#10B981", // Green
                    "#F59E0B", // Amber
                    "#8B5CF6", // Purple
                    "#EC4899", // Pink
                    "#06B6D4", // Cyan
                    "#84CC16" // Lime
                    );

    /** 소수점 자릿수 */
    private static final int SCALE = 4;

    /**
     * 여러 백테스트 결과 비교
     *
     * @param backtestIds 비교할 백테스트 ID 목록
     * @return 비교 결과 DTO
     */
    @Cacheable(value = "backtest_comparison", key = "#backtestIds.hashCode()")
    public BacktestComparisonDto compareBacktests(List<Long> backtestIds) {
        log.info("Comparing {} backtests: {}", backtestIds.size(), backtestIds);

        if (backtestIds == null || backtestIds.size() < 2) {
            throw new IllegalArgumentException("비교하려면 최소 2개의 백테스트가 필요합니다.");
        }

        List<BacktestResult> results = backtestResultRepository.findAllById(backtestIds);

        if (results.size() < 2) {
            throw new IllegalArgumentException("유효한 백테스트가 2개 미만입니다.");
        }

        // 비교 대상 백테스트 목록 생성
        List<ComparedBacktest> comparedBacktests = buildComparedBacktests(results);

        // 지표별 순위 계산
        MetricRankings rankings = buildMetricRankings(results);

        // 차트 데이터 생성
        ChartData chartData = buildChartData(results);

        // 비교 통계 요약 생성
        ComparisonSummary summary = buildComparisonSummary(results);

        log.info("Backtest comparison completed for {} backtests", results.size());

        return BacktestComparisonDto.builder()
                .backtests(comparedBacktests)
                .rankings(rankings)
                .chartData(chartData)
                .summary(summary)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 상위 성과 백테스트 비교 (자동 선택)
     *
     * @param metric 기준 지표 (return, sharpe, drawdown)
     * @param limit 비교 개수 (기본 5)
     * @return 비교 결과 DTO
     */
    public BacktestComparisonDto compareTopPerformers(String metric, int limit) {
        log.info("Comparing top {} performers by {}", limit, metric);

        List<BacktestResult> topResults = fetchTopByMetric(metric, limit);

        if (topResults.size() < 2) {
            throw new IllegalArgumentException("비교할 백테스트가 충분하지 않습니다.");
        }

        List<Long> ids = topResults.stream().map(BacktestResult::getId).toList();
        return compareBacktests(ids);
    }

    /**
     * 동일 전략 다른 파라미터 비교
     *
     * @param strategyName 전략 이름
     * @param limit 비교 개수
     * @return 비교 결과 DTO
     */
    public BacktestComparisonDto compareStrategyVariants(String strategyName, int limit) {
        log.info("Comparing variants of strategy: {}", strategyName);

        List<BacktestResult> variants =
                backtestResultRepository
                        .findByStrategyNameContainingIgnoreCaseOrderByExecutedAtDesc(strategyName);

        if (variants.size() < 2) {
            throw new IllegalArgumentException("비교할 전략 변형이 충분하지 않습니다.");
        }

        List<Long> ids = variants.stream().limit(limit).map(BacktestResult::getId).toList();
        return compareBacktests(ids);
    }

    // ============================================================
    // Private Methods - 비교 대상 생성
    // ============================================================

    private List<ComparedBacktest> buildComparedBacktests(List<BacktestResult> results) {
        List<ComparedBacktest> list = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            BacktestResult r = results.get(i);
            String color = COLOR_PALETTE.get(i % COLOR_PALETTE.size());

            list.add(
                    ComparedBacktest.builder()
                            .id(r.getId())
                            .strategyName(r.getStrategyName())
                            .strategyType(extractStrategyType(r.getStrategyConfig()))
                            .symbol(r.getSymbol())
                            .startDate(r.getStartDate())
                            .endDate(r.getEndDate())
                            .initialCapital(r.getInitialCapital())
                            .finalCapital(r.getFinalCapital())
                            .totalReturn(r.getTotalReturn())
                            .cagr(r.getCagr())
                            .maxDrawdown(r.getMaxDrawdown())
                            .sharpeRatio(r.getSharpeRatio())
                            .sortinoRatio(r.getSortinoRatio())
                            .profitFactor(r.getProfitFactor())
                            .winRate(r.getWinRate())
                            .totalTrades(r.getTotalTrades())
                            .avgHoldingDays(r.getAvgHoldingDays())
                            .executedAt(r.getExecutedAt())
                            .color(color)
                            .build());
        }

        return list;
    }

    // ============================================================
    // Private Methods - 순위 계산
    // ============================================================

    private MetricRankings buildMetricRankings(List<BacktestResult> results) {
        return MetricRankings.builder()
                .byTotalReturn(rankBy(results, BacktestResult::getTotalReturn, true))
                .bySharpeRatio(rankBy(results, BacktestResult::getSharpeRatio, true))
                .byMaxDrawdown(rankBy(results, BacktestResult::getMaxDrawdown, false))
                .byWinRate(rankBy(results, BacktestResult::getWinRate, true))
                .byProfitFactor(rankBy(results, BacktestResult::getProfitFactor, true))
                .byCagr(rankBy(results, BacktestResult::getCagr, true))
                .byOverallScore(rankByOverallScore(results))
                .build();
    }

    private List<RankEntry> rankBy(
            List<BacktestResult> results,
            java.util.function.Function<BacktestResult, BigDecimal> extractor,
            boolean descending) {

        List<BacktestResult> sorted =
                results.stream()
                        .filter(r -> extractor.apply(r) != null)
                        .sorted(
                                (a, b) -> {
                                    int cmp = extractor.apply(a).compareTo(extractor.apply(b));
                                    return descending ? -cmp : cmp;
                                })
                        .toList();

        List<RankEntry> rankings = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            BacktestResult r = sorted.get(i);
            rankings.add(
                    RankEntry.builder()
                            .rank(i + 1)
                            .backtestId(r.getId())
                            .strategyName(r.getStrategyName())
                            .value(extractor.apply(r))
                            .build());
        }
        return rankings;
    }

    private List<RankEntry> rankByOverallScore(List<BacktestResult> results) {
        // 종합 점수 = 정규화된 (수익률 + 샤프 + 승률 - 낙폭)
        Map<Long, BigDecimal> scores = new HashMap<>();

        BigDecimal maxReturn = getMax(results, BacktestResult::getTotalReturn);
        BigDecimal maxSharpe = getMax(results, BacktestResult::getSharpeRatio);
        BigDecimal maxWinRate = getMax(results, BacktestResult::getWinRate);
        BigDecimal maxDrawdown = getMax(results, BacktestResult::getMaxDrawdown);

        for (BacktestResult r : results) {
            BigDecimal score = BigDecimal.ZERO;

            if (r.getTotalReturn() != null && maxReturn.compareTo(BigDecimal.ZERO) > 0) {
                score =
                        score.add(
                                r.getTotalReturn()
                                        .divide(maxReturn, SCALE, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(30)));
            }
            if (r.getSharpeRatio() != null && maxSharpe.compareTo(BigDecimal.ZERO) > 0) {
                score =
                        score.add(
                                r.getSharpeRatio()
                                        .divide(maxSharpe, SCALE, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(30)));
            }
            if (r.getWinRate() != null && maxWinRate.compareTo(BigDecimal.ZERO) > 0) {
                score =
                        score.add(
                                r.getWinRate()
                                        .divide(maxWinRate, SCALE, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(20)));
            }
            if (r.getMaxDrawdown() != null && maxDrawdown.compareTo(BigDecimal.ZERO) > 0) {
                // 낙폭은 낮을수록 좋음
                score =
                        score.add(
                                BigDecimal.ONE
                                        .subtract(
                                                r.getMaxDrawdown()
                                                        .divide(
                                                                maxDrawdown,
                                                                SCALE,
                                                                RoundingMode.HALF_UP))
                                        .multiply(BigDecimal.valueOf(20)));
            }

            scores.put(r.getId(), score.setScale(2, RoundingMode.HALF_UP));
        }

        List<Map.Entry<Long, BigDecimal>> sorted =
                scores.entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .toList();

        List<RankEntry> rankings = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, BigDecimal> entry = sorted.get(i);
            BacktestResult r =
                    results.stream()
                            .filter(x -> x.getId().equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);

            if (r != null) {
                rankings.add(
                        RankEntry.builder()
                                .rank(i + 1)
                                .backtestId(r.getId())
                                .strategyName(r.getStrategyName())
                                .value(entry.getValue())
                                .build());
            }
        }
        return rankings;
    }

    private BigDecimal getMax(
            List<BacktestResult> results,
            java.util.function.Function<BacktestResult, BigDecimal> extractor) {
        return results.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
    }

    // ============================================================
    // Private Methods - 차트 데이터 생성
    // ============================================================

    private ChartData buildChartData(List<BacktestResult> results) {
        Map<Long, List<BigDecimal>> equityCurves = new HashMap<>();
        Map<Long, List<BigDecimal>> drawdownCurves = new HashMap<>();
        List<String> commonLabels = null;

        for (BacktestResult r : results) {
            try {
                // 수익률 곡선 파싱
                if (r.getEquityCurveJson() != null) {
                    List<BigDecimal> curve =
                            objectMapper.readValue(
                                    r.getEquityCurveJson(),
                                    new TypeReference<List<BigDecimal>>() {});
                    // 정규화 (시작=100)
                    List<BigDecimal> normalized = normalizeToPercent(curve);
                    equityCurves.put(r.getId(), normalized);
                }

                // 낙폭 곡선 파싱
                if (r.getDrawdownCurveJson() != null) {
                    List<BigDecimal> drawdown =
                            objectMapper.readValue(
                                    r.getDrawdownCurveJson(),
                                    new TypeReference<List<BigDecimal>>() {});
                    drawdownCurves.put(r.getId(), drawdown);
                }

                // 라벨 (첫 번째 결과에서 가져옴)
                if (commonLabels == null && r.getEquityLabelsJson() != null) {
                    commonLabels =
                            objectMapper.readValue(
                                    r.getEquityLabelsJson(), new TypeReference<List<String>>() {});
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to parse chart data for backtest {}: {}",
                        r.getId(),
                        e.getMessage());
            }
        }

        // 월별 수익률 비교
        List<MonthlyComparison> monthlyReturns = buildMonthlyComparison(results);

        return ChartData.builder()
                .labels(commonLabels != null ? commonLabels : Collections.emptyList())
                .equityCurves(equityCurves)
                .drawdownCurves(drawdownCurves)
                .monthlyReturns(monthlyReturns)
                .build();
    }

    private List<BigDecimal> normalizeToPercent(List<BigDecimal> curve) {
        if (curve == null || curve.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal initial = curve.get(0);
        if (initial.compareTo(BigDecimal.ZERO) == 0) {
            return curve;
        }

        return curve.stream()
                .map(
                        v ->
                                v.subtract(initial)
                                        .divide(initial, SCALE, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100)))
                .toList();
    }

    private List<MonthlyComparison> buildMonthlyComparison(List<BacktestResult> results) {
        Map<String, Map<Long, BigDecimal>> monthlyData = new LinkedHashMap<>();

        for (BacktestResult r : results) {
            if (r.getMonthlyPerformanceJson() != null) {
                try {
                    List<Map<String, Object>> monthly =
                            objectMapper.readValue(
                                    r.getMonthlyPerformanceJson(),
                                    new TypeReference<List<Map<String, Object>>>() {});

                    for (Map<String, Object> m : monthly) {
                        String month = (String) m.get("month");
                        Object returnObj = m.get("returnPct");
                        BigDecimal returnPct =
                                returnObj instanceof Number
                                        ? BigDecimal.valueOf(((Number) returnObj).doubleValue())
                                        : BigDecimal.ZERO;

                        monthlyData
                                .computeIfAbsent(month, k -> new HashMap<>())
                                .put(r.getId(), returnPct);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to parse monthly data for backtest {}: {}",
                            r.getId(),
                            e.getMessage());
                }
            }
        }

        return monthlyData.entrySet().stream()
                .map(
                        e ->
                                MonthlyComparison.builder()
                                        .month(e.getKey())
                                        .returns(e.getValue())
                                        .build())
                .toList();
    }

    // ============================================================
    // Private Methods - 통계 요약 생성
    // ============================================================

    private ComparisonSummary buildComparisonSummary(List<BacktestResult> results) {
        BacktestResult bestReturn =
                results.stream()
                        .filter(r -> r.getTotalReturn() != null)
                        .max(Comparator.comparing(BacktestResult::getTotalReturn))
                        .orElse(null);

        BacktestResult lowestDrawdown =
                results.stream()
                        .filter(r -> r.getMaxDrawdown() != null)
                        .min(Comparator.comparing(BacktestResult::getMaxDrawdown))
                        .orElse(null);

        BacktestResult bestSharpe =
                results.stream()
                        .filter(r -> r.getSharpeRatio() != null)
                        .max(Comparator.comparing(BacktestResult::getSharpeRatio))
                        .orElse(null);

        BigDecimal avgReturn = calculateAverage(results, BacktestResult::getTotalReturn);
        BigDecimal avgSharpe = calculateAverage(results, BacktestResult::getSharpeRatio);
        BigDecimal avgDrawdown = calculateAverage(results, BacktestResult::getMaxDrawdown);

        long profitable =
                results.stream()
                        .filter(
                                r ->
                                        r.getTotalReturn() != null
                                                && r.getTotalReturn().compareTo(BigDecimal.ZERO)
                                                        > 0)
                        .count();

        BigDecimal profitableRatio =
                BigDecimal.valueOf(profitable * 100.0 / results.size())
                        .setScale(2, RoundingMode.HALF_UP);

        LocalDate commonStart =
                results.stream()
                        .map(BacktestResult::getStartDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(null);

        LocalDate commonEnd =
                results.stream()
                        .map(BacktestResult::getEndDate)
                        .filter(Objects::nonNull)
                        .min(LocalDate::compareTo)
                        .orElse(null);

        return ComparisonSummary.builder()
                .totalBacktests(results.size())
                .bestReturnStrategy(bestReturn != null ? bestReturn.getStrategyName() : null)
                .bestReturn(bestReturn != null ? bestReturn.getTotalReturn() : null)
                .lowestRiskStrategy(
                        lowestDrawdown != null ? lowestDrawdown.getStrategyName() : null)
                .lowestDrawdown(lowestDrawdown != null ? lowestDrawdown.getMaxDrawdown() : null)
                .bestSharpeStrategy(bestSharpe != null ? bestSharpe.getStrategyName() : null)
                .bestSharpe(bestSharpe != null ? bestSharpe.getSharpeRatio() : null)
                .avgReturn(avgReturn)
                .avgSharpe(avgSharpe)
                .avgMaxDrawdown(avgDrawdown)
                .profitableRatio(profitableRatio)
                .commonStartDate(commonStart)
                .commonEndDate(commonEnd)
                .build();
    }

    private BigDecimal calculateAverage(
            List<BacktestResult> results,
            java.util.function.Function<BacktestResult, BigDecimal> extractor) {

        List<BigDecimal> values = results.stream().map(extractor).filter(Objects::nonNull).toList();

        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    // ============================================================
    // Private Methods - 유틸리티
    // ============================================================

    private List<BacktestResult> fetchTopByMetric(String metric, int limit) {
        return switch (metric.toLowerCase()) {
            case "return", "totalreturn" ->
                    backtestResultRepository.findTopPerformingBacktests().stream()
                            .limit(limit)
                            .toList();
            case "sharpe", "sharperatio" ->
                    backtestResultRepository.findByBestSharpeRatio().stream().limit(limit).toList();
            default ->
                    backtestResultRepository.findTop20ByOrderByExecutedAtDesc().stream()
                            .limit(limit)
                            .toList();
        };
    }

    private String extractStrategyType(String strategyConfig) {
        if (strategyConfig == null) {
            return "UNKNOWN";
        }
        try {
            Map<String, Object> config =
                    objectMapper.readValue(
                            strategyConfig, new TypeReference<Map<String, Object>>() {});
            return (String) config.getOrDefault("strategyType", "UNKNOWN");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
