package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.entity.SentimentZone;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 심리 지표 임계값 회귀 테스트.
 *
 * <p>각 지표의 경계값을 고정한다. 임계값은 지표 제공처의 공표 해석 기준이므로, 여기가 깨지면 판정 규칙이 바뀐 것이다.
 */
@DisplayName("심리 지표 임계값 평가 테스트")
class SentimentEvaluatorTest {

    private static SentimentZone eval(SentimentIndicator indicator, String value) {
        return SentimentEvaluator.evaluate(indicator, new BigDecimal(value));
    }

    @Nested
    @DisplayName("0-100 스케일 지표")
    class ZeroToHundredTests {

        @ParameterizedTest(name = "공포·탐욕 지수 {0} → {1}")
        @CsvSource({
            "0, EXTREME_FEAR",
            "25, EXTREME_FEAR",
            "26, FEAR",
            "45, FEAR",
            "46, NEUTRAL",
            "54, NEUTRAL",
            "55, GREED",
            "74, GREED",
            "75, EXTREME_GREED",
            "100, EXTREME_GREED"
        })
        void fearGreedIndex_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.FEAR_GREED_INDEX, value)).isEqualTo(expected);
        }

        @Test
        @DisplayName("암호화폐 공포·탐욕 지수도 같은 스케일을 쓴다")
        void cryptoFearGreed_UsesSameScale() {
            assertThat(eval(SentimentIndicator.CRYPTO_FEAR_GREED, "20"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
            assertThat(eval(SentimentIndicator.CRYPTO_FEAR_GREED, "80"))
                    .isEqualTo(SentimentZone.EXTREME_GREED);
        }

        @Test
        @DisplayName("RHODL 밴드 위치도 0-100으로 환산해 판정한다")
        void rhodlRatio_UsesBandPosition() {
            assertThat(eval(SentimentIndicator.RHODL_RATIO, "10"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
            assertThat(eval(SentimentIndicator.RHODL_RATIO, "90"))
                    .isEqualTo(SentimentZone.EXTREME_GREED);
        }
    }

    @Nested
    @DisplayName("미국 주식 지표")
    class UsStockTests {

        @ParameterizedTest(name = "풋/콜 비율 {0} → {1}")
        @CsvSource({
            "1.20, EXTREME_FEAR",
            "1.00, EXTREME_FEAR",
            "0.99, FEAR",
            "0.85, FEAR",
            "0.84, NEUTRAL",
            "0.71, NEUTRAL",
            "0.70, GREED",
            "0.61, GREED",
            "0.60, EXTREME_GREED",
            "0.55, EXTREME_GREED"
        })
        void putCallRatio_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.PUT_CALL_RATIO, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "NAAIM {0} → {1}")
        @CsvSource({
            "95, EXTREME_GREED",
            "90, EXTREME_GREED",
            "89, GREED",
            "70, GREED",
            "69, NEUTRAL",
            "31, NEUTRAL",
            "30, FEAR",
            "11, FEAR",
            "10, EXTREME_FEAR",
            "0, EXTREME_FEAR"
        })
        void naaimExposure_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.NAAIM_EXPOSURE, value)).isEqualTo(expected);
        }

        @Test
        @DisplayName("AAII 강세 50% 초과는 과도한 낙관 경계")
        void aaiiBullish_AboveFiftyIsExtremeGreed() {
            assertThat(eval(SentimentIndicator.AAII_BULLISH, "50.1"))
                    .isEqualTo(SentimentZone.EXTREME_GREED);
            assertThat(eval(SentimentIndicator.AAII_BULLISH, "50")).isEqualTo(SentimentZone.GREED);
            assertThat(eval(SentimentIndicator.AAII_BULLISH, "15"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
        }

        @Test
        @DisplayName("AAII 약세 50% 초과는 바닥 가능성")
        void aaiiBearish_AboveFiftyIsExtremeFear() {
            assertThat(eval(SentimentIndicator.AAII_BEARISH, "50.1"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
            assertThat(eval(SentimentIndicator.AAII_BEARISH, "50")).isEqualTo(SentimentZone.FEAR);
            assertThat(eval(SentimentIndicator.AAII_BEARISH, "15"))
                    .isEqualTo(SentimentZone.EXTREME_GREED);
        }

        @Test
        @DisplayName("같은 값이라도 강세/약세 비율은 반대 방향으로 해석된다")
        void aaiiBullishAndBearish_AreOpposite() {
            assertThat(eval(SentimentIndicator.AAII_BULLISH, "55"))
                    .isEqualTo(SentimentZone.EXTREME_GREED);
            assertThat(eval(SentimentIndicator.AAII_BEARISH, "55"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
        }

        @ParameterizedTest(name = "스마트머니 스프레드 {0} → {1}")
        @CsvSource({
            "40, EXTREME_GREED",
            "30, EXTREME_GREED",
            "29, GREED",
            "10, GREED",
            "9, NEUTRAL",
            "-9, NEUTRAL",
            "-10, FEAR",
            "-29, FEAR",
            "-30, EXTREME_FEAR"
        })
        void smartDumbMoney_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.SMART_DUMB_MONEY, value)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("암호화폐 지표")
    class CryptoTests {

        @ParameterizedTest(name = "펀딩 비율 {0}% → {1}")
        @CsvSource({
            "0.10, EXTREME_GREED",
            "0.05, EXTREME_GREED",
            "0.04, GREED",
            "0.02, GREED",
            "0.01, NEUTRAL",
            "0, NEUTRAL",
            "-0.01, FEAR",
            "-0.04, FEAR",
            "-0.05, EXTREME_FEAR"
        })
        void fundingRate_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.FUNDING_RATE, value)).isEqualTo(expected);
        }

        @Test
        @DisplayName("음(-) 펀딩비 과열은 숏 과열이라 반등 가능성으로 읽는다")
        void fundingRate_NegativeExtremeIsFear() {
            assertThat(eval(SentimentIndicator.FUNDING_RATE, "-0.08"))
                    .isEqualTo(SentimentZone.EXTREME_FEAR);
        }

        @ParameterizedTest(name = "롱/숏 비율 {0} → {1}")
        @CsvSource({
            "2.5, EXTREME_GREED",
            "2.0, EXTREME_GREED",
            "1.9, GREED",
            "1.3, GREED",
            "1.0, NEUTRAL",
            "0.78, NEUTRAL",
            "0.77, FEAR",
            "0.51, FEAR",
            "0.50, EXTREME_FEAR"
        })
        void longShortRatio_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.LONG_SHORT_RATIO, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "MVRV Z-Score {0} → {1}")
        @CsvSource({
            "8, EXTREME_GREED",
            "7, EXTREME_GREED",
            "6.9, GREED",
            "4, GREED",
            "3.9, NEUTRAL",
            "1, NEUTRAL",
            "0.9, FEAR",
            "0, FEAR",
            "-0.1, EXTREME_FEAR"
        })
        void mvrvZScore_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.MVRV_Z_SCORE, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "Puell Multiple {0} → {1}")
        @CsvSource({
            "6, EXTREME_GREED",
            "5, EXTREME_GREED",
            "4.9, GREED",
            "2.5, GREED",
            "2.4, NEUTRAL",
            "1.6, NEUTRAL",
            "1.5, FEAR",
            "1.1, FEAR",
            "1.0, EXTREME_FEAR",
            "0.6, EXTREME_FEAR"
        })
        void puellMultiple_Boundaries(String value, SentimentZone expected) {
            assertThat(eval(SentimentIndicator.PUELL_MULTIPLE, value)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("정규화 및 입력 검증")
    class NormalizationTests {

        @Test
        @DisplayName("모든 지표는 값이 주어지면 구간을 반환한다")
        void everyIndicator_IsEvaluable() {
            for (SentimentIndicator indicator : SentimentIndicator.values()) {
                assertThat(SentimentEvaluator.evaluate(indicator, new BigDecimal("1")))
                        .as("지표 %s 평가 누락", indicator)
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("구간 점수는 공포가 음수, 탐욕이 양수다")
        void zoneScores_AreSignedByDirection() {
            assertThat(SentimentZone.EXTREME_FEAR.getScore()).isEqualTo(-2);
            assertThat(SentimentZone.FEAR.getScore()).isEqualTo(-1);
            assertThat(SentimentZone.NEUTRAL.getScore()).isZero();
            assertThat(SentimentZone.GREED.getScore()).isEqualTo(1);
            assertThat(SentimentZone.EXTREME_GREED.getScore()).isEqualTo(2);
        }

        @ParameterizedTest(name = "평균 점수 {0} → {1}")
        @CsvSource({
            "-2.0, EXTREME_FEAR",
            "-1.5, EXTREME_FEAR",
            "-1.4, FEAR",
            "-0.5, FEAR",
            "-0.4, NEUTRAL",
            "0.4, NEUTRAL",
            "0.5, GREED",
            "1.4, GREED",
            "1.5, EXTREME_GREED"
        })
        void fromScore_Boundaries(double score, SentimentZone expected) {
            assertThat(SentimentZone.fromScore(score)).isEqualTo(expected);
        }

        @Test
        @DisplayName("지표나 값이 없으면 예외")
        void evaluate_RejectsNulls() {
            assertThatThrownBy(() -> SentimentEvaluator.evaluate(null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    SentimentEvaluator.evaluate(
                                            SentimentIndicator.FEAR_GREED_INDEX, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
