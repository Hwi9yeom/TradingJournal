package com.trading.journal.entity;

/** 거래 전략 유형 */
public enum TradeStrategy {
    SWING("스윙", "며칠~몇 주간 보유하는 중기 매매"),
    DAY_TRADE("데이트레이딩", "당일 매수/매도하는 단타 매매"),
    SCALPING("스캘핑", "초단타 매매"),
    MOMENTUM("모멘텀", "추세 추종 매매"),
    VALUE("가치투자", "저평가 종목 장기 보유"),
    DIVIDEND("배당투자", "배당수익 목적 투자"),
    GROWTH("성장주", "고성장 기업 투자"),
    SECTOR_ROTATION("섹터로테이션", "업종별 순환 매매"),
    BREAKOUT("돌파매매", "저항선/지지선 돌파 매매"),
    MEAN_REVERSION("평균회귀", "과매수/과매도 역추세 매매"),
    OTHER("기타", "기타 전략");

    private final String label;
    private final String description;

    TradeStrategy(String label, String description) {
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
