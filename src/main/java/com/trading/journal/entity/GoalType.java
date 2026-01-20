package com.trading.journal.entity;

/** 투자 목표 유형 */
public enum GoalType {
    /** 목표 수익률 (%) */
    RETURN_RATE,

    /** 목표 자산 금액 (원) */
    TARGET_AMOUNT,

    /** 목표 저축 금액 (원) */
    SAVINGS_AMOUNT,

    /** 목표 승률 (%) */
    WIN_RATE,

    /** 목표 거래 횟수 */
    TRADE_COUNT,

    /** 목표 배당 수익 (원) */
    DIVIDEND_INCOME,

    /** 최대 낙폭 제한 (%) */
    MAX_DRAWDOWN_LIMIT,

    /** 샤프 비율 목표 */
    SHARPE_RATIO,

    /** 기타 사용자 정의 목표 */
    CUSTOM
}
