package com.trading.journal.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 트레이딩 전략 인터페이스
 */
public interface TradingStrategy {

    /**
     * 시그널 생성
     * @param prices 가격 데이터 리스트
     * @param index 현재 인덱스
     * @return 매매 시그널
     */
    Signal generateSignal(List<PriceData> prices, int index);

    /**
     * 전략 이름 반환
     */
    String getName();

    /**
     * 전략 유형 반환
     */
    StrategyType getType();

    /**
     * 전략 파라미터 반환
     */
    Map<String, Object> getParameters();

    /**
     * 전략 설명 반환
     */
    String getDescription();

    /**
     * 최소 필요 데이터 수
     */
    int getMinimumDataPoints();

    /**
     * 매매 시그널
     */
    enum Signal {
        BUY,        // 매수 시그널
        SELL,       // 매도 시그널
        HOLD        // 관망
    }

    /**
     * 전략 유형
     */
    enum StrategyType {
        MOVING_AVERAGE("이동평균", "이동평균선 기반 전략"),
        RSI("RSI", "상대강도지수 기반 전략"),
        BOLLINGER_BAND("볼린저밴드", "볼린저밴드 기반 전략"),
        MOMENTUM("모멘텀", "가격 모멘텀 기반 전략"),
        MACD("MACD", "MACD 기반 전략"),
        CUSTOM("사용자정의", "사용자 정의 전략");

        private final String label;
        private final String description;

        StrategyType(String label, String description) {
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

    /**
     * 가격 데이터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PriceData {
        private LocalDate date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Long volume;

        /**
         * 전형적 가격 (Typical Price)
         */
        public BigDecimal getTypicalPrice() {
            return high.add(low).add(close).divide(BigDecimal.valueOf(3), 4, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * 시그널 결과
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SignalResult {
        private Signal signal;
        private BigDecimal price;
        private LocalDate date;
        private String reason;
        private BigDecimal strength;  // 시그널 강도 (0~1)
        private Map<String, Object> indicators;  // 관련 지표 값
    }
}
