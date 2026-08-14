package com.trading.journal.entity;

/**
 * 목표 기간 지평(horizon).
 *
 * <p>단기/중기/장기 목표를 한 화면에서 계층적으로 관리하기 위한 구분값이다. 기존 {@link GoalType}(무엇을 측정하는가)과 직교하며, 이 값은 "언제까지의
 * 목표인가"만 나타낸다.
 */
public enum GoalHorizon {
    /** 올해 목표 (연 단위) */
    THIS_YEAR,

    /** 5년 뒤 목표 */
    FIVE_YEAR,

    /** 10년 뒤 목표 */
    TEN_YEAR,

    /** 최종 목표 (기한 없음) */
    ULTIMATE
}
