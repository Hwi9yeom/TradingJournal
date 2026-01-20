package com.trading.journal.service;

import com.trading.journal.dto.TradingPatternDto;
import com.trading.journal.dto.TradingPatternDto.*;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래 패턴 분석 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingPatternService {

    private final TransactionRepository transactionRepository;

    private static final String[] DAY_LABELS = {
        "", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"
    };
    private static final String[] MONTH_LABELS = {
        "", "1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"
    };

    /** 종합 거래 패턴 분석 (Controller에서 호출) */
    @Transactional(readOnly = true)
    public TradingPatternDto analyzePatterns(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        return getFullAnalysis(accountId, startDate, endDate);
    }

    /** 종합 거래 패턴 분석 */
    @Transactional(readOnly = true)
    public TradingPatternDto getFullAnalysis(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Transaction> allTransactions =
                accountId != null
                        ? transactionRepository.findByAccountIdAndDateRange(
                                accountId, startDateTime, endDateTime)
                        : transactionRepository.findByDateRange(startDateTime, endDateTime);

        // 매도 거래만 추출 (실현 손익 기반 분석)
        List<Transaction> sellTransactions =
                allTransactions.stream()
                        .filter(t -> t.getType() == TransactionType.SELL)
                        .sorted(Comparator.comparing(Transaction::getTransactionDate))
                        .collect(Collectors.toList());

        return TradingPatternDto.builder()
                .streakAnalysis(analyzeStreaks(sellTransactions))
                .dayOfWeekPerformance(analyzeDayOfWeek(sellTransactions))
                .monthlySeasonality(analyzeMonthlySeasonality(sellTransactions))
                .tradeSizeAnalysis(analyzeTradeSize(sellTransactions))
                .holdingPeriodAnalysis(analyzeHoldingPeriod(allTransactions))
                .startDate(startDate)
                .endDate(endDate)
                .totalTrades(sellTransactions.size())
                .build();
    }

    /** 스트릭 분석 */
    public StreakAnalysis analyzeStreaks(List<Transaction> sellTransactions) {
        if (sellTransactions.isEmpty()) {
            return StreakAnalysis.builder()
                    .currentStreak(0)
                    .maxWinStreak(0)
                    .maxLossStreak(0)
                    .avgWinStreak(0)
                    .avgLossStreak(0)
                    .recentStreaks(Collections.emptyList())
                    .build();
        }

        List<StreakEvent> streaks = new ArrayList<>();
        int currentStreak = 0;
        int maxWinStreak = 0;
        int maxLossStreak = 0;
        List<Integer> winStreaks = new ArrayList<>();
        List<Integer> lossStreaks = new ArrayList<>();

        LocalDate streakStart = null;
        LocalDate streakEnd = null;
        BigDecimal streakPnl = BigDecimal.ZERO;
        Boolean lastWin = null;
        int streakLength = 0;

        for (Transaction tx : sellTransactions) {
            BigDecimal pnl = tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO;
            boolean isWin = pnl.compareTo(BigDecimal.ZERO) > 0;
            LocalDate txDate = tx.getTransactionDate().toLocalDate();

            if (lastWin == null) {
                // 첫 거래
                lastWin = isWin;
                streakStart = txDate;
                streakLength = 1;
                streakPnl = pnl;
            } else if (lastWin == isWin) {
                // 스트릭 계속
                streakLength++;
                streakPnl = streakPnl.add(pnl);
            } else {
                // 스트릭 종료
                streakEnd = txDate;
                streaks.add(
                        StreakEvent.builder()
                                .startDate(streakStart)
                                .endDate(streakEnd)
                                .length(streakLength)
                                .isWinStreak(lastWin)
                                .totalPnl(streakPnl)
                                .build());

                if (lastWin) {
                    winStreaks.add(streakLength);
                    maxWinStreak = Math.max(maxWinStreak, streakLength);
                } else {
                    lossStreaks.add(streakLength);
                    maxLossStreak = Math.max(maxLossStreak, streakLength);
                }

                // 새 스트릭 시작
                lastWin = isWin;
                streakStart = txDate;
                streakLength = 1;
                streakPnl = pnl;
            }
        }

        // 마지막 스트릭 처리
        if (streakLength > 0) {
            currentStreak = lastWin ? streakLength : -streakLength;
            if (lastWin) {
                winStreaks.add(streakLength);
                maxWinStreak = Math.max(maxWinStreak, streakLength);
            } else {
                lossStreaks.add(streakLength);
                maxLossStreak = Math.max(maxLossStreak, streakLength);
            }
        }

        int avgWinStreak =
                winStreaks.isEmpty()
                        ? 0
                        : (int) winStreaks.stream().mapToInt(i -> i).average().orElse(0);
        int avgLossStreak =
                lossStreaks.isEmpty()
                        ? 0
                        : (int) lossStreaks.stream().mapToInt(i -> i).average().orElse(0);

        // 최근 5개 스트릭
        List<StreakEvent> recentStreaks =
                streaks.size() > 5 ? streaks.subList(streaks.size() - 5, streaks.size()) : streaks;

        return StreakAnalysis.builder()
                .currentStreak(currentStreak)
                .maxWinStreak(maxWinStreak)
                .maxLossStreak(maxLossStreak)
                .avgWinStreak(avgWinStreak)
                .avgLossStreak(avgLossStreak)
                .recentStreaks(recentStreaks)
                .build();
    }

    /** 요일별 성과 분석 */
    public List<DayOfWeekPerformance> analyzeDayOfWeek(List<Transaction> sellTransactions) {
        Map<DayOfWeek, List<Transaction>> byDayOfWeek =
                sellTransactions.stream()
                        .collect(Collectors.groupingBy(t -> t.getTransactionDate().getDayOfWeek()));

        List<DayOfWeekPerformance> result = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            List<Transaction> dayTx = byDayOfWeek.getOrDefault(day, Collections.emptyList());

            if (dayTx.isEmpty()) {
                result.add(
                        DayOfWeekPerformance.builder()
                                .dayOfWeek(day)
                                .dayOfWeekLabel(DAY_LABELS[day.getValue()])
                                .tradeCount(0)
                                .winCount(0)
                                .winRate(BigDecimal.ZERO)
                                .avgReturn(BigDecimal.ZERO)
                                .totalPnl(BigDecimal.ZERO)
                                .avgPnl(BigDecimal.ZERO)
                                .build());
                continue;
            }

            int winCount = 0;
            BigDecimal totalPnl = BigDecimal.ZERO;
            BigDecimal totalReturn = BigDecimal.ZERO;

            for (Transaction tx : dayTx) {
                BigDecimal pnl =
                        tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO;
                if (pnl.compareTo(BigDecimal.ZERO) > 0) winCount++;
                totalPnl = totalPnl.add(pnl);

                // 수익률 계산 (원가 대비)
                if (tx.getCostBasis() != null && tx.getCostBasis().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal returnPct =
                            pnl.divide(tx.getCostBasis(), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                    totalReturn = totalReturn.add(returnPct);
                }
            }

            BigDecimal winRate =
                    BigDecimal.valueOf(winCount)
                            .divide(BigDecimal.valueOf(dayTx.size()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
            BigDecimal avgPnl =
                    totalPnl.divide(BigDecimal.valueOf(dayTx.size()), 2, RoundingMode.HALF_UP);
            BigDecimal avgReturn =
                    totalReturn.divide(BigDecimal.valueOf(dayTx.size()), 2, RoundingMode.HALF_UP);

            result.add(
                    DayOfWeekPerformance.builder()
                            .dayOfWeek(day)
                            .dayOfWeekLabel(DAY_LABELS[day.getValue()])
                            .tradeCount(dayTx.size())
                            .winCount(winCount)
                            .winRate(winRate)
                            .avgReturn(avgReturn)
                            .totalPnl(totalPnl)
                            .avgPnl(avgPnl)
                            .build());
        }

        return result;
    }

    /** 월별 계절성 분석 */
    public List<MonthlySeasonality> analyzeMonthlySeasonality(List<Transaction> sellTransactions) {
        Map<Integer, List<Transaction>> byMonth =
                sellTransactions.stream()
                        .collect(
                                Collectors.groupingBy(t -> t.getTransactionDate().getMonthValue()));

        List<MonthlySeasonality> result = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            List<Transaction> monthTx = byMonth.getOrDefault(month, Collections.emptyList());

            if (monthTx.isEmpty()) {
                result.add(
                        MonthlySeasonality.builder()
                                .month(month)
                                .monthLabel(MONTH_LABELS[month])
                                .tradeCount(0)
                                .winCount(0)
                                .winRate(BigDecimal.ZERO)
                                .avgReturn(BigDecimal.ZERO)
                                .totalPnl(BigDecimal.ZERO)
                                .yearCount(0)
                                .build());
                continue;
            }

            int winCount = 0;
            BigDecimal totalPnl = BigDecimal.ZERO;
            BigDecimal totalReturn = BigDecimal.ZERO;
            Set<Integer> years = new HashSet<>();

            for (Transaction tx : monthTx) {
                BigDecimal pnl =
                        tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO;
                if (pnl.compareTo(BigDecimal.ZERO) > 0) winCount++;
                totalPnl = totalPnl.add(pnl);
                years.add(tx.getTransactionDate().getYear());

                if (tx.getCostBasis() != null && tx.getCostBasis().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal returnPct =
                            pnl.divide(tx.getCostBasis(), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                    totalReturn = totalReturn.add(returnPct);
                }
            }

            BigDecimal winRate =
                    BigDecimal.valueOf(winCount)
                            .divide(BigDecimal.valueOf(monthTx.size()), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
            BigDecimal avgReturn =
                    totalReturn.divide(BigDecimal.valueOf(monthTx.size()), 2, RoundingMode.HALF_UP);

            result.add(
                    MonthlySeasonality.builder()
                            .month(month)
                            .monthLabel(MONTH_LABELS[month])
                            .tradeCount(monthTx.size())
                            .winCount(winCount)
                            .winRate(winRate)
                            .avgReturn(avgReturn)
                            .totalPnl(totalPnl)
                            .yearCount(years.size())
                            .build());
        }

        return result;
    }

    /** 거래 규모 분석 */
    public TradeSizeAnalysis analyzeTradeSize(List<Transaction> sellTransactions) {
        if (sellTransactions.isEmpty()) {
            return TradeSizeAnalysis.builder()
                    .avgTradeAmount(BigDecimal.ZERO)
                    .maxTradeAmount(BigDecimal.ZERO)
                    .minTradeAmount(BigDecimal.ZERO)
                    .medianTradeAmount(BigDecimal.ZERO)
                    .stdDeviation(BigDecimal.ZERO)
                    .distribution(Collections.emptyList())
                    .build();
        }

        List<BigDecimal> amounts =
                sellTransactions.stream()
                        .map(Transaction::getTotalAmount)
                        .sorted()
                        .collect(Collectors.toList());

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
        BigDecimal max = amounts.get(amounts.size() - 1);
        BigDecimal min = amounts.get(0);
        BigDecimal median = amounts.get(amounts.size() / 2);

        // 표준편차 계산
        BigDecimal variance =
                amounts.stream()
                        .map(a -> a.subtract(avg).pow(2))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(amounts.size()), 4, RoundingMode.HALF_UP);
        BigDecimal stdDev =
                BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                        .setScale(2, RoundingMode.HALF_UP);

        // 구간별 분포
        List<TradeSizeBucket> distribution = createTradeSizeBuckets(sellTransactions);

        return TradeSizeAnalysis.builder()
                .avgTradeAmount(avg)
                .maxTradeAmount(max)
                .minTradeAmount(min)
                .medianTradeAmount(median)
                .stdDeviation(stdDev)
                .distribution(distribution)
                .build();
    }

    private List<TradeSizeBucket> createTradeSizeBuckets(List<Transaction> transactions) {
        // 구간 정의 (원)
        BigDecimal[][] bucketRanges = {
            {BigDecimal.ZERO, BigDecimal.valueOf(1000000)}, // 100만원 미만
            {BigDecimal.valueOf(1000000), BigDecimal.valueOf(5000000)}, // 100-500만원
            {BigDecimal.valueOf(5000000), BigDecimal.valueOf(10000000)}, // 500-1000만원
            {BigDecimal.valueOf(10000000), BigDecimal.valueOf(50000000)}, // 1000-5000만원
            {BigDecimal.valueOf(50000000), BigDecimal.valueOf(Long.MAX_VALUE)} // 5000만원 이상
        };
        String[] labels = {"100만원 미만", "100-500만원", "500-1000만원", "1000-5000만원", "5000만원 이상"};

        List<TradeSizeBucket> buckets = new ArrayList<>();
        int total = transactions.size();

        for (int i = 0; i < bucketRanges.length; i++) {
            BigDecimal minAmt = bucketRanges[i][0];
            BigDecimal maxAmt = bucketRanges[i][1];

            List<Transaction> bucketTx =
                    transactions.stream()
                            .filter(
                                    t -> {
                                        BigDecimal amt = t.getTotalAmount();
                                        return amt.compareTo(minAmt) >= 0
                                                && amt.compareTo(maxAmt) < 0;
                                    })
                            .collect(Collectors.toList());

            int count = bucketTx.size();
            int winCount =
                    (int)
                            bucketTx.stream()
                                    .filter(
                                            t ->
                                                    t.getRealizedPnl() != null
                                                            && t.getRealizedPnl()
                                                                            .compareTo(
                                                                                    BigDecimal.ZERO)
                                                                    > 0)
                                    .count();

            BigDecimal avgReturn = BigDecimal.ZERO;
            if (!bucketTx.isEmpty()) {
                BigDecimal totalReturn =
                        bucketTx.stream()
                                .filter(
                                        t ->
                                                t.getCostBasis() != null
                                                        && t.getCostBasis()
                                                                        .compareTo(BigDecimal.ZERO)
                                                                > 0)
                                .map(
                                        t ->
                                                t.getRealizedPnl()
                                                        .divide(
                                                                t.getCostBasis(),
                                                                4,
                                                                RoundingMode.HALF_UP)
                                                        .multiply(BigDecimal.valueOf(100)))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                avgReturn =
                        totalReturn.divide(
                                BigDecimal.valueOf(bucketTx.size()), 2, RoundingMode.HALF_UP);
            }

            buckets.add(
                    TradeSizeBucket.builder()
                            .label(labels[i])
                            .minAmount(minAmt)
                            .maxAmount(maxAmt)
                            .count(count)
                            .percentage(
                                    total > 0
                                            ? BigDecimal.valueOf(count * 100.0 / total)
                                                    .setScale(1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .avgReturn(avgReturn)
                            .winRate(
                                    count > 0
                                            ? BigDecimal.valueOf(winCount * 100.0 / count)
                                                    .setScale(1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .build());
        }

        return buckets;
    }

    /** 보유 기간 분석 (매수-매도 쌍 기반) */
    public HoldingPeriodAnalysis analyzeHoldingPeriod(List<Transaction> allTransactions) {
        // 종목별로 매수/매도 거래를 그룹화하여 보유 기간 추정
        Map<Long, List<Transaction>> byStock =
                allTransactions.stream().collect(Collectors.groupingBy(t -> t.getStock().getId()));

        List<Integer> holdingDays = new ArrayList<>();
        List<Integer> winHoldingDays = new ArrayList<>();
        List<Integer> lossHoldingDays = new ArrayList<>();

        for (List<Transaction> stockTx : byStock.values()) {
            List<Transaction> buys =
                    stockTx.stream()
                            .filter(t -> t.getType() == TransactionType.BUY)
                            .sorted(Comparator.comparing(Transaction::getTransactionDate))
                            .collect(Collectors.toList());

            List<Transaction> sells =
                    stockTx.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .sorted(Comparator.comparing(Transaction::getTransactionDate))
                            .collect(Collectors.toList());

            // 간단한 FIFO 매칭으로 보유 기간 추정
            int buyIdx = 0;
            for (Transaction sell : sells) {
                if (buyIdx < buys.size()) {
                    Transaction buy = buys.get(buyIdx);
                    int days =
                            (int)
                                    java.time.temporal.ChronoUnit.DAYS.between(
                                            buy.getTransactionDate().toLocalDate(),
                                            sell.getTransactionDate().toLocalDate());
                    days = Math.max(0, days);
                    holdingDays.add(days);

                    BigDecimal pnl =
                            sell.getRealizedPnl() != null ? sell.getRealizedPnl() : BigDecimal.ZERO;
                    if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                        winHoldingDays.add(days);
                    } else {
                        lossHoldingDays.add(days);
                    }
                    buyIdx++;
                }
            }
        }

        if (holdingDays.isEmpty()) {
            return HoldingPeriodAnalysis.builder()
                    .avgHoldingDays(BigDecimal.ZERO)
                    .avgWinHoldingDays(BigDecimal.ZERO)
                    .avgLossHoldingDays(BigDecimal.ZERO)
                    .maxHoldingDays(0)
                    .minHoldingDays(0)
                    .distribution(
                            createHoldingPeriodBuckets(
                                    Collections.emptyList(), Collections.emptyList()))
                    .build();
        }

        Collections.sort(holdingDays);

        return HoldingPeriodAnalysis.builder()
                .avgHoldingDays(
                        BigDecimal.valueOf(
                                        holdingDays.stream().mapToInt(i -> i).average().orElse(0))
                                .setScale(1, RoundingMode.HALF_UP))
                .avgWinHoldingDays(
                        BigDecimal.valueOf(
                                        winHoldingDays.stream()
                                                .mapToInt(i -> i)
                                                .average()
                                                .orElse(0))
                                .setScale(1, RoundingMode.HALF_UP))
                .avgLossHoldingDays(
                        BigDecimal.valueOf(
                                        lossHoldingDays.stream()
                                                .mapToInt(i -> i)
                                                .average()
                                                .orElse(0))
                                .setScale(1, RoundingMode.HALF_UP))
                .maxHoldingDays(holdingDays.get(holdingDays.size() - 1))
                .minHoldingDays(holdingDays.get(0))
                .distribution(createHoldingPeriodBuckets(holdingDays, winHoldingDays))
                .build();
    }

    private List<HoldingPeriodBucket> createHoldingPeriodBuckets(
            List<Integer> allDays, List<Integer> winDays) {
        int[][] ranges = {{0, 0}, {1, 3}, {4, 7}, {8, 30}, {31, 90}, {91, Integer.MAX_VALUE}};
        String[] labels = {"당일", "1-3일", "4-7일", "1주-1개월", "1-3개월", "3개월 이상"};

        List<HoldingPeriodBucket> buckets = new ArrayList<>();
        int total = allDays.size();

        for (int i = 0; i < ranges.length; i++) {
            int minD = ranges[i][0];
            int maxD = ranges[i][1];

            int count = (int) allDays.stream().filter(d -> d >= minD && d <= maxD).count();
            int winCount = (int) winDays.stream().filter(d -> d >= minD && d <= maxD).count();

            buckets.add(
                    HoldingPeriodBucket.builder()
                            .label(labels[i])
                            .minDays(minD)
                            .maxDays(maxD)
                            .count(count)
                            .percentage(
                                    total > 0
                                            ? BigDecimal.valueOf(count * 100.0 / total)
                                                    .setScale(1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .avgReturn(BigDecimal.ZERO)
                            .winRate(
                                    count > 0
                                            ? BigDecimal.valueOf(winCount * 100.0 / count)
                                                    .setScale(1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .build());
        }

        return buckets;
    }
}
