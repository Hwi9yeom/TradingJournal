package com.trading.journal.service;

import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.entity.SentimentZone;
import java.math.BigDecimal;

/**
 * 심리 지표 값 → 판정 구간 변환 규칙.
 *
 * <p>지표마다 스케일과 방향이 다르다. 예를 들어 풋/콜 비율은 높을수록 공포이고, NAAIM 비중은 높을수록 탐욕이다. 이 클래스가 그 차이를 흡수해 모든 지표를
 * {@link SentimentZone} 하나의 척도로 정규화한다.
 *
 * <p>임계값은 각 지표 제공처가 공표한 해석 기준을 따른다. 상태를 갖지 않는 순수 함수만 두어 테스트로 경계를 고정한다.
 */
public final class SentimentEvaluator {

    private SentimentEvaluator() {}

    /**
     * 지표 값을 판정 구간으로 변환한다.
     *
     * @param indicator 지표
     * @param value 기록된 값
     * @return 판정 구간
     * @throws IllegalArgumentException 지표나 값이 null인 경우
     */
    public static SentimentZone evaluate(SentimentIndicator indicator, BigDecimal value) {
        if (indicator == null) {
            throw new IllegalArgumentException("지표(indicator)는 필수입니다");
        }
        if (value == null) {
            throw new IllegalArgumentException("지표 값(value)은 필수입니다");
        }

        validateRange(indicator, value);

        double v = value.doubleValue();

        return switch (indicator) {
            case FEAR_GREED_INDEX, CRYPTO_FEAR_GREED, RHODL_RATIO -> zeroToHundred(v);
            case PUT_CALL_RATIO -> putCallRatio(v);
            case NAAIM_EXPOSURE -> naaimExposure(v);
            case AAII_BULLISH -> aaiiBullish(v);
            case AAII_BEARISH -> aaiiBearish(v);
            case SMART_DUMB_MONEY -> smartDumbSpread(v);
            case FUNDING_RATE -> fundingRate(v);
            case LONG_SHORT_RATIO -> longShortRatio(v);
            case MVRV_Z_SCORE -> mvrvZScore(v);
            case PUELL_MULTIPLE -> puellMultiple(v);
        };
    }

    /**
     * 지표별 허용 범위 검증. 범위 밖 값(예: 0-100 지표에 900)은 입력 오류이므로 저장 전에 거부한다.
     *
     * @throws IllegalArgumentException 값이 지표의 허용 범위를 벗어난 경우
     */
    public static void validateRange(SentimentIndicator indicator, BigDecimal value) {
        double v = value.doubleValue();
        double min;
        double max;
        switch (indicator) {
            case FEAR_GREED_INDEX,
                    CRYPTO_FEAR_GREED,
                    RHODL_RATIO,
                    NAAIM_EXPOSURE,
                    AAII_BULLISH,
                    AAII_BEARISH -> {
                min = 0;
                max = 100;
            }
            case SMART_DUMB_MONEY -> {
                min = -100;
                max = 100;
            }
            case PUT_CALL_RATIO -> {
                min = 0;
                max = 5;
            }
            case LONG_SHORT_RATIO -> {
                min = 0;
                max = 10;
            }
            case FUNDING_RATE -> {
                min = -5;
                max = 5;
            }
            case MVRV_Z_SCORE -> {
                min = -10;
                max = 20;
            }
            case PUELL_MULTIPLE -> {
                min = 0;
                max = 20;
            }
            default -> throw new IllegalArgumentException("알 수 없는 지표: " + indicator);
        }
        if (v < min || v > max) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s 값이 허용 범위(%s~%s)를 벗어났습니다: %s",
                            indicator.getLabel(), min, max, value));
        }
    }

    /** 0-100 스케일 지표 (공포·탐욕 지수, RHODL 밴드 위치): 낮을수록 공포. */
    private static SentimentZone zeroToHundred(double v) {
        if (v <= 25) {
            return SentimentZone.EXTREME_FEAR;
        }
        if (v <= 45) {
            return SentimentZone.FEAR;
        }
        if (v < 55) {
            return SentimentZone.NEUTRAL;
        }
        if (v < 75) {
            return SentimentZone.GREED;
        }
        return SentimentZone.EXTREME_GREED;
    }

    /** 풋/콜 비율: 1.0 이상 공포 과도(반등 시그널), 0.7 이하 낙관 과도(하락 경계). */
    private static SentimentZone putCallRatio(double v) {
        if (v >= 1.0) {
            return SentimentZone.EXTREME_FEAR;
        }
        if (v >= 0.85) {
            return SentimentZone.FEAR;
        }
        if (v > 0.7) {
            return SentimentZone.NEUTRAL;
        }
        if (v > 0.6) {
            return SentimentZone.GREED;
        }
        return SentimentZone.EXTREME_GREED;
    }

    /** NAAIM 기관 포지션 비중: 90 이상 풀베팅 과열, 30 이하 공포, 10 이하 극단적 공포. */
    private static SentimentZone naaimExposure(double v) {
        if (v >= 90) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 70) {
            return SentimentZone.GREED;
        }
        if (v > 30) {
            return SentimentZone.NEUTRAL;
        }
        if (v > 10) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }

    /** AAII 강세 비율: 50% 초과면 과도한 낙관 경계. */
    private static SentimentZone aaiiBullish(double v) {
        if (v > 50) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 40) {
            return SentimentZone.GREED;
        }
        if (v >= 30) {
            return SentimentZone.NEUTRAL;
        }
        if (v >= 20) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }

    /** AAII 약세 비율: 50% 초과면 시장 바닥 가능성. */
    private static SentimentZone aaiiBearish(double v) {
        if (v > 50) {
            return SentimentZone.EXTREME_FEAR;
        }
        if (v >= 40) {
            return SentimentZone.FEAR;
        }
        if (v >= 30) {
            return SentimentZone.NEUTRAL;
        }
        if (v >= 20) {
            return SentimentZone.GREED;
        }
        return SentimentZone.EXTREME_GREED;
    }

    /**
     * 스마트머니-개인 스프레드(Smart minus Dumb).
     *
     * <p>양수 = 스마트머니가 개인보다 강세 = 개인은 비관적 → 역발상 저점 신호(공포 구간). 음수 = 개인이 스마트머니보다 강세 = 개인 과열 → 고점 경계(탐욕
     * 구간).
     */
    private static SentimentZone smartDumbSpread(double v) {
        if (v >= 30) {
            return SentimentZone.EXTREME_FEAR;
        }
        if (v >= 10) {
            return SentimentZone.FEAR;
        }
        if (v > -10) {
            return SentimentZone.NEUTRAL;
        }
        if (v > -30) {
            return SentimentZone.GREED;
        }
        return SentimentZone.EXTREME_GREED;
    }

    /** 펀딩 비율(%): 기준 펀딩비 0.01%를 중립으로 본다. 양(+)이 높으면 롱 과열, 음(-)이 크면 숏 과열(반등 가능성). */
    private static SentimentZone fundingRate(double v) {
        if (v >= 0.05) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 0.02) {
            return SentimentZone.GREED;
        }
        if (v > -0.01) {
            return SentimentZone.NEUTRAL;
        }
        if (v > -0.05) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }

    /** 롱/숏 비율: 롱 쏠림이 심하면 롱 청산 위험, 숏 쏠림이 심하면 반등 가능성. */
    private static SentimentZone longShortRatio(double v) {
        if (v >= 2.0) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 1.3) {
            return SentimentZone.GREED;
        }
        if (v > 0.77) {
            return SentimentZone.NEUTRAL;
        }
        if (v > 0.5) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }

    /** MVRV Z-Score: 7 초과 과열, 0 미만 저점 신호. */
    private static SentimentZone mvrvZScore(double v) {
        if (v >= 7) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 4) {
            return SentimentZone.GREED;
        }
        if (v >= 1) {
            return SentimentZone.NEUTRAL;
        }
        if (v >= 0) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }

    /** Puell Multiple: 1 이하 바닥 신호, 5 이상 매도 위험. */
    private static SentimentZone puellMultiple(double v) {
        if (v >= 5) {
            return SentimentZone.EXTREME_GREED;
        }
        if (v >= 2.5) {
            return SentimentZone.GREED;
        }
        if (v > 1.5) {
            return SentimentZone.NEUTRAL;
        }
        if (v > 1.0) {
            return SentimentZone.FEAR;
        }
        return SentimentZone.EXTREME_FEAR;
    }
}
