package com.trading.journal.dto;

import com.trading.journal.entity.EmotionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 트레이딩 일지 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingJournalDto {

    private Long id;
    private Long accountId;
    private LocalDate journalDate;
    private String dayOfWeek;           // 요일 표시용

    // === 텍스트 필드 ===
    private String marketOverview;
    private String tradingPlan;
    private String executionReview;
    private String lessonsLearned;
    private String tomorrowPlan;
    private String tags;

    // === 감정 상태 ===
    private EmotionState morningEmotion;
    private String morningEmotionLabel;
    private EmotionState eveningEmotion;
    private String eveningEmotionLabel;

    // === 점수 ===
    private Integer focusScore;
    private Integer disciplineScore;

    // === 거래 요약 ===
    private Integer tradeSummaryCount;
    private BigDecimal tradeSummaryProfit;
    private BigDecimal tradeSummaryWinRate;

    // === 메타데이터 ===
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 일지 생성/수정 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalRequest {
        private Long accountId;
        private LocalDate journalDate;
        private String marketOverview;
        private String tradingPlan;
        private String executionReview;
        private EmotionState morningEmotion;
        private EmotionState eveningEmotion;
        private Integer focusScore;
        private Integer disciplineScore;
        private String lessonsLearned;
        private String tomorrowPlan;
        private String tags;
    }

    /**
     * 일지 통계 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalStatistics {
        private Long totalJournals;
        private Double avgFocusScore;
        private Double avgDisciplineScore;
        private Map<String, Long> morningEmotionCounts;
        private Map<String, Long> eveningEmotionCounts;
        private List<EmotionTrend> emotionTrends;
        private List<ScoreTrend> scoreTrends;
        private List<String> recentLessons;
    }

    /**
     * 감정 추이 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrend {
        private LocalDate date;
        private EmotionState morningEmotion;
        private EmotionState eveningEmotion;
        private BigDecimal dailyProfit;  // 당일 손익
    }

    /**
     * 점수 추이 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreTrend {
        private LocalDate date;
        private Integer focusScore;
        private Integer disciplineScore;
        private BigDecimal dailyProfit;
    }

    /**
     * 일지 목록 아이템 DTO (간소화)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalListItem {
        private Long id;
        private LocalDate journalDate;
        private String dayOfWeek;
        private EmotionState morningEmotion;
        private EmotionState eveningEmotion;
        private Integer focusScore;
        private Integer disciplineScore;
        private Integer tradeSummaryCount;
        private BigDecimal tradeSummaryProfit;
        private boolean hasContent;      // 내용 작성 여부
    }
}
