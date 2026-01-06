package com.trading.journal.entity;

/**
 * 목표 상태
 */
public enum GoalStatus {
    /** 진행 중 */
    ACTIVE,

    /** 달성 완료 */
    COMPLETED,

    /** 미달성 (기한 초과) */
    FAILED,

    /** 일시 중지 */
    PAUSED,

    /** 취소됨 */
    CANCELLED
}
