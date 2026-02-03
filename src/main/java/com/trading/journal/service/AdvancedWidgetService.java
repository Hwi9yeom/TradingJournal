package com.trading.journal.service;

import com.trading.journal.dto.AdvancedWidgetDto.*;
import com.trading.journal.dto.HarvestingOpportunityDto;
import com.trading.journal.dto.MonteCarloRequestDto;
import com.trading.journal.dto.MonteCarloResultDto;
import com.trading.journal.dto.StressTestRequestDto;
import com.trading.journal.dto.StressTestResultDto;
import com.trading.journal.dto.TaxLossHarvestingDto;
import com.trading.journal.entity.StressScenario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 고급 분석 위젯 데이터 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedWidgetService {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final MonteCarloSimulationService monteCarloService;
    private final StressTestService stressTestService;
    private final TaxLossHarvestingService taxHarvestingService;

    /** 몬테카를로 시뮬레이션 요약 위젯 데이터 */
    public MonteCarloSummaryWidget getMonteCarloSummary() {
        try {
            MonteCarloRequestDto request =
                    MonteCarloRequestDto.builder()
                            .accountId(DEFAULT_ACCOUNT_ID)
                            .numSimulations(1000)
                            .projectionDays(30)
                            .build();

            MonteCarloResultDto result = monteCarloService.runSimulation(request);

            // Get percentile values for best/worst case
            BigDecimal bestCase =
                    result.getPercentileValues() != null
                            ? result.getPercentileValues().getOrDefault("95%", BigDecimal.ZERO)
                            : BigDecimal.ZERO;
            BigDecimal worstCase =
                    result.getPercentileValues() != null
                            ? result.getPercentileValues().getOrDefault("5%", BigDecimal.ZERO)
                            : BigDecimal.ZERO;

            return MonteCarloSummaryWidget.builder()
                    .expectedReturn(result.getMeanFinalValue())
                    .var95(result.getValueAtRisk95())
                    .var99(result.getValueAtRisk99())
                    .cvar95(result.getExpectedShortfall())
                    .bestCase(bestCase)
                    .worstCase(worstCase)
                    .simulationCount(
                            result.getNumSimulations() != null ? result.getNumSimulations() : 1000)
                    .timeHorizonDays(
                            result.getProjectionDays() != null ? result.getProjectionDays() : 30)
                    .lastCalculated(LocalDate.now())
                    .build();
        } catch (Exception e) {
            log.error("Error fetching Monte Carlo summary", e);
            return MonteCarloSummaryWidget.builder().lastCalculated(LocalDate.now()).build();
        }
    }

    /** 스트레스 테스트 요약 위젯 데이터 */
    public StressTestSummaryWidget getStressTestSummary() {
        try {
            List<StressTestResultDto> results = runAllScenarios();

            if (results.isEmpty()) {
                return StressTestSummaryWidget.builder()
                        .scenariosAnalyzed(0)
                        .lastCalculated(LocalDate.now())
                        .build();
            }

            StressTestResultDto worst =
                    results.stream()
                            .min((a, b) -> a.getAbsoluteLoss().compareTo(b.getAbsoluteLoss()))
                            .orElse(results.get(0));

            BigDecimal avgLoss =
                    results.stream()
                            .map(StressTestResultDto::getAbsoluteLoss)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(results.size()), 2, RoundingMode.HALF_UP);

            long criticalCount =
                    results.stream()
                            .filter(
                                    r ->
                                            r.getPercentageLoss() != null
                                                    && r.getPercentageLoss()
                                                                    .compareTo(
                                                                            BigDecimal.valueOf(20))
                                                            > 0)
                            .count();

            return StressTestSummaryWidget.builder()
                    .worstScenarioImpact(worst.getAbsoluteLoss())
                    .worstScenarioName(worst.getScenarioName())
                    .averageImpact(avgLoss)
                    .scenariosAnalyzed(results.size())
                    .criticalScenarios((int) criticalCount)
                    .lastCalculated(LocalDate.now())
                    .build();
        } catch (Exception e) {
            log.error("Error fetching stress test summary", e);
            return StressTestSummaryWidget.builder().lastCalculated(LocalDate.now()).build();
        }
    }

    /** 스트레스 테스트 시나리오 목록 위젯 데이터 */
    public StressTestScenariosWidget getStressTestScenarios() {
        try {
            List<StressTestResultDto> results = runAllScenarios();

            List<StressTestScenarioItem> items =
                    results.stream()
                            .map(
                                    r ->
                                            StressTestScenarioItem.builder()
                                                    .scenarioName(r.getScenarioName())
                                                    .description(r.getScenarioDescription())
                                                    .portfolioImpact(r.getAbsoluteLoss())
                                                    .impactPercent(
                                                            r.getPercentageLoss() != null
                                                                    ? r.getPercentageLoss().negate()
                                                                    : BigDecimal.ZERO)
                                                    .severity(
                                                            determineSeverity(
                                                                    r.getPercentageLoss()))
                                                    .build())
                            .sorted(
                                    (a, b) ->
                                            b.getPortfolioImpact()
                                                    .compareTo(a.getPortfolioImpact()))
                            .collect(Collectors.toList());

            return StressTestScenariosWidget.builder().scenarios(items).build();
        } catch (Exception e) {
            log.error("Error fetching stress test scenarios", e);
            return StressTestScenariosWidget.builder().scenarios(List.of()).build();
        }
    }

    /** Tax-Loss Harvesting 기회 위젯 데이터 */
    public TaxHarvestingWidget getTaxHarvestingOpportunities() {
        try {
            TaxLossHarvestingDto result =
                    taxHarvestingService.analyzeTaxLossHarvestingOpportunities(DEFAULT_ACCOUNT_ID);

            List<HarvestingOpportunityDto> opportunities = result.getOpportunities();

            List<TaxHarvestingOpportunityItem> items =
                    opportunities.stream()
                            .limit(10)
                            .map(
                                    o ->
                                            TaxHarvestingOpportunityItem.builder()
                                                    .symbol(o.getSymbol())
                                                    .stockName(o.getStockName())
                                                    .currentLoss(o.getUnrealizedLoss())
                                                    .potentialTaxSavings(o.getPotentialTaxSavings())
                                                    .washSaleRisk(
                                                            o.getWashSaleRisk() != null
                                                                    && o.getWashSaleRisk())
                                                    .lastPurchaseDate(o.getPurchaseDate())
                                                    .build())
                            .collect(Collectors.toList());

            BigDecimal totalSavings =
                    opportunities.stream()
                            .map(HarvestingOpportunityDto::getPotentialTaxSavings)
                            .filter(s -> s != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            return TaxHarvestingWidget.builder()
                    .opportunities(items)
                    .totalPotentialSavings(totalSavings)
                    .opportunityCount(opportunities.size())
                    .build();
        } catch (Exception e) {
            log.error("Error fetching tax harvesting opportunities", e);
            return TaxHarvestingWidget.builder()
                    .opportunities(List.of())
                    .totalPotentialSavings(BigDecimal.ZERO)
                    .opportunityCount(0)
                    .build();
        }
    }

    /** 모든 시나리오에 대해 스트레스 테스트 실행 */
    private List<StressTestResultDto> runAllScenarios() {
        List<StressScenario> scenarios = stressTestService.getAvailableScenarios();
        List<StressTestResultDto> results = new ArrayList<>();

        for (StressScenario scenario : scenarios) {
            try {
                StressTestRequestDto request =
                        StressTestRequestDto.builder()
                                .accountId(DEFAULT_ACCOUNT_ID)
                                .scenarioId(scenario.getId())
                                .build();

                StressTestResultDto result = stressTestService.runStressTest(request);
                results.add(result);
            } catch (Exception e) {
                log.warn("스트레스 테스트 실행 실패 - scenarioId: {}", scenario.getId(), e);
            }
        }

        return results;
    }

    private String determineSeverity(BigDecimal percentageLoss) {
        if (percentageLoss == null) return "LOW";
        double loss = percentageLoss.doubleValue();
        if (loss > 30) return "CRITICAL";
        if (loss > 20) return "HIGH";
        if (loss > 10) return "MEDIUM";
        return "LOW";
    }
}
