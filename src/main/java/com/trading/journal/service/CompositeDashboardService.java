package com.trading.journal.service;

import com.trading.journal.dto.CompositeDashboardDto;
import com.trading.journal.dto.CompositeDashboardDto.*;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.dto.RiskMetricsDto;
import com.trading.journal.dto.TradingPsychologyDto.PsychologicalScore;
import com.trading.journal.dto.TradingPsychologyDto.TiltAnalysis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 복합 지표 대시보드 서비스
 *
 * <p>포트폴리오 현황, 리스크 지표, 심리 점수, 거래 통계를 병렬로 조회하여 통합 대시보드 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompositeDashboardService {

    private final PortfolioAnalysisService portfolioAnalysisService;
    private final RiskMetricsService riskMetricsService;
    private final TradingPsychologyService tradingPsychologyService;
    private final TradingStatisticsService tradingStatisticsService;

    /** 기본 분석 기간 (일) */
    private static final int DEFAULT_ANALYSIS_DAYS = 90;

    /** 전용 스레드 풀 (I/O 바운드 작업에 최적화) */
    private java.util.concurrent.ExecutorService dashboardExecutor;

    @jakarta.annotation.PostConstruct
    public void init() {
        // I/O 바운드 작업에 적합한 스레드 풀 (코어 수 * 2)
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        dashboardExecutor = java.util.concurrent.Executors.newFixedThreadPool(poolSize);
        log.info("Dashboard executor initialized with {} threads", poolSize);
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (dashboardExecutor != null) {
            dashboardExecutor.shutdown();
            try {
                if (!dashboardExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    dashboardExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dashboardExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 복합 대시보드 데이터 조회
     *
     * <p>4개 서비스를 병렬로 호출하여 통합 대시보드 생성. 개별 섹션에서 에러가 발생해도 다른 섹션은 정상 반환됨.
     *
     * @param accountId 계좌 ID (null이면 전체)
     * @return 복합 대시보드 DTO
     */
    @Cacheable(
            value = "composite_dashboard",
            key = "'dashboard_' + (#accountId != null ? #accountId : 'all')")
    public CompositeDashboardDto getCompositeDashboard(Long accountId) {
        log.info("Generating composite dashboard for account: {}", accountId);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(DEFAULT_ANALYSIS_DAYS);

        // 병렬로 4개 서비스 호출 (전용 Executor 사용)
        CompletableFuture<PortfolioOverview> portfolioFuture =
                CompletableFuture.supplyAsync(() -> fetchPortfolioOverview(), dashboardExecutor);

        CompletableFuture<RiskMetricsSummary> riskFuture =
                CompletableFuture.supplyAsync(
                        () -> fetchRiskMetrics(accountId, startDate, endDate), dashboardExecutor);

        CompletableFuture<PsychologyScoreDetail> psychologyFuture =
                CompletableFuture.supplyAsync(
                        () -> fetchPsychologyScore(accountId, startDate, endDate),
                        dashboardExecutor);

        CompletableFuture<TradingStatisticsSummary> statisticsFuture =
                CompletableFuture.supplyAsync(this::fetchTradingStatistics, dashboardExecutor);

        // 모든 결과 수집 (개별 에러 핸들링)
        PortfolioOverview portfolioOverview =
                portfolioFuture
                        .exceptionally(
                                ex -> {
                                    log.warn(
                                            "Failed to fetch portfolio overview: {}",
                                            ex.getMessage());
                                    return buildEmptyPortfolioOverview();
                                })
                        .join();

        RiskMetricsSummary riskMetrics =
                riskFuture
                        .exceptionally(
                                ex -> {
                                    log.warn("Failed to fetch risk metrics: {}", ex.getMessage());
                                    return buildEmptyRiskMetrics();
                                })
                        .join();

        PsychologyScoreDetail psychologyScore =
                psychologyFuture
                        .exceptionally(
                                ex -> {
                                    log.warn(
                                            "Failed to fetch psychology score: {}",
                                            ex.getMessage());
                                    return buildEmptyPsychologyScore();
                                })
                        .join();

        TradingStatisticsSummary tradingStatistics =
                statisticsFuture
                        .exceptionally(
                                ex -> {
                                    log.warn(
                                            "Failed to fetch trading statistics: {}",
                                            ex.getMessage());
                                    return buildEmptyTradingStatistics();
                                })
                        .join();

        log.info("Composite dashboard generated successfully for account: {}", accountId);

        return CompositeDashboardDto.builder()
                .portfolioOverview(portfolioOverview)
                .riskMetrics(riskMetrics)
                .psychologyScore(psychologyScore)
                .tradingStatistics(tradingStatistics)
                .generatedAt(LocalDateTime.now())
                .accountId(accountId)
                .build();
    }

    // ============================================================
    // Private Methods - Data Fetching
    // ============================================================

    /** 포트폴리오 현황 조회 */
    private PortfolioOverview fetchPortfolioOverview() {
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        return PortfolioOverview.builder()
                .totalInvestment(summary.getTotalInvestment())
                .totalCurrentValue(summary.getTotalCurrentValue())
                .totalProfitLoss(summary.getTotalProfitLoss())
                .totalProfitLossPercent(summary.getTotalProfitLossPercent())
                .totalDayChange(summary.getTotalDayChange())
                .totalDayChangePercent(summary.getTotalDayChangePercent())
                .totalRealizedPnl(summary.getTotalRealizedPnl())
                .holdingsCount(summary.getHoldings() != null ? summary.getHoldings().size() : 0)
                .build();
    }

    /** 리스크 지표 조회 */
    private RiskMetricsSummary fetchRiskMetrics(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        RiskMetricsDto metrics =
                riskMetricsService.calculateRiskMetrics(accountId, startDate, endDate);

        VaRSummary var95Summary = null;
        if (metrics.getVar95() != null) {
            var95Summary =
                    VaRSummary.builder()
                            .dailyVaR(metrics.getVar95().getDailyVaR())
                            .dailyVaRAmount(metrics.getVar95().getDailyVaRAmount())
                            .build();
        }

        return RiskMetricsSummary.builder()
                .sharpeRatio(metrics.getSharpeRatio())
                .sortinoRatio(metrics.getSortinoRatio())
                .maxDrawdown(metrics.getMaxDrawdown())
                .volatility(metrics.getVolatility())
                .winRate(metrics.getWinRate())
                .profitFactor(metrics.getProfitFactor())
                .riskLevel(metrics.getRiskLevel() != null ? metrics.getRiskLevel().name() : "LOW")
                .var95(var95Summary)
                .build();
    }

    /** 심리 점수 조회 */
    private PsychologyScoreDetail fetchPsychologyScore(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        // 심리 점수 조회
        PsychologicalScore score =
                tradingPsychologyService.calculatePsychologicalScore(accountId, startDate, endDate);

        // 틸트 상태 조회
        TiltAnalysis tiltAnalysis = tradingPsychologyService.getCurrentTiltStatus(accountId);

        TiltStatus tiltStatus =
                TiltStatus.builder()
                        .tiltScore(tiltAnalysis.getTiltScore())
                        .tiltLevel(tiltAnalysis.getTiltLevel())
                        .tiltLevelLabel(getTiltLevelLabel(tiltAnalysis.getTiltLevel()))
                        .recommendations(tiltAnalysis.getRecommendations())
                        .build();

        BigDecimal focusScore = BigDecimal.ZERO;
        BigDecimal disciplineScore = BigDecimal.ZERO;
        BigDecimal emotionalStabilityScore = BigDecimal.ZERO;
        BigDecimal resilienceScore = BigDecimal.ZERO;

        if (score.getComponents() != null) {
            focusScore =
                    score.getComponents().getFocusScore() != null
                            ? score.getComponents().getFocusScore()
                            : BigDecimal.ZERO;
            disciplineScore =
                    score.getComponents().getDisciplineScore() != null
                            ? score.getComponents().getDisciplineScore()
                            : BigDecimal.ZERO;
            emotionalStabilityScore =
                    score.getComponents().getEmotionalStabilityScore() != null
                            ? score.getComponents().getEmotionalStabilityScore()
                            : BigDecimal.ZERO;
            resilienceScore =
                    score.getComponents().getResilienceScore() != null
                            ? score.getComponents().getResilienceScore()
                            : BigDecimal.ZERO;
        }

        return PsychologyScoreDetail.builder()
                .overallScore(score.getOverallScore())
                .grade(score.getGrade())
                .focusScore(focusScore)
                .disciplineScore(disciplineScore)
                .emotionalStabilityScore(emotionalStabilityScore)
                .resilienceScore(resilienceScore)
                .tiltStatus(tiltStatus)
                .build();
    }

    /** 거래 통계 조회 */
    private TradingStatisticsSummary fetchTradingStatistics() {
        Map<String, Object> stats = tradingStatisticsService.getOverallStatistics();

        return TradingStatisticsSummary.builder()
                .totalTrades(getIntValue(stats, "totalTrades"))
                .uniqueStocks(getIntValue(stats, "uniqueStocks"))
                .winRate(getDoubleValue(stats, "winRate"))
                .avgReturn(getDoubleValue(stats, "avgReturn"))
                .sharpeRatio(getDoubleValue(stats, "sharpeRatio"))
                .maxDrawdown(getDoubleValue(stats, "maxDrawdown"))
                .avgHoldingPeriod(getDoubleValue(stats, "avgHoldingPeriod"))
                .build();
    }

    // ============================================================
    // Private Methods - Empty Builders (for error handling)
    // ============================================================

    private PortfolioOverview buildEmptyPortfolioOverview() {
        return PortfolioOverview.builder()
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalProfitLossPercent(BigDecimal.ZERO)
                .totalDayChange(BigDecimal.ZERO)
                .totalDayChangePercent(BigDecimal.ZERO)
                .totalRealizedPnl(BigDecimal.ZERO)
                .holdingsCount(0)
                .build();
    }

    private RiskMetricsSummary buildEmptyRiskMetrics() {
        return RiskMetricsSummary.builder()
                .sharpeRatio(BigDecimal.ZERO)
                .sortinoRatio(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .volatility(BigDecimal.ZERO)
                .winRate(BigDecimal.ZERO)
                .profitFactor(BigDecimal.ZERO)
                .riskLevel("LOW")
                .var95(
                        VaRSummary.builder()
                                .dailyVaR(BigDecimal.ZERO)
                                .dailyVaRAmount(BigDecimal.ZERO)
                                .build())
                .build();
    }

    private PsychologyScoreDetail buildEmptyPsychologyScore() {
        return PsychologyScoreDetail.builder()
                .overallScore(BigDecimal.valueOf(50))
                .grade("C")
                .focusScore(BigDecimal.valueOf(50))
                .disciplineScore(BigDecimal.valueOf(50))
                .emotionalStabilityScore(BigDecimal.valueOf(50))
                .resilienceScore(BigDecimal.valueOf(50))
                .tiltStatus(
                        TiltStatus.builder()
                                .tiltScore(0)
                                .tiltLevel("NONE")
                                .tiltLevelLabel("없음")
                                .recommendations(java.util.Collections.emptyList())
                                .build())
                .build();
    }

    private TradingStatisticsSummary buildEmptyTradingStatistics() {
        return TradingStatisticsSummary.builder()
                .totalTrades(0)
                .uniqueStocks(0)
                .winRate(0.0)
                .avgReturn(0.0)
                .sharpeRatio(0.0)
                .maxDrawdown(0.0)
                .avgHoldingPeriod(0.0)
                .build();
    }

    // ============================================================
    // Private Methods - Utilities
    // ============================================================

    /** 틸트 수준 한글 라벨 변환 */
    private String getTiltLevelLabel(String tiltLevel) {
        if (tiltLevel == null) {
            return "없음";
        }
        return switch (tiltLevel) {
            case "SEVERE" -> "심각";
            case "MODERATE" -> "보통";
            case "MILD" -> "경미";
            default -> "없음";
        };
    }

    /** Map에서 int 값 추출 */
    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /** Map에서 double 값 추출 */
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
