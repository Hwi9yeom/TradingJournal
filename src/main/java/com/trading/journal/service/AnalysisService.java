package com.trading.journal.service;

import com.trading.journal.dto.CorrelationMatrixDto;
import com.trading.journal.dto.DrawdownDto;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.PairCorrelationDto;
import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.dto.RollingCorrelationDto;
import com.trading.journal.dto.SectorCorrelationDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalysisService {

    // ==================== CONSTANTS ====================

    /** Percentage multiplier for converting decimal to percentage */
    private static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");

    /** Threshold for major drawdown events (5%) */
    private static final BigDecimal MAJOR_DRAWDOWN_THRESHOLD = new BigDecimal("5");

    /** Threshold for identifying negative correlation (-0.3) */
    private static final BigDecimal NEGATIVE_CORRELATION_THRESHOLD = new BigDecimal("-0.3");

    /** Threshold for identifying low correlation (0.3) */
    private static final BigDecimal LOW_CORRELATION_THRESHOLD = new BigDecimal("0.3");

    /** Diversification score divisor for normalization */
    private static final BigDecimal DIVERSIFICATION_DIVISOR = new BigDecimal("2");

    /** Minimum transactions required for correlation analysis */
    private static final int MIN_TRANSACTIONS_FOR_CORRELATION = 2;

    /** Minimum data points required for statistical calculations */
    private static final int MIN_DATA_POINTS_FOR_STATISTICS = 2;

    /** Maximum diversification score */
    private static final BigDecimal MAX_DIVERSIFICATION_SCORE = new BigDecimal("100");

    /** Number of top diversification recommendations to return */
    private static final int TOP_DIVERSIFICATION_RECOMMENDATIONS = 5;

    /** Default scale for financial calculations */
    private static final int FINANCIAL_SCALE = 4;

    /** Extended scale for intermediate calculations */
    private static final int EXTENDED_SCALE = 6;

    /** Days per year for CAGR calculation */
    private static final double DAYS_PER_YEAR = 365.0;

    // ==================== DEPENDENCIES ====================

    private final TransactionRepository transactionRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;

    @Cacheable(value = "analysis", key = "#startDate + '_' + #endDate")
    public PeriodAnalysisDto analyzePeriod(LocalDate startDate, LocalDate endDate) {
        log.debug("Analyzing period from {} to {}", startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(startDateTime, endDateTime);
        log.debug("Found {} transactions for period analysis", transactions.size());

        // 기본 통계 계산
        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        int buyCount = 0;
        int sellCount = 0;

        for (Transaction transaction : transactions) {
            if (transaction.getType() == TransactionType.BUY) {
                totalBuyAmount = totalBuyAmount.add(transaction.getTotalAmount());
                buyCount++;
            } else {
                totalSellAmount = totalSellAmount.add(transaction.getTotalAmount());
                sellCount++;
            }
        }

        // 실현 손익 계산
        BigDecimal realizedProfit = calculateRealizedProfit(transactions);
        BigDecimal realizedProfitRate = BigDecimal.ZERO;
        if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            realizedProfitRate =
                    realizedProfit
                            .divide(totalBuyAmount, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(PERCENTAGE_MULTIPLIER);
        }

        // 미실현 손익 계산 (현재 포트폴리오 기준)
        PortfolioSummaryDto portfolioSummary = portfolioAnalysisService.getPortfolioSummary();
        BigDecimal unrealizedProfit = portfolioSummary.getTotalProfitLoss();
        BigDecimal unrealizedProfitRate = portfolioSummary.getTotalProfitLossPercent();

        // 총 손익
        BigDecimal totalProfit = realizedProfit.add(unrealizedProfit);
        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate =
                    totalProfit
                            .divide(totalBuyAmount, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(PERCENTAGE_MULTIPLIER);
        }

        // 월별 분석
        List<PeriodAnalysisDto.MonthlyAnalysisDto> monthlyAnalysis =
                analyzeMonthly(transactions, startDate, endDate);

        return PeriodAnalysisDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalBuyAmount(totalBuyAmount)
                .totalSellAmount(totalSellAmount)
                .realizedProfit(realizedProfit)
                .realizedProfitRate(realizedProfitRate)
                .unrealizedProfit(unrealizedProfit)
                .unrealizedProfitRate(unrealizedProfitRate)
                .totalProfit(totalProfit)
                .totalProfitRate(totalProfitRate)
                .totalTransactions(transactions.size())
                .buyTransactions(buyCount)
                .sellTransactions(sellCount)
                .monthlyAnalysis(monthlyAnalysis)
                .build();
    }

    private BigDecimal calculateRealizedProfit(List<Transaction> transactions) {
        // FIFO 방식으로 계산된 realizedPnl 합계 사용
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(Transaction::getRealizedPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PeriodAnalysisDto.MonthlyAnalysisDto> analyzeMonthly(
            List<Transaction> transactions, LocalDate startDate, LocalDate endDate) {

        Map<YearMonth, List<Transaction>> monthlyTransactions =
                transactions.stream()
                        .collect(
                                Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate())));

        List<PeriodAnalysisDto.MonthlyAnalysisDto> monthlyAnalysis = new ArrayList<>();

        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        while (!current.isAfter(end)) {
            List<Transaction> monthTransactions =
                    monthlyTransactions.getOrDefault(current, new ArrayList<>());

            BigDecimal buyAmount =
                    monthTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.BUY)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sellAmount =
                    monthTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // FIFO 기반 실현 손익 사용
            BigDecimal profit =
                    monthTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .map(Transaction::getRealizedPnl)
                            .filter(pnl -> pnl != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal profitRate = BigDecimal.ZERO;
            BigDecimal costBasis =
                    monthTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .map(Transaction::getCostBasis)
                            .filter(cb -> cb != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                profitRate =
                        profit.divide(costBasis, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                                .multiply(PERCENTAGE_MULTIPLIER);
            }

            monthlyAnalysis.add(
                    PeriodAnalysisDto.MonthlyAnalysisDto.builder()
                            .yearMonth(current.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                            .buyAmount(buyAmount)
                            .sellAmount(sellAmount)
                            .profit(profit)
                            .profitRate(profitRate)
                            .transactionCount(monthTransactions.size())
                            .build());

            current = current.plusMonths(1);
        }

        return monthlyAnalysis;
    }

    /** Equity Curve (누적 수익 곡선) 계산 accountId가 null이면 전체, 있으면 해당 계좌만 조회 */
    @Cacheable(
            value = "analysis",
            key = "'equity_curve_' + #accountId + '_' + #startDate + '_' + #endDate")
    public EquityCurveDto calculateEquityCurve(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug(
                "Calculating equity curve for accountId={} from {} to {}",
                accountId,
                startDate,
                endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions =
                (accountId == null)
                        ? transactionRepository.findByDateRange(startDateTime, endDateTime)
                        : transactionRepository.findByAccountIdAndDateRange(
                                accountId, startDateTime, endDateTime);
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        if (transactions.isEmpty()) {
            log.debug("No transactions found for equity curve calculation");
            return emptyEquityCurve();
        }

        log.debug("Processing {} transactions for equity curve", transactions.size());

        // 일별 투자금/가치 계산
        Map<LocalDate, BigDecimal> dailyInvestment = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyValue = new TreeMap<>();
        BigDecimal runningInvestment = BigDecimal.ZERO;
        BigDecimal runningValue = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            LocalDate date = tx.getTransactionDate().toLocalDate();
            if (tx.getType() == TransactionType.BUY) {
                runningInvestment = runningInvestment.add(tx.getTotalAmount());
                runningValue = runningValue.add(tx.getTotalAmount());
            } else {
                BigDecimal pnl =
                        tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO;
                BigDecimal cost =
                        tx.getCostBasis() != null ? tx.getCostBasis() : tx.getTotalAmount();
                runningValue = runningValue.subtract(cost).add(pnl);
            }
            dailyInvestment.put(date, runningInvestment);
            dailyValue.put(date, runningValue);
        }

        // 결과 리스트 구성
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> cumulativeReturns = new ArrayList<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();

        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvestment = BigDecimal.ZERO;
        BigDecimal prevValue = null;
        BigDecimal initialInvestment = null;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (dailyValue.containsKey(d)) {
                lastValue = dailyValue.get(d);
                lastInvestment = dailyInvestment.get(d);
            }
            if (initialInvestment == null && lastInvestment.compareTo(BigDecimal.ZERO) > 0) {
                initialInvestment = lastInvestment;
            }

            labels.add(d.format(fmt));
            values.add(lastValue);
            cumulativeReturns.add(
                    calcReturnPct(lastValue.subtract(lastInvestment), lastInvestment));
            dailyReturns.add(
                    prevValue != null
                            ? calcReturnPct(lastValue.subtract(prevValue), prevValue)
                            : BigDecimal.ZERO);
            prevValue = lastValue;
        }

        BigDecimal totalReturn =
                initialInvestment != null
                        ? calcReturnPct(lastValue.subtract(initialInvestment), initialInvestment)
                        : BigDecimal.ZERO;
        BigDecimal cagr = calcCagr(initialInvestment, lastValue, startDate, endDate);

        return EquityCurveDto.builder()
                .labels(labels)
                .values(values)
                .cumulativeReturns(cumulativeReturns)
                .dailyReturns(dailyReturns)
                .initialInvestment(initialInvestment != null ? initialInvestment : BigDecimal.ZERO)
                .finalValue(lastValue)
                .totalReturn(totalReturn)
                .cagr(cagr)
                .build();
    }

    // 전체 계좌용 오버로드 (하위 호환성)
    public EquityCurveDto calculateEquityCurve(LocalDate startDate, LocalDate endDate) {
        return calculateEquityCurve(null, startDate, endDate);
    }

    // 계좌별 조회 (벤치마크 비교용)
    public EquityCurveDto getEquityCurve(Long accountId, LocalDate startDate, LocalDate endDate) {
        return calculateEquityCurve(accountId, startDate, endDate);
    }

    private EquityCurveDto emptyEquityCurve() {
        return EquityCurveDto.builder()
                .labels(new ArrayList<>())
                .values(new ArrayList<>())
                .cumulativeReturns(new ArrayList<>())
                .dailyReturns(new ArrayList<>())
                .initialInvestment(BigDecimal.ZERO)
                .finalValue(BigDecimal.ZERO)
                .totalReturn(BigDecimal.ZERO)
                .cagr(BigDecimal.ZERO)
                .build();
    }

    private BigDecimal calcReturnPct(BigDecimal diff, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return diff.divide(base, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);
    }

    private BigDecimal calcCagr(
            BigDecimal initial, BigDecimal finalVal, LocalDate start, LocalDate end) {
        if (initial == null || initial.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        long days = ChronoUnit.DAYS.between(start, end);
        if (days <= 0 || finalVal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double years = days / DAYS_PER_YEAR;
        double ratio = finalVal.divide(initial, EXTENDED_SCALE, RoundingMode.HALF_UP).doubleValue();
        if (ratio <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(
                        (Math.pow(ratio, 1.0 / years) - 1) * PERCENTAGE_MULTIPLIER.doubleValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Drawdown (최대 낙폭) 분석 고점 대비 하락률을 계산하여 리스크 분석에 활용 */
    @Cacheable(value = "analysis", key = "'drawdown_' + #startDate + '_' + #endDate")
    public DrawdownDto calculateDrawdown(LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating drawdown analysis from {} to {}", startDate, endDate);

        // 먼저 Equity Curve 데이터를 활용
        EquityCurveDto equityCurve = calculateEquityCurve(startDate, endDate);

        if (equityCurve.getValues().isEmpty()) {
            log.debug("No equity curve data available for drawdown calculation");
            return DrawdownDto.builder()
                    .labels(new ArrayList<>())
                    .drawdowns(new ArrayList<>())
                    .maxDrawdown(BigDecimal.ZERO)
                    .currentDrawdown(BigDecimal.ZERO)
                    .majorDrawdowns(new ArrayList<>())
                    .build();
        }

        List<String> labels = equityCurve.getLabels();
        List<BigDecimal> values = equityCurve.getValues();
        List<BigDecimal> drawdowns = new ArrayList<>();

        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        LocalDate maxDrawdownDate = null;
        LocalDate peakDate = null;

        // Drawdown 이벤트 추적용
        List<DrawdownDto.DrawdownEvent> majorDrawdowns = new ArrayList<>();
        LocalDate currentDrawdownStart = null;
        BigDecimal currentDrawdownMax = BigDecimal.ZERO;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            LocalDate date = LocalDate.parse(labels.get(i), formatter);

            // 새로운 고점 발생
            if (value.compareTo(peak) > 0) {
                // 이전 Drawdown 이벤트 종료 처리
                if (currentDrawdownStart != null
                        && currentDrawdownMax.abs().compareTo(MAJOR_DRAWDOWN_THRESHOLD) >= 0) {
                    DrawdownDto.DrawdownEvent event =
                            DrawdownDto.DrawdownEvent.builder()
                                    .startDate(currentDrawdownStart)
                                    .endDate(date)
                                    .recoveryDate(date)
                                    .maxDrawdown(currentDrawdownMax)
                                    .duration(
                                            (int)
                                                    ChronoUnit.DAYS.between(
                                                            currentDrawdownStart, date))
                                    .build();
                    majorDrawdowns.add(event);
                }

                peak = value;
                peakDate = date;
                currentDrawdownStart = null;
                currentDrawdownMax = BigDecimal.ZERO;
            }

            // Drawdown 계산
            BigDecimal drawdown = BigDecimal.ZERO;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                drawdown =
                        value.subtract(peak)
                                .divide(peak, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                                .multiply(PERCENTAGE_MULTIPLIER);
            }
            drawdowns.add(drawdown);

            // Drawdown 시작 추적
            if (drawdown.compareTo(BigDecimal.ZERO) < 0 && currentDrawdownStart == null) {
                currentDrawdownStart = date;
            }

            // 최대 Drawdown 추적
            if (drawdown.compareTo(currentDrawdownMax) < 0) {
                currentDrawdownMax = drawdown;
            }

            // 전체 최대 Drawdown 업데이트
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
                maxDrawdownDate = date;
            }
        }

        // 현재 진행 중인 Drawdown
        BigDecimal currentDrawdown =
                drawdowns.isEmpty() ? BigDecimal.ZERO : drawdowns.get(drawdowns.size() - 1);

        // 회복 기간 계산 (최대 낙폭 이후)
        Integer recoveryDays = null;
        if (maxDrawdownDate != null) {
            for (int i = labels.indexOf(maxDrawdownDate.format(formatter)) + 1;
                    i < values.size();
                    i++) {
                if (values.get(i).compareTo(peak) >= 0) {
                    LocalDate recoveryDate = LocalDate.parse(labels.get(i), formatter);
                    recoveryDays = (int) ChronoUnit.DAYS.between(maxDrawdownDate, recoveryDate);
                    break;
                }
            }
        }

        return DrawdownDto.builder()
                .labels(labels)
                .drawdowns(drawdowns)
                .maxDrawdown(maxDrawdown)
                .maxDrawdownDate(maxDrawdownDate)
                .peakDate(peakDate)
                .recoveryDays(recoveryDays)
                .currentDrawdown(currentDrawdown)
                .majorDrawdowns(majorDrawdowns)
                .build();
    }

    /** 종목 간 상관관계 매트릭스 계산 각 종목의 일별 수익률을 기반으로 피어슨 상관계수 계산 */
    @Cacheable(value = "analysis", key = "'correlation_' + #startDate + '_' + #endDate")
    public CorrelationMatrixDto calculateCorrelationMatrix(LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating correlation matrix for period {} to {}", startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        int periodDays = (int) ChronoUnit.DAYS.between(startDate, endDate);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(startDateTime, endDateTime);

        if (transactions.isEmpty()) {
            log.debug("No transactions found for correlation matrix calculation");
            return buildEmptyCorrelationMatrix(periodDays);
        }

        // Step 1: Filter and prepare stock data
        CorrelationDataPreparation preparedData = prepareCorrelationData(transactions);

        if (preparedData.stocks().size() < MIN_TRANSACTIONS_FOR_CORRELATION) {
            log.debug(
                    "Insufficient stocks ({}) for correlation analysis",
                    preparedData.stocks().size());
            return buildInsufficientDataCorrelationMatrix(preparedData.stocks(), periodDays);
        }

        // Step 2: Calculate daily returns for each stock
        StockReturnsData returnsData = calculateStockReturns(preparedData);

        // Step 3: Build correlation matrix
        CorrelationMatrixResult matrixResult =
                buildCorrelationMatrix(
                        preparedData.stocks(),
                        returnsData.stockDailyReturns(),
                        returnsData.allDates());

        // Step 4: Calculate summary statistics
        BigDecimal averageCorrelation =
                calculateAverageCorrelation(
                        matrixResult.totalCorrelation(), matrixResult.correlationCount());
        BigDecimal diversificationScore = calculateDiversificationScore(averageCorrelation);

        log.debug(
                "Correlation matrix calculated: {} stocks, {} data points, avg correlation: {}",
                preparedData.stocks().size(),
                returnsData.allDates().size(),
                averageCorrelation);

        return CorrelationMatrixDto.builder()
                .symbols(
                        preparedData.stocks().stream()
                                .map(Stock::getSymbol)
                                .collect(Collectors.toList()))
                .names(
                        preparedData.stocks().stream()
                                .map(Stock::getName)
                                .collect(Collectors.toList()))
                .matrix(matrixResult.matrix())
                .periodDays(periodDays)
                .dataPoints(returnsData.allDates().size())
                .averageCorrelation(averageCorrelation)
                .diversificationScore(diversificationScore)
                .build();
    }

    /** Builds an empty correlation matrix DTO for cases with no transactions */
    private CorrelationMatrixDto buildEmptyCorrelationMatrix(int periodDays) {
        return CorrelationMatrixDto.builder()
                .symbols(new ArrayList<>())
                .names(new ArrayList<>())
                .matrix(new ArrayList<>())
                .periodDays(periodDays)
                .dataPoints(0)
                .averageCorrelation(BigDecimal.ZERO)
                .diversificationScore(BigDecimal.ZERO)
                .build();
    }

    /** Builds a correlation matrix DTO for cases with insufficient stock data */
    private CorrelationMatrixDto buildInsufficientDataCorrelationMatrix(
            List<Stock> stocks, int periodDays) {
        return CorrelationMatrixDto.builder()
                .symbols(stocks.stream().map(Stock::getSymbol).collect(Collectors.toList()))
                .names(stocks.stream().map(Stock::getName).collect(Collectors.toList()))
                .matrix(new ArrayList<>())
                .periodDays(periodDays)
                .dataPoints(0)
                .averageCorrelation(BigDecimal.ZERO)
                .diversificationScore(MAX_DIVERSIFICATION_SCORE)
                .build();
    }

    /** Record to hold prepared correlation data */
    private record CorrelationDataPreparation(
            List<Stock> stocks, Map<Stock, List<Transaction>> stockTransactions) {}

    /** Prepares and filters stock data for correlation analysis */
    private CorrelationDataPreparation prepareCorrelationData(List<Transaction> transactions) {
        // Group transactions by stock
        Map<Stock, List<Transaction>> stockTransactions =
                transactions.stream().collect(Collectors.groupingBy(Transaction::getStock));

        // Filter stocks with minimum required transactions
        List<Stock> stocks =
                stockTransactions.entrySet().stream()
                        .filter(e -> e.getValue().size() >= MIN_TRANSACTIONS_FOR_CORRELATION)
                        .map(Map.Entry::getKey)
                        .sorted(Comparator.comparing(Stock::getSymbol))
                        .collect(Collectors.toList());

        return new CorrelationDataPreparation(stocks, stockTransactions);
    }

    /** Record to hold stock returns calculation results */
    private record StockReturnsData(
            Map<Stock, Map<LocalDate, BigDecimal>> stockDailyReturns, List<LocalDate> allDates) {}

    /** Calculates daily returns for all stocks in the prepared data */
    private StockReturnsData calculateStockReturns(CorrelationDataPreparation preparedData) {
        Map<Stock, Map<LocalDate, BigDecimal>> stockDailyReturns = new HashMap<>();
        Set<LocalDate> allDates = new TreeSet<>();

        for (Stock stock : preparedData.stocks()) {
            Map<LocalDate, BigDecimal> dailyReturns =
                    calculateDailyReturns(preparedData.stockTransactions().get(stock));
            stockDailyReturns.put(stock, dailyReturns);
            allDates.addAll(dailyReturns.keySet());
        }

        return new StockReturnsData(stockDailyReturns, new ArrayList<>(allDates));
    }

    /** Record to hold correlation matrix calculation results */
    private record CorrelationMatrixResult(
            List<List<BigDecimal>> matrix, BigDecimal totalCorrelation, int correlationCount) {}

    /** Builds the correlation matrix from stock returns data */
    private CorrelationMatrixResult buildCorrelationMatrix(
            List<Stock> stocks,
            Map<Stock, Map<LocalDate, BigDecimal>> stockDailyReturns,
            List<LocalDate> commonDates) {

        int n = stocks.size();
        List<List<BigDecimal>> matrix = new ArrayList<>();
        BigDecimal totalCorrelation = BigDecimal.ZERO;
        int correlationCount = 0;

        for (int i = 0; i < n; i++) {
            List<BigDecimal> row = new ArrayList<>();
            Map<LocalDate, BigDecimal> returnsI = stockDailyReturns.get(stocks.get(i));

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    row.add(BigDecimal.ONE);
                } else {
                    Map<LocalDate, BigDecimal> returnsJ = stockDailyReturns.get(stocks.get(j));
                    BigDecimal correlation =
                            calculatePairwiseCorrelation(returnsI, returnsJ, commonDates);
                    row.add(correlation);

                    // Track correlation sum for average (avoid double counting)
                    if (i < j) {
                        totalCorrelation = totalCorrelation.add(correlation.abs());
                        correlationCount++;
                    }
                }
            }
            matrix.add(row);
        }

        return new CorrelationMatrixResult(matrix, totalCorrelation, correlationCount);
    }

    /** Calculates pairwise correlation between two stocks based on their daily returns */
    private BigDecimal calculatePairwiseCorrelation(
            Map<LocalDate, BigDecimal> returnsI,
            Map<LocalDate, BigDecimal> returnsJ,
            List<LocalDate> commonDates) {

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (LocalDate date : commonDates) {
            if (returnsI.containsKey(date) && returnsJ.containsKey(date)) {
                x.add(returnsI.get(date).doubleValue());
                y.add(returnsJ.get(date).doubleValue());
            }
        }

        if (x.size() >= MIN_DATA_POINTS_FOR_STATISTICS) {
            return calculatePearsonCorrelation(x, y);
        }
        return BigDecimal.ZERO;
    }

    /** Calculates the average correlation from total correlation sum and count */
    private BigDecimal calculateAverageCorrelation(
            BigDecimal totalCorrelation, int correlationCount) {
        if (correlationCount > 0) {
            return totalCorrelation.divide(
                    new BigDecimal(correlationCount), FINANCIAL_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculates the diversification score from average correlation Score ranges from 0-100 where
     * lower average correlation results in higher score
     */
    private BigDecimal calculateDiversificationScore(BigDecimal averageCorrelation) {
        return averageCorrelation
                .add(BigDecimal.ONE)
                .divide(DIVERSIFICATION_DIVISOR, FINANCIAL_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);
    }

    /** 거래 목록에서 일별 수익률 계산 */
    private Map<LocalDate, BigDecimal> calculateDailyReturns(List<Transaction> transactions) {
        Map<LocalDate, BigDecimal> dailyReturns = new TreeMap<>();

        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        BigDecimal runningValue = BigDecimal.ZERO;
        BigDecimal previousValue = null;

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate().toLocalDate();
            BigDecimal amount = transaction.getTotalAmount();

            if (transaction.getType() == TransactionType.BUY) {
                runningValue = runningValue.add(amount);
            } else {
                BigDecimal realizedPnl =
                        transaction.getRealizedPnl() != null
                                ? transaction.getRealizedPnl()
                                : BigDecimal.ZERO;
                runningValue = runningValue.add(realizedPnl);
            }

            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn =
                        runningValue
                                .subtract(previousValue)
                                .divide(previousValue, EXTENDED_SCALE, RoundingMode.HALF_UP);
                dailyReturns.put(date, dailyReturn);
            }

            previousValue = runningValue;
        }

        return dailyReturns;
    }

    /** 피어슨 상관계수 계산 */
    private BigDecimal calculatePearsonCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.size() < MIN_DATA_POINTS_FOR_STATISTICS) {
            return BigDecimal.ZERO;
        }

        int n = x.size();

        // 평균 계산
        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // 공분산 및 표준편차 계산
        double covariance = 0;
        double varianceX = 0;
        double varianceY = 0;

        for (int i = 0; i < n; i++) {
            double diffX = x.get(i) - meanX;
            double diffY = y.get(i) - meanY;
            covariance += diffX * diffY;
            varianceX += diffX * diffX;
            varianceY += diffY * diffY;
        }

        double stdDevX = Math.sqrt(varianceX);
        double stdDevY = Math.sqrt(varianceY);

        if (stdDevX == 0 || stdDevY == 0) {
            return BigDecimal.ZERO;
        }

        double correlation = covariance / (stdDevX * stdDevY);

        // 범위 제한 (-1 ~ 1)
        correlation = Math.max(-1, Math.min(1, correlation));

        return BigDecimal.valueOf(correlation).setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP);
    }

    /** 롤링 상관관계 계산 시간에 따른 두 종목 간 상관관계 변화 추적 */
    public RollingCorrelationDto calculateRollingCorrelation(
            String symbol1,
            String symbol2,
            LocalDate startDate,
            LocalDate endDate,
            int windowDays) {
        log.debug(
                "Calculating rolling correlation for {} and {} with window {} days",
                symbol1,
                symbol2,
                windowDays);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(startDateTime, endDateTime);

        // 두 종목의 거래만 필터링
        Map<String, List<Transaction>> bySymbol =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getStock().getSymbol().equals(symbol1)
                                                || t.getStock().getSymbol().equals(symbol2))
                        .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        List<Transaction> txs1 = bySymbol.getOrDefault(symbol1, new ArrayList<>());
        List<Transaction> txs2 = bySymbol.getOrDefault(symbol2, new ArrayList<>());

        if (txs1.isEmpty() || txs2.isEmpty()) {
            log.debug(
                    "Insufficient transaction data for rolling correlation: {} has {}, {} has {} transactions",
                    symbol1,
                    txs1.size(),
                    symbol2,
                    txs2.size());
            return RollingCorrelationDto.builder()
                    .symbol1(symbol1)
                    .symbol2(symbol2)
                    .windowDays(windowDays)
                    .dates(new ArrayList<>())
                    .correlations(new ArrayList<>())
                    .build();
        }

        String name1 = txs1.get(0).getStock().getName();
        String name2 = txs2.get(0).getStock().getName();

        // 일별 수익률 계산
        Map<LocalDate, BigDecimal> returns1 = calculateDailyReturns(txs1);
        Map<LocalDate, BigDecimal> returns2 = calculateDailyReturns(txs2);

        // 공통 날짜 정렬
        Set<LocalDate> commonDates = new TreeSet<>(returns1.keySet());
        commonDates.retainAll(returns2.keySet());
        List<LocalDate> sortedDates = new ArrayList<>(commonDates);

        if (sortedDates.size() < windowDays) {
            return RollingCorrelationDto.builder()
                    .symbol1(symbol1)
                    .symbol2(symbol2)
                    .name1(name1)
                    .name2(name2)
                    .windowDays(windowDays)
                    .dates(new ArrayList<>())
                    .correlations(new ArrayList<>())
                    .build();
        }

        // 롤링 상관관계 계산
        List<LocalDate> rollingDates = new ArrayList<>();
        List<BigDecimal> rollingCorrelations = new ArrayList<>();
        BigDecimal totalCorr = BigDecimal.ZERO;
        BigDecimal maxCorr = new BigDecimal("-1");
        BigDecimal minCorr = BigDecimal.ONE;

        for (int i = windowDays - 1; i < sortedDates.size(); i++) {
            List<Double> windowX = new ArrayList<>();
            List<Double> windowY = new ArrayList<>();

            for (int j = i - windowDays + 1; j <= i; j++) {
                LocalDate date = sortedDates.get(j);
                windowX.add(returns1.get(date).doubleValue());
                windowY.add(returns2.get(date).doubleValue());
            }

            BigDecimal corr = calculatePearsonCorrelation(windowX, windowY);
            rollingDates.add(sortedDates.get(i));
            rollingCorrelations.add(corr);

            totalCorr = totalCorr.add(corr);
            if (corr.compareTo(maxCorr) > 0) maxCorr = corr;
            if (corr.compareTo(minCorr) < 0) minCorr = corr;
        }

        BigDecimal avgCorr =
                rollingCorrelations.isEmpty()
                        ? BigDecimal.ZERO
                        : totalCorr.divide(
                                new BigDecimal(rollingCorrelations.size()),
                                FINANCIAL_SCALE,
                                RoundingMode.HALF_UP);

        // 변동성 (표준편차) 계산
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal c : rollingCorrelations) {
            BigDecimal diff = c.subtract(avgCorr);
            variance = variance.add(diff.multiply(diff));
        }
        BigDecimal volatility =
                rollingCorrelations.isEmpty()
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(
                                        Math.sqrt(
                                                variance.divide(
                                                                new BigDecimal(
                                                                        rollingCorrelations.size()),
                                                                10,
                                                                RoundingMode.HALF_UP)
                                                        .doubleValue()))
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP);

        return RollingCorrelationDto.builder()
                .symbol1(symbol1)
                .symbol2(symbol2)
                .name1(name1)
                .name2(name2)
                .dates(rollingDates)
                .correlations(rollingCorrelations)
                .windowDays(windowDays)
                .periodDays((int) ChronoUnit.DAYS.between(startDate, endDate))
                .currentCorrelation(
                        rollingCorrelations.isEmpty()
                                ? BigDecimal.ZERO
                                : rollingCorrelations.get(rollingCorrelations.size() - 1))
                .averageCorrelation(avgCorr)
                .maxCorrelation(maxCorr)
                .minCorrelation(minCorr)
                .correlationVolatility(volatility)
                .build();
    }

    /** 종목 쌍 상세 분석 */
    public PairCorrelationDto calculatePairCorrelation(
            String symbol1, String symbol2, LocalDate startDate, LocalDate endDate) {
        log.debug(
                "Calculating pair correlation for {} and {} from {} to {}",
                symbol1,
                symbol2,
                startDate,
                endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(startDateTime, endDateTime);

        Map<String, List<Transaction>> bySymbol =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getStock().getSymbol().equals(symbol1)
                                                || t.getStock().getSymbol().equals(symbol2))
                        .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        List<Transaction> txs1 = bySymbol.getOrDefault(symbol1, new ArrayList<>());
        List<Transaction> txs2 = bySymbol.getOrDefault(symbol2, new ArrayList<>());

        if (txs1.isEmpty() || txs2.isEmpty()) {
            log.debug(
                    "Insufficient data for pair correlation: {} has {}, {} has {} transactions",
                    symbol1,
                    txs1.size(),
                    symbol2,
                    txs2.size());
            return PairCorrelationDto.builder()
                    .symbol1(symbol1)
                    .symbol2(symbol2)
                    .correlation(BigDecimal.ZERO)
                    .build();
        }

        Stock stock1 = txs1.get(0).getStock();
        Stock stock2 = txs2.get(0).getStock();

        Map<LocalDate, BigDecimal> returns1 = calculateDailyReturns(txs1);
        Map<LocalDate, BigDecimal> returns2 = calculateDailyReturns(txs2);

        Set<LocalDate> commonDates = new TreeSet<>(returns1.keySet());
        commonDates.retainAll(returns2.keySet());
        List<LocalDate> sortedDates = new ArrayList<>(commonDates);

        List<BigDecimal> r1List = new ArrayList<>();
        List<BigDecimal> r2List = new ArrayList<>();
        List<BigDecimal> cumR1 = new ArrayList<>();
        List<BigDecimal> cumR2 = new ArrayList<>();

        BigDecimal cum1 = BigDecimal.ONE;
        BigDecimal cum2 = BigDecimal.ONE;

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (LocalDate date : sortedDates) {
            BigDecimal ret1 = returns1.get(date);
            BigDecimal ret2 = returns2.get(date);

            r1List.add(ret1);
            r2List.add(ret2);

            cum1 = cum1.multiply(BigDecimal.ONE.add(ret1));
            cum2 = cum2.multiply(BigDecimal.ONE.add(ret2));

            cumR1.add(
                    cum1.subtract(BigDecimal.ONE)
                            .multiply(PERCENTAGE_MULTIPLIER)
                            .setScale(2, RoundingMode.HALF_UP));
            cumR2.add(
                    cum2.subtract(BigDecimal.ONE)
                            .multiply(PERCENTAGE_MULTIPLIER)
                            .setScale(2, RoundingMode.HALF_UP));

            x.add(ret1.doubleValue());
            y.add(ret2.doubleValue());
        }

        BigDecimal correlation = calculatePearsonCorrelation(x, y);

        // 평균 및 변동성 계산
        double avgR1 = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgR2 = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double vol1 =
                Math.sqrt(x.stream().mapToDouble(v -> Math.pow(v - avgR1, 2)).average().orElse(0));
        double vol2 =
                Math.sqrt(y.stream().mapToDouble(v -> Math.pow(v - avgR2, 2)).average().orElse(0));

        // 분산투자 효과 (상관관계가 낮을수록 높음)
        BigDecimal diversBenefit =
                BigDecimal.ONE
                        .subtract(correlation.abs())
                        .multiply(PERCENTAGE_MULTIPLIER)
                        .setScale(2, RoundingMode.HALF_UP);

        return PairCorrelationDto.builder()
                .symbol1(symbol1)
                .symbol2(symbol2)
                .name1(stock1.getName())
                .name2(stock2.getName())
                .sector1(stock1.getSector() != null ? stock1.getSector().name() : "UNKNOWN")
                .sector2(stock2.getSector() != null ? stock2.getSector().name() : "UNKNOWN")
                .correlation(correlation)
                .startDate(startDate)
                .endDate(endDate)
                .periodDays((int) ChronoUnit.DAYS.between(startDate, endDate))
                .dates(sortedDates)
                .returns1(r1List)
                .returns2(r2List)
                .cumulativeReturns1(cumR1)
                .cumulativeReturns2(cumR2)
                .avgReturn1(
                        BigDecimal.valueOf(avgR1 * PERCENTAGE_MULTIPLIER.doubleValue())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .avgReturn2(
                        BigDecimal.valueOf(avgR2 * PERCENTAGE_MULTIPLIER.doubleValue())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .volatility1(
                        BigDecimal.valueOf(vol1 * PERCENTAGE_MULTIPLIER.doubleValue())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .volatility2(
                        BigDecimal.valueOf(vol2 * PERCENTAGE_MULTIPLIER.doubleValue())
                                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP))
                .diversificationBenefit(diversBenefit)
                .build();
    }

    /** 섹터별 상관관계 요약 */
    public SectorCorrelationDto calculateSectorCorrelation(LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating sector correlation from {} to {}", startDate, endDate);

        CorrelationMatrixDto matrix = calculateCorrelationMatrix(startDate, endDate);

        if (matrix.getSymbols().isEmpty()) {
            log.debug("No symbols available for sector correlation analysis");
            return SectorCorrelationDto.builder()
                    .sectorSummaries(new ArrayList<>())
                    .sectors(new ArrayList<>())
                    .sectorMatrix(new ArrayList<>())
                    .diversificationRecommendations(new ArrayList<>())
                    .build();
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(startDateTime, endDateTime);

        // 종목-섹터 매핑
        Map<String, String> symbolToSector =
                transactions.stream()
                        .map(Transaction::getStock)
                        .distinct()
                        .collect(
                                Collectors.toMap(
                                        Stock::getSymbol,
                                        s ->
                                                s.getSector() != null
                                                        ? s.getSector().name()
                                                        : "UNKNOWN",
                                        (a, b) -> a));

        // 섹터별 종목 그룹화
        Map<String, List<String>> sectorStocks = new HashMap<>();
        for (int i = 0; i < matrix.getSymbols().size(); i++) {
            String symbol = matrix.getSymbols().get(i);
            String sector = symbolToSector.getOrDefault(symbol, "UNKNOWN");
            sectorStocks.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        }

        // 섹터별 요약
        List<SectorCorrelationDto.SectorSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sectorStocks.entrySet()) {
            String sector = entry.getKey();
            List<String> stocks = entry.getValue();

            // 섹터 내 평균 상관계수 계산
            BigDecimal internalCorr = BigDecimal.ZERO;
            int count = 0;

            for (int i = 0; i < stocks.size(); i++) {
                int idx1 = matrix.getSymbols().indexOf(stocks.get(i));
                for (int j = i + 1; j < stocks.size(); j++) {
                    int idx2 = matrix.getSymbols().indexOf(stocks.get(j));
                    if (idx1 >= 0 && idx2 >= 0) {
                        internalCorr =
                                internalCorr.add(matrix.getMatrix().get(idx1).get(idx2).abs());
                        count++;
                    }
                }
            }

            summaries.add(
                    SectorCorrelationDto.SectorSummary.builder()
                            .sector(sector)
                            .sectorLabel(getSectorLabel(sector))
                            .stockCount(stocks.size())
                            .stocks(stocks)
                            .internalCorrelation(
                                    count > 0
                                            ? internalCorr.divide(
                                                    new BigDecimal(count),
                                                    FINANCIAL_SCALE,
                                                    RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .build());
        }

        // 분산투자 추천 (상관관계 낮은 종목 쌍)
        List<SectorCorrelationDto.SectorPair> recommendations = new ArrayList<>();
        List<String> symbols = matrix.getSymbols();

        // 모든 쌍의 상관관계를 계산하고 정렬
        List<Object[]> pairs = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i++) {
            for (int j = i + 1; j < symbols.size(); j++) {
                BigDecimal corr = matrix.getMatrix().get(i).get(j);
                pairs.add(new Object[] {symbols.get(i), symbols.get(j), corr});
            }
        }

        // 상관관계 낮은 순으로 정렬
        pairs.sort((a, b) -> ((BigDecimal) a[2]).compareTo((BigDecimal) b[2]));

        // 상위 추천
        for (int i = 0; i < Math.min(TOP_DIVERSIFICATION_RECOMMENDATIONS, pairs.size()); i++) {
            Object[] pair = pairs.get(i);
            String s1 = (String) pair[0];
            String s2 = (String) pair[1];
            BigDecimal corr = (BigDecimal) pair[2];

            String recommendation;
            if (corr.compareTo(NEGATIVE_CORRELATION_THRESHOLD) < 0) {
                recommendation = "매우 좋은 분산투자 조합 (역상관)";
            } else if (corr.compareTo(LOW_CORRELATION_THRESHOLD) < 0) {
                recommendation = "좋은 분산투자 조합";
            } else {
                recommendation = "보통";
            }

            recommendations.add(
                    SectorCorrelationDto.SectorPair.builder()
                            .sector1(s1)
                            .sector2(s2)
                            .correlation(corr)
                            .recommendation(recommendation)
                            .build());
        }

        return SectorCorrelationDto.builder()
                .sectorSummaries(summaries)
                .sectors(new ArrayList<>(sectorStocks.keySet()))
                .sectorMatrix(new ArrayList<>())
                .diversificationRecommendations(recommendations)
                .build();
    }

    private String getSectorLabel(String sector) {
        Map<String, String> labels =
                Map.of(
                        "TECHNOLOGY", "기술",
                        "FINANCE", "금융",
                        "HEALTHCARE", "헬스케어",
                        "CONSUMER", "소비재",
                        "INDUSTRIAL", "산업재",
                        "ENERGY", "에너지",
                        "MATERIALS", "소재",
                        "UTILITIES", "유틸리티",
                        "REAL_ESTATE", "부동산",
                        "UNKNOWN", "미분류");
        return labels.getOrDefault(sector, sector);
    }
}
