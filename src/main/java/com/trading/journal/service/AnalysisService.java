package com.trading.journal.service;

import com.trading.journal.dto.CorrelationMatrixDto;
import com.trading.journal.dto.DrawdownDto;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalysisService {
    
    private final TransactionRepository transactionRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    
    @Cacheable(value = "analysis", key = "#startDate + '_' + #endDate")
    public PeriodAnalysisDto analyzePeriod(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        
        List<Transaction> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        
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
            realizedProfitRate = realizedProfit.divide(totalBuyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        // 미실현 손익 계산 (현재 포트폴리오 기준)
        PortfolioSummaryDto portfolioSummary = portfolioAnalysisService.getPortfolioSummary();
        BigDecimal unrealizedProfit = portfolioSummary.getTotalProfitLoss();
        BigDecimal unrealizedProfitRate = portfolioSummary.getTotalProfitLossPercent();
        
        // 총 손익
        BigDecimal totalProfit = realizedProfit.add(unrealizedProfit);
        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = totalProfit.divide(totalBuyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
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
        
        Map<YearMonth, List<Transaction>> monthlyTransactions = transactions.stream()
                .collect(Collectors.groupingBy(t -> 
                        YearMonth.from(t.getTransactionDate())));
        
        List<PeriodAnalysisDto.MonthlyAnalysisDto> monthlyAnalysis = new ArrayList<>();
        
        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        
        while (!current.isAfter(end)) {
            List<Transaction> monthTransactions = monthlyTransactions.getOrDefault(current, new ArrayList<>());
            
            BigDecimal buyAmount = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sellAmount = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // FIFO 기반 실현 손익 사용
            BigDecimal profit = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getRealizedPnl)
                    .filter(pnl -> pnl != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal profitRate = BigDecimal.ZERO;
            BigDecimal costBasis = monthTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .map(Transaction::getCostBasis)
                    .filter(cb -> cb != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = profit.divide(costBasis, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            monthlyAnalysis.add(PeriodAnalysisDto.MonthlyAnalysisDto.builder()
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

    /**
     * Equity Curve (누적 수익 곡선) 계산
     * 시간에 따른 포트폴리오 가치와 누적 수익률을 계산
     */
    @Cacheable(value = "analysis", key = "'equity_curve_' + #startDate + '_' + #endDate")
    public EquityCurveDto calculateEquityCurve(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        if (transactions.isEmpty()) {
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

        // 일별 포트폴리오 가치 계산
        Map<LocalDate, BigDecimal> dailyInvestment = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyValue = new TreeMap<>();
        BigDecimal runningInvestment = BigDecimal.ZERO;
        BigDecimal runningValue = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate().toLocalDate();
            BigDecimal amount = transaction.getTotalAmount();

            if (transaction.getType() == TransactionType.BUY) {
                runningInvestment = runningInvestment.add(amount);
                runningValue = runningValue.add(amount);
            } else {
                // 매도 시: 실현 손익 반영
                BigDecimal realizedPnl = transaction.getRealizedPnl() != null
                        ? transaction.getRealizedPnl() : BigDecimal.ZERO;
                runningValue = runningValue.subtract(transaction.getCostBasis() != null
                        ? transaction.getCostBasis() : amount);
                runningValue = runningValue.add(realizedPnl);
            }

            dailyInvestment.put(date, runningInvestment);
            dailyValue.put(date, runningValue);
        }

        // 날짜 범위 채우기 및 누적 수익률 계산
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> cumulativeReturns = new ArrayList<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();

        LocalDate current = startDate;
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvestment = BigDecimal.ZERO;
        BigDecimal previousValue = null;
        BigDecimal initialInvestment = null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        while (!current.isAfter(endDate)) {
            if (dailyValue.containsKey(current)) {
                lastValue = dailyValue.get(current);
                lastInvestment = dailyInvestment.get(current);
            }

            if (initialInvestment == null && lastInvestment.compareTo(BigDecimal.ZERO) > 0) {
                initialInvestment = lastInvestment;
            }

            labels.add(current.format(formatter));
            values.add(lastValue);

            // 누적 수익률 계산
            BigDecimal cumulativeReturn = BigDecimal.ZERO;
            if (lastInvestment.compareTo(BigDecimal.ZERO) > 0) {
                cumulativeReturn = lastValue.subtract(lastInvestment)
                        .divide(lastInvestment, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            cumulativeReturns.add(cumulativeReturn);

            // 일간 수익률 계산
            BigDecimal dailyReturn = BigDecimal.ZERO;
            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                dailyReturn = lastValue.subtract(previousValue)
                        .divide(previousValue, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            dailyReturns.add(dailyReturn);
            previousValue = lastValue;

            current = current.plusDays(1);
        }

        // 전체 수익률 및 CAGR 계산
        BigDecimal totalReturn = BigDecimal.ZERO;
        BigDecimal cagr = BigDecimal.ZERO;

        if (initialInvestment != null && initialInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalReturn = lastValue.subtract(initialInvestment)
                    .divide(initialInvestment, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            // CAGR = ((Final / Initial) ^ (1/years)) - 1
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 0 && lastValue.compareTo(BigDecimal.ZERO) > 0) {
                double years = days / 365.0;
                double ratio = lastValue.divide(initialInvestment, 6, RoundingMode.HALF_UP).doubleValue();
                if (ratio > 0 && years > 0) {
                    double cagrValue = (Math.pow(ratio, 1.0 / years) - 1) * 100;
                    cagr = BigDecimal.valueOf(cagrValue).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

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

    /**
     * 계좌별 Equity Curve 조회 (벤치마크 비교용)
     */
    public EquityCurveDto getEquityCurve(Long accountId, LocalDate startDate, LocalDate endDate) {
        if (accountId == null) {
            return calculateEquityCurve(startDate, endDate);
        }
        return calculateEquityCurveByAccount(accountId, startDate, endDate);
    }

    /**
     * 계좌별 Equity Curve 계산
     */
    private EquityCurveDto calculateEquityCurveByAccount(Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findByAccountIdAndDateRange(accountId, startDateTime, endDateTime);
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        if (transactions.isEmpty()) {
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

        // 일별 포트폴리오 가치 계산
        Map<LocalDate, BigDecimal> dailyInvestment = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyValue = new TreeMap<>();
        BigDecimal runningInvestment = BigDecimal.ZERO;
        BigDecimal runningValue = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate().toLocalDate();
            BigDecimal amount = transaction.getTotalAmount();

            if (transaction.getType() == TransactionType.BUY) {
                runningInvestment = runningInvestment.add(amount);
                runningValue = runningValue.add(amount);
            } else {
                BigDecimal realizedPnl = transaction.getRealizedPnl() != null
                        ? transaction.getRealizedPnl() : BigDecimal.ZERO;
                runningValue = runningValue.subtract(transaction.getCostBasis() != null
                        ? transaction.getCostBasis() : amount);
                runningValue = runningValue.add(realizedPnl);
            }

            dailyInvestment.put(date, runningInvestment);
            dailyValue.put(date, runningValue);
        }

        // 날짜 범위 채우기 및 누적 수익률 계산
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> cumulativeReturns = new ArrayList<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();

        LocalDate current = startDate;
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvestment = BigDecimal.ZERO;
        BigDecimal previousValue = null;
        BigDecimal initialInvestment = null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        while (!current.isAfter(endDate)) {
            if (dailyValue.containsKey(current)) {
                lastValue = dailyValue.get(current);
                lastInvestment = dailyInvestment.get(current);
            }

            if (initialInvestment == null && lastInvestment.compareTo(BigDecimal.ZERO) > 0) {
                initialInvestment = lastInvestment;
            }

            labels.add(current.format(formatter));
            values.add(lastValue);

            // 누적 수익률 계산
            BigDecimal cumulativeReturn = BigDecimal.ZERO;
            if (lastInvestment.compareTo(BigDecimal.ZERO) > 0) {
                cumulativeReturn = lastValue.subtract(lastInvestment)
                        .divide(lastInvestment, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            cumulativeReturns.add(cumulativeReturn);

            // 일간 수익률 계산
            BigDecimal dailyReturn = BigDecimal.ZERO;
            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                dailyReturn = lastValue.subtract(previousValue)
                        .divide(previousValue, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            dailyReturns.add(dailyReturn);
            previousValue = lastValue;

            current = current.plusDays(1);
        }

        // 전체 수익률 및 CAGR 계산
        BigDecimal totalReturn = BigDecimal.ZERO;
        BigDecimal cagr = BigDecimal.ZERO;

        if (initialInvestment != null && initialInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalReturn = lastValue.subtract(initialInvestment)
                    .divide(initialInvestment, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 0 && lastValue.compareTo(BigDecimal.ZERO) > 0) {
                double years = days / 365.0;
                double ratio = lastValue.divide(initialInvestment, 6, RoundingMode.HALF_UP).doubleValue();
                if (ratio > 0 && years > 0) {
                    double cagrValue = (Math.pow(ratio, 1.0 / years) - 1) * 100;
                    cagr = BigDecimal.valueOf(cagrValue).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

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

    /**
     * Drawdown (최대 낙폭) 분석
     * 고점 대비 하락률을 계산하여 리스크 분석에 활용
     */
    @Cacheable(value = "analysis", key = "'drawdown_' + #startDate + '_' + #endDate")
    public DrawdownDto calculateDrawdown(LocalDate startDate, LocalDate endDate) {
        // 먼저 Equity Curve 데이터를 활용
        EquityCurveDto equityCurve = calculateEquityCurve(startDate, endDate);

        if (equityCurve.getValues().isEmpty()) {
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
                if (currentDrawdownStart != null && currentDrawdownMax.abs().compareTo(new BigDecimal("5")) >= 0) {
                    DrawdownDto.DrawdownEvent event = DrawdownDto.DrawdownEvent.builder()
                            .startDate(currentDrawdownStart)
                            .endDate(date)
                            .recoveryDate(date)
                            .maxDrawdown(currentDrawdownMax)
                            .duration((int) ChronoUnit.DAYS.between(currentDrawdownStart, date))
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
                drawdown = value.subtract(peak)
                        .divide(peak, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
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
        BigDecimal currentDrawdown = drawdowns.isEmpty() ? BigDecimal.ZERO
                : drawdowns.get(drawdowns.size() - 1);

        // 회복 기간 계산 (최대 낙폭 이후)
        Integer recoveryDays = null;
        if (maxDrawdownDate != null) {
            for (int i = labels.indexOf(maxDrawdownDate.format(formatter)) + 1; i < values.size(); i++) {
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

    /**
     * 종목 간 상관관계 매트릭스 계산
     * 각 종목의 일별 수익률을 기반으로 피어슨 상관계수 계산
     */
    @Cacheable(value = "analysis", key = "'correlation_' + #startDate + '_' + #endDate")
    public CorrelationMatrixDto calculateCorrelationMatrix(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);

        if (transactions.isEmpty()) {
            return CorrelationMatrixDto.builder()
                    .symbols(new ArrayList<>())
                    .names(new ArrayList<>())
                    .matrix(new ArrayList<>())
                    .periodDays((int) ChronoUnit.DAYS.between(startDate, endDate))
                    .dataPoints(0)
                    .averageCorrelation(BigDecimal.ZERO)
                    .diversificationScore(BigDecimal.ZERO)
                    .build();
        }

        // 종목별 거래 그룹화
        Map<Stock, List<Transaction>> stockTransactions = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getStock));

        // 최소 2개 거래가 있는 종목만 필터링
        List<Stock> stocks = stockTransactions.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Stock::getSymbol))
                .collect(Collectors.toList());

        if (stocks.size() < 2) {
            return CorrelationMatrixDto.builder()
                    .symbols(stocks.stream().map(Stock::getSymbol).collect(Collectors.toList()))
                    .names(stocks.stream().map(Stock::getName).collect(Collectors.toList()))
                    .matrix(new ArrayList<>())
                    .periodDays((int) ChronoUnit.DAYS.between(startDate, endDate))
                    .dataPoints(0)
                    .averageCorrelation(BigDecimal.ZERO)
                    .diversificationScore(new BigDecimal("100"))
                    .build();
        }

        // 각 종목별 일별 수익률 계산
        Map<Stock, Map<LocalDate, BigDecimal>> stockDailyReturns = new HashMap<>();
        Set<LocalDate> allDates = new TreeSet<>();

        for (Stock stock : stocks) {
            Map<LocalDate, BigDecimal> dailyReturns = calculateDailyReturns(stockTransactions.get(stock));
            stockDailyReturns.put(stock, dailyReturns);
            allDates.addAll(dailyReturns.keySet());
        }

        // 공통 날짜만 사용하여 수익률 배열 생성
        List<LocalDate> commonDates = new ArrayList<>(allDates);

        // 상관관계 매트릭스 계산
        int n = stocks.size();
        List<List<BigDecimal>> matrix = new ArrayList<>();
        BigDecimal totalCorrelation = BigDecimal.ZERO;
        int correlationCount = 0;

        for (int i = 0; i < n; i++) {
            List<BigDecimal> row = new ArrayList<>();
            Stock stockI = stocks.get(i);
            Map<LocalDate, BigDecimal> returnsI = stockDailyReturns.get(stockI);

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    row.add(BigDecimal.ONE);
                } else {
                    Stock stockJ = stocks.get(j);
                    Map<LocalDate, BigDecimal> returnsJ = stockDailyReturns.get(stockJ);

                    // 공통 날짜에서 수익률 추출
                    List<Double> x = new ArrayList<>();
                    List<Double> y = new ArrayList<>();

                    for (LocalDate date : commonDates) {
                        if (returnsI.containsKey(date) && returnsJ.containsKey(date)) {
                            x.add(returnsI.get(date).doubleValue());
                            y.add(returnsJ.get(date).doubleValue());
                        }
                    }

                    BigDecimal correlation = BigDecimal.ZERO;
                    if (x.size() >= 2) {
                        correlation = calculatePearsonCorrelation(x, y);
                        if (i < j) { // 중복 카운트 방지
                            totalCorrelation = totalCorrelation.add(correlation.abs());
                            correlationCount++;
                        }
                    }
                    row.add(correlation);
                }
            }
            matrix.add(row);
        }

        // 평균 상관계수 계산
        BigDecimal averageCorrelation = correlationCount > 0
                ? totalCorrelation.divide(new BigDecimal(correlationCount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 분산투자 효과 점수 (0-100, 평균 상관계수가 낮을수록 좋음)
        // 상관계수 -1 ~ 1을 0 ~ 100으로 변환 (낮을수록 좋음)
        BigDecimal diversificationScore = averageCorrelation
                .add(BigDecimal.ONE)
                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        return CorrelationMatrixDto.builder()
                .symbols(stocks.stream().map(Stock::getSymbol).collect(Collectors.toList()))
                .names(stocks.stream().map(Stock::getName).collect(Collectors.toList()))
                .matrix(matrix)
                .periodDays((int) ChronoUnit.DAYS.between(startDate, endDate))
                .dataPoints(commonDates.size())
                .averageCorrelation(averageCorrelation)
                .diversificationScore(diversificationScore)
                .build();
    }

    /**
     * 거래 목록에서 일별 수익률 계산
     */
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
                BigDecimal realizedPnl = transaction.getRealizedPnl() != null
                        ? transaction.getRealizedPnl() : BigDecimal.ZERO;
                runningValue = runningValue.add(realizedPnl);
            }

            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = runningValue.subtract(previousValue)
                        .divide(previousValue, 6, RoundingMode.HALF_UP);
                dailyReturns.put(date, dailyReturn);
            }

            previousValue = runningValue;
        }

        return dailyReturns;
    }

    /**
     * 피어슨 상관계수 계산
     */
    private BigDecimal calculatePearsonCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.size() < 2) {
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

        return BigDecimal.valueOf(correlation).setScale(4, RoundingMode.HALF_UP);
    }
}