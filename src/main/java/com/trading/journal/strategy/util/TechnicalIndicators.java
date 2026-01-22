package com.trading.journal.strategy.util;

import com.trading.journal.strategy.TradingStrategy.PriceData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 기술적 지표 계산을 위한 유틸리티 클래스. SMA, EMA 등 공통 계산 로직을 제공합니다. */
public final class TechnicalIndicators {

    /** 기본 계산 스케일 */
    public static final int DEFAULT_SCALE = 6;

    /** 가격 표시 스케일 */
    public static final int PRICE_SCALE = 4;

    /** 퍼센트 계산용 상수 */
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TechnicalIndicators() {
        // 유틸리티 클래스
    }

    /**
     * 단순이동평균 (SMA) 계산
     *
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @param period 이동평균 기간
     * @return SMA 값
     */
    public static BigDecimal calculateSMA(List<PriceData> prices, int index, int period) {
        return calculateSMA(prices, index, period, DEFAULT_SCALE);
    }

    /**
     * 단순이동평균 (SMA) 계산 (스케일 지정)
     *
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @param period 이동평균 기간
     * @param scale 소수점 스케일
     * @return SMA 값
     */
    public static BigDecimal calculateSMA(
            List<PriceData> prices, int index, int period, int scale) {
        if (index < period - 1) {
            throw new IllegalArgumentException(
                    String.format("Insufficient data: index=%d, period=%d", index, period));
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            sum = sum.add(prices.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), scale, RoundingMode.HALF_UP);
    }

    /**
     * 지수이동평균 (EMA) 계산
     *
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @param period 이동평균 기간
     * @return EMA 값
     */
    public static BigDecimal calculateEMA(List<PriceData> prices, int index, int period) {
        return calculateEMA(prices, index, period, DEFAULT_SCALE);
    }

    /**
     * 지수이동평균 (EMA) 계산 (스케일 지정)
     *
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @param period 이동평균 기간
     * @param scale 소수점 스케일
     * @return EMA 값
     */
    public static BigDecimal calculateEMA(
            List<PriceData> prices, int index, int period, int scale) {
        if (index < period - 1) {
            throw new IllegalArgumentException(
                    String.format("Insufficient data: index=%d, period=%d", index, period));
        }

        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        // 첫 번째 EMA는 SMA로 계산
        BigDecimal ema = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            ema = ema.add(prices.get(i).getClose());
        }
        ema = ema.divide(BigDecimal.valueOf(period), scale, RoundingMode.HALF_UP);

        // 이후 EMA 계산
        for (int i = period; i <= index; i++) {
            BigDecimal close = prices.get(i).getClose();
            ema = close.subtract(ema).multiply(multiplier).add(ema);
        }

        return ema;
    }

    /**
     * 표준편차 계산
     *
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @param period 기간
     * @param mean 평균값
     * @return 표준편차
     */
    public static BigDecimal calculateStandardDeviation(
            List<PriceData> prices, int index, int period, BigDecimal mean) {
        BigDecimal variance = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal diff = prices.get(i).getClose().subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(period), DEFAULT_SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    /**
     * 크로스오버 감지 - 상향 돌파
     *
     * @param prevValue 이전 값
     * @param currentValue 현재 값
     * @param threshold 기준선
     * @return 상향 돌파 여부
     */
    public static boolean crossedAbove(
            BigDecimal prevValue, BigDecimal currentValue, BigDecimal threshold) {
        return prevValue.compareTo(threshold) <= 0 && currentValue.compareTo(threshold) > 0;
    }

    /**
     * 크로스오버 감지 - 하향 돌파
     *
     * @param prevValue 이전 값
     * @param currentValue 현재 값
     * @param threshold 기준선
     * @return 하향 돌파 여부
     */
    public static boolean crossedBelow(
            BigDecimal prevValue, BigDecimal currentValue, BigDecimal threshold) {
        return prevValue.compareTo(threshold) >= 0 && currentValue.compareTo(threshold) < 0;
    }

    /**
     * 두 지표의 골든크로스 감지
     *
     * @param prevFast 이전 빠른 지표
     * @param currentFast 현재 빠른 지표
     * @param prevSlow 이전 느린 지표
     * @param currentSlow 현재 느린 지표
     * @return 골든크로스 여부
     */
    public static boolean isGoldenCross(
            BigDecimal prevFast,
            BigDecimal currentFast,
            BigDecimal prevSlow,
            BigDecimal currentSlow) {
        return prevFast.compareTo(prevSlow) <= 0 && currentFast.compareTo(currentSlow) > 0;
    }

    /**
     * 두 지표의 데드크로스 감지
     *
     * @param prevFast 이전 빠른 지표
     * @param currentFast 현재 빠른 지표
     * @param prevSlow 이전 느린 지표
     * @param currentSlow 현재 느린 지표
     * @return 데드크로스 여부
     */
    public static boolean isDeadCross(
            BigDecimal prevFast,
            BigDecimal currentFast,
            BigDecimal prevSlow,
            BigDecimal currentSlow) {
        return prevFast.compareTo(prevSlow) >= 0 && currentFast.compareTo(currentSlow) < 0;
    }
}
