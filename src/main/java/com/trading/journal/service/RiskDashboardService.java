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

    // ===== Risk Threshold Constants =====
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal WARNING_THRESHOLD_PERCENT = BigDecimal.valueOf(80);
    private static final int DECIMAL_SCALE = 4;

    // R-Multiple Distribution Boundaries
    private static final double R_BOUNDARY_NEGATIVE_3 = -3.0;
    private static final double R_BOUNDARY_NEGATIVE_2 = -2.0;
    private static final double R_BOUNDARY_NEGATIVE_1 = -1.0;
    private static final double R_BOUNDARY_ZERO = 0.0;
    private static final double R_BOUNDARY_POSITIVE_1 = 1.0;
    private static final double R_BOUNDARY_POSITIVE_2 = 2.0;
    private static final double R_BOUNDARY_POSITIVE_3 = 3.0;

    // R-Multiple Distribution Labels
    private static final String R_LABEL_BELOW_NEG_3 = "-3R 이하";
    private static final String R_LABEL_NEG_3_TO_NEG_2 = "-3R ~ -2R";
    private static final String R_LABEL_NEG_2_TO_NEG_1 = "-2R ~ -1R";
    private static final String R_LABEL_NEG_1_TO_ZERO = "-1R ~ 0R";
    private static final String R_LABEL_ZERO_TO_POS_1 = "0R ~ 1R";
    private static final String R_LABEL_POS_1_TO_POS_2 = "1R ~ 2R";
    private static final String R_LABEL_POS_2_TO_POS_3 = "2R ~ 3R";
    private static final String R_LABEL_ABOVE_POS_3 = "3R 이상";

    // Status Labels
    private static final String STATUS_BREACHED = "BREACHED";
    private static final String STATUS_WARNING = "WARNING";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_NA = "N/A";

    // Alert Types
    private static final String ALERT_TYPE_STOCK = "STOCK";

    // Historical data start date for queries
    private static final LocalDateTime HISTORICAL_START_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);

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
                totalOpenRisk.divide(capital, DECIMAL_SCALE, RoundingMode.HALF_UP).multiply(PERCENT_MULTIPLIER) :
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
        BigDecimal average = sum.divide(BigDecimal.valueOf(rMultiples.size()), DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal median = rMultiples.get(rMultiples.size() / 2);
        BigDecimal best = rMultiples.get(rMultiples.size() - 1);
        BigDecimal worst = rMultiples.get(0);

        int positiveR = (int) rMultiples.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();
        int negativeR = (int) rMultiples.stream().filter(r -> r.compareTo(BigDecimal.ZERO) < 0).count();

        // 기대값 (Expectancy) - Average R-multiple already represents expectancy
        BigDecimal expectancy = average;

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

        // 배치 로딩: 모든 BUY 거래를 한 번에 조회 (N+1 쿼리 최적화)
        Map<Long, List<Transaction>> buyTransactionsByStock = batchFetchBuyTransactions(targetAccountId);

        // 배치로 가격 조회 (N+1 쿼리 방지)
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);

        List<PositionRiskSummary> risks = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String symbol = portfolio.getStock().getSymbol();
            BigDecimal currentPrice = getPrice(priceMap, symbol, portfolio.getAveragePrice());

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            BigDecimal unrealizedPnl = positionValue.subtract(portfolio.getTotalInvestment());
            BigDecimal unrealizedPnlPercent = portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0 ?
                    unrealizedPnl.divide(portfolio.getTotalInvestment(), DECIMAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(PERCENT_MULTIPLIER) : BigDecimal.ZERO;

            // 손절가 및 초기 리스크 조회 (최근 BUY 거래에서)
            BigDecimal stopLossPrice = null;
            BigDecimal takeProfitPrice = null;
            BigDecimal riskAmount = BigDecimal.ZERO;
            BigDecimal currentR = null;

            // Map에서 해당 종목의 BUY 거래 조회 (이미 로드된 데이터 사용)
            List<Transaction> buyTransactions = buyTransactionsByStock
                    .getOrDefault(portfolio.getStock().getId(), Collections.emptyList());

            if (!buyTransactions.isEmpty()) {
                Transaction latestBuy = buyTransactions.get(buyTransactions.size() - 1);
                stopLossPrice = latestBuy.getStopLossPrice();
                takeProfitPrice = latestBuy.getTakeProfitPrice();

                if (stopLossPrice != null) {
                    riskAmount = portfolio.getAveragePrice().subtract(stopLossPrice).abs()
                            .multiply(portfolio.getQuantity());

                    // 현재 R 계산
                    currentR = calculateCurrentR(latestBuy, currentPrice, portfolio.getAveragePrice());
                }
            }

            BigDecimal positionRiskPercent = capital.compareTo(BigDecimal.ZERO) > 0 ?
                    riskAmount.divide(capital, DECIMAL_SCALE, RoundingMode.HALF_UP).multiply(PERCENT_MULTIPLIER) :
                    BigDecimal.ZERO;

            risks.add(PositionRiskSummary.builder()
                    .stockSymbol(symbol)
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

        // 배치로 가격 조회 (N+1 쿼리 방지)
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);

        // 종목별 집중도 체크
        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String symbol = portfolio.getStock().getSymbol();
            BigDecimal currentPrice = getPrice(priceMap, symbol, portfolio.getAveragePrice());

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            BigDecimal concentration = positionValue.divide(capital, DECIMAL_SCALE, RoundingMode.HALF_UP)
                    .multiply(PERCENT_MULTIPLIER);

            if (concentration.compareTo(settings.getMaxStockConcentrationPercent()) > 0) {
                alerts.add(ConcentrationAlert.builder()
                        .stockSymbol(symbol)
                        .stockName(portfolio.getStock().getName())
                        .sector(portfolio.getStock().getSector())
                        .concentration(concentration)
                        .limit(settings.getMaxStockConcentrationPercent())
                        .alertType(ALERT_TYPE_STOCK)
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

        // 배치로 가격 조회 (N+1 쿼리 방지)
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);

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

            String symbol = portfolio.getStock().getSymbol();
            BigDecimal currentPrice = getPrice(priceMap, symbol, portfolio.getAveragePrice());

            BigDecimal positionValue = portfolio.getQuantity().multiply(currentPrice);
            sectorValues.merge(sector, positionValue, BigDecimal::add);
        }

        BigDecimal sectorLimit = settings.getMaxSectorConcentrationPercent();
        final BigDecimal finalCapital = capital;

        return sectorValues.entrySet().stream()
                .map(entry -> {
                    BigDecimal percentage = entry.getValue()
                            .divide(finalCapital, DECIMAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(PERCENT_MULTIPLIER);
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

    /**
     * 포트폴리오의 모든 종목에 대해 현재 가격을 배치로 조회합니다. (N+1 쿼리 방지)
     * 가격 조회 실패 시 평균 매수가를 fallback으로 사용합니다.
     *
     * @param portfolios 포트폴리오 목록
     * @return 종목 심볼 -> 현재 가격 Map
     */
    private Map<String, BigDecimal> batchFetchCurrentPrices(List<Portfolio> portfolios) {
        Map<String, BigDecimal> priceMap = new HashMap<>();

        // 활성 포지션의 심볼만 추출
        List<String> symbols = portfolios.stream()
                .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(p -> p.getStock().getSymbol())
                .distinct()
                .collect(Collectors.toList());

        if (symbols.isEmpty()) {
            return priceMap;
        }

        // 배치로 가격 조회 시도
        for (String symbol : symbols) {
            try {
                BigDecimal price = stockPriceService.getCurrentPrice(symbol);
                priceMap.put(symbol, price);
            } catch (Exception e) {
                log.debug("Price fetch failed for {}, will use average price as fallback: {}",
                        symbol, e.getMessage());
                // fallback 가격은 호출 시점에 처리
            }
        }

        return priceMap;
    }

    /**
     * 캐시된 가격 Map에서 현재 가격을 조회합니다.
     * 가격이 없으면 fallback 가격을 반환합니다.
     *
     * @param priceMap 캐시된 가격 Map
     * @param symbol 종목 심볼
     * @param fallbackPrice fallback 가격 (일반적으로 평균 매수가)
     * @return 현재 가격 또는 fallback 가격
     */
    private BigDecimal getPrice(Map<String, BigDecimal> priceMap, String symbol, BigDecimal fallbackPrice) {
        return priceMap.getOrDefault(symbol, fallbackPrice);
    }

    /**
     * 계정의 모든 BUY 거래를 배치로 조회합니다. (N+1 쿼리 방지)
     *
     * @param accountId 계정 ID
     * @return 종목 ID -> BUY 거래 리스트 Map
     */
    private Map<Long, List<Transaction>> batchFetchBuyTransactions(Long accountId) {
        return transactionRepository
                .findByAccountIdAndTypeAndDateRange(
                        accountId,
                        com.trading.journal.entity.TransactionType.BUY,
                        HISTORICAL_START_DATE,
                        LocalDateTime.now())
                .stream()
                .collect(Collectors.groupingBy(t -> t.getStock().getId()));
    }

    /**
     * 현재 R-Multiple 값을 계산합니다.
     *
     * @param buyTransaction 매수 거래
     * @param currentPrice 현재 가격
     * @param averagePrice 평균 매수가
     * @return 현재 R 값 (계산 불가 시 null)
     */
    private BigDecimal calculateCurrentR(Transaction buyTransaction, BigDecimal currentPrice, BigDecimal averagePrice) {
        if (buyTransaction.getInitialRiskAmount() == null ||
            buyTransaction.getInitialRiskAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal avgRiskPerShare = buyTransaction.getInitialRiskAmount()
                .divide(buyTransaction.getQuantity(), DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal currentRiskPerShare = currentPrice.subtract(averagePrice);
        return currentRiskPerShare.divide(avgRiskPerShare, DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalPortfolioValue(Long accountId) {
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(accountId);

        // 배치로 가격 조회 (N+1 쿼리 방지)
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);

        BigDecimal total = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String symbol = portfolio.getStock().getSymbol();
            BigDecimal currentPrice = getPrice(priceMap, symbol, portfolio.getAveragePrice());

            total = total.add(portfolio.getQuantity().multiply(currentPrice));
        }

        return total;
    }

    private BigDecimal calculateTotalOpenRisk(Long accountId) {
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(accountId);
        BigDecimal totalRisk = BigDecimal.ZERO;

        // 배치 로딩: 모든 BUY 거래를 한 번에 조회 (N+1 쿼리 최적화)
        Map<Long, List<Transaction>> buyTransactionsByStock = batchFetchBuyTransactions(accountId);

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Map에서 해당 종목의 BUY 거래 조회 (이미 로드된 데이터 사용)
            List<Transaction> buyTransactions = buyTransactionsByStock
                    .getOrDefault(portfolio.getStock().getId(), Collections.emptyList());

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
        return pnl.divide(capital, DECIMAL_SCALE, RoundingMode.HALF_UP).multiply(PERCENT_MULTIPLIER);
    }

    private RiskLimitStatus calculateDailyLossStatus(Long accountId, AccountRiskSettingsDto settings,
                                                      BigDecimal todayPnl) {
        BigDecimal capital = settings.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) == 0) {
            return RiskLimitStatus.builder().statusLabel(STATUS_NA).build();
        }

        BigDecimal limit = capital.multiply(settings.getMaxDailyLossPercent())
                .divide(PERCENT_MULTIPLIER, DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal current = todayPnl.negate(); // 손실은 양수로 표시
        BigDecimal remaining = limit.subtract(current.max(BigDecimal.ZERO));
        BigDecimal percentUsed = current.compareTo(BigDecimal.ZERO) > 0 ?
                current.divide(limit, DECIMAL_SCALE, RoundingMode.HALF_UP).multiply(PERCENT_MULTIPLIER) :
                BigDecimal.ZERO;
        boolean breached = todayPnl.compareTo(limit.negate()) < 0;

        String statusLabel = determineStatusLabel(breached, percentUsed);

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
            return RiskLimitStatus.builder().statusLabel(STATUS_NA).build();
        }

        BigDecimal limit = capital.multiply(settings.getMaxWeeklyLossPercent())
                .divide(PERCENT_MULTIPLIER, DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal current = weekPnl.negate();
        BigDecimal remaining = limit.subtract(current.max(BigDecimal.ZERO));
        BigDecimal percentUsed = current.compareTo(BigDecimal.ZERO) > 0 ?
                current.divide(limit, DECIMAL_SCALE, RoundingMode.HALF_UP).multiply(PERCENT_MULTIPLIER) :
                BigDecimal.ZERO;
        boolean breached = weekPnl.compareTo(limit.negate()) < 0;

        String statusLabel = determineStatusLabel(breached, percentUsed);

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
                .divide(BigDecimal.valueOf(limit), DECIMAL_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT_MULTIPLIER);
        boolean breached = currentPositions >= limit;

        String statusLabel = determineStatusLabel(breached, percentUsed);

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
        distribution.put(R_LABEL_BELOW_NEG_3, 0);
        distribution.put(R_LABEL_NEG_3_TO_NEG_2, 0);
        distribution.put(R_LABEL_NEG_2_TO_NEG_1, 0);
        distribution.put(R_LABEL_NEG_1_TO_ZERO, 0);
        distribution.put(R_LABEL_ZERO_TO_POS_1, 0);
        distribution.put(R_LABEL_POS_1_TO_POS_2, 0);
        distribution.put(R_LABEL_POS_2_TO_POS_3, 0);
        distribution.put(R_LABEL_ABOVE_POS_3, 0);

        for (BigDecimal r : rMultiples) {
            double rValue = r.doubleValue();
            String bucket = categorizeRMultiple(rValue);
            distribution.merge(bucket, 1, Integer::sum);
        }

        int total = rMultiples.size();
        return distribution.entrySet().stream()
                .map(entry -> RMultipleDistribution.builder()
                        .range(entry.getKey())
                        .count(entry.getValue())
                        .percentage(BigDecimal.valueOf(entry.getValue())
                                .divide(BigDecimal.valueOf(total), DECIMAL_SCALE, RoundingMode.HALF_UP)
                                .multiply(PERCENT_MULTIPLIER))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * R-Multiple 값을 해당 범위 버킷으로 분류합니다.
     *
     * @param rValue R-Multiple 값
     * @return 범위 레이블
     */
    private String categorizeRMultiple(double rValue) {
        if (rValue <= R_BOUNDARY_NEGATIVE_3) {
            return R_LABEL_BELOW_NEG_3;
        } else if (rValue <= R_BOUNDARY_NEGATIVE_2) {
            return R_LABEL_NEG_3_TO_NEG_2;
        } else if (rValue <= R_BOUNDARY_NEGATIVE_1) {
            return R_LABEL_NEG_2_TO_NEG_1;
        } else if (rValue <= R_BOUNDARY_ZERO) {
            return R_LABEL_NEG_1_TO_ZERO;
        } else if (rValue <= R_BOUNDARY_POSITIVE_1) {
            return R_LABEL_ZERO_TO_POS_1;
        } else if (rValue <= R_BOUNDARY_POSITIVE_2) {
            return R_LABEL_POS_1_TO_POS_2;
        } else if (rValue <= R_BOUNDARY_POSITIVE_3) {
            return R_LABEL_POS_2_TO_POS_3;
        } else {
            return R_LABEL_ABOVE_POS_3;
        }
    }

    /**
     * 리스크 한도 상태 레이블을 결정합니다.
     *
     * @param breached 한도 초과 여부
     * @param percentUsed 사용된 비율
     * @return 상태 레이블 (BREACHED, WARNING, OK)
     */
    private String determineStatusLabel(boolean breached, BigDecimal percentUsed) {
        if (breached) {
            return STATUS_BREACHED;
        }
        return percentUsed.compareTo(WARNING_THRESHOLD_PERCENT) > 0 ? STATUS_WARNING : STATUS_OK;
    }
}
