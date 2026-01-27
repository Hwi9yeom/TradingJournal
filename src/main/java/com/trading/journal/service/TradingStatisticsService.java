package com.trading.journal.service;

import com.trading.journal.dto.TradingStatisticsDto;
import com.trading.journal.dto.TradingStatisticsDto.*;
import com.trading.journal.entity.AccountRiskSettings;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.AccountRiskSettingsRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 거래 통계 분석 서비스
 *
 * <p>시간대별, 요일별, 종목별 성과 분석 및 실수 패턴 분석을 제공합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingStatisticsService {

    // ============================================================
    // Constants - 임계값 및 설정값
    // ============================================================

    /** 낮은 승률 임계값 (%) - 이 값 미만이면 경고 표시 */
    private static final BigDecimal LOW_WIN_RATE_THRESHOLD = new BigDecimal("40");

    /** 시간대 기반 제안을 위한 최소 거래 횟수 */
    private static final int MIN_TRADES_FOR_TIME_SUGGESTION = 3;

    /** 종목별 손실 분석을 위한 최소 거래 횟수 */
    private static final int MIN_TRADES_FOR_SYMBOL_ANALYSIS = 3;

    /** 최적 시간대 분석을 위한 최소 거래 횟수 */
    private static final int MIN_TRADES_FOR_BEST_TIME = 2;

    /** 과도한 거래 판단 기준 (일일 최대 거래 횟수) */
    private static final int OVERTRADING_THRESHOLD = 5;

    /** 복수 매매 판단 기준 (손실 후 재진입까지의 최대 시간, 시간 단위) */
    private static final long REVENGE_TRADE_HOURS_THRESHOLD = 2;

    /** 요일별 거래 제안 시 권장 감소율 (%) */
    private static final String TRADE_REDUCTION_PERCENTAGE = "50%";

    /** 기본 예상 개선 효과 - 요일 기반 (%) */
    private static final BigDecimal DEFAULT_DAY_IMPACT = new BigDecimal("5");

    /** 기본 예상 개선 효과 - 시간대 기반 (%) */
    private static final BigDecimal DEFAULT_TIME_IMPACT = new BigDecimal("8");

    /** 수익 영향도 계산을 위한 나눗셈 값 */
    private static final BigDecimal IMPACT_DIVISOR = new BigDecimal("10000");

    /** 통계 계산 소수점 자릿수 */
    private static final int CALCULATION_SCALE = 4;

    /** 표시용 소수점 자릿수 */
    private static final int DISPLAY_SCALE = 2;

    /** 일관성 점수 최대값 */
    private static final double MAX_CONSISTENCY_SCORE = 100.0;

    /** 일관성 점수 계산 시 표준편차 가중치 */
    private static final double CONSISTENCY_STD_DEV_WEIGHT = 2.0;

    /** 백분율 변환 승수 */
    private static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");

    /** 샤프 비율 계산 시 무위험 수익률 (연간 2% 기준 월간 환산) */
    private static final double RISK_FREE_RATE_MONTHLY = 0.02 / 12;

    /** 거래 시간 범위 - 시작 시간 */
    private static final int TRADING_HOUR_START = 8;

    /** 거래 시간 범위 - 종료 시간 */
    private static final int TRADING_HOUR_END = 16;

    /** 상위 종목 통계 표시 개수 */
    private static final int TOP_SYMBOLS_LIMIT = 10;

    /** 과도한 보유 판단 기준 (일 단위) */
    private static final BigDecimal HOLDING_TOO_LONG_THRESHOLD = new BigDecimal("30");

    /** 실수 예시 표시 개수 */
    private static final int MISTAKE_EXAMPLES_LIMIT = 3;

    /** 손실 종목 표시 개수 */
    private static final int LOSING_SYMBOLS_LIMIT = 3;

    /** 우선순위 값 - 높음 */
    private static final int PRIORITY_HIGH = 3;

    /** 우선순위 값 - 중간 */
    private static final int PRIORITY_MEDIUM = 2;

    /** 우선순위 값 - 낮음 */
    private static final int PRIORITY_LOW = 1;

    /** 우선순위 문자열 - 높음 */
    private static final String PRIORITY_STR_HIGH = "HIGH";

    /** 우선순위 문자열 - 중간 */
    private static final String PRIORITY_STR_MEDIUM = "MEDIUM";

    /** 우선순위 문자열 - 낮음 */
    private static final String PRIORITY_STR_LOW = "LOW";

    /** 카테고리 - 시간 */
    private static final String CATEGORY_TIME = "TIME";

    /** 카테고리 - 종목 */
    private static final String CATEGORY_SYMBOL = "SYMBOL";

    /** 카테고리 - 행동 */
    private static final String CATEGORY_BEHAVIOR = "BEHAVIOR";

    /** 기본값 - 데이터 없음 표시 */
    private static final String NO_DATA_PLACEHOLDER = "-";

    // ============================================================
    // Dependencies
    // ============================================================

    private final TransactionRepository transactionRepository;
    private final AccountRiskSettingsRepository accountRiskSettingsRepository;

    // ============================================================
    // Public API - 통계 조회 메서드
    // ============================================================

    /**
     * 시간대별 성과 분석
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 시간대별 통계 목록
     */
    public List<TimeOfDayStats> getTimeOfDayPerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("시간대별 성과 분석 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<Transaction> sellTransactions =
                getSellTransactionsWithPnl(accountId, startDate, endDate);
        Map<Integer, List<Transaction>> byHour = groupTransactionsByHour(sellTransactions);

        List<TimeOfDayStats> stats = buildTimeOfDayStats(byHour);

        log.debug("시간대별 성과 분석 완료 - {} 시간대 분석됨", stats.size());
        return stats;
    }

    /**
     * 요일별 성과 분석
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 요일별 통계 목록
     */
    public List<WeekdayStats> getWeekdayPerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("요일별 성과 분석 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<Transaction> sellTransactions =
                getSellTransactionsWithPnl(accountId, startDate, endDate);
        Map<DayOfWeek, List<Transaction>> byDay = groupTransactionsByDayOfWeek(sellTransactions);

        List<WeekdayStats> stats = buildWeekdayStats(byDay);

        log.debug("요일별 성과 분석 완료 - {} 요일 분석됨", stats.size());
        return stats;
    }

    /**
     * 종목별 성과 분석
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 종목별 통계 목록 (수익 기준 정렬, 순위 포함)
     */
    public List<SymbolStats> getSymbolPerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("종목별 성과 분석 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<Transaction> sellTransactions =
                getSellTransactionsWithPnl(accountId, startDate, endDate);
        Map<String, List<Transaction>> bySymbol = groupTransactionsBySymbol(sellTransactions);

        // FIFO 기반 보유 기간 계산
        Map<Long, BigDecimal> holdingDaysMap =
                calculateFifoHoldingDays(accountId, sellTransactions);

        List<SymbolStats> stats = buildSymbolStats(bySymbol, holdingDaysMap);
        sortAndRankSymbolStats(stats);

        log.debug("종목별 성과 분석 완료 - {} 종목 분석됨", stats.size());
        return stats;
    }

    /**
     * 실수 패턴 분석
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 실수 패턴 목록
     */
    public List<MistakePattern> getMistakePatterns(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("실수 패턴 분석 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<Transaction> sellTransactions = getSellTransactions(accountId, startDate, endDate);
        List<MistakePattern> patterns = new ArrayList<>();

        analyzeNoStopLossPattern(sellTransactions, patterns);
        analyzeOvertradingPattern(sellTransactions, patterns);
        analyzeRevengeTradingPattern(sellTransactions, patterns);
        analyzeHoldingTooLongPattern(accountId, sellTransactions, patterns);

        log.debug("실수 패턴 분석 완료 - {} 패턴 발견됨", patterns.size());
        return patterns;
    }

    /**
     * 개선 제안 생성
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 우선순위 정렬된 개선 제안 목록
     */
    public List<ImprovementSuggestion> getImprovementSuggestions(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("개선 제안 생성 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<ImprovementSuggestion> suggestions = new ArrayList<>();

        List<WeekdayStats> weekdayStats = getWeekdayPerformance(accountId, startDate, endDate);
        List<TimeOfDayStats> timeStats = getTimeOfDayPerformance(accountId, startDate, endDate);
        List<SymbolStats> symbolStats = getSymbolPerformance(accountId, startDate, endDate);
        List<MistakePattern> mistakes = getMistakePatterns(accountId, startDate, endDate);

        addWeekdayBasedSuggestions(weekdayStats, suggestions);
        addTimeBasedSuggestions(timeStats, suggestions);
        addSymbolBasedSuggestions(symbolStats, suggestions);
        addMistakeBasedSuggestions(mistakes, suggestions);

        sortSuggestionsByPriority(suggestions);

        log.debug("개선 제안 생성 완료 - {} 제안 생성됨", suggestions.size());
        return suggestions;
    }

    /**
     * 전체 통계 요약 조회
     *
     * @param accountId 계좌 ID (null이면 전체 조회)
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 전체 통계 DTO
     */
    public TradingStatisticsDto getFullStatistics(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.debug("전체 통계 요약 조회 시작 - accountId: {}, period: {} ~ {}", accountId, startDate, endDate);

        List<TimeOfDayStats> timeStats = getTimeOfDayPerformance(accountId, startDate, endDate);
        List<WeekdayStats> weekdayStats = getWeekdayPerformance(accountId, startDate, endDate);
        List<SymbolStats> symbolStats = getSymbolPerformance(accountId, startDate, endDate);
        List<MistakePattern> mistakes = getMistakePatterns(accountId, startDate, endDate);
        List<ImprovementSuggestion> suggestions =
                getImprovementSuggestions(accountId, startDate, endDate);

        OverallSummary summary =
                buildOverallSummary(
                        accountId, startDate, endDate, weekdayStats, timeStats, mistakes);

        log.debug("전체 통계 요약 조회 완료");
        return TradingStatisticsDto.builder()
                .timeOfDayStats(timeStats)
                .weekdayStats(weekdayStats)
                .symbolStats(symbolStats.stream().limit(TOP_SYMBOLS_LIMIT).toList())
                .mistakePatterns(mistakes)
                .suggestions(suggestions)
                .overallSummary(summary)
                .build();
    }

    /**
     * 전체 거래 통계 조회
     *
     * @return 전체 통계 맵
     */
    public Map<String, Object> getOverallStatistics() {
        log.debug("전체 거래 통계 조회 시작");

        // FETCH JOIN으로 Stock 함께 로딩하여 N+1 쿼리 방지
        List<Transaction> allTx = transactionRepository.findAllWithStock();
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTrades", allTx.size());
        stats.put("uniqueStocks", allTx.stream().map(Transaction::getStock).distinct().count());

        List<Transaction> sells = filterSellTransactions(allTx);
        ReturnStatistics returnStats = calculateReturnStatistics(allTx, sells);

        stats.put("avgHoldingPeriod", calcAvgHoldingPeriod(allTx));
        stats.put("winRate", sells.isEmpty() ? 0 : (double) returnStats.wins / sells.size() * 100);
        stats.put("avgReturn", sells.isEmpty() ? 0 : returnStats.totalReturnPct / sells.size());
        stats.put(
                "maxReturn",
                returnStats.maxReturnPct == Double.MIN_VALUE ? 0 : returnStats.maxReturnPct);
        stats.put("sharpeRatio", calcSharpeRatio(allTx));
        stats.put("maxDrawdown", calcMaxDrawdown(allTx));

        log.debug("전체 거래 통계 조회 완료 - totalTrades: {}", allTx.size());
        return stats;
    }

    /**
     * 자산 히스토리 조회
     *
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 일별 자산 히스토리 (labels, values)
     */
    public Map<String, Object> getAssetHistory(LocalDate startDate, LocalDate endDate) {
        log.debug("자산 히스토리 조회 시작 - period: {} ~ {}", startDate, endDate);

        List<Transaction> transactions =
                transactionRepository.findByDateRange(
                        startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        Map<LocalDate, BigDecimal> dailyValues = calculateDailyAssetValues(transactions);
        Map<String, Object> history = buildAssetHistoryResponse(startDate, endDate, dailyValues);

        log.debug("자산 히스토리 조회 완료");
        return history;
    }

    /**
     * 월별 수익률 조회
     *
     * @return 월별 수익률 목록 (정렬됨)
     */
    public List<Map<String, Object>> getMonthlyReturns() {
        log.debug("월별 수익률 조회 시작");

        // FETCH JOIN으로 Stock 함께 로딩하여 N+1 쿼리 방지
        List<Transaction> allTx = transactionRepository.findAllWithStock();
        Map<String, List<Transaction>> byMonth = groupTransactionsByMonth(allTx);

        List<Map<String, Object>> result = buildMonthlyReturnsResponse(byMonth);
        result.sort((a, b) -> ((String) a.get("month")).compareTo((String) b.get("month")));

        log.debug("월별 수익률 조회 완료 - {} 개월 분석됨", result.size());
        return result;
    }

    // ============================================================
    // Private Methods - 데이터 조회 및 필터링
    // ============================================================

    /** 기간 내 거래 조회 */
    private List<Transaction> getTransactionsInRange(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        if (accountId != null) {
            return transactionRepository.findByAccountIdAndDateRange(accountId, start, end);
        }
        return transactionRepository.findByDateRange(start, end);
    }

    /** 실현손익이 있는 매도 거래만 필터링하여 조회 */
    private List<Transaction> getSellTransactionsWithPnl(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        return transactions.stream().filter(this::isSellTransactionWithPnl).toList();
    }

    /** 매도 거래만 필터링하여 조회 */
    private List<Transaction> getSellTransactions(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsInRange(accountId, startDate, endDate);
        return transactions.stream().filter(t -> t.getType() == TransactionType.SELL).toList();
    }

    /** 전체 거래에서 매도 거래만 필터링 */
    private List<Transaction> filterSellTransactions(List<Transaction> allTx) {
        return allTx.stream().filter(t -> t.getType() == TransactionType.SELL).toList();
    }

    /** 실현손익이 있는 매도 거래인지 확인 */
    private boolean isSellTransactionWithPnl(Transaction t) {
        return t.getType() == TransactionType.SELL && t.getRealizedPnl() != null;
    }

    /** 수익 거래인지 확인 */
    private boolean isWinningTrade(Transaction t) {
        return t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0;
    }

    /** 손실 거래인지 확인 */
    private boolean isLosingTrade(Transaction t) {
        return t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0;
    }

    // ============================================================
    // Private Methods - 그룹핑
    // ============================================================

    private Map<Integer, List<Transaction>> groupTransactionsByHour(
            List<Transaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().getHour()));
    }

    private Map<DayOfWeek, List<Transaction>> groupTransactionsByDayOfWeek(
            List<Transaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().getDayOfWeek()));
    }

    private Map<String, List<Transaction>> groupTransactionsBySymbol(
            List<Transaction> transactions) {
        return transactions.stream().collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));
    }

    private Map<LocalDate, List<Transaction>> groupTransactionsByDate(
            List<Transaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().toLocalDate()));
    }

    private Map<String, List<Transaction>> groupTransactionsByMonth(
            List<Transaction> transactions) {
        return transactions.stream()
                .collect(
                        Collectors.groupingBy(
                                t ->
                                        t.getTransactionDate()
                                                .format(
                                                        java.time.format.DateTimeFormatter
                                                                .ofPattern("yyyy-MM"))));
    }

    // ============================================================
    // Private Methods - 통계 계산
    // ============================================================

    /** 거래 목록에서 승리 거래 수 계산 */
    private int countWinningTrades(List<Transaction> trades) {
        return (int) trades.stream().filter(this::isWinningTrade).count();
    }

    /** 거래 목록에서 총 손익 계산 */
    private BigDecimal calculateTotalProfit(List<Transaction> trades) {
        return trades.stream()
                .map(t -> t.getRealizedPnl() != null ? t.getRealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 평균 수익 계산 */
    private BigDecimal calculateAverageReturn(BigDecimal totalProfit, int tradeCount) {
        if (tradeCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalProfit.divide(
                BigDecimal.valueOf(tradeCount), CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /** 승률 계산 */
    private BigDecimal calculateWinRate(int winningTrades, int totalTrades) {
        if (totalTrades == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(winningTrades * 100.0 / totalTrades)
                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** 총 수익률(%) 계산 */
    private BigDecimal calculateTotalReturnPercent(List<Transaction> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost =
                trades.stream()
                        .filter(t -> t.getCostBasis() != null)
                        .map(Transaction::getCostBasis)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfit =
                trades.stream()
                        .filter(t -> t.getRealizedPnl() != null)
                        .map(Transaction::getRealizedPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalProfit
                .divide(totalCost, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER)
                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** 거래 기본 통계 계산 (공통 로직) */
    private TradeBasicStats calculateBasicStats(List<Transaction> trades) {
        int total = trades.size();
        int winning = countWinningTrades(trades);
        BigDecimal totalProfit = calculateTotalProfit(trades);
        BigDecimal avgReturn = calculateAverageReturn(totalProfit, total);
        BigDecimal winRate = calculateWinRate(winning, total);
        BigDecimal totalReturn = calculateTotalReturnPercent(trades);

        return new TradeBasicStats(total, winning, totalProfit, avgReturn, winRate, totalReturn);
    }

    /** 거래 기본 통계 레코드 */
    private record TradeBasicStats(
            int totalTrades,
            int winningTrades,
            BigDecimal totalProfit,
            BigDecimal avgReturn,
            BigDecimal winRate,
            BigDecimal totalReturn) {}

    /** 수익률 통계 레코드 */
    private record ReturnStatistics(int wins, double totalReturnPct, double maxReturnPct) {}

    /** FIFO 시뮬레이션용 매수 항목 */
    private static class FifoBuyEntry {
        final LocalDateTime buyDate;
        BigDecimal remainingQuantity;

        FifoBuyEntry(LocalDateTime buyDate, BigDecimal quantity) {
            this.buyDate = buyDate;
            this.remainingQuantity = quantity;
        }
    }

    // ============================================================
    // Private Methods - 시간대별 통계 생성
    // ============================================================

    private List<TimeOfDayStats> buildTimeOfDayStats(Map<Integer, List<Transaction>> byHour) {
        List<TimeOfDayStats> stats = new ArrayList<>();

        for (int hour = TRADING_HOUR_START; hour <= TRADING_HOUR_END; hour++) {
            List<Transaction> hourTrades = byHour.getOrDefault(hour, Collections.emptyList());

            if (!hourTrades.isEmpty()) {
                stats.add(buildSingleTimeOfDayStats(hour, hourTrades));
            }
        }

        return stats;
    }

    private TimeOfDayStats buildSingleTimeOfDayStats(int hour, List<Transaction> trades) {
        TradeBasicStats basicStats = calculateBasicStats(trades);

        return TimeOfDayStats.builder()
                .timePeriod(String.format("%02d:00-%02d:00", hour, hour + 1))
                .hour(hour)
                .totalTrades(basicStats.totalTrades())
                .winningTrades(basicStats.winningTrades())
                .winRate(basicStats.winRate())
                .totalReturn(basicStats.totalReturn())
                .avgReturn(basicStats.avgReturn())
                .totalProfit(basicStats.totalProfit())
                .build();
    }

    // ============================================================
    // Private Methods - 요일별 통계 생성
    // ============================================================

    private List<WeekdayStats> buildWeekdayStats(Map<DayOfWeek, List<Transaction>> byDay) {
        List<WeekdayStats> stats = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            List<Transaction> dayTrades = byDay.getOrDefault(day, Collections.emptyList());
            stats.add(buildSingleWeekdayStats(day, dayTrades));
        }

        return stats;
    }

    private WeekdayStats buildSingleWeekdayStats(DayOfWeek day, List<Transaction> trades) {
        TradeBasicStats basicStats = calculateBasicStats(trades);

        return WeekdayStats.builder()
                .dayName(day.getDisplayName(TextStyle.SHORT, Locale.KOREAN))
                .dayOfWeek(day.getValue())
                .totalTrades(basicStats.totalTrades())
                .winningTrades(basicStats.winningTrades())
                .winRate(basicStats.winRate())
                .totalReturn(basicStats.totalReturn())
                .avgReturn(basicStats.avgReturn())
                .totalProfit(basicStats.totalProfit())
                .bestTimeSlot(findBestTimeSlot(trades))
                .build();
    }

    // ============================================================
    // Private Methods - 종목별 통계 생성
    // ============================================================

    private List<SymbolStats> buildSymbolStats(
            Map<String, List<Transaction>> bySymbol, Map<Long, BigDecimal> holdingDaysMap) {
        List<SymbolStats> stats = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Transaction> symbolTrades = entry.getValue();
            stats.add(buildSingleSymbolStats(symbol, symbolTrades, holdingDaysMap));
        }

        return stats;
    }

    private SymbolStats buildSingleSymbolStats(
            String symbol, List<Transaction> trades, Map<Long, BigDecimal> holdingDaysMap) {
        TradeBasicStats basicStats = calculateBasicStats(trades);
        String stockName = trades.get(0).getStock().getName();

        return SymbolStats.builder()
                .symbol(symbol)
                .stockName(stockName != null ? stockName : symbol)
                .totalTrades(basicStats.totalTrades())
                .winningTrades(basicStats.winningTrades())
                .winRate(basicStats.winRate())
                .totalReturn(basicStats.totalReturn())
                .avgReturn(basicStats.avgReturn())
                .totalProfit(basicStats.totalProfit())
                .avgHoldingDays(calculateAvgHoldingDaysForSymbol(trades, holdingDaysMap))
                .build();
    }

    private void sortAndRankSymbolStats(List<SymbolStats> stats) {
        stats.sort((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()));
        for (int i = 0; i < stats.size(); i++) {
            stats.get(i).setRank(i + 1);
        }
    }

    // ============================================================
    // Private Methods - 실수 패턴 분석
    // ============================================================

    private void analyzeNoStopLossPattern(
            List<Transaction> sellTransactions, List<MistakePattern> patterns) {
        List<Transaction> noStopLoss =
                sellTransactions.stream()
                        .filter(t -> t.getStopLossPrice() == null && isLosingTrade(t))
                        .toList();

        if (!noStopLoss.isEmpty()) {
            log.debug("손절가 미설정 패턴 발견 - {} 건", noStopLoss.size());
            patterns.add(
                    createMistakePattern(
                            MistakeTypes.NO_STOP_LOSS,
                            "손절가 미설정",
                            "손절가를 설정하지 않고 거래하여 손실이 발생했습니다.",
                            noStopLoss,
                            PRIORITY_STR_HIGH));
        }
    }

    private void analyzeOvertradingPattern(
            List<Transaction> sellTransactions, List<MistakePattern> patterns) {
        Map<LocalDate, List<Transaction>> byDate = groupTransactionsByDate(sellTransactions);
        List<Transaction> overtradingDays = new ArrayList<>();

        for (List<Transaction> dayTrades : byDate.values()) {
            if (dayTrades.size() >= OVERTRADING_THRESHOLD) {
                overtradingDays.addAll(dayTrades);
            }
        }

        if (!overtradingDays.isEmpty()) {
            log.debug("과도한 거래 패턴 발견 - {} 건", overtradingDays.size());
            patterns.add(
                    createMistakePattern(
                            MistakeTypes.OVERTRADING,
                            "과도한 거래",
                            String.format("하루에 %d건 이상의 거래를 실행했습니다.", OVERTRADING_THRESHOLD),
                            overtradingDays,
                            PRIORITY_STR_MEDIUM));
        }
    }

    private void analyzeRevengeTradingPattern(
            List<Transaction> sellTransactions, List<MistakePattern> patterns) {
        List<Transaction> revengeTrades = findRevengeTrades(sellTransactions);

        if (!revengeTrades.isEmpty()) {
            log.debug("복수 매매 패턴 발견 - {} 건", revengeTrades.size());
            patterns.add(
                    createMistakePattern(
                            MistakeTypes.REVENGE_TRADING,
                            "복수 매매",
                            "손실 발생 후 짧은 시간 내에 동일 종목에 재진입했습니다.",
                            revengeTrades,
                            PRIORITY_STR_HIGH));
        }
    }

    /** 과도한 보유 패턴 분석 - FIFO 매칭 기반 보유 기간 계산 (기준: 계좌 리스크 설정의 maxHoldingDays) */
    private void analyzeHoldingTooLongPattern(
            Long accountId, List<Transaction> sellTransactions, List<MistakePattern> patterns) {
        BigDecimal threshold = resolveMaxHoldingDays(accountId);

        Map<Long, BigDecimal> holdingDays = calculateFifoHoldingDays(accountId, sellTransactions);

        List<Transaction> longHolding =
                sellTransactions.stream()
                        .filter(
                                t -> {
                                    BigDecimal days = holdingDays.get(t.getId());
                                    return days != null
                                            && days.compareTo(threshold) > 0
                                            && isLosingTrade(t);
                                })
                        .toList();

        if (!longHolding.isEmpty()) {
            log.debug("과도한 보유 패턴 발견 - {} 건 (기준: {}일)", longHolding.size(), threshold);
            patterns.add(
                    createMistakePattern(
                            MistakeTypes.HOLDING_TOO_LONG,
                            "과도한 보유",
                            String.format(
                                    "%s일 이상 보유하여 손실이 발생한 거래가 %d건 있습니다.",
                                    threshold.stripTrailingZeros().toPlainString(),
                                    longHolding.size()),
                            longHolding,
                            PRIORITY_STR_MEDIUM));
        }
    }

    /** 계좌 리스크 설정에서 maxHoldingDays 조회 (설정 없으면 기본값 사용) */
    private BigDecimal resolveMaxHoldingDays(Long accountId) {
        if (accountId != null) {
            return accountRiskSettingsRepository
                    .findByAccountId(accountId)
                    .map(AccountRiskSettings::getMaxHoldingDays)
                    .map(BigDecimal::valueOf)
                    .orElse(HOLDING_TOO_LONG_THRESHOLD);
        }
        return HOLDING_TOO_LONG_THRESHOLD;
    }

    private MistakePattern createMistakePattern(
            String type,
            String description,
            String fullDesc,
            List<Transaction> trades,
            String severity) {
        BigDecimal totalLoss =
                trades.stream()
                        .filter(this::isLosingTrade)
                        .map(Transaction::getRealizedPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MistakeExample> examples = buildMistakeExamples(trades);

        return MistakePattern.builder()
                .type(type)
                .description(description)
                .count(trades.size())
                .totalLoss(totalLoss)
                .avgLoss(
                        trades.isEmpty()
                                ? BigDecimal.ZERO
                                : totalLoss.divide(
                                        BigDecimal.valueOf(trades.size()),
                                        DISPLAY_SCALE,
                                        RoundingMode.HALF_UP))
                .severity(severity)
                .examples(examples)
                .build();
    }

    private List<MistakeExample> buildMistakeExamples(List<Transaction> trades) {
        return trades.stream()
                .filter(this::isLosingTrade)
                .sorted(Comparator.comparing(Transaction::getRealizedPnl))
                .limit(MISTAKE_EXAMPLES_LIMIT)
                .map(this::buildMistakeExample)
                .toList();
    }

    private MistakeExample buildMistakeExample(Transaction t) {
        return MistakeExample.builder()
                .transactionId(t.getId())
                .symbol(t.getStock().getSymbol())
                .date(t.getTransactionDate().toLocalDate())
                .loss(t.getRealizedPnl())
                .note(t.getNotes())
                .build();
    }

    private List<Transaction> findRevengeTrades(List<Transaction> trades) {
        List<Transaction> revengeTrades = new ArrayList<>();
        Map<String, List<Transaction>> bySymbol =
                trades.stream()
                        .sorted(Comparator.comparing(Transaction::getTransactionDate))
                        .collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        for (List<Transaction> symbolTrades : bySymbol.values()) {
            for (int i = 1; i < symbolTrades.size(); i++) {
                Transaction prev = symbolTrades.get(i - 1);
                Transaction curr = symbolTrades.get(i);

                if (isRevengeTrade(prev, curr)) {
                    revengeTrades.add(curr);
                }
            }
        }
        return revengeTrades;
    }

    private boolean isRevengeTrade(Transaction prev, Transaction curr) {
        if (prev.getRealizedPnl() == null
                || prev.getRealizedPnl().compareTo(BigDecimal.ZERO) >= 0) {
            return false;
        }
        long hoursBetween =
                java.time.Duration.between(prev.getTransactionDate(), curr.getTransactionDate())
                        .toHours();
        return hoursBetween <= REVENGE_TRADE_HOURS_THRESHOLD;
    }

    // ============================================================
    // Private Methods - 개선 제안 생성
    // ============================================================

    private void addWeekdayBasedSuggestions(
            List<WeekdayStats> weekdayStats, List<ImprovementSuggestion> suggestions) {
        WeekdayStats worstDay =
                weekdayStats.stream()
                        .filter(s -> s.getTotalTrades() > 0)
                        .min(Comparator.comparing(WeekdayStats::getWinRate))
                        .orElse(null);

        if (worstDay != null && worstDay.getWinRate().compareTo(LOW_WIN_RATE_THRESHOLD) < 0) {
            suggestions.add(
                    ImprovementSuggestion.builder()
                            .category(CATEGORY_TIME)
                            .title(worstDay.getDayName() + "요일 거래 주의")
                            .message(
                                    String.format(
                                            "%s요일 승률이 %.1f%%로 낮습니다. 이 요일의 거래를 줄이는 것을 고려하세요.",
                                            worstDay.getDayName(), worstDay.getWinRate()))
                            .priority(PRIORITY_STR_MEDIUM)
                            .actionItem(
                                    worstDay.getDayName()
                                            + "요일 거래 횟수 "
                                            + TRADE_REDUCTION_PERCENTAGE
                                            + " 감소")
                            .potentialImpact(DEFAULT_DAY_IMPACT)
                            .build());
        }
    }

    private void addTimeBasedSuggestions(
            List<TimeOfDayStats> timeStats, List<ImprovementSuggestion> suggestions) {
        TimeOfDayStats bestTime =
                timeStats.stream()
                        .filter(s -> s.getTotalTrades() >= MIN_TRADES_FOR_TIME_SUGGESTION)
                        .max(Comparator.comparing(TimeOfDayStats::getWinRate))
                        .orElse(null);

        if (bestTime != null) {
            suggestions.add(
                    ImprovementSuggestion.builder()
                            .category(CATEGORY_TIME)
                            .title("최적 거래 시간대 집중")
                            .message(
                                    String.format(
                                            "%s 시간대의 승률이 %.1f%%로 가장 높습니다. 이 시간대에 거래를 집중하세요.",
                                            bestTime.getTimePeriod(), bestTime.getWinRate()))
                            .priority(PRIORITY_STR_HIGH)
                            .actionItem(bestTime.getTimePeriod() + " 시간대 거래 비중 증가")
                            .potentialImpact(DEFAULT_TIME_IMPACT)
                            .build());
        }
    }

    private void addSymbolBasedSuggestions(
            List<SymbolStats> symbolStats, List<ImprovementSuggestion> suggestions) {
        List<SymbolStats> losingSymbols =
                symbolStats.stream()
                        .filter(
                                s ->
                                        s.getTotalProfit().compareTo(BigDecimal.ZERO) < 0
                                                && s.getTotalTrades()
                                                        >= MIN_TRADES_FOR_SYMBOL_ANALYSIS)
                        .sorted(Comparator.comparing(SymbolStats::getTotalProfit))
                        .limit(LOSING_SYMBOLS_LIMIT)
                        .toList();

        for (SymbolStats loser : losingSymbols) {
            suggestions.add(
                    ImprovementSuggestion.builder()
                            .category(CATEGORY_SYMBOL)
                            .title(loser.getSymbol() + " 거래 재검토")
                            .message(
                                    String.format(
                                            "%s 종목에서 총 %s 손실이 발생했습니다. 이 종목의 거래 전략을 재검토하세요.",
                                            loser.getSymbol(),
                                            formatCurrency(loser.getTotalProfit())))
                            .priority(PRIORITY_STR_HIGH)
                            .actionItem(loser.getSymbol() + " 거래 일시 중단 또는 전략 변경")
                            .potentialImpact(
                                    loser.getTotalProfit()
                                            .abs()
                                            .divide(
                                                    IMPACT_DIVISOR,
                                                    DISPLAY_SCALE,
                                                    RoundingMode.HALF_UP))
                            .build());
        }
    }

    private void addMistakeBasedSuggestions(
            List<MistakePattern> mistakes, List<ImprovementSuggestion> suggestions) {
        for (MistakePattern mistake : mistakes) {
            if (PRIORITY_STR_HIGH.equals(mistake.getSeverity())) {
                suggestions.add(
                        ImprovementSuggestion.builder()
                                .category(CATEGORY_BEHAVIOR)
                                .title(mistake.getDescription() + " 개선")
                                .message(
                                        String.format(
                                                "%s 패턴이 %d회 발생하여 총 %s 손실이 발생했습니다.",
                                                mistake.getDescription(),
                                                mistake.getCount(),
                                                formatCurrency(mistake.getTotalLoss())))
                                .priority(PRIORITY_STR_HIGH)
                                .actionItem(getActionItemForMistake(mistake.getType()))
                                .potentialImpact(
                                        mistake.getTotalLoss()
                                                .abs()
                                                .divide(
                                                        IMPACT_DIVISOR,
                                                        DISPLAY_SCALE,
                                                        RoundingMode.HALF_UP))
                                .build());
            }
        }
    }

    private void sortSuggestionsByPriority(List<ImprovementSuggestion> suggestions) {
        suggestions.sort(
                (a, b) -> {
                    int priorityCompare =
                            getPriorityValue(b.getPriority()) - getPriorityValue(a.getPriority());
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return b.getPotentialImpact().compareTo(a.getPotentialImpact());
                });
    }

    // ============================================================
    // Private Methods - 전체 요약 생성
    // ============================================================

    private OverallSummary buildOverallSummary(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            List<WeekdayStats> weekdayStats,
            List<TimeOfDayStats> timeStats,
            List<MistakePattern> mistakes) {
        List<Transaction> sellTransactions =
                getSellTransactionsWithPnl(accountId, startDate, endDate);
        TradeBasicStats basicStats = calculateBasicStats(sellTransactions);

        WeekdayStats bestDay = findBestWeekday(weekdayStats);
        WeekdayStats worstDay = findWorstWeekday(weekdayStats);
        TimeOfDayStats bestTime = findBestTimeOfDay(timeStats);

        List<SymbolStats> symbolStats = getSymbolPerformance(accountId, startDate, endDate);
        SymbolStats bestSymbol =
                symbolStats.stream()
                        .max(Comparator.comparing(SymbolStats::getTotalProfit))
                        .orElse(null);

        int mistakeCount = mistakes.stream().mapToInt(MistakePattern::getCount).sum();

        return OverallSummary.builder()
                .totalTrades(basicStats.totalTrades())
                .winningTrades(basicStats.winningTrades())
                .losingTrades(basicStats.totalTrades() - basicStats.winningTrades())
                .overallWinRate(basicStats.winRate())
                .totalProfit(basicStats.totalProfit())
                .avgProfit(basicStats.avgReturn())
                .bestDay(bestDay != null ? bestDay.getDayName() : NO_DATA_PLACEHOLDER)
                .worstDay(worstDay != null ? worstDay.getDayName() : NO_DATA_PLACEHOLDER)
                .bestTimeSlot(bestTime != null ? bestTime.getTimePeriod() : NO_DATA_PLACEHOLDER)
                .bestSymbol(bestSymbol != null ? bestSymbol.getSymbol() : NO_DATA_PLACEHOLDER)
                .mistakeCount(mistakeCount)
                .consistencyScore(calculateConsistencyScore(weekdayStats, timeStats))
                .build();
    }

    private WeekdayStats findBestWeekday(List<WeekdayStats> weekdayStats) {
        return weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .max(Comparator.comparing(WeekdayStats::getWinRate))
                .orElse(null);
    }

    private WeekdayStats findWorstWeekday(List<WeekdayStats> weekdayStats) {
        return weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .min(Comparator.comparing(WeekdayStats::getWinRate))
                .orElse(null);
    }

    private TimeOfDayStats findBestTimeOfDay(List<TimeOfDayStats> timeStats) {
        return timeStats.stream()
                .filter(s -> s.getTotalTrades() >= MIN_TRADES_FOR_BEST_TIME)
                .max(Comparator.comparing(TimeOfDayStats::getWinRate))
                .orElse(null);
    }

    // ============================================================
    // Private Methods - 수익률 및 성과 계산
    // ============================================================

    private ReturnStatistics calculateReturnStatistics(
            List<Transaction> allTx, List<Transaction> sells) {
        int wins = 0;
        double totalReturnPct = 0;
        double maxReturnPct = Double.MIN_VALUE;

        for (Transaction sell : sells) {
            List<Transaction> buys =
                    allTx.stream()
                            .filter(
                                    t ->
                                            t.getType() == TransactionType.BUY
                                                    && t.getStock().equals(sell.getStock())
                                                    && t.getTransactionDate()
                                                            .isBefore(sell.getTransactionDate()))
                            .toList();

            if (!buys.isEmpty()) {
                BigDecimal avgBuy =
                        buys.stream()
                                .map(Transaction::getPrice)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(
                                        BigDecimal.valueOf(buys.size()),
                                        DISPLAY_SCALE,
                                        RoundingMode.HALF_UP);

                double ret =
                        sell.getPrice()
                                .subtract(avgBuy)
                                .divide(avgBuy, CALCULATION_SCALE, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();

                if (ret > 0) wins++;
                totalReturnPct += ret;
                maxReturnPct = Math.max(maxReturnPct, ret);
            }
        }

        return new ReturnStatistics(wins, totalReturnPct, maxReturnPct);
    }

    private double calcAvgHoldingPeriod(List<Transaction> transactions) {
        Map<String, List<Transaction>> byStock =
                transactions.stream().collect(Collectors.groupingBy(t -> t.getStock().getSymbol()));

        long totalDays = 0;
        int completedTrades = 0;

        for (List<Transaction> stockTx : byStock.values()) {
            var buys =
                    stockTx.stream()
                            .filter(t -> t.getType() == TransactionType.BUY)
                            .sorted(Comparator.comparing(Transaction::getTransactionDate))
                            .toList();
            var sells =
                    stockTx.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .sorted(Comparator.comparing(Transaction::getTransactionDate))
                            .toList();

            for (Transaction sell : sells) {
                for (Transaction buy : buys) {
                    if (buy.getTransactionDate().isBefore(sell.getTransactionDate())) {
                        totalDays +=
                                java.time.temporal.ChronoUnit.DAYS.between(
                                        buy.getTransactionDate().toLocalDate(),
                                        sell.getTransactionDate().toLocalDate());
                        completedTrades++;
                        break;
                    }
                }
            }
        }
        return completedTrades > 0 ? (double) totalDays / completedTrades : 0;
    }

    private double calcSharpeRatio(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;

        Map<String, List<Transaction>> monthly = groupTransactionsByMonth(transactions);
        List<Double> monthlyReturns = calculateMonthlyReturns(monthly);

        if (monthlyReturns.size() < 2) return 0;

        double avg = monthlyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance =
                monthlyReturns.stream().mapToDouble(r -> Math.pow(r - avg, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        return stdDev != 0 ? (avg - RISK_FREE_RATE_MONTHLY) / stdDev : 0;
    }

    private List<Double> calculateMonthlyReturns(Map<String, List<Transaction>> monthly) {
        List<Double> monthlyReturns = new ArrayList<>();

        for (List<Transaction> monthTx : monthly.values()) {
            BigDecimal buy =
                    monthTx.stream()
                            .filter(t -> t.getType() == TransactionType.BUY)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sell =
                    monthTx.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (buy.compareTo(BigDecimal.ZERO) > 0) {
                monthlyReturns.add(
                        sell.subtract(buy)
                                .divide(buy, CALCULATION_SCALE, RoundingMode.HALF_UP)
                                .doubleValue());
            }
        }

        return monthlyReturns;
    }

    private double calcMaxDrawdown(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;

        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(Transaction::getTransactionDate));

        BigDecimal running = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        double maxDD = 0;

        for (Transaction tx : sorted) {
            running =
                    (tx.getType() == TransactionType.BUY)
                            ? running.add(tx.getTotalAmount())
                            : running.subtract(tx.getTotalAmount());

            if (running.compareTo(peak) > 0) {
                peak = running;
            }

            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                double dd =
                        peak.subtract(running)
                                        .divide(peak, CALCULATION_SCALE, RoundingMode.HALF_UP)
                                        .doubleValue()
                                * 100;
                maxDD = Math.max(maxDD, dd);
            }
        }
        return maxDD;
    }

    private BigDecimal calculateConsistencyScore(
            List<WeekdayStats> weekdayStats, List<TimeOfDayStats> timeStats) {
        List<BigDecimal> winRates = collectWinRatesForConsistency(weekdayStats, timeStats);

        if (winRates.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal mean =
                winRates.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(winRates.size()),
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP);

        BigDecimal variance =
                winRates.stream()
                        .map(r -> r.subtract(mean).pow(2))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(winRates.size()),
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP);

        double stdDev = Math.sqrt(variance.doubleValue());
        double score = Math.max(0, MAX_CONSISTENCY_SCORE - stdDev * CONSISTENCY_STD_DEV_WEIGHT);

        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> collectWinRatesForConsistency(
            List<WeekdayStats> weekdayStats, List<TimeOfDayStats> timeStats) {
        List<BigDecimal> winRates = new ArrayList<>();

        weekdayStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .forEach(s -> winRates.add(s.getWinRate()));
        timeStats.stream()
                .filter(s -> s.getTotalTrades() > 0)
                .forEach(s -> winRates.add(s.getWinRate()));

        return winRates;
    }

    // ============================================================
    // Private Methods - 자산 히스토리
    // ============================================================

    private Map<LocalDate, BigDecimal> calculateDailyAssetValues(List<Transaction> transactions) {
        Map<LocalDate, BigDecimal> dailyValues = new TreeMap<>();
        BigDecimal running = BigDecimal.ZERO;

        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(Transaction::getTransactionDate));

        for (Transaction tx : sorted) {
            LocalDate date = tx.getTransactionDate().toLocalDate();
            running =
                    (tx.getType() == TransactionType.BUY)
                            ? running.add(tx.getTotalAmount())
                            : running.subtract(tx.getTotalAmount());
            dailyValues.put(date, running);
        }

        return dailyValues;
    }

    private Map<String, Object> buildAssetHistoryResponse(
            LocalDate startDate, LocalDate endDate, Map<LocalDate, BigDecimal> dailyValues) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal lastValue = BigDecimal.ZERO;

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (dailyValues.containsKey(d)) {
                lastValue = dailyValues.get(d);
            }
            labels.add(d.format(formatter));
            values.add(lastValue);
        }

        Map<String, Object> history = new HashMap<>();
        history.put("labels", labels);
        history.put("values", values);
        return history;
    }

    // ============================================================
    // Private Methods - 월별 수익률
    // ============================================================

    private List<Map<String, Object>> buildMonthlyReturnsResponse(
            Map<String, List<Transaction>> byMonth) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : byMonth.entrySet()) {
            String month = entry.getKey();
            List<Transaction> txs = entry.getValue();

            BigDecimal buy =
                    txs.stream()
                            .filter(t -> t.getType() == TransactionType.BUY)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sell =
                    txs.stream()
                            .filter(t -> t.getType() == TransactionType.SELL)
                            .map(Transaction::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal rate =
                    buy.compareTo(BigDecimal.ZERO) > 0
                            ? sell.subtract(buy)
                                    .divide(buy, CALCULATION_SCALE, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", month);
            monthData.put("returnRate", rate);
            monthData.put("investment", buy);
            result.add(monthData);
        }

        return result;
    }

    // ============================================================
    // Private Methods - FIFO 기반 보유 기간 계산
    // ============================================================

    /**
     * FIFO 매칭 기반 보유 기간 계산
     *
     * <p>종목별 매수/매도 거래를 FIFO 순서로 매칭하여 각 매도 거래의 가중 평균 보유 기간을 산출합니다.
     *
     * @param accountId 계좌 ID (null이면 전체)
     * @param sellTransactions 매도 거래 목록
     * @return 매도 거래 ID → 보유 기간(일) 매핑
     */
    private Map<Long, BigDecimal> calculateFifoHoldingDays(
            Long accountId, List<Transaction> sellTransactions) {
        Map<Long, BigDecimal> holdingDaysMap = new HashMap<>();
        if (sellTransactions.isEmpty()) {
            return holdingDaysMap;
        }

        // 매도 거래를 (계좌, 종목) 쌍으로 그룹핑
        Map<String, List<Transaction>> grouped =
                sellTransactions.stream().collect(Collectors.groupingBy(this::accountStockKey));

        for (List<Transaction> sells : grouped.values()) {
            Transaction sample = sells.get(0);
            Long accId = sample.getAccount() != null ? sample.getAccount().getId() : null;
            Long stockId = sample.getStock().getId();

            List<Transaction> sortedSells =
                    sells.stream()
                            .sorted(Comparator.comparing(Transaction::getTransactionDate))
                            .toList();

            // 해당 계좌/종목의 모든 매수 거래 조회 (FIFO 순서)
            List<Transaction> buys =
                    transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                            accId, stockId, TransactionType.BUY);

            simulateFifoMatching(sortedSells, buys, holdingDaysMap);
        }

        return holdingDaysMap;
    }

    /** FIFO 순서로 매수/매도를 매칭하여 보유 기간 계산 */
    private void simulateFifoMatching(
            List<Transaction> sortedSells,
            List<Transaction> buys,
            Map<Long, BigDecimal> holdingDaysMap) {
        List<FifoBuyEntry> buyQueue = new ArrayList<>();
        for (Transaction buy : buys) {
            buyQueue.add(new FifoBuyEntry(buy.getTransactionDate(), buy.getQuantity()));
        }

        int buyIndex = 0;
        for (Transaction sell : sortedSells) {
            BigDecimal remainingToSell = sell.getQuantity();
            BigDecimal weightedDays = BigDecimal.ZERO;
            BigDecimal totalConsumed = BigDecimal.ZERO;

            while (remainingToSell.compareTo(BigDecimal.ZERO) > 0 && buyIndex < buyQueue.size()) {
                FifoBuyEntry entry = buyQueue.get(buyIndex);

                if (entry.remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    buyIndex++;
                    continue;
                }

                BigDecimal consumed = entry.remainingQuantity.min(remainingToSell);
                long days =
                        java.time.temporal.ChronoUnit.DAYS.between(
                                entry.buyDate.toLocalDate(),
                                sell.getTransactionDate().toLocalDate());

                weightedDays = weightedDays.add(BigDecimal.valueOf(days).multiply(consumed));
                totalConsumed = totalConsumed.add(consumed);

                entry.remainingQuantity = entry.remainingQuantity.subtract(consumed);
                remainingToSell = remainingToSell.subtract(consumed);

                if (entry.remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    buyIndex++;
                }
            }

            if (totalConsumed.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avgDays =
                        weightedDays.divide(totalConsumed, DISPLAY_SCALE, RoundingMode.HALF_UP);
                holdingDaysMap.put(sell.getId(), avgDays);
            }
        }
    }

    /** 종목별 평균 보유 기간 계산 (FIFO 결과 활용) */
    private BigDecimal calculateAvgHoldingDaysForSymbol(
            List<Transaction> trades, Map<Long, BigDecimal> holdingDaysMap) {
        List<BigDecimal> days =
                trades.stream()
                        .map(t -> holdingDaysMap.get(t.getId()))
                        .filter(Objects::nonNull)
                        .toList();

        if (days.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = days.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(days.size()), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** (계좌ID, 종목ID) 키 생성 */
    private String accountStockKey(Transaction t) {
        Long accId = t.getAccount() != null ? t.getAccount().getId() : null;
        return accId + ":" + t.getStock().getId();
    }

    // ============================================================
    // Private Methods - 유틸리티
    // ============================================================

    private String findBestTimeSlot(List<Transaction> trades) {
        if (trades.isEmpty()) {
            return NO_DATA_PLACEHOLDER;
        }

        Map<Integer, BigDecimal> profitByHour =
                trades.stream()
                        .filter(t -> t.getRealizedPnl() != null)
                        .collect(
                                Collectors.groupingBy(
                                        t -> t.getTransactionDate().getHour(),
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Transaction::getRealizedPnl,
                                                BigDecimal::add)));

        return profitByHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> String.format("%02d:00", e.getKey()))
                .orElse(NO_DATA_PLACEHOLDER);
    }

    private String getActionItemForMistake(String mistakeType) {
        return switch (mistakeType) {
            case MistakeTypes.NO_STOP_LOSS -> "모든 거래에 손절가 필수 설정";
            case MistakeTypes.OVERTRADING -> "일일 최대 거래 횟수 3회로 제한";
            case MistakeTypes.REVENGE_TRADING -> "손실 후 최소 1시간 휴식 후 거래";
            case MistakeTypes.FOMO_ENTRY -> "진입 전 체크리스트 확인 필수";
            case MistakeTypes.HOLDING_TOO_LONG -> "손실 포지션 보유 기간 30일 이내로 관리";
            default -> "거래 규칙 재검토";
        };
    }

    private int getPriorityValue(String priority) {
        return switch (priority) {
            case PRIORITY_STR_HIGH -> PRIORITY_HIGH;
            case PRIORITY_STR_MEDIUM -> PRIORITY_MEDIUM;
            case PRIORITY_STR_LOW -> PRIORITY_LOW;
            default -> 0;
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0원";
        }
        return String.format("%,.0f원", amount);
    }
}
