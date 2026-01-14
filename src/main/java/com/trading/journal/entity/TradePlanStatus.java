package com.trading.journal.entity;

/**
 * 트레이드 플랜 상태
 */
public enum TradePlanStatus {
    PLANNED("계획됨", "거래가 아직 실행되지 않음"),
    EXECUTED("실행됨", "거래가 실행됨"),
    CANCELLED("취소됨", "거래 계획이 취소됨"),
    EXPIRED("만료됨", "유효기간이 지남");

    private final String label;
    private final String description;

    TradePlanStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
