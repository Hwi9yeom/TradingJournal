package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 거래 통계 분석 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingStatisticsDto {

    // === 시간대별 성과 ===
    private List<TimeOfDayStats> timeOfDayStats;

    // === 요일별 성과 ===
    private List<WeekdayStats> weekdayStats;

    // === 종목별 성과 ===
    private List<SymbolStats> symbolStats;

    // === 실수 패턴 ===
    private List<MistakePattern> mistakePatterns;

    // === 개선 제안 ===
    private List<ImprovementSuggestion> suggestions;

    // === 전체 요약 ===
    private OverallSummary overallSummary;

    /** 시간대별 성과 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeOfDayStats {
        private String timePeriod; // 시간대 (예: "09:00-10:00", "오전", "오후")
        private Integer hour; // 시간 (0-23)
        private Integer totalTrades; // 총 거래 수
        private Integer winningTrades; // 승리 거래 수
        private BigDecimal winRate; // 승률 (%)
        private BigDecimal totalReturn; // 총 수익률 (%)
        private BigDecimal avgReturn; // 평균 수익률 (%)
        private BigDecimal totalProfit; // 총 손익
    }

    /** 요일별 성과 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekdayStats {
        private String dayName; // 요일 이름 (월, 화, 수, 목, 금)
        private Integer dayOfWeek; // 요일 번호 (1=월, 7=일)
        private Integer totalTrades; // 총 거래 수
        private Integer winningTrades; // 승리 거래 수
        private BigDecimal winRate; // 승률 (%)
        private BigDecimal totalReturn; // 총 수익률 (%)
        private BigDecimal avgReturn; // 평균 수익률 (%)
        private BigDecimal totalProfit; // 총 손익
        private String bestTimeSlot; // 가장 성과 좋은 시간대
    }

    /** 종목별 성과 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SymbolStats {
        private String symbol; // 종목 코드
        private String stockName; // 종목 이름
        private Integer totalTrades; // 총 거래 수
        private Integer winningTrades; // 승리 거래 수
        private BigDecimal winRate; // 승률 (%)
        private BigDecimal totalReturn; // 총 수익률 (%)
        private BigDecimal avgReturn; // 평균 수익률 (%)
        private BigDecimal totalProfit; // 총 손익
        private BigDecimal avgHoldingDays; // 평균 보유 기간
        private Integer rank; // 순위
    }

    /** 실수 패턴 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MistakePattern {
        private String type; // 실수 유형
        private String description; // 설명
        private Integer count; // 발생 횟수
        private BigDecimal totalLoss; // 총 손실액
        private BigDecimal avgLoss; // 평균 손실액
        private String severity; // 심각도 (HIGH, MEDIUM, LOW)
        private List<MistakeExample> examples; // 예시 거래
    }

    /** 실수 예시 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MistakeExample {
        private Long transactionId;
        private String symbol;
        private LocalDate date;
        private BigDecimal loss;
        private String note;
    }

    /** 개선 제안 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImprovementSuggestion {
        private String category; // 카테고리 (TIME, SYMBOL, RISK, BEHAVIOR)
        private String title; // 제목
        private String message; // 상세 메시지
        private String priority; // 우선순위 (HIGH, MEDIUM, LOW)
        private String actionItem; // 실행 항목
        private BigDecimal potentialImpact; // 예상 개선 효과 (%)
    }

    /** 전체 요약 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallSummary {
        private Integer totalTrades;
        private Integer winningTrades;
        private Integer losingTrades;
        private BigDecimal overallWinRate;
        private BigDecimal totalProfit;
        private BigDecimal avgProfit;
        private String bestDay; // 가장 성과 좋은 요일
        private String worstDay; // 가장 성과 나쁜 요일
        private String bestTimeSlot; // 가장 성과 좋은 시간대
        private String bestSymbol; // 가장 성과 좋은 종목
        private Integer mistakeCount; // 실수 총 횟수
        private BigDecimal consistencyScore; // 일관성 점수 (0-100)
    }

    /** 실수 유형 상수 */
    public static class MistakeTypes {
        public static final String NO_STOP_LOSS = "NO_STOP_LOSS"; // 손절 미설정
        public static final String STOP_LOSS_IGNORED = "STOP_LOSS_IGNORED"; // 손절 무시
        public static final String OVERTRADING = "OVERTRADING"; // 과도한 거래
        public static final String REVENGE_TRADING = "REVENGE_TRADING"; // 복수 매매
        public static final String FOMO_ENTRY = "FOMO_ENTRY"; // FOMO 진입
        public static final String EARLY_EXIT = "EARLY_EXIT"; // 조기 청산
        public static final String HOLDING_TOO_LONG = "HOLDING_TOO_LONG"; // 과도한 홀딩
        public static final String POSITION_SIZE = "POSITION_SIZE"; // 포지션 크기 오류
    }
}
