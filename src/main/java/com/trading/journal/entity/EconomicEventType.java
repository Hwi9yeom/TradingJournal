package com.trading.journal.entity;

/** 경제 이벤트 유형 */
public enum EconomicEventType {
    /** 경제 지표 (CPI, GDP, 실업률 등) */
    ECONOMIC_INDICATOR,

    /** 중앙은행 이벤트 (FOMC, ECB 회의 등) */
    CENTRAL_BANK,

    /** 실적 발표 */
    EARNINGS,

    /** 배당락일 */
    DIVIDEND,

    /** IPO */
    IPO,

    /** 공휴일/휴장 */
    HOLIDAY,

    /** 기타 */
    OTHER
}
