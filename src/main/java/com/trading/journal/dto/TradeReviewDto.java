package com.trading.journal.dto;

import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradeStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 거래 복기 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReviewDto {
    private Long id;
    private Long transactionId;

    // 거래 정보 (조회용)
    private String stockSymbol;
    private String stockName;
    private LocalDateTime transactionDate;
    private BigDecimal realizedPnl;
    private BigDecimal profitPercent;

    // 복기 내용
    private TradeStrategy strategy;
    private String strategyLabel;
    private String entryReason;
    private String exitReason;
    private EmotionState emotionBefore;
    private String emotionBeforeLabel;
    private EmotionState emotionAfter;
    private String emotionAfterLabel;
    private String reviewNote;
    private String lessonsLearned;
    private Integer ratingScore;
    private String tags;
    private Boolean followedPlan;
    private String screenshotPath;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 복기 통계 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewStatisticsDto {
        private int totalReviews;
        private int totalTransactions;
        private BigDecimal reviewRate;  // 복기율 (%)
        private Double averageRating;
        private Map<TradeStrategy, StrategyStats> strategyStats;
        private Map<EmotionState, EmotionStats> emotionStats;
        private int followedPlanCount;
        private int notFollowedPlanCount;
        private List<String> topTags;
        private List<LessonSummary> recentLessons;
    }

    /**
     * 전략별 통계
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyStats {
        private TradeStrategy strategy;
        private String strategyLabel;
        private int count;
        private BigDecimal winRate;
        private BigDecimal avgProfit;
        private BigDecimal totalProfit;
    }

    /**
     * 감정별 통계
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionStats {
        private EmotionState emotion;
        private String emotionLabel;
        private int count;
        private BigDecimal winRate;
    }

    /**
     * 교훈 요약
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LessonSummary {
        private Long reviewId;
        private String stockName;
        private String lessonsLearned;
        private LocalDateTime reviewedAt;
        private boolean isWin;
    }
}
