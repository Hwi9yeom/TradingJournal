package com.trading.journal.service;

import com.trading.journal.dto.TradingPatternDto;
import com.trading.journal.dto.TradingPatternDto.*;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 거래 패턴 분석 서비스
 * 연승/연패, 시간대별 성과, 보유 기간 분석 등
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TradingPatternService {

    private final TransactionRepository transactionRepository;

    private static final String[] DAY_OF_WEEK_KOREAN = {
            "", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"
    };

    private static final String[] MONTH_NAMES = {
            "", "1월", "2월", "3월", "4월", "5월", "6월",
            "7월", "8월", "9월", "10월", "11월", "12월"
    };

    /**
     * 종합 거래 패턴 분석
     */
    @Cacheable(value = "analysis", key = "'pattern_' + #accountId + '_' + #startDate + '_' + #endDate")
    public TradingPatternDto analyzePatterns(Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("Analyzing trading patterns for account {} from {} to {}", accountId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Transaction> transactions;
        if (accountId != null) {
            transactions = transactionRepository.findByAccountIdAndDateRange(accountId, startDateTime, endDateTime);
        } else {
            transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        }

        // 매도 거래만 필터링 (실현 손익 기준)
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .filter(t -> t.getRealizedPnl() != null)
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        return TradingPatternDto.builder()
                .streakAnalysis(analyzeStreaks(sellTransactions))
                .dayOfWeekPerformance(analyzeDayOfWeek(sellTransactions))
                .monthlySeasonality(analyzeMonthlySeasonality(sellTransactions))
                .tradeSizeAnalysis(analyzeTradeSizes(sellTransactions))
                .holdingPeriodAnalysis(analyzeHoldingPeriods(transactions))
                .startDate(startDate)
                .endDate(endDate)
                .totalTrades(sellTransactions.size())
                .build();
    }

    public TradingPatternDto analyzePatterns(LocalDate startDate, LocalDate endDate) {
        return analyzePatterns(null, startDate, endDate);
    }

    /**
     * 연승/연패 분석
     */
    public StreakAnalysis analyzeStreaks(List<Transaction> sellTransactions) {
        if (sellTransactions.isEmpty()) {
            return StreakAnalysis.builder()
                    .currentStreak(0)
                    .maxWinStreak(0)
                    .maxLossStreak(0)
                    .avgWinStreak(BigDecimal.ZERO)
                    .avgLossStreak(BigDecimal.ZERO)
                    .winStreakCount(0)
                    .lossStreakCount(0)
                    .recentStreaks(new ArrayList<>())
                    .build();
        }

        List<StreakEvent> streaks = new ArrayList<>();
        int currentStreak = 0;
        int maxWinStreak = 0;
        int maxLossStreak = 0;

        LocalDate streakStartDate = null;
        BigDecimal streakProfit = BigDecimal.ZERO;
        boolean currentIsWin = false;

        for (int i = 0; i < sellTransactions.size(); i++) {
            Transaction tx = sellTransactions.get(i);
            boolean isWin = tx.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0;
            LocalDate txDate = tx.getTransactionDate().toLocalDate();

            if (i == 0) {
                currentStreak = isWin ? 1 : -1;
                currentIsWin = isWin;
                streakStartDate = txDate;
                streakProfit = tx.getRealizedPnl();
            } else if ((isWin && currentStreak > 0) || (!isWin && currentStreak < 0)) {
                // 스트릭 계속
                currentStreak += isWin ? 1 : -1;
                streakProfit = streakProfit.add(tx.getRealizedPnl());
            } else {
                // 스트릭 종료 - 이벤트 저장
                if (Math.abs(currentStreak) >= 2) {
                    streaks.add(StreakEvent.builder()
                            .startDate(streakStartDate)
                            .endDate(sellTransactions.get(i - 1).getTransactionDate().toLocalDate())
                            .streakLength(Math.abs(currentStreak))
                            .isWinStreak(currentIsWin)
                            .totalProfit(streakProfit)
                            .build());
                }

                // 최대 스트릭 업데이트
                if (currentStreak > 0) {
                    maxWinStreak = Math.max(maxWinStreak, currentStreak);
                } else {
                    maxLossStreak = Math.max(maxLossStreak, Math.abs(currentStreak));
                }

                // 새 스트릭 시작
                currentStreak = isWin ? 1 : -1;
                currentIsWin = isWin;
                streakStartDate = txDate;
                streakProfit = tx.getRealizedPnl();
            }
        }

        // 마지막 스트릭 처리
        if (currentStreak > 0) {
            maxWinStreak = Math.max(maxWinStreak, currentStreak);
        } else {
            maxLossStreak = Math.max(maxLossStreak, Math.abs(currentStreak));
        }

        if (Math.abs(currentStreak) >= 2) {
            streaks.add(StreakEvent.builder()
                    .startDate(streakStartDate)
                    .endDate(sellTransactions.get(sellTransactions.size() - 1).getTransactionDate().toLocalDate())
                    .streakLength(Math.abs(currentStreak))
                    .isWinStreak(currentIsWin)
                    .totalProfit(streakProfit)
                    .build());
        }

        // 평균 스트릭 계산
        List<StreakEvent> winStreaks = streaks.stream()
                .filter(StreakEvent::isWinStreak)
                .collect(Collectors.toList());
        List<StreakEvent> lossStreaks = streaks.stream()
                .filter(s -> !s.isWinStreak())
                .collect(Collectors.toList());

        BigDecimal avgWinStreak = winStreaks.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(winStreaks.stream().mapToInt(StreakEvent::getStreakLength).average().orElse(0))
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal avgLossStreak = lossStreaks.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(lossStreaks.stream().mapToInt(StreakEvent::getStreakLength).average().orElse(0))
                        .setScale(2, RoundingMode.HALF_UP);

        // 최근 5개 스트릭만
        List<StreakEvent> recentStreaks = streaks.stream()
                .sorted(Comparator.comparing(StreakEvent::getEndDate).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return StreakAnalysis.builder()
                .currentStreak(currentStreak)
                .maxWinStreak(maxWinStreak)
                .maxLossStreak(maxLossStreak)
                .avgWinStreak(avgWinStreak)
                .avgLossStreak(avgLossStreak)
                .winStreakCount(winStreaks.size())
                .lossStreakCount(lossStreaks.size())
                .recentStreaks(recentStreaks)
                .build();
    }

    /**
     * 요일별 성과 분석
     */
    public List<DayOfWeekPerformance> analyzeDayOfWeek(List<Transaction> sellTransactions) {
        Map<DayOfWeek, List<Transaction>> byDayOfWeek = sellTransactions.stream()
                .collect(Collectors.groupingBy(t ->
                        t.getTransactionDate().getDayOfWeek()));

        List<DayOfWeekPerformance> result = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            List<Transaction> dayTransactions = byDayOfWeek.getOrDefault(day, new ArrayList<>());

            int tradeCount = dayTransactions.size();
            int winCount = (int) dayTransactions.stream()
                    .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                    .count();
            int lossCount = tradeCount - winCount;

            BigDecimal winRate = tradeCount > 0
                    ? BigDecimal.valueOf((double) winCount / tradeCount * 100).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal totalProfit = dayTransactions.stream()
                    .map(Transaction::getRealizedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avgReturn = BigDecimal.ZERO;
            BigDecimal totalCost = dayTransactions.stream()
                    .map(t -> t.getCostBasis() != null ? t.getCostBasis() : t.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                avgReturn = totalProfit.divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            result.add(DayOfWeekPerformance.builder()
                    .dayOfWeek(day)
                    .dayOfWeekKorean(DAY_OF_WEEK_KOREAN[day.getValue()])
                    .tradeCount(tradeCount)
                    .winRate(winRate)
                    .avgReturn(avgReturn)
                    .totalProfit(totalProfit)
                    .winCount(winCount)
                    .lossCount(lossCount)
                    .build());
        }

        return result;
    }

    /**
     * 월별 계절성 분석
     */
    public List<MonthlySeasonality> analyzeMonthlySeasonality(List<Transaction> sellTransactions) {
        Map<Integer, List<Transaction>> byMonth = sellTransactions.stream()
                .collect(Collectors.groupingBy(t ->
                        t.getTransactionDate().getMonthValue()));

        List<MonthlySeasonality> result = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            List<Transaction> monthTransactions = byMonth.getOrDefault(month, new ArrayList<>());

            int tradeCount = monthTransactions.size();
            int winCount = (int) monthTransactions.stream()
                    .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                    .count();
            int lossCount = tradeCount - winCount;

            BigDecimal winRate = tradeCount > 0
                    ? BigDecimal.valueOf((double) winCount / tradeCount * 100).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal totalProfit = monthTransactions.stream()
                    .map(Transaction::getRealizedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avgReturn = BigDecimal.ZERO;
            BigDecimal totalCost = monthTransactions.stream()
                    .map(t -> t.getCostBasis() != null ? t.getCostBasis() : t.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                avgReturn = totalProfit.divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            result.add(MonthlySeasonality.builder()
                    .month(month)
                    .monthName(MONTH_NAMES[month])
                    .tradeCount(tradeCount)
                    .winRate(winRate)
                    .avgReturn(avgReturn)
                    .totalProfit(totalProfit)
                    .winCount(winCount)
                    .lossCount(lossCount)
                    .build());
        }

        return result;
    }

    /**
     * 거래 규모 분석
     */
    public TradeSizeAnalysis analyzeTradeSizes(List<Transaction> sellTransactions) {
        if (sellTransactions.isEmpty()) {
            return TradeSizeAnalysis.builder()
                    .avgTradeAmount(BigDecimal.ZERO)
                    .maxTradeAmount(BigDecimal.ZERO)
                    .minTradeAmount(BigDecimal.ZERO)
                    .medianTradeAmount(BigDecimal.ZERO)
                    .stdDevTradeAmount(BigDecimal.ZERO)
                    .avgWinTradeAmount(BigDecimal.ZERO)
                    .avgLossTradeAmount(BigDecimal.ZERO)
                    .build();
        }

        List<BigDecimal> amounts = sellTransactions.stream()
                .map(Transaction::getTotalAmount)
                .sorted()
                .collect(Collectors.toList());

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(new BigDecimal(amounts.size()), 2, RoundingMode.HALF_UP);

        BigDecimal max = amounts.get(amounts.size() - 1);
        BigDecimal min = amounts.get(0);
        BigDecimal median = amounts.get(amounts.size() / 2);

        // 표준편차 계산
        BigDecimal variance = amounts.stream()
                .map(a -> a.subtract(avg).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(amounts.size()), 6, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);

        // 수익/손실 거래 평균 금액
        List<Transaction> winTrades = sellTransactions.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        List<Transaction> lossTrades = sellTransactions.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) <= 0)
                .collect(Collectors.toList());

        BigDecimal avgWin = winTrades.isEmpty() ? BigDecimal.ZERO :
                winTrades.stream().map(Transaction::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(winTrades.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss = lossTrades.isEmpty() ? BigDecimal.ZERO :
                lossTrades.stream().map(Transaction::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(lossTrades.size()), 2, RoundingMode.HALF_UP);

        return TradeSizeAnalysis.builder()
                .avgTradeAmount(avg)
                .maxTradeAmount(max)
                .minTradeAmount(min)
                .medianTradeAmount(median)
                .stdDevTradeAmount(stdDev)
                .avgWinTradeAmount(avgWin)
                .avgLossTradeAmount(avgLoss)
                .build();
    }

    /**
     * 보유 기간 분석
     */
    public HoldingPeriodAnalysis analyzeHoldingPeriods(List<Transaction> transactions) {
        // 종목별로 매수-매도 매칭하여 보유 기간 계산
        Map<Long, List<Transaction>> byStock = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getStock().getId()));

        List<HoldingPeriodRecord> records = new ArrayList<>();

        for (List<Transaction> stockTxs : byStock.values()) {
            List<Transaction> buys = stockTxs.stream()
                    .filter(t -> t.getType() == TransactionType.BUY)
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .collect(Collectors.toList());

            List<Transaction> sells = stockTxs.stream()
                    .filter(t -> t.getType() == TransactionType.SELL)
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .collect(Collectors.toList());

            // FIFO 매칭
            int buyIndex = 0;
            for (Transaction sell : sells) {
                if (buyIndex < buys.size()) {
                    Transaction buy = buys.get(buyIndex);
                    long days = ChronoUnit.DAYS.between(
                            buy.getTransactionDate().toLocalDate(),
                            sell.getTransactionDate().toLocalDate());

                    boolean isWin = sell.getRealizedPnl() != null &&
                            sell.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0;

                    records.add(new HoldingPeriodRecord((int) days, isWin,
                            sell.getRealizedPnl() != null ? sell.getRealizedPnl() : BigDecimal.ZERO));
                    buyIndex++;
                }
            }
        }

        if (records.isEmpty()) {
            return HoldingPeriodAnalysis.builder()
                    .avgHoldingDays(BigDecimal.ZERO)
                    .maxHoldingDays(0)
                    .minHoldingDays(0)
                    .medianHoldingDays(0)
                    .avgWinHoldingDays(BigDecimal.ZERO)
                    .avgLossHoldingDays(BigDecimal.ZERO)
                    .holdingPeriodDistribution(createEmptyDistribution())
                    .build();
        }

        List<Integer> allDays = records.stream()
                .map(r -> r.days)
                .sorted()
                .collect(Collectors.toList());

        int sum = allDays.stream().mapToInt(Integer::intValue).sum();
        BigDecimal avg = BigDecimal.valueOf((double) sum / allDays.size()).setScale(1, RoundingMode.HALF_UP);

        List<HoldingPeriodRecord> winRecords = records.stream()
                .filter(r -> r.isWin)
                .collect(Collectors.toList());
        List<HoldingPeriodRecord> lossRecords = records.stream()
                .filter(r -> !r.isWin)
                .collect(Collectors.toList());

        BigDecimal avgWin = winRecords.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(winRecords.stream().mapToInt(r -> r.days).average().orElse(0))
                        .setScale(1, RoundingMode.HALF_UP);

        BigDecimal avgLoss = lossRecords.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(lossRecords.stream().mapToInt(r -> r.days).average().orElse(0))
                        .setScale(1, RoundingMode.HALF_UP);

        return HoldingPeriodAnalysis.builder()
                .avgHoldingDays(avg)
                .maxHoldingDays(allDays.get(allDays.size() - 1))
                .minHoldingDays(allDays.get(0))
                .medianHoldingDays(allDays.get(allDays.size() / 2))
                .avgWinHoldingDays(avgWin)
                .avgLossHoldingDays(avgLoss)
                .holdingPeriodDistribution(createDistribution(records))
                .build();
    }

    private List<HoldingPeriodBucket> createEmptyDistribution() {
        return Arrays.asList(
                createBucket("1-7일", 1, 7, new ArrayList<>()),
                createBucket("1-2주", 8, 14, new ArrayList<>()),
                createBucket("2-4주", 15, 30, new ArrayList<>()),
                createBucket("1-3개월", 31, 90, new ArrayList<>()),
                createBucket("3개월+", 91, Integer.MAX_VALUE, new ArrayList<>())
        );
    }

    private List<HoldingPeriodBucket> createDistribution(List<HoldingPeriodRecord> records) {
        return Arrays.asList(
                createBucket("1-7일", 1, 7, records),
                createBucket("1-2주", 8, 14, records),
                createBucket("2-4주", 15, 30, records),
                createBucket("1-3개월", 31, 90, records),
                createBucket("3개월+", 91, Integer.MAX_VALUE, records)
        );
    }

    private HoldingPeriodBucket createBucket(String label, int minDays, int maxDays,
                                              List<HoldingPeriodRecord> records) {
        List<HoldingPeriodRecord> bucketRecords = records.stream()
                .filter(r -> r.days >= minDays && r.days <= maxDays)
                .collect(Collectors.toList());

        int tradeCount = bucketRecords.size();
        int winCount = (int) bucketRecords.stream().filter(r -> r.isWin).count();

        BigDecimal winRate = tradeCount > 0
                ? BigDecimal.valueOf((double) winCount / tradeCount * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalProfit = bucketRecords.stream()
                .map(r -> r.profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgReturn = BigDecimal.ZERO;

        return HoldingPeriodBucket.builder()
                .label(label)
                .minDays(minDays)
                .maxDays(maxDays)
                .tradeCount(tradeCount)
                .winRate(winRate)
                .avgReturn(avgReturn)
                .totalProfit(totalProfit)
                .build();
    }

    private static class HoldingPeriodRecord {
        int days;
        boolean isWin;
        BigDecimal profit;

        HoldingPeriodRecord(int days, boolean isWin, BigDecimal profit) {
            this.days = days;
            this.isWin = isWin;
            this.profit = profit;
        }
    }
}
