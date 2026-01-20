package com.trading.journal.entity;

/** 벤치마크 지수 유형 */
public enum BenchmarkType {
    SP500("S&P 500", "^GSPC", "미국 대형주 500개 종목 지수"),
    NASDAQ("NASDAQ", "^IXIC", "미국 나스닥 종합 지수"),
    KOSPI("KOSPI", "^KS11", "한국 종합주가지수"),
    KOSDAQ("KOSDAQ", "^KQ11", "한국 코스닥 지수"),
    DOW("다우존스", "^DJI", "미국 다우존스 산업평균지수");

    private final String label;
    private final String symbol;
    private final String description;

    BenchmarkType(String label, String symbol, String description) {
        this.label = label;
        this.symbol = symbol;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }
}
