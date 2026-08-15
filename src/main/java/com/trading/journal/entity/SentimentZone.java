package com.trading.journal.entity;

/**
 * 심리 지표 판정 구간.
 *
 * <p>{@code score}는 지표를 가로질러 합산하기 위한 정규화 값이다. 공포(저점 가능성)가 음수, 탐욕(과열 경계)이 양수다.
 */
public enum SentimentZone {
    /** 극단적 공포 - 저점 가능성 */
    EXTREME_FEAR(-2, "극단적 공포", "저점 가능성 - 분할 매수 검토 구간"),

    /** 공포 */
    FEAR(-1, "공포", "저가 매수 관심 구간"),

    /** 중립 */
    NEUTRAL(0, "중립", "추세 추종, 별도 대응 불필요"),

    /** 탐욕 */
    GREED(1, "탐욕", "신규 진입 신중, 손절선 상향"),

    /** 극단적 탐욕 - 과열 경계 */
    EXTREME_GREED(2, "극단적 탐욕", "과열 경계 - 분할 익절 검토 구간");

    private final int score;
    private final String label;
    private final String action;

    SentimentZone(int score, String label, String action) {
        this.score = score;
        this.label = label;
        this.action = action;
    }

    /** 정규화 점수 (-2 ~ +2) */
    public int getScore() {
        return score;
    }

    /** 한글 라벨 */
    public String getLabel() {
        return label;
    }

    /** 구간별 대응 가이드 */
    public String getAction() {
        return action;
    }

    /**
     * 평균 점수를 구간으로 되돌린다. 종합 심리 산출에 쓴다.
     *
     * @param averageScore -2 ~ +2 범위의 평균 점수
     */
    public static SentimentZone fromScore(double averageScore) {
        if (averageScore <= -1.5) {
            return EXTREME_FEAR;
        }
        if (averageScore <= -0.5) {
            return FEAR;
        }
        if (averageScore < 0.5) {
            return NEUTRAL;
        }
        if (averageScore < 1.5) {
            return GREED;
        }
        return EXTREME_GREED;
    }
}
