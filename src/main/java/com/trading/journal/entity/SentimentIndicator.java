package com.trading.journal.entity;

/**
 * 시장 심리 지표 목록.
 *
 * <p>각 상수는 지표의 표시 이름, 소속 시장, 단위, 참고 출처, 해석 메모를 함께 들고 있다. 판정 임계값은 {@code
 * com.trading.journal.service.SentimentEvaluator}가 소유한다.
 */
public enum SentimentIndicator {
    FEAR_GREED_INDEX(
            "공포·탐욕 지수",
            SentimentMarket.US_STOCK,
            "0-100",
            "https://edition.cnn.com/markets/fear-and-greed",
            "0에 가까울수록 공포, 100에 가까울수록 탐욕"),

    PUT_CALL_RATIO(
            "풋/콜 비율",
            SentimentMarket.US_STOCK,
            "비율",
            "https://ycharts.com/indicators/cboe_equity_put_call_ratio",
            "1.0 이상이면 공포 과도(반등 시그널), 0.7 이하면 낙관 과도(하락 경계)"),

    NAAIM_EXPOSURE(
            "NAAIM 기관 포지션 비중",
            SentimentMarket.US_STOCK,
            "0-100",
            "https://naaim.org/programs/naaim-exposure-index/",
            "90 이상 풀베팅 과열, 30 이하 공포 구간, 10 이하 극단적 공포"),

    AAII_BULLISH(
            "AAII 개인 강세 비율",
            SentimentMarket.US_STOCK,
            "%",
            "https://www.aaii.com/sentimentsurvey",
            "50% 초과면 과도한 낙관 경계"),

    AAII_BEARISH(
            "AAII 개인 약세 비율",
            SentimentMarket.US_STOCK,
            "%",
            "https://www.aaii.com/sentimentsurvey",
            "50% 초과면 시장 바닥 가능성"),

    SMART_DUMB_MONEY(
            "스마트머니 - 개인 스프레드",
            SentimentMarket.US_STOCK,
            "-100~100",
            "https://sentimentrader.com/",
            "양수가 클수록 개인 과열(고점 경계), 음수가 클수록 기관 매집(저점 가능성)"),

    CRYPTO_FEAR_GREED(
            "암호화폐 공포·탐욕 지수",
            SentimentMarket.CRYPTO,
            "0-100",
            "https://alternative.me/crypto/fear-and-greed-index/",
            "0에 가까울수록 공포, 100에 가까울수록 탐욕"),

    FUNDING_RATE(
            "펀딩 비율",
            SentimentMarket.CRYPTO,
            "%",
            "https://www.coinglass.com/FundingRate",
            "양(+)이 높으면 롱 과열(되돌림 위험), 음(-)이 크면 숏 과열(반등 가능성)"),

    LONG_SHORT_RATIO(
            "롱/숏 비율",
            SentimentMarket.CRYPTO,
            "비율",
            "https://www.coinglass.com/LongShortRatio",
            "롱 비중이 과도하면 롱 청산 위험, 숏 비중이 과도하면 반등 가능성"),

    MVRV_Z_SCORE(
            "MVRV Z-Score",
            SentimentMarket.CRYPTO,
            "Z",
            "https://www.bitcoinmagazinepro.com/charts/mvrv-zscore/",
            "7 초과면 과열, 0 미만이면 저점 신호"),

    PUELL_MULTIPLE(
            "Puell Multiple",
            SentimentMarket.CRYPTO,
            "배수",
            "https://www.bitcoinmagazinepro.com/charts/puell-multiple/",
            "0.5~1 구간은 바닥 신호, 5 이상이면 매도 위험 증가"),

    RHODL_RATIO(
            "RHODL 밴드 위치",
            SentimentMarket.CRYPTO,
            "0-100",
            "https://www.bitcoinmagazinepro.com/charts/rhodl-ratio/",
            "차트 밴드에서의 위치를 0(저점 밴드)~100(고점 밴드)으로 환산해 입력");

    private final String label;
    private final SentimentMarket market;
    private final String unit;
    private final String sourceUrl;
    private final String interpretation;

    SentimentIndicator(
            String label,
            SentimentMarket market,
            String unit,
            String sourceUrl,
            String interpretation) {
        this.label = label;
        this.market = market;
        this.unit = unit;
        this.sourceUrl = sourceUrl;
        this.interpretation = interpretation;
    }

    public String getLabel() {
        return label;
    }

    public SentimentMarket getMarket() {
        return market;
    }

    public String getUnit() {
        return unit;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getInterpretation() {
        return interpretation;
    }
}
