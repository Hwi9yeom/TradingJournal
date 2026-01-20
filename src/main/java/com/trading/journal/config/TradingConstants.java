package com.trading.journal.config;

import java.math.BigDecimal;

/**
 * 트레이딩 저널 애플리케이션의 상수 정의
 */
public final class TradingConstants {

    private TradingConstants() {
        // 인스턴스화 방지
    }

    // ============================================
    // 금융 계산 관련 상수
    // ============================================

    /**
     * 연간 거래일 수 (주말 및 공휴일 제외)
     */
    public static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * 무위험 이자율 (연간, 기본값 3%)
     */
    public static final BigDecimal DEFAULT_RISK_FREE_RATE = new BigDecimal("0.03");

    /**
     * 백분율 변환 계수
     */
    public static final BigDecimal PERCENT_MULTIPLIER = new BigDecimal("100");

    // ============================================
    // 세금 관련 상수 (한국 주식 양도소득세 2024년 기준)
    // ============================================

    /**
     * 기본공제액 (250만원)
     */
    public static final BigDecimal TAX_BASIC_DEDUCTION = new BigDecimal("2500000");

    /**
     * 양도소득세율 (22%, 지방소득세 포함)
     */
    public static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    /**
     * 장기보유 기준일 (1년 = 365일)
     */
    public static final int LONG_TERM_HOLDING_DAYS = 365;

    // ============================================
    // 기술적 분석 지표 기본값
    // ============================================

    public static final class TechnicalIndicators {
        private TechnicalIndicators() {}

        // 이동평균선
        public static final int MA_SHORT_PERIOD = 20;
        public static final int MA_LONG_PERIOD = 60;

        // RSI (Relative Strength Index)
        public static final int RSI_PERIOD = 14;
        public static final int RSI_OVERBOUGHT_LEVEL = 70;
        public static final int RSI_OVERSOLD_LEVEL = 30;

        // 볼린저 밴드
        public static final int BOLLINGER_PERIOD = 20;
        public static final double BOLLINGER_STD_DEV_MULTIPLIER = 2.0;

        // MACD
        public static final int MACD_FAST_PERIOD = 12;
        public static final int MACD_SLOW_PERIOD = 26;
        public static final int MACD_SIGNAL_PERIOD = 9;

        // 모멘텀
        public static final int MOMENTUM_PERIOD = 20;
        public static final double MOMENTUM_ENTRY_THRESHOLD = 0.0;
        public static final double MOMENTUM_EXIT_THRESHOLD = 0.0;
    }

    // ============================================
    // 리스크 관리 상수
    // ============================================

    public static final class RiskManagement {
        private RiskManagement() {}

        // 리스크 등급 임계값 (변동성)
        public static final BigDecimal VOLATILITY_HIGH_THRESHOLD = new BigDecimal("0.20");
        public static final BigDecimal VOLATILITY_MEDIUM_THRESHOLD = new BigDecimal("0.10");

        // 리스크 등급 임계값 (최대 낙폭)
        public static final BigDecimal MDD_HIGH_THRESHOLD = new BigDecimal("20");
        public static final BigDecimal MDD_MEDIUM_THRESHOLD = new BigDecimal("10");

        // 리스크 등급 임계값 (샤프 비율)
        public static final BigDecimal SHARPE_LOW_THRESHOLD = new BigDecimal("0.5");
        public static final BigDecimal SHARPE_MEDIUM_THRESHOLD = new BigDecimal("1.0");

        // VaR 신뢰수준
        public static final BigDecimal VAR_CONFIDENCE_95 = new BigDecimal("0.95");
        public static final BigDecimal VAR_CONFIDENCE_99 = new BigDecimal("0.99");

        // 시간 스케일링 계수 (√t 규칙)
        public static final double WEEKLY_SCALING_FACTOR = Math.sqrt(5);
        public static final double MONTHLY_SCALING_FACTOR = Math.sqrt(21);

        // 최대 손익비/소르티노 상한
        public static final BigDecimal MAX_RATIO_VALUE = new BigDecimal("10.00");
        public static final BigDecimal MAX_SORTINO_VALUE = new BigDecimal("999.99");
    }

    // ============================================
    // 포지션 사이징 상수
    // ============================================

    public static final class PositionSizing {
        private PositionSizing() {}

        // Kelly Criterion 기본값
        public static final BigDecimal DEFAULT_KELLY_FRACTION = new BigDecimal("0.50");
        public static final int KELLY_FULL_DIVISOR = 1;
        public static final int KELLY_HALF_DIVISOR = 2;
        public static final int KELLY_QUARTER_DIVISOR = 4;

        // 분석 기간 (개월)
        public static final int KELLY_ANALYSIS_MONTHS = 6;
        public static final int R_MULTIPLE_ANALYSIS_MONTHS = 3;
        public static final int RISK_METRICS_ANALYSIS_MONTHS = 6;

        // 시나리오 리스크 비율 (%)
        public static final BigDecimal SCENARIO_RISK_CONSERVATIVE = new BigDecimal("1.0");
        public static final BigDecimal SCENARIO_RISK_MODERATE = new BigDecimal("2.0");
        public static final BigDecimal SCENARIO_RISK_AGGRESSIVE = new BigDecimal("3.0");
        public static final BigDecimal SCENARIO_RISK_HIGH = new BigDecimal("5.0");
    }

    // ============================================
    // 차트 및 UI 관련 상수
    // ============================================

    public static final class Chart {
        private Chart() {}

        public static final int DEFAULT_WIDTH = 600;
        public static final int DEFAULT_HEIGHT = 400;

        // 색상 (RGB)
        public static final int[] COLOR_PRIMARY = {54, 162, 235};
        public static final int[] COLOR_SUCCESS = {75, 192, 192};
        public static final int[] COLOR_DANGER = {255, 99, 132};
        public static final int[] COLOR_WARNING = {255, 205, 86};
        public static final int[] COLOR_INFO = {153, 102, 255};
    }

    // ============================================
    // 알림 관련 상수
    // ============================================

    public static final class Alert {
        private Alert() {}

        /**
         * 기본 알림 만료 기간 (일)
         */
        public static final int DEFAULT_EXPIRY_DAYS = 7;

        /**
         * 목표 달성 마일스톤 (%)
         */
        public static final int[] GOAL_MILESTONES = {25, 50, 75, 100};
    }

    // ============================================
    // 데이터 처리 관련 상수
    // ============================================

    public static final class Data {
        private Data() {}

        /**
         * BigDecimal 계산 시 기본 소수점 자릿수
         */
        public static final int DEFAULT_SCALE = 4;
        public static final int CURRENCY_SCALE = 0;
        public static final int PERCENT_SCALE = 2;
        public static final int HIGH_PRECISION_SCALE = 6;
        public static final int EXTREME_PRECISION_SCALE = 8;

        /**
         * 페이지네이션 기본값
         */
        public static final int DEFAULT_PAGE_SIZE = 20;
        public static final int MAX_PAGE_SIZE = 100;

        /**
         * 지원되는 날짜 형식
         */
        public static final String[] SUPPORTED_DATE_FORMATS = {
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "dd-MM-yyyy",
            "yyyyMMdd"
        };
    }

    // ============================================
    // 백테스트 관련 상수
    // ============================================

    public static final class Backtest {
        private Backtest() {}

        /**
         * 샘플 데이터 생성 시 기준 가격
         */
        public static final BigDecimal SAMPLE_BASE_PRICE = new BigDecimal("100");

        /**
         * 샘플 데이터 변동 범위 (±3%)
         */
        public static final double SAMPLE_CHANGE_RANGE = 0.06;
        public static final double SAMPLE_CHANGE_OFFSET = 0.48;

        /**
         * 진행률 로깅 간격 (%)
         */
        public static final int PROGRESS_LOG_INTERVAL_PERCENT = 10;

        /**
         * 연간 일수 (CAGR 계산용)
         */
        public static final double DAYS_PER_YEAR = 365.0;
    }
}