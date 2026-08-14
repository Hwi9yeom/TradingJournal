package com.trading.journal.entity;

/** 저축 기록 분류 */
public enum SavingsCategory {
    /** 비상금 */
    EMERGENCY_FUND,

    /** 정기 저축/적금 */
    REGULAR_SAVINGS,

    /** 투자 계좌 이체 */
    INVESTMENT_TRANSFER,

    /** 연금/퇴직연금 */
    PENSION,

    /** 부채 상환 (순자산 증가로 취급) */
    DEBT_REPAYMENT,

    /** 기타 */
    OTHER
}
