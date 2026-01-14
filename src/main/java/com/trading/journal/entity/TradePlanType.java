package com.trading.journal.entity;

/**
 * 트레이드 플랜 유형 (롱/숏)
 */
public enum TradePlanType {
    LONG("매수", "가격 상승을 기대하는 롱 포지션"),
    SHORT("매도", "가격 하락을 기대하는 숏 포지션");

    private final String label;
    private final String description;

    TradePlanType(String label, String description) {
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
