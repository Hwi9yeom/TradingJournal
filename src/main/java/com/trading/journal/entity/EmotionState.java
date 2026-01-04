package com.trading.journal.entity;

/**
 * 거래 시 감정 상태
 */
public enum EmotionState {
    CONFIDENT("자신감", "확신을 가지고 거래"),
    FEARFUL("두려움", "불안하고 조심스러운 상태"),
    GREEDY("탐욕", "과도한 수익 기대"),
    NEUTRAL("중립", "감정에 영향받지 않는 상태"),
    ANXIOUS("불안", "조급하거나 초조한 상태"),
    EXCITED("흥분", "지나치게 들뜬 상태"),
    FRUSTRATED("좌절", "실패로 인한 낙담"),
    CALM("침착", "차분하고 이성적인 상태");

    private final String label;
    private final String description;

    EmotionState(String label, String description) {
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
