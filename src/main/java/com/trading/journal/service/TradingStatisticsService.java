package com.trading.journal.service;

import com.trading.journal.dto.TradingStatisticsDto;
import com.trading.journal.dto.TradingStatisticsDto.*;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 거래 통계 분석 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingStatisticsService {

    private final TransactionRepository transactionRepository;

    private static final String[] TIME_PERIODS = {"장전(08-09)", "오전(09-11)", "오후(11-14)", "장마감(14-16)"};
    private static final String[] DAY_NAMES = {"월", "화", "수", "목", "금", "토", "일"};

    /**
     * 시간대별 성과 분석
     */
    public List<TimeOfDayStats> getTimeOfDayPerformance(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL && t.getRealizedPnl() != null)
                .toList();

        Map<Integer, List<Transaction>> byHour = sellTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().getHour()));

        List<TimeOfDayStats> stats = new ArrayList<>();

        // 시간대별 집계 (8시-16시)
        for (int hour = 8; hour <= 16; hour++) {
            List<Transaction> hourTrades = byHour.getOrDefault(hour, Collections.emptyList());

            if (!hourTrades.isEmpty()) {
                int total = hourTrades.size();
                int winning = (int) hourTrades.stream()
                        .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                        .count();
                BigDecimal totalProfit = hourTrades.stream()
                        .map(Transaction::getRealizedPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avgReturn = totalProfit.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
                BigDecimal winRate = BigDecimal.valueOf(winning * 100.0 / total).setScale(2, RoundingMode.HALF_UP);

                stats.add(TimeOfDayStats.builder()
                        .timePeriod(String.format("%02d:00-%02d:00", hour, hour + 1))
                        .hour(hour)
                        .totalTrades(total)
                        .winningTrades(winning)
                        .winRate(winRate)
                        .totalReturn(calculateTotalReturnPercent(hourTrades))
                        .avgReturn(avgReturn)
                        .totalProfit(totalProfit)
                        .build());
            }
        }

        return stats;
    }

    /**
     * 요일별 성과 분석
     */
    public List<WeekdayStats> getWeekdayPerformance(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL && t.getRealizedPnl() != null)
                .toList();

        Map<DayOfWeek, List<Transaction>> byDay = sellTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().getDayOfWeek()));

        List<WeekdayStats> stats = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            List<Transaction> dayTrades = byDay.getOrDefault(day, Collections.emptyList());

            int total = dayTrades.size();
            int winning = (int) dayTrades.stream()
                    .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                    .count();
            BigDecimal totalProfit = dayTrades.stream()
                    .map(t -> t.getRealizedPnl() != null ? t.getRealizedPnl() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgReturn = total > 0
                    ? totalProfit.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal winRate = total > 0
                    ? BigDecimal.valueOf(winning * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            stats.add(WeekdayStats.builder()
                    .dayName(day.getDisplayName(TextStyle.SHORT, Locale.KOREAN))
                    .dayOfWeek(day.getValue())
                    .totalTrades(total)
                    .winningTrades(winning)
                    .winRate(winRate)
                    .totalReturn(calculateTotalReturnPercent(dayTrades))
                    .avgReturn(avgReturn)
                    .totalProfit(totalProfit)
                    .bestTimeSlot(findBestTimeSlot(dayTrades))
                    .build());
        }

        return stats;
    }

    /**
     * 종목별 성과 분석
     */
    public List<SymbolStats> getSymbolPerformance(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL && t.getRealizedPnl() != null)
                .toList();

        Map<String, List<Transaction>> bySymbol = sellTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        List<SymbolStats> stats = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Transaction> symbolTrades = entry.getValue();

            int total = symbolTrades.size();
            int winning = (int) symbolTrades.stream()
                    .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                    .count();
            BigDecimal totalProfit = symbolTrades.stream()
                    .map(Transaction::getRealizedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgReturn = totalProfit.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            BigDecimal winRate = BigDecimal.valueOf(winning * 100.0 / total).setScale(2, RoundingMode.HALF_UP);

            String stockName = symbolTrades.get(0).getStock().getName();

            stats.add(SymbolStats.builder()
                    .symbol(symbol)
                    .stockName(stockName != null ? stockName : symbol)
                    .totalTrades(total)
                    .winningTrades(winning)
                    .winRate(winRate)
                    .totalReturn(calculateTotalReturnPercent(symbolTrades))
                    .avgReturn(avgReturn)
                    .totalProfit(totalProfit)
                    .avgHoldingDays(BigDecimal.ZERO) // TODO: FIFO 매칭으로 계산
                    .build());
        }

        // 총 수익 기준 정렬 및 순위 부여
        stats.sort((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()));
        for (int i = 0; i < stats.size(); i++) {
            stats.get(i).setRank(i + 1);
        }

        return stats;
    }

    /**
     * 실수 패턴 분석
     */
    public List<MistakePattern> getMistakePatterns(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .toList();

        List<MistakePattern> patterns = new ArrayList<>();

        // 1. 손절 미설정 분석
        List<Transaction> noStopLoss = sellTransactions.stream()
                .filter(t -> t.getStopLossPrice() == null && t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                .toList();
        if (!noStopLoss.isEmpty()) {
            patterns.add(createMistakePattern(
                    MistakeTypes.NO_STOP_LOSS,
                    "손절가 미설정",
                    "손절가를 설정하지 않고 거래하여 손실이 발생했습니다.",
                    noStopLoss,
                    "HIGH"
            ));
        }

        // 2. 과도한 거래 (하루 5건 이상)
        Map<LocalDate, List<Transaction>> byDate = sellTransactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().toLocalDate()));
        List<Transaction> overtradingDays = new ArrayList<>();
        for (List<Transaction> dayTrades : byDate.values()) {
            if (dayTrades.size() >= 5) {
                overtradingDays.addAll(dayTrades);
            }
        }
        if (!overtradingDays.isEmpty()) {
            patterns.add(createMistakePattern(
                    MistakeTypes.OVERTRADING,
                    "과도한 거래",
                    "하루에 5건 이상의 거래를 실행했습니다.",
                    overtradingDays,
                    "MEDIUM"
            ));
        }

        // 3. 복수 매매 (손실 후 1시간 내 동일 종목 재진입)
        List<Transaction> revengeTrades = findRevengeTrades(sellTransactions);
        if (!revengeTrades.isEmpty()) {
            patterns.add(createMistakePattern(
                    MistakeTypes.REVENGE_TRADING,
                    "복수 매매",
                    "손실 발생 후 짧은 시간 내에 동일 종목에 재진입했습니다.",
                    revengeTrades,
                    "HIGH"
            ));
        }

        // 4. 과도한 보유 (30일 이상 보유 후 손실)
        // TODO: FIFO 매칭 기반 보유 기간 계산 필요

        return patterns;
    }

    /**
     * 개선 제안 생성
     */
    public List<ImprovementSuggestion> getImprovementSuggestions(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<ImprovementSuggestion> suggestions = new ArrayList<>();

        List<WeekdayStats> weekdayStats = getWeekdayPerformance(accountId, startDate, endDate);
        List<TimeOfDayStats> timeStats = getTimeOfDayPerformance(accountId, startDate, endDate);
        List<SymbolStats> symbolStats = getSymbolPerformance(accountId, startDate, endDate);
        List<MistakePattern> mistakes = getMistakePatterns(accountId, startDate, endDate);

        // 1. 요일 기반 제안
        WeekdayStats worstDay = weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .min(Comparator.comparing(WeekdayStats::getWinRate))
                .orElse(null);
        if (worstDay != null && worstDay.getWinRate().compareTo(new BigDecimal("40")) < 0) {
            suggestions.add(ImprovementSuggestion.builder()
                    .category("TIME")
                    .title(worstDay.getDayName() + "요일 거래 주의")
                    .message(String.format("%s요일 승률이 %.1f%%로 낮습니다. 이 요일의 거래를 줄이는 것을 고려하세요.",
                            worstDay.getDayName(), worstDay.getWinRate()))
                    .priority("MEDIUM")
                    .actionItem(worstDay.getDayName() + "요일 거래 횟수 50% 감소")
                    .potentialImpact(new BigDecimal("5"))
                    .build());
        }

        // 2. 시간대 기반 제안
        TimeOfDayStats bestTime = timeStats.stream()
                .filter(s -> s.getTotalTrades() >= 3)
                .max(Comparator.comparing(TimeOfDayStats::getWinRate))
                .orElse(null);
        if (bestTime != null) {
            suggestions.add(ImprovementSuggestion.builder()
                    .category("TIME")
                    .title("최적 거래 시간대 집중")
                    .message(String.format("%s 시간대의 승률이 %.1f%%로 가장 높습니다. 이 시간대에 거래를 집중하세요.",
                            bestTime.getTimePeriod(), bestTime.getWinRate()))
                    .priority("HIGH")
                    .actionItem(bestTime.getTimePeriod() + " 시간대 거래 비중 증가")
                    .potentialImpact(new BigDecimal("8"))
                    .build());
        }

        // 3. 종목 기반 제안
        List<SymbolStats> losingSymbols = symbolStats.stream()
                .filter(s -> s.getTotalProfit().compareTo(BigDecimal.ZERO) < 0 && s.getTotalTrades() >= 3)
                .sorted(Comparator.comparing(SymbolStats::getTotalProfit))
                .limit(3)
                .toList();
        for (SymbolStats loser : losingSymbols) {
            suggestions.add(ImprovementSuggestion.builder()
                    .category("SYMBOL")
                    .title(loser.getSymbol() + " 거래 재검토")
                    .message(String.format("%s 종목에서 총 %s 손실이 발생했습니다. 이 종목의 거래 전략을 재검토하세요.",
                            loser.getSymbol(), formatCurrency(loser.getTotalProfit())))
                    .priority("HIGH")
                    .actionItem(loser.getSymbol() + " 거래 일시 중단 또는 전략 변경")
                    .potentialImpact(loser.getTotalProfit().abs().divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP))
                    .build());
        }

        // 4. 실수 패턴 기반 제안
        for (MistakePattern mistake : mistakes) {
            if ("HIGH".equals(mistake.getSeverity())) {
                suggestions.add(ImprovementSuggestion.builder()
                        .category("BEHAVIOR")
                        .title(mistake.getDescription() + " 개선")
                        .message(String.format("%s 패턴이 %d회 발생하여 총 %s 손실이 발생했습니다.",
                                mistake.getDescription(), mistake.getCount(), formatCurrency(mistake.getTotalLoss())))
                        .priority("HIGH")
                        .actionItem(getActionItemForMistake(mistake.getType()))
                        .potentialImpact(mistake.getTotalLoss().abs().divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP))
                        .build());
            }
        }

        // 우선순위 정렬
        suggestions.sort((a, b) -> {
            int priorityCompare = getPriorityValue(b.getPriority()) - getPriorityValue(a.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return b.getPotentialImpact().compareTo(a.getPotentialImpact());
        });

        return suggestions;
    }

    /**
     * 전체 통계 요약
     */
    public TradingStatisticsDto getFullStatistics(Long accountId, LocalDate startDate, LocalDate endDate) {
        List<TimeOfDayStats> timeStats = getTimeOfDayPerformance(accountId, startDate, endDate);
        List<WeekdayStats> weekdayStats = getWeekdayPerformance(accountId, startDate, endDate);
        List<SymbolStats> symbolStats = getSymbolPerformance(accountId, startDate, endDate);
        List<MistakePattern> mistakes = getMistakePatterns(accountId, startDate, endDate);
        List<ImprovementSuggestion> suggestions = getImprovementSuggestions(accountId, startDate, endDate);

        // 전체 요약 계산
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL && t.getRealizedPnl() != null)
                .toList();

        int total = sellTransactions.size();
        int winning = (int) sellTransactions.stream()
                .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal totalProfit = sellTransactions.stream()
                .map(Transaction::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WeekdayStats bestDay = weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .max(Comparator.comparing(WeekdayStats::getWinRate))
                .orElse(null);
        WeekdayStats worstDay = weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .min(Comparator.comparing(WeekdayStats::getWinRate))
                .orElse(null);
        TimeOfDayStats bestTime = timeStats.stream()
                .filter(s -> s.getTotalTrades() >= 2)
                .max(Comparator.comparing(TimeOfDayStats::getWinRate))
                .orElse(null);
        SymbolStats bestSymbol = symbolStats.stream()
                .max(Comparator.comparing(SymbolStats::getTotalProfit))
                .orElse(null);

        int mistakeCount = mistakes.stream().mapToInt(MistakePattern::getCount).sum();

        OverallSummary summary = OverallSummary.builder()
                .totalTrades(total)
                .winningTrades(winning)
                .losingTrades(total - winning)
                .overallWinRate(total > 0 ? BigDecimal.valueOf(winning * 100.0 / total).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .totalProfit(totalProfit)
                .avgProfit(total > 0 ? totalProfit.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .bestDay(bestDay != null ? bestDay.getDayName() : "-")
                .worstDay(worstDay != null ? worstDay.getDayName() : "-")
                .bestTimeSlot(bestTime != null ? bestTime.getTimePeriod() : "-")
                .bestSymbol(bestSymbol != null ? bestSymbol.getSymbol() : "-")
                .mistakeCount(mistakeCount)
                .consistencyScore(calculateConsistencyScore(weekdayStats, timeStats))
                .build();

        return TradingStatisticsDto.builder()
                .timeOfDayStats(timeStats)
                .weekdayStats(weekdayStats)
                .symbolStats(symbolStats.stream().limit(10).toList())
                .mistakePatterns(mistakes)
                .suggestions(suggestions)
                .overallSummary(summary)
                .build();
    }

    // === Helper Methods ===

    private List<Transaction> getTransactionsInRange(Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        if (accountId != null) {
            return transactionRepository.findByAccountIdAndDateRange(accountId, start, end);
        }
        return transactionRepository.findByDateRange(start, end);
    }

    private BigDecimal calculateTotalReturnPercent(List<Transaction> trades) {
        if (trades.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalCost = trades.stream()
                .filter(t -> t.getCostBasis() != null)
                .map(Transaction::getCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfit = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .map(Transaction::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCost.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return totalProfit.divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String findBestTimeSlot(List<Transaction> trades) {
        if (trades.isEmpty()) return "-";

        Map<Integer, BigDecimal> profitByHour = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionDate().getHour(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getRealizedPnl, BigDecimal::add)
                ));

        return profitByHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> String.format("%02d:00", e.getKey()))
                .orElse("-");
    }

    private MistakePattern createMistakePattern(String type, String description, String fullDesc,
                                                 List<Transaction> trades, String severity) {
        BigDecimal totalLoss = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                .map(Transaction::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MistakeExample> examples = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(Transaction::getRealizedPnl))
                .limit(3)
                .map(t -> MistakeExample.builder()
                        .transactionId(t.getId())
                        .symbol(t.getStock().getSymbol())
                        .date(t.getTransactionDate().toLocalDate())
                        .loss(t.getRealizedPnl())
                        .note(t.getNotes())
                        .build())
                .toList();

        return MistakePattern.builder()
                .type(type)
                .description(description)
                .count(trades.size())
                .totalLoss(totalLoss)
                .avgLoss(trades.isEmpty() ? BigDecimal.ZERO : totalLoss.divide(BigDecimal.valueOf(trades.size()), 2, RoundingMode.HALF_UP))
                .severity(severity)
                .examples(examples)
                .build();
    }

    private List<Transaction> findRevengeTrades(List<Transaction> trades) {
        List<Transaction> revengeTrades = new ArrayList<>();
        Map<String, List<Transaction>> bySymbol = trades.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        for (List<Transaction> symbolTrades : bySymbol.values()) {
            for (int i = 1; i < symbolTrades.size(); i++) {
                Transaction prev = symbolTrades.get(i - 1);
                Transaction curr = symbolTrades.get(i);

                if (prev.getRealizedPnl() != null && prev.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0) {
                    long hoursBetween = java.time.Duration.between(
                            prev.getTransactionDate(), curr.getTransactionDate()).toHours();
                    if (hoursBetween <= 2) {
                        revengeTrades.add(curr);
                    }
                }
            }
        }
        return revengeTrades;
    }

    private String getActionItemForMistake(String mistakeType) {
        return switch (mistakeType) {
            case MistakeTypes.NO_STOP_LOSS -> "모든 거래에 손절가 필수 설정";
            case MistakeTypes.OVERTRADING -> "일일 최대 거래 횟수 3회로 제한";
            case MistakeTypes.REVENGE_TRADING -> "손실 후 최소 1시간 휴식 후 거래";
            case MistakeTypes.FOMO_ENTRY -> "진입 전 체크리스트 확인 필수";
            default -> "거래 규칙 재검토";
        };
    }

    private int getPriorityValue(String priority) {
        return switch (priority) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private BigDecimal calculateConsistencyScore(List<WeekdayStats> weekdayStats, List<TimeOfDayStats> timeStats) {
        // 일관성 점수: 요일별/시간대별 승률의 표준편차가 낮을수록 높은 점수
        List<BigDecimal> winRates = new ArrayList<>();
        weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .forEach(s -> winRates.add(s.getWinRate()));
        timeStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .forEach(s -> winRates.add(s.getWinRate()));

        if (winRates.isEmpty()) return BigDecimal.ZERO;

        BigDecimal mean = winRates.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(winRates.size()), 4, RoundingMode.HALF_UP);

        BigDecimal variance = winRates.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(winRates.size()), 4, RoundingMode.HALF_UP);

        double stdDev = Math.sqrt(variance.doubleValue());

        // 표준편차가 낮을수록 높은 점수 (최대 100점)
        double score = Math.max(0, 100 - stdDev * 2);
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0원";
        return String.format("%,.0f원", amount);
    }
}
