package com.trading.journal.service;

import com.trading.journal.dto.DrawdownDto;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.RiskMetricsDto;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 리스크 지표 분석 서비스
 * VaR, Sortino, Calmar 등 고급 리스크 메트릭스 계산
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RiskMetricsService {

    private final TransactionRepository transactionRepository;
    private final AnalysisService analysisService;

    // 무위험 이자율 (연간, 기본값 3%)
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.03");
    // 거래일 기준 연간 일수
    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * 종합 리스크 메트릭스 계산
     */
    @Cacheable(value = "analysis", key = "'risk_metrics_' + #accountId + '_' + #startDate + '_' + #endDate")
    public RiskMetricsDto calculateRiskMetrics(Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating risk metrics for account {} from {} to {}", accountId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        // 거래 데이터 조회
        List<Transaction> transactions;
        if (accountId != null) {
            transactions = transactionRepository.findByAccountIdAndDateRange(accountId, startDateTime, endDateTime);
        } else {
            transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        }

        if (transactions.isEmpty()) {
            return buildEmptyRiskMetrics(startDate, endDate);
        }

        // 일별 수익률 계산
        List<BigDecimal> dailyReturns = calculateDailyReturns(transactions);

        if (dailyReturns.size() < 2) {
            return buildEmptyRiskMetrics(startDate, endDate);
        }

        // Equity Curve와 Drawdown 데이터 가져오기
        EquityCurveDto equityCurve = analysisService.calculateEquityCurve(startDate, endDate);
        DrawdownDto drawdownDto = analysisService.calculateDrawdown(startDate, endDate);

        // 기본 통계 계산
        BigDecimal avgReturn = calculateMean(dailyReturns);
        BigDecimal volatility = calculateStandardDeviation(dailyReturns);
        BigDecimal annualizedVolatility = volatility.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR)));

        // 하락 변동성 (Downside Deviation)
        BigDecimal downsideDeviation = calculateDownsideDeviation(dailyReturns, RISK_FREE_RATE);
        BigDecimal annualizedDownsideDeviation = downsideDeviation.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR)));

        // 샤프 비율
        BigDecimal sharpeRatio = calculateSharpeRatio(dailyReturns, RISK_FREE_RATE);

        // 소르티노 비율
        BigDecimal sortinoRatio = calculateSortinoRatio(dailyReturns, RISK_FREE_RATE);

        // 칼마 비율
        BigDecimal cagr = equityCurve.getCagr() != null ? equityCurve.getCagr() : BigDecimal.ZERO;
        BigDecimal maxDrawdown = drawdownDto.getMaxDrawdown() != null
                ? drawdownDto.getMaxDrawdown().abs() : BigDecimal.ZERO;
        BigDecimal calmarRatio = calculateCalmarRatio(cagr, maxDrawdown);

        // VaR 계산
        RiskMetricsDto.VaRDto var95 = calculateVaR(dailyReturns, new BigDecimal("0.95"), equityCurve.getFinalValue());
        RiskMetricsDto.VaRDto var99 = calculateVaR(dailyReturns, new BigDecimal("0.99"), equityCurve.getFinalValue());

        // 승률 및 손익비 계산
        BigDecimal winRate = calculateWinRate(transactions);
        BigDecimal profitFactor = calculateProfitFactor(transactions);

        // 리스크 등급 결정
        RiskMetricsDto.RiskLevel riskLevel = determineRiskLevel(
                annualizedVolatility, maxDrawdown, sharpeRatio);

        return RiskMetricsDto.builder()
                .sharpeRatio(sharpeRatio)
                .sortinoRatio(sortinoRatio)
                .calmarRatio(calmarRatio)
                .informationRatio(BigDecimal.ZERO) // 벤치마크 데이터 필요
                .var95(var95)
                .var99(var99)
                .maxDrawdown(maxDrawdown.negate()) // 음수로 표시
                .volatility(annualizedVolatility.multiply(new BigDecimal("100")))
                .downsideDeviation(annualizedDownsideDeviation.multiply(new BigDecimal("100")))
                .beta(BigDecimal.ONE) // 벤치마크 데이터 필요
                .alpha(BigDecimal.ZERO) // 벤치마크 데이터 필요
                .cagr(cagr)
                .winRate(winRate)
                .profitFactor(profitFactor)
                .riskLevel(riskLevel)
                .startDate(startDate)
                .endDate(endDate)
                .tradingDays(dailyReturns.size())
                .build();
    }

    /**
     * 계좌 ID 없이 전체 데이터로 리스크 메트릭스 계산
     */
    public RiskMetricsDto calculateRiskMetrics(LocalDate startDate, LocalDate endDate) {
        return calculateRiskMetrics(null, startDate, endDate);
    }

    /**
     * VaR (Value at Risk) 계산 - Historical Method
     * @param returns 일별 수익률 목록
     * @param confidenceLevel 신뢰수준 (0.95 또는 0.99)
     * @param portfolioValue 현재 포트폴리오 가치
     */
    public RiskMetricsDto.VaRDto calculateVaR(List<BigDecimal> returns, BigDecimal confidenceLevel,
                                               BigDecimal portfolioValue) {
        if (returns.isEmpty()) {
            return RiskMetricsDto.VaRDto.builder()
                    .dailyVaR(BigDecimal.ZERO)
                    .weeklyVaR(BigDecimal.ZERO)
                    .monthlyVaR(BigDecimal.ZERO)
                    .dailyVaRAmount(BigDecimal.ZERO)
                    .confidenceLevel(confidenceLevel)
                    .build();
        }

        // 수익률을 오름차순 정렬
        List<BigDecimal> sortedReturns = returns.stream()
                .sorted()
                .collect(Collectors.toList());

        // VaR 퍼센타일 인덱스 계산 (1 - 신뢰수준)
        double percentile = 1 - confidenceLevel.doubleValue();
        int varIndex = (int) Math.floor(percentile * sortedReturns.size());
        varIndex = Math.max(0, Math.min(varIndex, sortedReturns.size() - 1));

        // 일간 VaR (음수 값)
        BigDecimal dailyVaR = sortedReturns.get(varIndex);

        // 시간 스케일링 (√t 규칙 적용)
        BigDecimal weeklyVaR = dailyVaR.multiply(BigDecimal.valueOf(Math.sqrt(5)));
        BigDecimal monthlyVaR = dailyVaR.multiply(BigDecimal.valueOf(Math.sqrt(21)));

        // 금액 기준 VaR
        BigDecimal dailyVaRAmount = portfolioValue != null
                ? portfolioValue.multiply(dailyVaR.abs())
                : BigDecimal.ZERO;

        return RiskMetricsDto.VaRDto.builder()
                .dailyVaR(dailyVaR.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP))
                .weeklyVaR(weeklyVaR.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP))
                .monthlyVaR(monthlyVaR.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP))
                .dailyVaRAmount(dailyVaRAmount.setScale(0, RoundingMode.HALF_UP))
                .confidenceLevel(confidenceLevel)
                .build();
    }

    /**
     * 샤프 비율 계산
     * Sharpe Ratio = (평균 수익률 - 무위험 이자율) / 표준편차
     */
    public BigDecimal calculateSharpeRatio(List<BigDecimal> returns, BigDecimal riskFreeRate) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgReturn = calculateMean(returns);
        BigDecimal stdDev = calculateStandardDeviation(returns);

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 일간 무위험 이자율
        BigDecimal dailyRiskFreeRate = riskFreeRate.divide(
                new BigDecimal(TRADING_DAYS_PER_YEAR), 8, RoundingMode.HALF_UP);

        // 일간 샤프 비율 계산 후 연환산
        BigDecimal dailySharpe = avgReturn.subtract(dailyRiskFreeRate)
                .divide(stdDev, 4, RoundingMode.HALF_UP);

        // 연환산: √252 곱하기
        return dailySharpe.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 소르티노 비율 계산
     * Sortino Ratio = (평균 수익률 - 무위험 이자율) / 하락 편차
     */
    public BigDecimal calculateSortinoRatio(List<BigDecimal> returns, BigDecimal targetReturn) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgReturn = calculateMean(returns);
        BigDecimal downsideDeviation = calculateDownsideDeviation(returns, targetReturn);

        if (downsideDeviation.compareTo(BigDecimal.ZERO) == 0) {
            // 하락이 없으면 매우 좋은 것으로 간주
            return avgReturn.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("10.00") : BigDecimal.ZERO;
        }

        // 일간 목표 수익률
        BigDecimal dailyTarget = targetReturn.divide(
                new BigDecimal(TRADING_DAYS_PER_YEAR), 8, RoundingMode.HALF_UP);

        // 일간 소르티노 비율 계산 후 연환산
        BigDecimal dailySortino = avgReturn.subtract(dailyTarget)
                .divide(downsideDeviation, 4, RoundingMode.HALF_UP);

        return dailySortino.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 칼마 비율 계산
     * Calmar Ratio = CAGR / |Maximum Drawdown|
     */
    public BigDecimal calculateCalmarRatio(BigDecimal cagr, BigDecimal maxDrawdown) {
        if (maxDrawdown == null || maxDrawdown.abs().compareTo(BigDecimal.ZERO) == 0) {
            return cagr != null && cagr.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("10.00") : BigDecimal.ZERO;
        }

        if (cagr == null) {
            return BigDecimal.ZERO;
        }

        return cagr.divide(maxDrawdown.abs(), 2, RoundingMode.HALF_UP);
    }

    /**
     * 일별 수익률 계산
     */
    private List<BigDecimal> calculateDailyReturns(List<Transaction> transactions) {
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        Map<LocalDate, BigDecimal> dailyValues = new TreeMap<>();
        BigDecimal runningValue = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate().toLocalDate();
            BigDecimal amount = transaction.getTotalAmount();

            if (transaction.getType() == TransactionType.BUY) {
                runningValue = runningValue.add(amount);
            } else {
                BigDecimal realizedPnl = transaction.getRealizedPnl() != null
                        ? transaction.getRealizedPnl() : BigDecimal.ZERO;
                BigDecimal costBasis = transaction.getCostBasis() != null
                        ? transaction.getCostBasis() : amount;
                runningValue = runningValue.subtract(costBasis).add(amount).add(realizedPnl);
            }

            dailyValues.put(date, runningValue);
        }

        List<BigDecimal> dailyReturns = new ArrayList<>();
        BigDecimal previousValue = null;

        for (BigDecimal value : dailyValues.values()) {
            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = value.subtract(previousValue)
                        .divide(previousValue, 6, RoundingMode.HALF_UP);
                dailyReturns.add(dailyReturn);
            }
            previousValue = value;
        }

        return dailyReturns;
    }

    /**
     * 평균 계산
     */
    private BigDecimal calculateMean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * 표준편차 계산
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal mean = calculateMean(values);
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;

        for (BigDecimal value : values) {
            BigDecimal diff = value.subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal variance = sumSquaredDiff.divide(
                new BigDecimal(values.size() - 1), 8, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 하락 편차 계산 (Downside Deviation)
     * 목표 수익률 이하의 수익률만 고려
     */
    private BigDecimal calculateDownsideDeviation(List<BigDecimal> returns, BigDecimal targetReturn) {
        BigDecimal dailyTarget = targetReturn.divide(
                new BigDecimal(TRADING_DAYS_PER_YEAR), 8, RoundingMode.HALF_UP);

        List<BigDecimal> negativeDeviations = returns.stream()
                .filter(r -> r.compareTo(dailyTarget) < 0)
                .map(r -> r.subtract(dailyTarget).pow(2))
                .collect(Collectors.toList());

        if (negativeDeviations.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumSquared = negativeDeviations.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = sumSquared.divide(
                new BigDecimal(returns.size()), 8, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 승률 계산
     */
    private BigDecimal calculateWinRate(List<Transaction> transactions) {
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .filter(t -> t.getRealizedPnl() != null)
                .collect(Collectors.toList());

        if (sellTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long winCount = sellTransactions.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();

        return new BigDecimal(winCount)
                .divide(new BigDecimal(sellTransactions.size()), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 손익비 (Profit Factor) 계산
     * Profit Factor = 총 이익 / 총 손실
     */
    private BigDecimal calculateProfitFactor(List<Transaction> transactions) {
        BigDecimal totalProfit = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(Transaction::getRealizedPnl)
                .filter(pnl -> pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLoss = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .map(Transaction::getRealizedPnl)
                .filter(pnl -> pnl != null && pnl.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalLoss.compareTo(BigDecimal.ZERO) == 0) {
            return totalProfit.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("10.00") : BigDecimal.ZERO;
        }

        return totalProfit.divide(totalLoss, 2, RoundingMode.HALF_UP);
    }

    /**
     * 리스크 등급 결정
     */
    private RiskMetricsDto.RiskLevel determineRiskLevel(
            BigDecimal volatility, BigDecimal maxDrawdown, BigDecimal sharpeRatio) {

        // 변동성 20% 초과, MDD 20% 초과, 샤프 비율 0.5 미만이면 HIGH
        if (volatility.compareTo(new BigDecimal("0.20")) > 0
                || maxDrawdown.compareTo(new BigDecimal("20")) > 0
                || sharpeRatio.compareTo(new BigDecimal("0.5")) < 0) {
            return RiskMetricsDto.RiskLevel.HIGH;
        }

        // 변동성 10% 초과, MDD 10% 초과, 샤프 비율 1 미만이면 MEDIUM
        if (volatility.compareTo(new BigDecimal("0.10")) > 0
                || maxDrawdown.compareTo(new BigDecimal("10")) > 0
                || sharpeRatio.compareTo(new BigDecimal("1.0")) < 0) {
            return RiskMetricsDto.RiskLevel.MEDIUM;
        }

        return RiskMetricsDto.RiskLevel.LOW;
    }

    /**
     * 빈 리스크 메트릭스 빌더
     */
    private RiskMetricsDto buildEmptyRiskMetrics(LocalDate startDate, LocalDate endDate) {
        RiskMetricsDto.VaRDto emptyVaR = RiskMetricsDto.VaRDto.builder()
                .dailyVaR(BigDecimal.ZERO)
                .weeklyVaR(BigDecimal.ZERO)
                .monthlyVaR(BigDecimal.ZERO)
                .dailyVaRAmount(BigDecimal.ZERO)
                .confidenceLevel(new BigDecimal("0.95"))
                .build();

        return RiskMetricsDto.builder()
                .sharpeRatio(BigDecimal.ZERO)
                .sortinoRatio(BigDecimal.ZERO)
                .calmarRatio(BigDecimal.ZERO)
                .informationRatio(BigDecimal.ZERO)
                .var95(emptyVaR)
                .var99(emptyVaR)
                .maxDrawdown(BigDecimal.ZERO)
                .volatility(BigDecimal.ZERO)
                .downsideDeviation(BigDecimal.ZERO)
                .beta(BigDecimal.ZERO)
                .alpha(BigDecimal.ZERO)
                .cagr(BigDecimal.ZERO)
                .winRate(BigDecimal.ZERO)
                .profitFactor(BigDecimal.ZERO)
                .riskLevel(RiskMetricsDto.RiskLevel.LOW)
                .startDate(startDate)
                .endDate(endDate)
                .tradingDays(0)
                .build();
    }
}
