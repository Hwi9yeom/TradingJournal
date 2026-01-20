package com.trading.journal.entity;

/** GICS 기반 섹터 분류 */
public enum Sector {
    TECH("정보기술", "Information Technology", "소프트웨어, 하드웨어, 반도체 등 기술 관련 기업"),
    HEALTH("헬스케어", "Health Care", "제약, 바이오, 의료기기 등 건강 관련 기업"),
    FINANCE("금융", "Financials", "은행, 보험, 자산운용 등 금융 관련 기업"),
    CONSUMER_DISC("경기소비재", "Consumer Discretionary", "자동차, 패션, 레저 등 경기에 민감한 소비재"),
    CONSUMER_STAP("필수소비재", "Consumer Staples", "식품, 음료, 생활용품 등 필수 소비재"),
    INDUSTRIAL("산업재", "Industrials", "기계, 항공, 건설 등 산업 관련 기업"),
    ENERGY("에너지", "Energy", "석유, 가스, 신재생에너지 등 에너지 관련 기업"),
    MATERIALS("소재", "Materials", "화학, 철강, 건설자재 등 기초 소재 기업"),
    UTILITIES("유틸리티", "Utilities", "전력, 가스, 수도 등 공공 유틸리티 기업"),
    REAL_ESTATE("부동산", "Real Estate", "리츠, 부동산 개발 등 부동산 관련 기업"),
    COMMUNICATION("통신", "Communication Services", "통신, 미디어, 엔터테인먼트 등 통신 서비스 기업"),
    OTHER("기타", "Other", "분류되지 않은 기타 섹터");

    private final String label;
    private final String labelEn;
    private final String description;

    Sector(String label, String labelEn, String description) {
        this.label = label;
        this.labelEn = labelEn;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getDescription() {
        return description;
    }
}
