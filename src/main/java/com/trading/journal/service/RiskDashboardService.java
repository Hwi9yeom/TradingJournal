package com.trading.journal.service;

import com.trading.journal.dto.AccountRiskSettingsDto;
import com.trading.journal.dto.RiskDashboardDto;
import com.trading.journal.dto.RiskDashboardDto.*;
import com.trading.journal.dto.RiskMetricsDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Sector;
import com.trading.journal.entity.Transaction;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RiskDashboardService {

    private final AccountRiskSettingsService riskSettingsService;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final RiskMetricsService riskMetricsService;
    private final StockPriceService stockPriceService;
    private final AccountService accountService;

    /**
     * 종합 리스크 대시보드 조회
     */
    @Cacheable(value = "risk", key = "'dashboard_' + #accountId")
    public RiskDashboardDto getRiskDashboard(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettingsDto settings = riskSettingsService.getRiskSettings(targetAccountId);
        BigDecimal capital = settings.getAccountCapital() != null ?
                settings.getAccountCapital() : BigDecimal.ZERO;

        // 포트폴리오 가치 계산
        BigDecimal totalPortfolioValue = calculateTotalPortfolioValue(targetAccountId);

        // 오픈 리스크 계산
        BigDecimal totalOpenRisk = calculateTotalOpenRisk(targetAccountId);
        BigDecimal openRiskPercent = capital.compareTo(BigDecimal.ZERO) > 0 ?
                totalOpenRisk.divide(capital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // P&L 계산
        BigDecimal todayPnl = riskSettingsService.getTodayPnl(targetAccountId);
        BigDecimal weekPnl = riskSettingsService.getWeekPnl(targetAccountId);
        BigDecimal monthPnl = getMonthPnl(targetAccountId);

        BigDecimal todayPnlPercent = calculatePnlPercent(todayPnl, capital);
        BigDecimal weekPnlPercent = calculatePnlPercent(weekPnl, capital);
        BigDecimal monthPnlPercent = calculatePnlPercent(monthPnl, capital);

        // 한도 상태
        RiskLimitStatus dailyStatus = calculateDailyLossStatus(targetAccountId, settings, todayPnl);
        RiskLimitStatus weeklyStatus = calculateWeeklyLossStatus(targetAccountId, settings, weekPnl);
        RiskLimitStatus positionStatus = calculatePositionCountStatus(targetAccountId, settings);

        // 집중도 알림
        List<ConcentrationAlert> alerts = checkConcentrationLimits(targetAccountId, settings);

        // R-multiple 분석
        RMultipleAnalysis rMultipleAnalysis = analyzeRMultiples(targetAccountId,
                LocalDate.now().minusMonths(3), LocalDate.now());

        // 포지션별 리스크
        List<PositionRiskSummary> positionRisks = getPositionRisks(targetAccountId);

        // 섹터 노출
        List<SectorExposure> sectorExposures = getSectorExposures(targetAccountId, settings);

        // 기존 리스크 지표
        RiskMetricsDto riskMetrics = null;
        try {
            riskMetrics = riskMetricsService.calculateRiskMetrics(targetAccountId,
                    LocalDate.now().minusMonths(6), LocalDate.now());
        } catch (Exception e) {
            log.warn("Failed to get risk metrics: {}", e.getMessage());
        }

        return RiskDashboardDto.builder()
                .totalPortfolioValue(totalPortfolioValue)
                .totalOpenRisk(totalOpenRisk)
                .openRiskPercent(openRiskPercent)
                .accountCapital(capital)
                .todayPnl(todayPnl)
                .todayPnlPercent(todayPnlPercent)
                .weekPnl(weekPnl)
                .weekPnlPercent(weekPnlPercent)
                .monthPnl(monthPnl)
                .monthPnlPercent(monthPnlPercent)
                .dailyLossStatus(dailyStatus)
                .weeklyLossStatus(weeklyStatus)
                .positionCountStatus(positionStatus)
                .concentrationAlerts(alerts)
                .rMultipleAnalysis(rMultipleAnalysis)
                .positionRisks(positionRisks)
                .sectorExposures(sectorExposures)
                .riskMetrics(riskMetrics)
                .build();
    }

    /**
     * R-multiple 분석
     */
    public RMultipleAnalysis analyzeRMultiples(Long accountId, LocalDate startDate, LocalDate endDate) {
        Long targetAccountId = resolveAccountId(accountId);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findSellTransactionsWithRMultiple(
                targetAccountId, start, end);

        if (transactions.isEmpty()) {
            return RMultipleAnalysis.builder()
                    .averageRMultiple(BigDecimal.ZERO)
                    .tradesWithPositiveR(0)
                    .tradesWithNegativeR(0)
                    .distribution(Collections.emptyList())
                    .build();
        }

        List<BigDecimal> rMultiples = transactions.stream()
                .map(Transaction::getRMultiple)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (rMultiples.isEmpty()) {
            return RMultipleAnalysis.builder()
                    .averageRMultiple(BigDecimal.ZERO)
                    .tradesWithPositiveR(0)
                    .tradesWithNegativeR(0)
                    .distribution(Collections.emptyList())
                    .build();
        }

        // 통계 계산
        BigDecimal sum = rMultiples.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(rMultiples.size()), 4, RoundingMode.HALF_UP);
        BigDecimal median = rMultiples.get(rMultiples.size() / 2);
        BigDecimal best = rMultiples.get(rMultiples.size() - 1);
        BigDecimal worst = rMultiples.get(0);

        int positiveR = (int) rMultiples.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();
        int negativeR = (int) rMultiples.stream().filter(r -> r.compareTo(BigDecimal.ZERO) < 0).count();

        // 기대값 (Expectancy)
        BigDecimal winRate = BigDecimal.valueOf(positiveR)
                .divide(BigDecimal.valueOf(rMultiples.size()), 4, RoundingMode.HALF_UP);
        BigDecimal expectancy = average.multiply(winRate);

        // 분포 계산
        List<RMultipleDistribution> distribution = calculateRMultipleDistribution(rMultiples);

        return RMultipleAnalysis.builder()
                .averageRMultiple(average)
                .medianRMultiple(median)
                .bestRMultiple(best)
                .worstRMultiple(worst)
                .tradesWithPositiveR(positiveR)
                .tradesWithNegativeR(negativeR)
                .expectancy(expectancy)
                .distribution(distribution)
                .build();
    }

    /**
     * 포지션별 리스크 요약
     */
    public List<PositionRiskSummary> getPositionRisks(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(targetAccountId);

        AccountRiskSettingsDto settings = riskSettingsService.getRiskSettings(targetAccountId);
        BigDecimal capital = settings.getAccountCapital() != null ?
                settings.getAccountCapital() : BigDecimal.ZERO;

        List<PositionRiskSummary> risks = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = BigDecimal.ZERO;
            try {
                currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            } catch (Exception e) {
                log.warn("Failed to get current price for {}", portfolio.getStock().getSymbol());
                currentPrice = portfolio.getAveragePrice();
            }

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            BigDecimal unrealizedPnl = positionValue.subtract(portfolio.getTotalInvestment());
            BigDecimal unrealizedPnlPercent = portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0 ?
                    unrealizedPnl.divide(portfolio.getTotalInvestment(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

            // 손절가 및 초기 리스크 조회 (최근 BUY 거래에서)
            BigDecimal stopLossPrice = null;
            BigDecimal takeProfitPrice = null;
            BigDecimal riskAmount = BigDecimal.ZERO;
            BigDecimal currentR = null;

            // 해당 종목의 BUY 거래 조회
            List<Transaction> buyTransactions = transactionRepository
                    .findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            targetAccountId, portfolio.getStock().getId(),
                            com.trading.journal.entity.TransactionType.BUY);

            if (!buyTransactions.isEmpty()) {
                Transaction latestBuy = buyTransactions.get(buyTransactions.size() - 1);
                stopLossPrice = latestBuy.getStopLossPrice();
                takeProfitPrice = latestBuy.getTakeProfitPrice();

                if (stopLossPrice != null) {
                    riskAmount = portfolio.getAveragePrice().subtract(stopLossPrice).abs()
                            .multiply(portfolio.getQuantity());

                    // 현재 R 계산
                    if (latestBuy.getInitialRiskAmount() != null &&
                        latestBuy.getInitialRiskAmount().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgRiskPerShare = latestBuy.getInitialRiskAmount()
                                .divide(latestBuy.getQuantity(), 4, RoundingMode.HALF_UP);
                        BigDecimal currentRiskPerShare = currentPrice.subtract(portfolio.getAveragePrice());
                        currentR = currentRiskPerShare.divide(avgRiskPerShare, 4, RoundingMode.HALF_UP);
                    }
                }
            }

            BigDecimal positionRiskPercent = capital.compareTo(BigDecimal.ZERO) > 0 ?
                    riskAmount.divide(capital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;

            risks.add(PositionRiskSummary.builder()
                    .stockSymbol(portfolio.getStock().getSymbol())
                    .stockName(portfolio.getStock().getName())
                    .quantity(portfolio.getQuantity())
                    .entryPrice(portfolio.getAveragePrice())
                    .currentPrice(currentPrice)
                    .stopLossPrice(stopLossPrice)
                    .takeProfitPrice(takeProfitPrice)
                    .unrealizedPnl(unrealizedPnl)
                    .unrealizedPnlPercent(unrealizedPnlPercent)
                    .riskAmount(riskAmount)
                    .positionRiskPercent(positionRiskPercent)
                    .currentR(currentR)
                    .build());
        }

        return risks;
    }

    /**
     * 집중도 한도 체크
     */
    public List<ConcentrationAlert> checkConcentrationLimits(Long accountId, AccountRiskSettingsDto settings) {
        Long targetAccountId = resolveAccountId(accountId);
        List<ConcentrationAlert> alerts = new ArrayList<>();

        if (settings == null) {
            settings = riskSettingsService.getRiskSettings(targetAccountId);
        }

        BigDecimal capital = settings.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            return alerts;
        }

        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(targetAccountId);

        // 종목별 집중도 체크
        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = portfolio.getAveragePrice();
            try {
                currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            } catch (Exception e) {
                // ignore
            }

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            BigDecimal concentration = positionValue.divide(capital, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (concentration.compareTo(settings.getMaxStockConcentrationPercent()) > 0) {
                alerts.add(ConcentrationAlert.builder()
                        .stockSymbol(portfolio.getStock().getSymbol())
                        .stockName(portfolio.getStock().getName())
                        .sector(portfolio.getStock().getSector())
                        .concentration(concentration)
                        .limit(settings.getMaxStockConcentrationPercent())
                        .alertType("STOCK")
                        .build());
            }
        }

        return alerts;
    }

    /**
     * 섹터 노출 조회
     */
    public List<SectorExposure> getSectorExposures(Long accountId, AccountRiskSettingsDto settings) {
        Long targetAccountId = resolveAccountId(accountId);

        if (settings == null) {
            settings = riskSettingsService.getRiskSettings(targetAccountId);
        }

        BigDecimal capital = settings.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            capital = BigDecimal.ONE; // Avoid division by zero
        }

        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(targetAccountId);

        // 섹터별 합계
        Map<Sector, BigDecimal> sectorValues = new HashMap<>();

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Sector sector = portfolio.getStock().getSector();
            if (sector == null) {
                sector = Sector.OTHER;
            }

            BigDecimal currentPrice = portfolio.getAveragePrice();
            try {
                currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            } catch (Exception e) {
                // ignore
            }

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            sectorValues.merge(sector, positionValue, BigDecimal::add);
        }

        BigDecimal sectorLimit = settings.getMaxSectorConcentrationPercent();
        final BigDecimal finalCapital = capital;

        return sectorValues.entrySet().stream()
                .map(entry -> {
                    BigDecimal percentage = entry.getValue()
                            .divide(finalCapital, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    return SectorExposure.builder()
                            .sector(entry.getKey())
                            .sectorLabel(entry.getKey().getLabel())
                            .value(entry.getValue())
                            .percentage(percentage)
                            .limit(sectorLimit)
                            .exceedsLimit(percentage.compareTo(sectorLimit) > 0)
                            .build();
                })
                .sorted((a, b) -> b.getPercentage().compareTo(a.getPercentage()))
                .collect(Collectors.toList());
    }

    // ===== Helper Methods =====

    private Long resolveAccountId(Long accountId) {
        if (accountId == null) {
            return accountService.getDefaultAccount().getId();
        }
        return accountId;
    }

    private BigDecimal calculateTotalPortfolioValue(Long accountId) {
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(accountId);
        BigDecimal total = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentPrice = portfolio.getAveragePrice();
            try {
                currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            } catch (Exception e) {
                // ignore
            }

            total = total.add(portfolio.getQuantity().multiply(currentPrice));
        }

        return total;
    }

    private BigDecimal calculateTotalOpenRisk(Long accountId) {
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(accountId);
        BigDecimal totalRisk = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 해당 종목의 BUY 거래에서 손절가 조회
            List<Transaction> buyTransactions = transactionRepository
                    .findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            accountId, portfolio.getStock().getId(),
                            com.trading.journal.entity.TransactionType.BUY);

            if (!buyTransactions.isEmpty()) {
                Transaction latestBuy = buyTransactions.get(buyTransactions.size() - 1);
                if (latestBuy.getStopLossPrice() != null) {
                    BigDecimal riskPerShare = portfolio.getAveragePrice()
                            .subtract(latestBuy.getStopLossPrice()).abs();
                    totalRisk = totalRisk.add(riskPerShare.multiply(portfolio.getQuantity()));
                }
            }
        }

        return totalRisk;
    }

    private BigDecimal getMonthPnl(Long accountId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDateTime start = startOfMonth.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        return transactionRepository.sumRealizedPnlByAccountAndDateRange(accountId, start, end);
    }

    private BigDecimal calculatePnlPercent(BigDecimal pnl, BigDecimal capital) {
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pnl.divide(capital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private RiskLimitStatus calculateDailyLossStatus(Long accountId, AccountRiskSettingsDto settings,
                                                      BigDecimal todayPnl) {
        BigDecimal capital = settings.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            return RiskLimitStatus.builder().statusLabel("N/A").build();
        }

        BigDecimal limit = capital.multiply(settings.getMaxDailyLossPercent())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal current = todayPnl.negate(); // 손실은 양수로 표시
        BigDecimal remaining = limit.subtract(current.max(BigDecimal.ZERO));
        BigDecimal percentUsed = current.compareTo(BigDecimal.ZERO) > 0 ?
                current.divide(limit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
        boolean breached = todayPnl.compareTo(limit.negate()) < 0;

        String statusLabel = breached ? "BREACHED" :
                (percentUsed.compareTo(BigDecimal.valueOf(80)) > 0 ? "WARNING" : "OK");

        return RiskLimitStatus.builder()
                .limit(limit)
                .current(current)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .isBreached(breached)
                .statusLabel(statusLabel)
                .build();
    }

    private RiskLimitStatus calculateWeeklyLossStatus(Long accountId, AccountRiskSettingsDto settings,
                                                       BigDecimal weekPnl) {
        BigDecimal capital = settings.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            return RiskLimitStatus.builder().statusLabel("N/A").build();
        }

        BigDecimal limit = capital.multiply(settings.getMaxWeeklyLossPercent())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal current = weekPnl.negate();
        BigDecimal remaining = limit.subtract(current.max(BigDecimal.ZERO));
        BigDecimal percentUsed = current.compareTo(BigDecimal.ZERO) > 0 ?
                current.divide(limit, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
        boolean breached = weekPnl.compareTo(limit.negate()) < 0;

        String statusLabel = breached ? "BREACHED" :
                (percentUsed.compareTo(BigDecimal.valueOf(80)) > 0 ? "WARNING" : "OK");

        return RiskLimitStatus.builder()
                .limit(limit)
                .current(current)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .isBreached(breached)
                .statusLabel(statusLabel)
                .build();
    }

    private RiskLimitStatus calculatePositionCountStatus(Long accountId, AccountRiskSettingsDto settings) {
        int currentPositions = portfolioRepository.countActivePositionsByAccountId(accountId);
        int limit = settings.getMaxOpenPositions();
        int remaining = limit - currentPositions;
        BigDecimal percentUsed = BigDecimal.valueOf(currentPositions)
                .divide(BigDecimal.valueOf(limit), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        boolean breached = currentPositions >= limit;

        String statusLabel = breached ? "BREACHED" :
                (percentUsed.compareTo(BigDecimal.valueOf(80)) > 0 ? "WARNING" : "OK");

        return RiskLimitStatus.builder()
                .limit(BigDecimal.valueOf(limit))
                .current(BigDecimal.valueOf(currentPositions))
                .remaining(BigDecimal.valueOf(remaining))
                .percentUsed(percentUsed)
                .isBreached(breached)
                .statusLabel(statusLabel)
                .build();
    }

    private List<RMultipleDistribution> calculateRMultipleDistribution(List<BigDecimal> rMultiples) {
        // R-multiple 범위별 분포 계산
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("-3R 이하", 0);
        distribution.put("-3R ~ -2R", 0);
        distribution.put("-2R ~ -1R", 0);
        distribution.put("-1R ~ 0R", 0);
        distribution.put("0R ~ 1R", 0);
        distribution.put("1R ~ 2R", 0);
        distribution.put("2R ~ 3R", 0);
        distribution.put("3R 이상", 0);

        for (BigDecimal r : rMultiples) {
            double rValue = r.doubleValue();
            if (rValue <= -3) distribution.merge("-3R 이하", 1, Integer::sum);
            else if (rValue <= -2) distribution.merge("-3R ~ -2R", 1, Integer::sum);
            else if (rValue <= -1) distribution.merge("-2R ~ -1R", 1, Integer::sum);
            else if (rValue <= 0) distribution.merge("-1R ~ 0R", 1, Integer::sum);
            else if (rValue <= 1) distribution.merge("0R ~ 1R", 1, Integer::sum);
            else if (rValue <= 2) distribution.merge("1R ~ 2R", 1, Integer::sum);
            else if (rValue <= 3) distribution.merge("2R ~ 3R", 1, Integer::sum);
            else distribution.merge("3R 이상", 1, Integer::sum);
        }

        int total = rMultiples.size();
        return distribution.entrySet().stream()
                .map(entry -> RMultipleDistribution.builder()
                        .range(entry.getKey())
                        .count(entry.getValue())
                        .percentage(BigDecimal.valueOf(entry.getValue())
                                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)))
                        .build())
                .collect(Collectors.toList());
    }
}
