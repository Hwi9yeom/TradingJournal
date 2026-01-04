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
 * 볼린저 밴드 전략
 * - 하단 밴드 터치 후 반등: 매수
 * - 상단 밴드 터치 후 하락: 매도
 */
@Data
@Builder
public class BollingerBandStrategy implements TradingStrategy {

    /** 이동평균 기간 */
    @Builder.Default
    private int period = 20;

    /** 표준편차 배수 */
    @Builder.Default
    private double stdDevMultiplier = 2.0;

    @Override
    public Signal generateSignal(List<PriceData> prices, int index) {
        if (index < period) {
            return Signal.HOLD;
        }

        BollingerBands currentBands = calculateBands(prices, index);
        BollingerBands prevBands = calculateBands(prices, index - 1);

        BigDecimal currentClose = prices.get(index).getClose();
        BigDecimal prevClose = prices.get(index - 1).getClose();

        // 하단 밴드 터치 후 반등 (이전 종가가 하단 밴드 이하, 현재 종가가 하단 밴드 이상)
        boolean lowerBandBounce = prevClose.compareTo(prevBands.lowerBand) <= 0 &&
                                  currentClose.compareTo(currentBands.lowerBand) > 0;

        // 상단 밴드 터치 후 하락 (이전 종가가 상단 밴드 이상, 현재 종가가 상단 밴드 이하)
        boolean upperBandReject = prevClose.compareTo(prevBands.upperBand) >= 0 &&
                                  currentClose.compareTo(currentBands.upperBand) < 0;

        if (lowerBandBounce) {
            return Signal.BUY;
        } else if (upperBandReject) {
            return Signal.SELL;
        }

        return Signal.HOLD;
    }

    /**
     * 볼린저 밴드 계산
     */
    private BollingerBands calculateBands(List<PriceData> prices, int index) {
        // SMA 계산
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            sum = sum.add(prices.get(i).getClose());
        }
        BigDecimal sma = sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);

        // 표준편차 계산
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal diff = prices.get(i).getClose().subtract(sma);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        // 밴드 계산
        BigDecimal bandWidth = stdDev.multiply(BigDecimal.valueOf(stdDevMultiplier));

        return new BollingerBands(
                sma,
                sma.add(bandWidth),
                sma.subtract(bandWidth),
                stdDev
        );
    }

    @Override
    public String getName() {
        return String.format("Bollinger Band (%d, %.1f)", period, stdDevMultiplier);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.BOLLINGER_BAND;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("period", period);
        params.put("stdDevMultiplier", stdDevMultiplier);
        return params;
    }

    @Override
    public String getDescription() {
        return String.format("%d일 볼린저 밴드 (%.1f σ) 기준, 하단 밴드 반등 시 매수, 상단 밴드 하락 시 매도",
                period, stdDevMultiplier);
    }

    @Override
    public int getMinimumDataPoints() {
        return period + 1;
    }

    /**
     * 볼린저 밴드 데이터
     */
    @Data
    private static class BollingerBands {
        private final BigDecimal middleBand;
        private final BigDecimal upperBand;
        private final BigDecimal lowerBand;
        private final BigDecimal standardDeviation;

        public BollingerBands(BigDecimal middleBand, BigDecimal upperBand,
                              BigDecimal lowerBand, BigDecimal standardDeviation) {
            this.middleBand = middleBand;
            this.upperBand = upperBand;
            this.lowerBand = lowerBand;
            this.standardDeviation = standardDeviation;
        }
    }
}
