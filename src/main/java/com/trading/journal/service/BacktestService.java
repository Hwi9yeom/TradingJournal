package com.trading.journal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.BacktestRequestDto;
import com.trading.journal.dto.BacktestResultDto;
import com.trading.journal.dto.BacktestSummaryDto;
import com.trading.journal.dto.OptimizationRequestDto;
import com.trading.journal.dto.OptimizationRequestDto.ParameterRange;
import com.trading.journal.dto.OptimizationResultDto;
import com.trading.journal.dto.OptimizationResultDto.ParameterResult;
import com.trading.journal.entity.BacktestResult;
import com.trading.journal.entity.BacktestTrade;
import com.trading.journal.repository.BacktestResultRepository;
import com.trading.journal.strategy.TradingStrategy;
import com.trading.journal.strategy.TradingStrategy.PriceData;
import com.trading.journal.strategy.TradingStrategy.Signal;
import com.trading.journal.strategy.impl.BollingerBandStrategy;
import com.trading.journal.strategy.impl.MACDStrategy;
import com.trading.journal.strategy.impl.MomentumStrategy;
import com.trading.journal.strategy.impl.MovingAverageCrossStrategy;
import com.trading.journal.strategy.impl.RSIStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yahoofinance.histquotes.HistoricalQuote;

/** 백테스트 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    // === Constants ===

    /** 무위험 수익률 (Risk-free rate) - 연간 3% 가정 */
    private static final BigDecimal RISK_FREE_RATE = BigDecimal.valueOf(3);

    /** 손실이 없거나 극단적 상황에서 사용할 최대 비율 값 */
    private static final BigDecimal MAX_RATIO_VALUE = BigDecimal.valueOf(999.99);

    /** 백분율 변환을 위한 스케일 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 연간 거래일 수 */
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    /** 연간 일수 */
    private static final double DAYS_PER_YEAR = 365.0;

    /** 차트 데이터 샘플링 간격 (주 단위) */
    private static final int CHART_SAMPLING_INTERVAL = 5;

    /** 금액 계산 스케일 */
    private static final int DECIMAL_SCALE = 6;

    /** 수량 계산 스케일 */
    private static final int QUANTITY_SCALE = 4;

    /** 금액 표시 스케일 */
    private static final int DISPLAY_SCALE = 2;

    /** 샘플 데이터 기본 가격 */
    private static final BigDecimal SAMPLE_BASE_PRICE = BigDecimal.valueOf(100);

    /** 샘플 데이터 일일 변동 범위 (6% = 0.06) */
    private static final double SAMPLE_DAILY_CHANGE_RANGE = 0.06;

    /** 샘플 데이터 일일 변동 편향 (양의 수익률 편향) */
    private static final double SAMPLE_DAILY_CHANGE_BIAS = 0.48;

    /** 샘플 데이터 일중 변동 범위 (2% = 0.02) */
    private static final double SAMPLE_INTRADAY_RANGE = 0.02;

    /** 샘플 데이터 시가 변동 범위 (1% = 0.01) */
    private static final double SAMPLE_OPEN_VARIATION = 0.01;

    /** 샘플 데이터 기본 거래량 */
    private static final long SAMPLE_BASE_VOLUME = 100000L;

    /** 주말을 구분하는 요일 값 (월~금: 1~5) */
    private static final int LAST_WEEKDAY = 5;

    // === Dependencies ===

    private final BacktestResultRepository backtestResultRepository;
    private final ObjectMapper objectMapper;
    private final StockPriceService stockPriceService;

    // === Inner Helper Classes ===

    /** 포지션 상태를 관리하는 헬퍼 클래스 */
    private static class PositionState {
        private BigDecimal capital;
        private BigDecimal position = BigDecimal.ZERO;
        private BigDecimal entryPrice = BigDecimal.ZERO;
        private LocalDate entryDate;
        private String entrySignal;

        PositionState(BigDecimal initialCapital) {
            this.capital = initialCapital;
        }

        boolean hasPosition() {
            return position.compareTo(BigDecimal.ZERO) > 0;
        }

        boolean hasNoPosition() {
            return position.compareTo(BigDecimal.ZERO) == 0;
        }

        BigDecimal getPortfolioValue(BigDecimal currentPrice) {
            return capital.add(position.multiply(currentPrice));
        }

        void openPosition(
                BigDecimal newPosition,
                BigDecimal price,
                BigDecimal cost,
                LocalDate date,
                String signal) {
            this.position = newPosition;
            this.entryPrice = price;
            this.entryDate = date;
            this.entrySignal = signal;
            this.capital = capital.subtract(cost);
        }

        void closePosition(BigDecimal proceeds) {
            this.capital = capital.add(proceeds);
            this.position = BigDecimal.ZERO;
            this.entryPrice = BigDecimal.ZERO;
            this.entryDate = null;
        }

        BigDecimal calculateCurrentReturn(BigDecimal currentPrice) {
            return currentPrice
                    .subtract(entryPrice)
                    .divide(entryPrice, DECIMAL_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
        }
    }

    /** 거래 통계를 추적하는 헬퍼 클래스 */
    private static class TradeStatistics {
        private int tradeNumber = 0;
        private int winningTrades = 0;
        private int losingTrades = 0;
        private BigDecimal totalWinAmount = BigDecimal.ZERO;
        private BigDecimal totalLossAmount = BigDecimal.ZERO;
        private int currentWinStreak = 0;
        private int currentLossStreak = 0;
        private int maxWinStreak = 0;
        private int maxLossStreak = 0;

        int nextTradeNumber() {
            return ++tradeNumber;
        }

        void recordWin(BigDecimal profit) {
            winningTrades++;
            totalWinAmount = totalWinAmount.add(profit);
            currentWinStreak++;
            currentLossStreak = 0;
            maxWinStreak = Math.max(maxWinStreak, currentWinStreak);
        }

        void recordLoss(BigDecimal loss) {
            losingTrades++;
            totalLossAmount = totalLossAmount.add(loss.abs());
            currentLossStreak++;
            currentWinStreak = 0;
            maxLossStreak = Math.max(maxLossStreak, currentLossStreak);
        }

        void recordTrade(BigDecimal profit) {
            if (profit.compareTo(BigDecimal.ZERO) > 0) {
                recordWin(profit);
            } else {
                recordLoss(profit);
            }
        }

        int getTotalTrades() {
            return winningTrades + losingTrades;
        }
    }

    /** 최대 낙폭(Drawdown) 추적용 헬퍼 클래스 */
    private static class DrawdownTracker {
        private BigDecimal peakEquity;
        private BigDecimal maxDrawdown = BigDecimal.ZERO;

        DrawdownTracker(BigDecimal initialCapital) {
            this.peakEquity = initialCapital;
        }

        BigDecimal updateAndGetDrawdown(BigDecimal portfolioValue) {
            if (portfolioValue.compareTo(peakEquity) > 0) {
                peakEquity = portfolioValue;
            }
            BigDecimal drawdown =
                    peakEquity
                            .subtract(portfolioValue)
                            .divide(peakEquity, DECIMAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(HUNDRED);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
            return drawdown;
        }
    }

    // === Public API ===

    /** 백테스트 실행 */
    @Transactional
    public BacktestResultDto runBacktest(BacktestRequestDto request) {
        long startTime = System.currentTimeMillis();

        // 1. 전략 생성
        TradingStrategy strategy = createStrategy(request);

        // 2. 실제 가격 데이터 조회 (Yahoo Finance API)
        List<PriceData> prices =
                fetchHistoricalPriceData(
                        request.getSymbol(), request.getStartDate(), request.getEndDate());

        // 3. 백테스트 시뮬레이션 실행
        BacktestResult result = executeBacktest(request, strategy, prices);

        // 4. 실행 시간 기록
        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        // 5. 차트 데이터 계산 및 캐싱
        cacheComputedData(result, prices);

        // 6. 결과 저장
        BacktestResult saved = backtestResultRepository.save(result);

        // 7. DTO 변환 및 반환
        return convertToDto(saved, prices);
    }

    /** 전략 생성 */
    private TradingStrategy createStrategy(BacktestRequestDto request) {
        Map<String, Object> params = request.getStrategyParams();
        if (params == null) {
            params = new HashMap<>();
        }

        switch (request.getStrategyType()) {
            case MOVING_AVERAGE:
                return MovingAverageCrossStrategy.builder()
                        .shortPeriod(getIntParam(params, "shortPeriod", 20))
                        .longPeriod(getIntParam(params, "longPeriod", 60))
                        .maType(getMAType(params, "maType", MovingAverageCrossStrategy.MAType.SMA))
                        .build();

            case RSI:
                return RSIStrategy.builder()
                        .period(getIntParam(params, "period", 14))
                        .overboughtLevel(getIntParam(params, "overboughtLevel", 70))
                        .oversoldLevel(getIntParam(params, "oversoldLevel", 30))
                        .build();

            case BOLLINGER_BAND:
                return BollingerBandStrategy.builder()
                        .period(getIntParam(params, "period", 20))
                        .stdDevMultiplier(getDoubleParam(params, "stdDevMultiplier", 2.0))
                        .build();

            case MOMENTUM:
                return MomentumStrategy.builder()
                        .period(getIntParam(params, "period", 20))
                        .entryThreshold(getDoubleParam(params, "entryThreshold", 0.0))
                        .exitThreshold(getDoubleParam(params, "exitThreshold", 0.0))
                        .build();

            case MACD:
                return MACDStrategy.builder()
                        .fastPeriod(getIntParam(params, "fastPeriod", 12))
                        .slowPeriod(getIntParam(params, "slowPeriod", 26))
                        .signalPeriod(getIntParam(params, "signalPeriod", 9))
                        .build();

            default:
                throw new IllegalArgumentException("지원하지 않는 전략 유형: " + request.getStrategyType());
        }
    }

    /** 백테스트 시뮬레이션 실행 */
    private BacktestResult executeBacktest(
            BacktestRequestDto request, TradingStrategy strategy, List<PriceData> prices) {
        // 상태 초기화
        PositionState positionState = new PositionState(request.getInitialCapital());
        TradeStatistics stats = new TradeStatistics();
        DrawdownTracker drawdownTracker = new DrawdownTracker(request.getInitialCapital());
        List<BacktestTrade> trades = new ArrayList<>();

        // 비용 파라미터 계산
        BigDecimal commissionRate = toDecimalRate(request.getCommissionRate());
        BigDecimal slippage = toDecimalRate(request.getSlippage());

        // 가격 데이터 순회 및 시뮬레이션
        for (int i = 0; i < prices.size(); i++) {
            PriceData price = prices.get(i);
            BigDecimal currentPrice = price.getClose();

            // 포트폴리오 가치 및 낙폭 업데이트
            BigDecimal portfolioValue = positionState.getPortfolioValue(currentPrice);
            drawdownTracker.updateAndGetDrawdown(portfolioValue);

            // 시그널 결정
            Signal signal =
                    determineSignal(strategy, prices, i, request, positionState, currentPrice);

            // 시그널에 따른 거래 실행
            processSignal(
                    signal,
                    positionState,
                    stats,
                    trades,
                    request,
                    strategy,
                    price,
                    currentPrice,
                    commissionRate,
                    slippage);
        }

        // 잔여 포지션 청산
        liquidateRemainingPosition(positionState, prices);

        // 최종 결과 계산 및 반환
        return buildBacktestResult(
                request, strategy, positionState, stats, drawdownTracker, trades);
    }

    /** 백분율을 소수점 비율로 변환 */
    private BigDecimal toDecimalRate(BigDecimal percentage) {
        return percentage.divide(HUNDRED, DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /** 거래 시그널 결정 (전략 시그널 + 손절/익절 체크) */
    private Signal determineSignal(
            TradingStrategy strategy,
            List<PriceData> prices,
            int index,
            BacktestRequestDto request,
            PositionState positionState,
            BigDecimal currentPrice) {
        Signal signal = strategy.generateSignal(prices, index);

        // 포지션 보유 중일 때만 손절/익절 체크
        if (!positionState.hasPosition()) {
            return signal;
        }

        BigDecimal currentReturn = positionState.calculateCurrentReturn(currentPrice);

        if (shouldTriggerStopLoss(request, currentReturn)) {
            return Signal.SELL;
        }

        if (shouldTriggerTakeProfit(request, currentReturn)) {
            return Signal.SELL;
        }

        return signal;
    }

    /** 손절 조건 체크 */
    private boolean shouldTriggerStopLoss(BacktestRequestDto request, BigDecimal currentReturn) {
        return request.getStopLossPercent() != null
                && currentReturn.compareTo(request.getStopLossPercent().negate()) <= 0;
    }

    /** 익절 조건 체크 */
    private boolean shouldTriggerTakeProfit(BacktestRequestDto request, BigDecimal currentReturn) {
        return request.getTakeProfitPercent() != null
                && currentReturn.compareTo(request.getTakeProfitPercent()) >= 0;
    }

    /** 시그널에 따른 거래 처리 */
    private void processSignal(
            Signal signal,
            PositionState positionState,
            TradeStatistics stats,
            List<BacktestTrade> trades,
            BacktestRequestDto request,
            TradingStrategy strategy,
            PriceData price,
            BigDecimal currentPrice,
            BigDecimal commissionRate,
            BigDecimal slippage) {
        if (signal == Signal.BUY && positionState.hasNoPosition()) {
            executeBuyOrder(
                    positionState,
                    request,
                    strategy,
                    price,
                    currentPrice,
                    commissionRate,
                    slippage);
        } else if (signal == Signal.SELL && positionState.hasPosition()) {
            executeSellOrder(
                    positionState,
                    stats,
                    trades,
                    request,
                    strategy,
                    price,
                    currentPrice,
                    commissionRate,
                    slippage);
        }
    }

    /** 매수 주문 실행 */
    private void executeBuyOrder(
            PositionState positionState,
            BacktestRequestDto request,
            TradingStrategy strategy,
            PriceData price,
            BigDecimal currentPrice,
            BigDecimal commissionRate,
            BigDecimal slippage) {
        BigDecimal positionSizeRate = toDecimalRate(request.getPositionSizePercent());
        BigDecimal investAmount = positionState.capital.multiply(positionSizeRate);

        // 슬리피지 적용 (매수 시 가격 상승)
        BigDecimal buyPrice = currentPrice.multiply(BigDecimal.ONE.add(slippage));

        // 수수료 차감
        BigDecimal commission = investAmount.multiply(commissionRate);
        BigDecimal netAmount = investAmount.subtract(commission);

        // 포지션 수량 계산
        BigDecimal quantity = netAmount.divide(buyPrice, QUANTITY_SCALE, RoundingMode.DOWN);

        positionState.openPosition(
                quantity, buyPrice, investAmount, price.getDate(), strategy.getName());
    }

    /** 매도 주문 실행 */
    private void executeSellOrder(
            PositionState positionState,
            TradeStatistics stats,
            List<BacktestTrade> trades,
            BacktestRequestDto request,
            TradingStrategy strategy,
            PriceData price,
            BigDecimal currentPrice,
            BigDecimal commissionRate,
            BigDecimal slippage) {
        // 슬리피지 적용 (매도 시 가격 하락)
        BigDecimal sellPrice = currentPrice.multiply(BigDecimal.ONE.subtract(slippage));

        BigDecimal grossAmount = positionState.position.multiply(sellPrice);
        BigDecimal commission = grossAmount.multiply(commissionRate);
        BigDecimal netAmount = grossAmount.subtract(commission);

        // 손익 계산
        BigDecimal investedAmount = positionState.position.multiply(positionState.entryPrice);
        BigDecimal profit = netAmount.subtract(investedAmount);
        BigDecimal profitPercent =
                profit.divide(investedAmount, DECIMAL_SCALE, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);

        // 거래 기록 생성
        BacktestTrade trade =
                createTradeRecord(
                        positionState,
                        stats,
                        request,
                        strategy,
                        price,
                        sellPrice,
                        profit,
                        profitPercent,
                        netAmount);
        trades.add(trade);

        // 통계 업데이트
        stats.recordTrade(profit);

        // 포지션 청산
        positionState.closePosition(netAmount);
    }

    /** 거래 기록 생성 */
    private BacktestTrade createTradeRecord(
            PositionState positionState,
            TradeStatistics stats,
            BacktestRequestDto request,
            TradingStrategy strategy,
            PriceData price,
            BigDecimal sellPrice,
            BigDecimal profit,
            BigDecimal profitPercent,
            BigDecimal netAmount) {
        BigDecimal investedAmount = positionState.position.multiply(positionState.entryPrice);

        return BacktestTrade.builder()
                .tradeNumber(stats.nextTradeNumber())
                .symbol(request.getSymbol())
                .entryDate(positionState.entryDate)
                .exitDate(price.getDate())
                .entryPrice(positionState.entryPrice)
                .exitPrice(sellPrice)
                .quantity(positionState.position)
                .profit(profit)
                .profitPercent(profitPercent)
                .entrySignal(positionState.entrySignal)
                .exitSignal(strategy.getName())
                .holdingDays(
                        (int) ChronoUnit.DAYS.between(positionState.entryDate, price.getDate()))
                .portfolioValueAtEntry(investedAmount.add(positionState.capital))
                .portfolioValueAtExit(positionState.capital.add(netAmount))
                .build();
    }

    /** 잔여 포지션 청산 */
    private void liquidateRemainingPosition(PositionState positionState, List<PriceData> prices) {
        if (!positionState.hasPosition()) {
            return;
        }

        PriceData lastPrice = prices.get(prices.size() - 1);
        BigDecimal sellPrice = lastPrice.getClose();
        BigDecimal grossAmount = positionState.position.multiply(sellPrice);
        positionState.closePosition(grossAmount);
    }

    /** 백테스트 결과 엔티티 생성 */
    private BacktestResult buildBacktestResult(
            BacktestRequestDto request,
            TradingStrategy strategy,
            PositionState positionState,
            TradeStatistics stats,
            DrawdownTracker drawdownTracker,
            List<BacktestTrade> trades) {
        BigDecimal finalCapital = positionState.capital;
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());

        // 수익률 계산
        BigDecimal totalReturn = calculateTotalReturn(request.getInitialCapital(), finalCapital);
        double cagr = calculateCAGR(request.getInitialCapital(), finalCapital, days);

        // 통계 계산
        BigDecimal winRate = calculateWinRate(stats);
        BigDecimal avgWin = calculateAverageWin(stats);
        BigDecimal avgLoss = calculateAverageLoss(stats);
        BigDecimal profitFactor = calculateProfitFactor(stats);
        BigDecimal avgHoldingDays = calculateAverageHoldingDays(trades);

        // 위험 조정 수익률 계산
        BigDecimal sharpeRatio = calculateSharpeRatio(trades, totalReturn, days);
        BigDecimal sortinoRatio = calculateSortinoRatio(trades, totalReturn, days);

        // 결과 엔티티 생성
        BacktestResult result =
                BacktestResult.builder()
                        .strategyName(strategy.getName())
                        .strategyConfig(toJson(strategy.getParameters()))
                        .symbol(request.getSymbol())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .initialCapital(request.getInitialCapital())
                        .finalCapital(finalCapital)
                        .totalReturn(totalReturn)
                        .cagr(BigDecimal.valueOf(cagr))
                        .maxDrawdown(drawdownTracker.maxDrawdown)
                        .sharpeRatio(sharpeRatio)
                        .sortinoRatio(sortinoRatio)
                        .totalTrades(stats.getTotalTrades())
                        .winningTrades(stats.winningTrades)
                        .losingTrades(stats.losingTrades)
                        .winRate(winRate)
                        .avgWin(avgWin)
                        .avgLoss(avgLoss)
                        .profitFactor(profitFactor)
                        .maxWinStreak(stats.maxWinStreak)
                        .maxLossStreak(stats.maxLossStreak)
                        .avgHoldingDays(avgHoldingDays)
                        .trades(new ArrayList<>())
                        .build();

        // 거래 연결
        linkTradesToResult(trades, result);

        return result;
    }

    /** 총 수익률 계산 */
    private BigDecimal calculateTotalReturn(BigDecimal initialCapital, BigDecimal finalCapital) {
        return finalCapital
                .subtract(initialCapital)
                .divide(initialCapital, DECIMAL_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    /** CAGR (연평균 복합 성장률) 계산 */
    private double calculateCAGR(BigDecimal initialCapital, BigDecimal finalCapital, long days) {
        double years = days / DAYS_PER_YEAR;
        if (years <= 0) {
            return 0;
        }
        double growthRatio =
                finalCapital
                        .divide(initialCapital, DECIMAL_SCALE, RoundingMode.HALF_UP)
                        .doubleValue();
        return (Math.pow(growthRatio, 1.0 / years) - 1) * 100;
    }

    /** 승률 계산 */
    private BigDecimal calculateWinRate(TradeStatistics stats) {
        int totalTrades = stats.getTotalTrades();
        if (totalTrades == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(stats.winningTrades)
                .divide(BigDecimal.valueOf(totalTrades), QUANTITY_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    /** 평균 수익 계산 */
    private BigDecimal calculateAverageWin(TradeStatistics stats) {
        if (stats.winningTrades == 0) {
            return BigDecimal.ZERO;
        }
        return stats.totalWinAmount.divide(
                BigDecimal.valueOf(stats.winningTrades), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** 평균 손실 계산 */
    private BigDecimal calculateAverageLoss(TradeStatistics stats) {
        if (stats.losingTrades == 0) {
            return BigDecimal.ZERO;
        }
        return stats.totalLossAmount.divide(
                BigDecimal.valueOf(stats.losingTrades), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** Profit Factor 계산 */
    private BigDecimal calculateProfitFactor(TradeStatistics stats) {
        if (stats.totalLossAmount.compareTo(BigDecimal.ZERO) == 0) {
            return MAX_RATIO_VALUE;
        }
        return stats.totalWinAmount.divide(
                stats.totalLossAmount, QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /** 평균 보유 기간 계산 */
    private BigDecimal calculateAverageHoldingDays(List<BacktestTrade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(
                trades.stream().mapToInt(BacktestTrade::getHoldingDays).average().orElse(0));
    }

    /** 거래 목록을 결과에 연결 */
    private void linkTradesToResult(List<BacktestTrade> trades, BacktestResult result) {
        for (BacktestTrade trade : trades) {
            trade.setBacktestResult(result);
            result.getTrades().add(trade);
        }
    }

    /** 샤프 비율 계산 */
    private BigDecimal calculateSharpeRatio(
            List<BacktestTrade> trades, BigDecimal totalReturn, long days) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal stdDev = calculateStandardDeviation(trades);
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualizedReturn = annualizeReturn(totalReturn, days);
        BigDecimal annualizedStdDev = annualizeVolatility(stdDev, trades.size());

        return annualizedReturn
                .subtract(RISK_FREE_RATE)
                .divide(annualizedStdDev, QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /** 소르티노 비율 계산 */
    private BigDecimal calculateSortinoRatio(
            List<BacktestTrade> trades, BigDecimal totalReturn, long days) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal downsideDeviation = calculateDownsideDeviation(trades);
        if (downsideDeviation.compareTo(BigDecimal.ZERO) == 0) {
            return MAX_RATIO_VALUE;
        }

        BigDecimal annualizedReturn = annualizeReturn(totalReturn, days);
        BigDecimal annualizedDownside = annualizeVolatility(downsideDeviation, trades.size());

        return annualizedReturn
                .subtract(RISK_FREE_RATE)
                .divide(annualizedDownside, QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /** 수익률의 표준편차 계산 */
    private BigDecimal calculateStandardDeviation(List<BacktestTrade> trades) {
        List<BigDecimal> returns =
                trades.stream().map(BacktestTrade::getProfitPercent).collect(Collectors.toList());

        BigDecimal avgReturn =
                returns.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(returns.size()),
                                DECIMAL_SCALE,
                                RoundingMode.HALF_UP);

        BigDecimal sumSquaredDiff =
                returns.stream()
                        .map(r -> r.subtract(avgReturn).pow(2))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance =
                sumSquaredDiff.divide(
                        BigDecimal.valueOf(returns.size()), DECIMAL_SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    /** 하방 편차 계산 (음수 수익률만 고려) */
    private BigDecimal calculateDownsideDeviation(List<BacktestTrade> trades) {
        List<BigDecimal> negativeReturns =
                trades.stream()
                        .map(BacktestTrade::getProfitPercent)
                        .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                        .collect(Collectors.toList());

        if (negativeReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal downside =
                negativeReturns.stream()
                        .map(r -> r.pow(2))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(trades.size()),
                                DECIMAL_SCALE,
                                RoundingMode.HALF_UP);

        return BigDecimal.valueOf(Math.sqrt(downside.doubleValue()));
    }

    /** 수익률 연환산 */
    private BigDecimal annualizeReturn(BigDecimal totalReturn, long days) {
        return totalReturn.multiply(BigDecimal.valueOf(DAYS_PER_YEAR / days));
    }

    /** 변동성 연환산 */
    private BigDecimal annualizeVolatility(BigDecimal volatility, int tradeCount) {
        return volatility.multiply(
                BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR / tradeCount)));
    }

    /** Calmar Ratio 계산 (CAGR / MaxDrawdown) */
    private BigDecimal calculateCalmarRatio(BacktestResult result) {
        if (result.getMaxDrawdown() == null || result.getCagr() == null) {
            return BigDecimal.ZERO;
        }
        if (result.getMaxDrawdown().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return result.getCagr()
                .divide(result.getMaxDrawdown(), QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    // === Price Data Fetching ===

    /** Yahoo Finance API로 실제 가격 데이터 조회 실패 시 샘플 데이터로 폴백 */
    private List<PriceData> fetchHistoricalPriceData(
            String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Yahoo Finance에서 가격 데이터 조회: {} ({} ~ {})", symbol, startDate, endDate);
            List<HistoricalQuote> quotes =
                    stockPriceService.getHistoricalQuotes(symbol, startDate, endDate);

            if (quotes == null || quotes.isEmpty()) {
                log.warn("가격 데이터가 없습니다. 샘플 데이터 사용: {}", symbol);
                return generateSamplePriceData(symbol, startDate, endDate);
            }

            List<PriceData> prices =
                    quotes.stream()
                            .filter(q -> q.getClose() != null)
                            .map(
                                    q ->
                                            PriceData.builder()
                                                    .date(
                                                            q.getDate()
                                                                    .toInstant()
                                                                    .atZone(ZoneId.systemDefault())
                                                                    .toLocalDate())
                                                    .open(
                                                            q.getOpen() != null
                                                                    ? q.getOpen()
                                                                    : q.getClose())
                                                    .high(
                                                            q.getHigh() != null
                                                                    ? q.getHigh()
                                                                    : q.getClose())
                                                    .low(
                                                            q.getLow() != null
                                                                    ? q.getLow()
                                                                    : q.getClose())
                                                    .close(q.getClose())
                                                    .volume(
                                                            q.getVolume() != null
                                                                    ? q.getVolume()
                                                                    : 0L)
                                                    .build())
                            .sorted(Comparator.comparing(PriceData::getDate))
                            .collect(Collectors.toList());

            log.info("가격 데이터 {} 건 조회 완료", prices.size());
            return prices;
        } catch (Exception e) {
            log.warn("실제 데이터 조회 실패, 샘플 데이터 사용: {}", e.getMessage());
            return generateSamplePriceData(symbol, startDate, endDate);
        }
    }

    /** 샘플 가격 데이터 생성 (테스트용 / 폴백용) */
    private List<PriceData> generateSamplePriceData(
            String symbol, LocalDate startDate, LocalDate endDate) {
        List<PriceData> prices = new ArrayList<>();
        Random random = new Random(symbol.hashCode());

        BigDecimal price = SAMPLE_BASE_PRICE;
        LocalDate date = startDate;

        while (!date.isAfter(endDate)) {
            if (isWeekday(date)) {
                price = applyDailyPriceChange(price, random);
                prices.add(createSamplePriceData(date, price, random));
            }
            date = date.plusDays(1);
        }

        return prices;
    }

    /** 주중인지 확인 */
    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue() <= LAST_WEEKDAY;
    }

    /** 일일 가격 변동 적용 */
    private BigDecimal applyDailyPriceChange(BigDecimal currentPrice, Random random) {
        double change =
                (random.nextDouble() - SAMPLE_DAILY_CHANGE_BIAS) * SAMPLE_DAILY_CHANGE_RANGE;
        return currentPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(change)));
    }

    /** 샘플 OHLCV 데이터 생성 */
    private PriceData createSamplePriceData(LocalDate date, BigDecimal closePrice, Random random) {
        BigDecimal high =
                closePrice.multiply(
                        BigDecimal.valueOf(1 + random.nextDouble() * SAMPLE_INTRADAY_RANGE));
        BigDecimal low =
                closePrice.multiply(
                        BigDecimal.valueOf(1 - random.nextDouble() * SAMPLE_INTRADAY_RANGE));
        BigDecimal open =
                closePrice.multiply(
                        BigDecimal.valueOf(
                                1 + (random.nextDouble() - 0.5) * SAMPLE_OPEN_VARIATION));

        return PriceData.builder()
                .date(date)
                .open(open.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .high(high.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .low(low.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .close(closePrice.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP))
                .volume(SAMPLE_BASE_VOLUME + random.nextInt((int) SAMPLE_BASE_VOLUME))
                .build();
    }

    /** 계산된 차트 데이터를 엔티티에 캐싱 (저장 시 한 번만 계산) */
    private void cacheComputedData(BacktestResult result, List<PriceData> prices) {
        try {
            cacheMonthlyPerformance(result);
            cacheEquityCurveData(result, prices);
        } catch (JsonProcessingException e) {
            log.warn("차트 데이터 직렬화 실패: {}", e.getMessage());
        } catch (ArithmeticException e) {
            log.warn("차트 데이터 계산 실패: {}", e.getMessage());
        }
    }

    /** 월별 성과 데이터 캐싱 */
    private void cacheMonthlyPerformance(BacktestResult result) throws JsonProcessingException {
        Map<String, BacktestResultDto.MonthlyPerformance> monthlyMap = new LinkedHashMap<>();

        for (BacktestTrade trade : result.getTrades()) {
            String month = extractMonth(trade.getExitDate());
            BacktestResultDto.MonthlyPerformance mp =
                    monthlyMap.computeIfAbsent(month, this::createEmptyMonthlyPerformance);
            updateMonthlyPerformance(mp, trade);
        }

        result.setMonthlyPerformanceJson(
                objectMapper.writeValueAsString(new ArrayList<>(monthlyMap.values())));
    }

    /** 날짜에서 월 문자열 추출 (YYYY-MM) */
    private String extractMonth(LocalDate date) {
        return date.toString().substring(0, 7);
    }

    /** 빈 월별 성과 객체 생성 */
    private BacktestResultDto.MonthlyPerformance createEmptyMonthlyPerformance(String month) {
        return BacktestResultDto.MonthlyPerformance.builder()
                .month(month)
                .returnPct(BigDecimal.ZERO)
                .tradeCount(0)
                .profit(BigDecimal.ZERO)
                .build();
    }

    /** 월별 성과 업데이트 */
    private void updateMonthlyPerformance(
            BacktestResultDto.MonthlyPerformance mp, BacktestTrade trade) {
        mp.setTradeCount(mp.getTradeCount() + 1);
        mp.setProfit(mp.getProfit().add(trade.getProfit()));
        mp.setReturnPct(mp.getReturnPct().add(trade.getProfitPercent()));
    }

    /** Equity curve 및 관련 차트 데이터 캐싱 */
    private void cacheEquityCurveData(BacktestResult result, List<PriceData> prices)
            throws JsonProcessingException {
        List<String> equityLabels = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        List<BigDecimal> drawdownCurve = new ArrayList<>();
        List<BigDecimal> benchmarkCurve = new ArrayList<>();

        if (!prices.isEmpty()) {
            computeEquityCurves(
                    result, prices, equityLabels, equityCurve, drawdownCurve, benchmarkCurve);
        }

        result.setEquityLabelsJson(objectMapper.writeValueAsString(equityLabels));
        result.setEquityCurveJson(objectMapper.writeValueAsString(equityCurve));
        result.setDrawdownCurveJson(objectMapper.writeValueAsString(drawdownCurve));
        result.setBenchmarkCurveJson(objectMapper.writeValueAsString(benchmarkCurve));
    }

    /** Equity curve 계산 */
    private void computeEquityCurves(
            BacktestResult result,
            List<PriceData> prices,
            List<String> equityLabels,
            List<BigDecimal> equityCurve,
            List<BigDecimal> drawdownCurve,
            List<BigDecimal> benchmarkCurve) {
        BigDecimal peakEquity = result.getInitialCapital();
        BigDecimal benchmarkStart = prices.get(0).getClose();
        BigDecimal profitDelta = result.getFinalCapital().subtract(result.getInitialCapital());

        for (int i = 0; i < prices.size(); i += CHART_SAMPLING_INTERVAL) {
            PriceData price = prices.get(i);
            equityLabels.add(price.getDate().toString());

            // 선형 보간된 equity 계산
            double progress = (double) i / prices.size();
            BigDecimal currentEquity =
                    result.getInitialCapital()
                            .add(profitDelta.multiply(BigDecimal.valueOf(progress)));
            equityCurve.add(currentEquity);

            // Drawdown 계산
            if (currentEquity.compareTo(peakEquity) > 0) {
                peakEquity = currentEquity;
            }
            BigDecimal drawdown = calculateDrawdownPercent(peakEquity, currentEquity);
            drawdownCurve.add(drawdown);

            // Benchmark (Buy & Hold) 계산
            BigDecimal benchmarkValue =
                    calculateBenchmarkValue(
                            result.getInitialCapital(), price.getClose(), benchmarkStart);
            benchmarkCurve.add(benchmarkValue);
        }
    }

    /** Drawdown 백분율 계산 (음수로 반환) */
    private BigDecimal calculateDrawdownPercent(BigDecimal peakEquity, BigDecimal currentEquity) {
        return peakEquity
                .subtract(currentEquity)
                .divide(peakEquity, QUANTITY_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED.negate());
    }

    /** 벤치마크 가치 계산 */
    private BigDecimal calculateBenchmarkValue(
            BigDecimal initialCapital, BigDecimal currentPrice, BigDecimal startPrice) {
        return initialCapital
                .multiply(currentPrice)
                .divide(startPrice, DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** 결과를 DTO로 변환 (캐시된 데이터 우선 사용) */
    private BacktestResultDto convertToDto(BacktestResult result, List<PriceData> prices) {
        List<BacktestResultDto.TradeDto> tradeDtos =
                result.getTrades().stream()
                        .map(
                                trade ->
                                        BacktestResultDto.TradeDto.builder()
                                                .tradeNumber(trade.getTradeNumber())
                                                .symbol(trade.getSymbol())
                                                .entryDate(trade.getEntryDate())
                                                .exitDate(trade.getExitDate())
                                                .entryPrice(trade.getEntryPrice())
                                                .exitPrice(trade.getExitPrice())
                                                .quantity(trade.getQuantity())
                                                .profit(trade.getProfit())
                                                .profitPercent(trade.getProfitPercent())
                                                .holdingDays(trade.getHoldingDays())
                                                .entrySignal(trade.getEntrySignal())
                                                .exitSignal(trade.getExitSignal())
                                                .portfolioValueAtEntry(
                                                        trade.getPortfolioValueAtEntry())
                                                .portfolioValueAtExit(
                                                        trade.getPortfolioValueAtExit())
                                                .build())
                        .collect(Collectors.toList());

        // 캐시된 데이터 사용 시도
        List<String> equityLabels =
                loadCachedList(result.getEquityLabelsJson(), new TypeReference<>() {});
        List<BigDecimal> equityCurve =
                loadCachedList(result.getEquityCurveJson(), new TypeReference<>() {});
        List<BigDecimal> drawdownCurve =
                loadCachedList(result.getDrawdownCurveJson(), new TypeReference<>() {});
        List<BigDecimal> benchmarkCurve =
                loadCachedList(result.getBenchmarkCurveJson(), new TypeReference<>() {});
        List<BacktestResultDto.MonthlyPerformance> monthlyPerformance =
                loadCachedList(result.getMonthlyPerformanceJson(), new TypeReference<>() {});

        // 캐시가 없으면 실시간 계산 (하위 호환성)
        if (equityLabels.isEmpty() && !prices.isEmpty()) {
            log.debug("캐시된 차트 데이터 없음, 실시간 계산 수행: {}", result.getId());
            computeChartDataRealtime(
                    result, prices, equityLabels, equityCurve, drawdownCurve, benchmarkCurve);
        }

        if (monthlyPerformance.isEmpty()) {
            monthlyPerformance = computeMonthlyPerformance(result);
        }

        // Calmar Ratio 계산
        BigDecimal calmarRatio = calculateCalmarRatio(result);

        return BacktestResultDto.builder()
                .id(result.getId())
                .strategyName(result.getStrategyName())
                .strategyType(extractStrategyType(result.getStrategyName()))
                .strategyConfig(fromJson(result.getStrategyConfig()))
                .symbol(result.getSymbol())
                .startDate(result.getStartDate())
                .endDate(result.getEndDate())
                .initialCapital(result.getInitialCapital())
                .finalCapital(result.getFinalCapital())
                .totalProfit(result.getFinalCapital().subtract(result.getInitialCapital()))
                .totalReturn(result.getTotalReturn())
                .cagr(result.getCagr())
                .maxDrawdown(result.getMaxDrawdown())
                .sharpeRatio(result.getSharpeRatio())
                .sortinoRatio(result.getSortinoRatio())
                .calmarRatio(calmarRatio)
                .totalTrades(result.getTotalTrades())
                .winningTrades(result.getWinningTrades())
                .losingTrades(result.getLosingTrades())
                .winRate(result.getWinRate())
                .avgWin(result.getAvgWin())
                .avgLoss(result.getAvgLoss())
                .profitFactor(result.getProfitFactor())
                .maxWinStreak(result.getMaxWinStreak())
                .maxLossStreak(result.getMaxLossStreak())
                .avgHoldingDays(result.getAvgHoldingDays())
                .trades(tradeDtos)
                .equityLabels(equityLabels)
                .equityCurve(equityCurve)
                .drawdownCurve(drawdownCurve)
                .benchmarkCurve(benchmarkCurve)
                .monthlyPerformance(monthlyPerformance)
                .executedAt(result.getExecutedAt())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    /** 캐시된 JSON 데이터를 리스트로 로드 */
    private <T> List<T> loadCachedList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("캐시 데이터 파싱 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 차트 데이터 실시간 계산 (캐시 미스 시) */
    private void computeChartDataRealtime(
            BacktestResult result,
            List<PriceData> prices,
            List<String> equityLabels,
            List<BigDecimal> equityCurve,
            List<BigDecimal> drawdownCurve,
            List<BigDecimal> benchmarkCurve) {
        // 기존 computeEquityCurves 메서드 재사용
        computeEquityCurves(
                result, prices, equityLabels, equityCurve, drawdownCurve, benchmarkCurve);
    }

    /** 월별 성과 계산 */
    private List<BacktestResultDto.MonthlyPerformance> computeMonthlyPerformance(
            BacktestResult result) {
        Map<String, BacktestResultDto.MonthlyPerformance> monthlyMap = new LinkedHashMap<>();
        for (BacktestTrade trade : result.getTrades()) {
            String month = extractMonth(trade.getExitDate());
            BacktestResultDto.MonthlyPerformance mp =
                    monthlyMap.computeIfAbsent(month, this::createEmptyMonthlyPerformance);
            updateMonthlyPerformance(mp, trade);
        }
        return new ArrayList<>(monthlyMap.values());
    }

    /** 백테스트 히스토리 조회 (요약 정보만) */
    @Transactional(readOnly = true)
    public List<BacktestSummaryDto> getHistory() {
        return backtestResultRepository.findTop20ByOrderByExecutedAtDesc().stream()
                .map(this::convertToSummaryDto)
                .collect(Collectors.toList());
    }

    /** 결과를 요약 DTO로 변환 (목록 조회용 - 차트/거래 데이터 제외) */
    private BacktestSummaryDto convertToSummaryDto(BacktestResult result) {
        return BacktestSummaryDto.builder()
                .id(result.getId())
                .strategyName(result.getStrategyName())
                .strategyType(extractStrategyType(result.getStrategyName()))
                .symbol(result.getSymbol())
                .startDate(result.getStartDate())
                .endDate(result.getEndDate())
                .initialCapital(result.getInitialCapital())
                .finalCapital(result.getFinalCapital())
                .totalReturn(result.getTotalReturn())
                .cagr(result.getCagr())
                .maxDrawdown(result.getMaxDrawdown())
                .sharpeRatio(result.getSharpeRatio())
                .profitFactor(result.getProfitFactor())
                .totalTrades(result.getTotalTrades())
                .winRate(result.getWinRate())
                .executedAt(result.getExecutedAt())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    /** 백테스트 결과 상세 조회 */
    @Transactional(readOnly = true)
    public BacktestResultDto getResult(Long id) {
        BacktestResult result =
                backtestResultRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException("백테스트 결과를 찾을 수 없습니다: " + id));

        // 가격 데이터 재생성 (차트용)
        List<PriceData> prices =
                generateSamplePriceData(
                        result.getSymbol(), result.getStartDate(), result.getEndDate());

        return convertToDto(result, prices);
    }

    /** 사용 가능한 전략 목록 */
    public List<Map<String, Object>> getAvailableStrategies() {
        return Arrays.stream(TradingStrategy.StrategyType.values())
                .map(
                        type -> {
                            Map<String, Object> strategy = new HashMap<>();
                            strategy.put("type", type.name());
                            strategy.put("label", type.getLabel());
                            strategy.put("description", type.getDescription());
                            strategy.put("parameters", getDefaultParameters(type));
                            return strategy;
                        })
                .collect(Collectors.toList());
    }

    /** 전략별 기본 파라미터 */
    private Map<String, Object> getDefaultParameters(TradingStrategy.StrategyType type) {
        Map<String, Object> params = new HashMap<>();
        switch (type) {
            case MOVING_AVERAGE:
                params.put("shortPeriod", 20);
                params.put("longPeriod", 60);
                params.put("maType", "SMA");
                break;
            case RSI:
                params.put("period", 14);
                params.put("overboughtLevel", 70);
                params.put("oversoldLevel", 30);
                break;
            case BOLLINGER_BAND:
                params.put("period", 20);
                params.put("stdDevMultiplier", 2.0);
                break;
            case MOMENTUM:
                params.put("period", 20);
                params.put("entryThreshold", 0.0);
                params.put("exitThreshold", 0.0);
                break;
            case MACD:
                params.put("fastPeriod", 12);
                params.put("slowPeriod", 26);
                params.put("signalPeriod", 9);
                break;
            default:
                break;
        }
        return params;
    }

    // === 전략 최적화 ===

    /** 전략 파라미터 최적화 (그리드 서치) */
    @Transactional
    public OptimizationResultDto optimizeStrategy(OptimizationRequestDto request) {
        long startTime = System.currentTimeMillis();
        log.info("전략 최적화 시작: {} - {}", request.getStrategyType(), request.getSymbol());

        // 1. 파라미터 조합 생성
        List<Map<String, Object>> paramCombinations =
                generateParameterCombinations(request.getParameterRanges());
        log.info("테스트할 파라미터 조합 수: {}", paramCombinations.size());

        // 2. 가격 데이터 미리 조회 (재사용)
        List<PriceData> prices =
                fetchHistoricalPriceData(
                        request.getSymbol(), request.getStartDate(), request.getEndDate());

        // 3. 각 조합에 대해 백테스트 실행 (병렬 처리)
        List<ParameterResult> results = Collections.synchronizedList(new ArrayList<>());
        final int totalCombinations = paramCombinations.size();
        final AtomicInteger completedCount = new AtomicInteger(0);
        final AtomicInteger logInterval =
                new AtomicInteger(Math.max(1, totalCombinations / 10)); // 10% 단위 로깅

        paramCombinations.parallelStream()
                .forEach(
                        params -> {
                            try {
                                BacktestRequestDto backtestRequest =
                                        createBacktestRequestFromOptimization(request, params);
                                TradingStrategy strategy = createStrategy(backtestRequest);
                                BacktestResult result =
                                        executeBacktest(backtestRequest, strategy, prices);

                                BigDecimal targetValue =
                                        getTargetValue(result, request.getTarget());

                                results.add(
                                        ParameterResult.builder()
                                                .parameters(params)
                                                .targetValue(targetValue)
                                                .totalReturn(result.getTotalReturn())
                                                .maxDrawdown(result.getMaxDrawdown())
                                                .sharpeRatio(result.getSharpeRatio())
                                                .profitFactor(result.getProfitFactor())
                                                .totalTrades(result.getTotalTrades())
                                                .winRate(result.getWinRate())
                                                .build());

                                // 진행률 로깅 (10% 단위)
                                int completed = completedCount.incrementAndGet();
                                if (completed % logInterval.get() == 0
                                        || completed == totalCombinations) {
                                    log.info(
                                            "최적화 진행률: {}/{} ({}%)",
                                            completed,
                                            totalCombinations,
                                            String.format(
                                                    "%.0f",
                                                    (double) completed / totalCombinations * 100));
                                }
                            } catch (Exception e) {
                                log.warn("파라미터 조합 테스트 실패: {} - {}", params, e.getMessage());
                                completedCount.incrementAndGet();
                            }
                        });

        if (results.isEmpty()) {
            throw new RuntimeException("최적화 실패: 유효한 결과가 없습니다");
        }

        // 4. 최적 파라미터 선택
        ParameterResult best =
                results.stream()
                        .max(
                                Comparator.comparing(
                                        r ->
                                                r.getTargetValue() != null
                                                        ? r.getTargetValue()
                                                        : BigDecimal.ZERO))
                        .orElseThrow(() -> new RuntimeException("최적 파라미터를 찾을 수 없습니다"));

        // 5. 최적 파라미터로 전체 결과 생성 (저장 포함)
        BacktestRequestDto bestRequest =
                createBacktestRequestFromOptimization(request, best.getParameters());
        BacktestResultDto bestFullResult = runBacktest(bestRequest);

        long executionTime = System.currentTimeMillis() - startTime;
        log.info(
                "최적화 완료: {} 조합 테스트, 최적 수익률 {}%, 실행시간 {}ms",
                results.size(), best.getTotalReturn(), executionTime);

        return OptimizationResultDto.builder()
                .bestParameters(best.getParameters())
                .bestResult(bestFullResult)
                .allResults(results)
                .totalCombinations(paramCombinations.size())
                .executionTimeMs(executionTime)
                .targetType(request.getTarget().name())
                .build();
    }

    /** 파라미터 조합 생성 (카르테시안 곱) */
    private List<Map<String, Object>> generateParameterCombinations(
            Map<String, ParameterRange> ranges) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        combinations.add(new HashMap<>());

        if (ranges == null || ranges.isEmpty()) {
            return combinations;
        }

        for (Map.Entry<String, ParameterRange> entry : ranges.entrySet()) {
            String paramName = entry.getKey();
            ParameterRange range = entry.getValue();

            List<Number> values = generateRangeValues(range);
            List<Map<String, Object>> newCombinations = new ArrayList<>();

            for (Map<String, Object> existing : combinations) {
                for (Number value : values) {
                    Map<String, Object> newCombo = new HashMap<>(existing);
                    newCombo.put(paramName, value);
                    newCombinations.add(newCombo);
                }
            }
            combinations = newCombinations;
        }

        return combinations;
    }

    /** 범위 값 생성 */
    private List<Number> generateRangeValues(ParameterRange range) {
        List<Number> values = new ArrayList<>();

        double min = range.getMin().doubleValue();
        double max = range.getMax().doubleValue();
        double step = range.getStep().doubleValue();

        if (step <= 0) {
            step = 1;
        }

        for (double v = min; v <= max; v += step) {
            // 정수인지 실수인지 판단
            if (step == Math.floor(step) && min == Math.floor(min)) {
                values.add((int) v);
            } else {
                values.add(v);
            }
        }

        return values;
    }

    /** 최적화 요청에서 백테스트 요청 생성 */
    private BacktestRequestDto createBacktestRequestFromOptimization(
            OptimizationRequestDto optRequest, Map<String, Object> params) {
        return BacktestRequestDto.builder()
                .symbol(optRequest.getSymbol())
                .strategyType(optRequest.getStrategyType())
                .strategyParams(params)
                .startDate(optRequest.getStartDate())
                .endDate(optRequest.getEndDate())
                .initialCapital(optRequest.getInitialCapital())
                .positionSizePercent(optRequest.getPositionSizePercent())
                .commissionRate(optRequest.getCommissionRate())
                .slippage(optRequest.getSlippage())
                .build();
    }

    /** 최적화 목표에 따른 값 추출 */
    private BigDecimal getTargetValue(
            BacktestResult result, OptimizationRequestDto.OptimizationTarget target) {
        switch (target) {
            case TOTAL_RETURN:
                return result.getTotalReturn();
            case SHARPE_RATIO:
                return result.getSharpeRatio() != null ? result.getSharpeRatio() : BigDecimal.ZERO;
            case PROFIT_FACTOR:
                return result.getProfitFactor() != null
                        ? result.getProfitFactor()
                        : BigDecimal.ZERO;
            case MIN_DRAWDOWN:
                // MDD는 작을수록 좋으므로 음수로 변환
                return result.getMaxDrawdown() != null
                        ? result.getMaxDrawdown().negate()
                        : BigDecimal.ZERO;
            case CALMAR_RATIO:
                if (result.getMaxDrawdown() != null
                        && result.getMaxDrawdown().compareTo(BigDecimal.ZERO) > 0) {
                    return result.getCagr()
                            .divide(result.getMaxDrawdown(), 4, RoundingMode.HALF_UP);
                }
                return BigDecimal.ZERO;
            default:
                return result.getTotalReturn();
        }
    }

    // === Helper methods ===

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private MovingAverageCrossStrategy.MAType getMAType(
            Map<String, Object> params,
            String key,
            MovingAverageCrossStrategy.MAType defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        return MovingAverageCrossStrategy.MAType.valueOf(value.toString());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** 리스트를 JSON 문자열로 변환 */
    private String toJsonList(Object list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String extractStrategyType(String strategyName) {
        if (strategyName.contains("MA")) return "MOVING_AVERAGE";
        if (strategyName.contains("RSI")) return "RSI";
        if (strategyName.contains("Bollinger")) return "BOLLINGER_BAND";
        if (strategyName.contains("Momentum")) return "MOMENTUM";
        return "CUSTOM";
    }
}
