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
 * RSI (상대강도지수) 전략
 * - RSI가 과매도 구간에서 반등: 매수
 * - RSI가 과매수 구간에서 하락: 매도
 */
@Data
@Builder
public class RSIStrategy implements TradingStrategy {

    /** RSI 기간 */
    @Builder.Default
    private int period = 14;

    /** 과매수 기준 */
    @Builder.Default
    private int overboughtLevel = 70;

    /** 과매도 기준 */
    @Builder.Default
    private int oversoldLevel = 30;

    @Override
    public Signal generateSignal(List<PriceData> prices, int index) {
        if (index < period + 1) {
            return Signal.HOLD;
        }

        BigDecimal currentRSI = calculateRSI(prices, index);
        BigDecimal prevRSI = calculateRSI(prices, index - 1);

        BigDecimal overbought = BigDecimal.valueOf(overboughtLevel);
        BigDecimal oversold = BigDecimal.valueOf(oversoldLevel);

        // 과매도 구간에서 반등 (RSI가 oversold 아래였다가 위로)
        if (prevRSI.compareTo(oversold) < 0 && currentRSI.compareTo(oversold) >= 0) {
            return Signal.BUY;
        }

        // 과매수 구간에서 하락 (RSI가 overbought 위였다가 아래로)
        if (prevRSI.compareTo(overbought) > 0 && currentRSI.compareTo(overbought) <= 0) {
            return Signal.SELL;
        }

        return Signal.HOLD;
    }

    /**
     * RSI 계산
     */
    private BigDecimal calculateRSI(List<PriceData> prices, int index) {
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // 첫 번째 평균 계산
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal change = prices.get(i).getClose().subtract(prices.get(i - 1).getClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }

        avgGain = avgGain.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 6, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
        );

        return rsi;
    }

    @Override
    public String getName() {
        return String.format("RSI (%d, %d/%d)", period, oversoldLevel, overboughtLevel);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.RSI;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("period", period);
        params.put("overboughtLevel", overboughtLevel);
        params.put("oversoldLevel", oversoldLevel);
        return params;
    }

    @Override
    public String getDescription() {
        return String.format("RSI %d일 기준, %d 이하에서 반등 시 매수, %d 이상에서 하락 시 매도",
                period, oversoldLevel, overboughtLevel);
    }

    @Override
    public int getMinimumDataPoints() {
        return period + 2;
    }
}
