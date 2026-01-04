package com.trading.journal.strategy.impl;

import com.trading.journal.strategy.TradingStrategy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이동평균 교차 전략
 * - 골든크로스 (단기 MA > 장기 MA): 매수
 * - 데드크로스 (단기 MA < 장기 MA): 매도
 */
@Data
@Builder
public class MovingAverageCrossStrategy implements TradingStrategy {

    /** 단기 이동평균 기간 */
    @Builder.Default
    private int shortPeriod = 20;

    /** 장기 이동평균 기간 */
    @Builder.Default
    private int longPeriod = 60;

    /** 이동평균 유형 (SMA, EMA) */
    @Builder.Default
    private MAType maType = MAType.SMA;

    public enum MAType {
        SMA, EMA
    }

    @Override
    public Signal generateSignal(List<PriceData> prices, int index) {
        if (index < longPeriod) {
            return Signal.HOLD;
        }

        BigDecimal shortMA = calculateMA(prices, index, shortPeriod);
        BigDecimal longMA = calculateMA(prices, index, longPeriod);

        BigDecimal prevShortMA = calculateMA(prices, index - 1, shortPeriod);
        BigDecimal prevLongMA = calculateMA(prices, index - 1, longPeriod);

        // 골든크로스: 이전에 단기 < 장기였다가 현재 단기 > 장기
        boolean goldenCross = prevShortMA.compareTo(prevLongMA) <= 0 &&
                              shortMA.compareTo(longMA) > 0;

        // 데드크로스: 이전에 단기 > 장기였다가 현재 단기 < 장기
        boolean deadCross = prevShortMA.compareTo(prevLongMA) >= 0 &&
                            shortMA.compareTo(longMA) < 0;

        if (goldenCross) {
            return Signal.BUY;
        } else if (deadCross) {
            return Signal.SELL;
        }

        return Signal.HOLD;
    }

    /**
     * 이동평균 계산
     */
    private BigDecimal calculateMA(List<PriceData> prices, int index, int period) {
        if (maType == MAType.EMA) {
            return calculateEMA(prices, index, period);
        }
        return calculateSMA(prices, index, period);
    }

    /**
     * 단순이동평균 (SMA) 계산
     */
    private BigDecimal calculateSMA(List<PriceData> prices, int index, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            sum = sum.add(prices.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    /**
     * 지수이동평균 (EMA) 계산
     */
    private BigDecimal calculateEMA(List<PriceData> prices, int index, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        // 첫 번째 EMA는 SMA로 계산
        BigDecimal ema = calculateSMA(prices, period - 1, period);

        for (int i = period; i <= index; i++) {
            BigDecimal close = prices.get(i).getClose();
            ema = close.subtract(ema).multiply(multiplier).add(ema);
        }

        return ema;
    }

    @Override
    public String getName() {
        return String.format("MA Cross (%d/%d %s)", shortPeriod, longPeriod, maType);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.MOVING_AVERAGE;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", shortPeriod);
        params.put("longPeriod", longPeriod);
        params.put("maType", maType.name());
        return params;
    }

    @Override
    public String getDescription() {
        return String.format("%d일 이동평균이 %d일 이동평균을 상향 돌파하면 매수, 하향 돌파하면 매도",
                shortPeriod, longPeriod);
    }

    @Override
    public int getMinimumDataPoints() {
        return longPeriod + 1;
    }
}
