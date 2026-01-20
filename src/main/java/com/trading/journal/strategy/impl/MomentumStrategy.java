package com.trading.journal.strategy.impl;

import com.trading.journal.strategy.TradingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 모멘텀 전략 - N일 수익률이 양수로 전환: 매수 - N일 수익률이 음수로 전환: 매도 */
@Data
@Builder
public class MomentumStrategy implements TradingStrategy {

    /** 모멘텀 기간 */
    @Builder.Default private int period = 20;

    /** 매수 진입 임계값 (%) */
    @Builder.Default private double entryThreshold = 0.0;

    /** 매도 청산 임계값 (%) */
    @Builder.Default private double exitThreshold = 0.0;

    @Override
    public Signal generateSignal(List<PriceData> prices, int index) {
        if (index < period + 1) {
            return Signal.HOLD;
        }

        BigDecimal currentMomentum = calculateMomentum(prices, index);
        BigDecimal prevMomentum = calculateMomentum(prices, index - 1);

        BigDecimal entryLevel = BigDecimal.valueOf(entryThreshold);
        BigDecimal exitLevel = BigDecimal.valueOf(exitThreshold);

        // 모멘텀이 임계값을 상향 돌파: 매수
        if (prevMomentum.compareTo(entryLevel) <= 0 && currentMomentum.compareTo(entryLevel) > 0) {
            return Signal.BUY;
        }

        // 모멘텀이 임계값을 하향 돌파: 매도
        if (prevMomentum.compareTo(exitLevel) >= 0 && currentMomentum.compareTo(exitLevel) < 0) {
            return Signal.SELL;
        }

        return Signal.HOLD;
    }

    /** 모멘텀 계산 (N일 수익률 %) */
    private BigDecimal calculateMomentum(List<PriceData> prices, int index) {
        BigDecimal currentPrice = prices.get(index).getClose();
        BigDecimal pastPrice = prices.get(index - period).getClose();

        if (pastPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentPrice
                .subtract(pastPrice)
                .divide(pastPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    @Override
    public String getName() {
        return String.format("Momentum (%d days)", period);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.MOMENTUM;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("period", period);
        params.put("entryThreshold", entryThreshold);
        params.put("exitThreshold", exitThreshold);
        return params;
    }

    @Override
    public String getDescription() {
        return String.format(
                "%d일 모멘텀(수익률)이 %.1f%% 돌파 시 매수, %.1f%% 하향 돌파 시 매도",
                period, entryThreshold, exitThreshold);
    }

    @Override
    public int getMinimumDataPoints() {
        return period + 2;
    }
}
