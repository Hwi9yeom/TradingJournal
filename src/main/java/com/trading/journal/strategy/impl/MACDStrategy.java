package com.trading.journal.strategy.impl;

import com.trading.journal.strategy.TradingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * MACD (Moving Average Convergence Divergence) 전략 - MACD Line = EMA(fast) - EMA(slow) - Signal Line
 * = EMA(signal) of MACD Line - Histogram = MACD Line - Signal Line
 *
 * <p>매매 시그널: - 골든크로스 (MACD > Signal): 매수 - 데드크로스 (MACD < Signal): 매도
 */
@Data
@Builder
public class MACDStrategy implements TradingStrategy {

    /** 단기 EMA 기간 (기본 12일) */
    @Builder.Default private int fastPeriod = 12;

    /** 장기 EMA 기간 (기본 26일) */
    @Builder.Default private int slowPeriod = 26;

    /** 시그널 EMA 기간 (기본 9일) */
    @Builder.Default private int signalPeriod = 9;

    @Override
    public Signal generateSignal(List<PriceData> prices, int index) {
        // 충분한 데이터가 필요
        if (index < getMinimumDataPoints()) {
            return Signal.HOLD;
        }

        // 현재 MACD와 시그널 계산
        BigDecimal currentMACD = calculateMACD(prices, index);
        BigDecimal currentSignal = calculateSignalLine(prices, index);

        // 이전 MACD와 시그널 계산
        BigDecimal prevMACD = calculateMACD(prices, index - 1);
        BigDecimal prevSignal = calculateSignalLine(prices, index - 1);

        // 골든크로스: 이전에 MACD < Signal 이었다가 현재 MACD > Signal
        boolean goldenCross =
                prevMACD.compareTo(prevSignal) <= 0 && currentMACD.compareTo(currentSignal) > 0;

        // 데드크로스: 이전에 MACD > Signal 이었다가 현재 MACD < Signal
        boolean deadCross =
                prevMACD.compareTo(prevSignal) >= 0 && currentMACD.compareTo(currentSignal) < 0;

        if (goldenCross) {
            return Signal.BUY;
        } else if (deadCross) {
            return Signal.SELL;
        }

        return Signal.HOLD;
    }

    /** MACD Line 계산 (EMA(fast) - EMA(slow)) */
    private BigDecimal calculateMACD(List<PriceData> prices, int index) {
        BigDecimal fastEMA = calculateEMA(prices, index, fastPeriod);
        BigDecimal slowEMA = calculateEMA(prices, index, slowPeriod);
        return fastEMA.subtract(slowEMA);
    }

    /** Signal Line 계산 (MACD의 EMA) */
    private BigDecimal calculateSignalLine(List<PriceData> prices, int index) {
        // 시그널 라인을 계산하려면 최소 signalPeriod 개의 MACD 값이 필요
        if (index < slowPeriod + signalPeriod - 1) {
            return calculateMACD(prices, index);
        }

        // MACD 값들의 EMA 계산
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (signalPeriod + 1));

        // 첫 번째 Signal은 MACD의 SMA
        BigDecimal signal = BigDecimal.ZERO;
        for (int i = 0; i < signalPeriod; i++) {
            signal = signal.add(calculateMACD(prices, slowPeriod - 1 + i));
        }
        signal = signal.divide(BigDecimal.valueOf(signalPeriod), 6, RoundingMode.HALF_UP);

        // 이후 EMA 계산
        for (int i = slowPeriod - 1 + signalPeriod; i <= index; i++) {
            BigDecimal macd = calculateMACD(prices, i);
            signal = macd.subtract(signal).multiply(multiplier).add(signal);
        }

        return signal;
    }

    /** EMA (지수이동평균) 계산 */
    private BigDecimal calculateEMA(List<PriceData> prices, int index, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        // 첫 번째 EMA는 SMA로 계산
        BigDecimal ema = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            ema = ema.add(prices.get(i).getClose());
        }
        ema = ema.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);

        // 이후 EMA 계산
        for (int i = period; i <= index; i++) {
            BigDecimal close = prices.get(i).getClose();
            ema = close.subtract(ema).multiply(multiplier).add(ema);
        }

        return ema;
    }

    @Override
    public String getName() {
        return String.format("MACD (%d/%d/%d)", fastPeriod, slowPeriod, signalPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.MACD;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("fastPeriod", fastPeriod);
        params.put("slowPeriod", slowPeriod);
        params.put("signalPeriod", signalPeriod);
        return params;
    }

    @Override
    public String getDescription() {
        return String.format(
                "MACD(%d, %d)와 Signal(%d) 교차 시 매매. 골든크로스 매수, 데드크로스 매도",
                fastPeriod, slowPeriod, signalPeriod);
    }

    @Override
    public int getMinimumDataPoints() {
        return slowPeriod + signalPeriod;
    }
}
