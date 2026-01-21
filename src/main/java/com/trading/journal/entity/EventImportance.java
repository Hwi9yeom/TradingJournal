package com.trading.journal.entity;

/** 이벤트 중요도 */
public enum EventImportance {
    /** 낮음 - 시장 영향 미미 */
    LOW,

    /** 중간 - 시장 영향 보통 */
    MEDIUM,

    /** 높음 - 시장 영향 큼 (FOMC, CPI 등) */
    HIGH
}
