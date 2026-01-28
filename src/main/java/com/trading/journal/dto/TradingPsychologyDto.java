package com.trading.journal.dto;

import com.trading.journal.entity.EmotionState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingPsychologyDto {
    // Main response wrapper
    private EmotionTransitionAnalysis transitionAnalysis;
    private TiltAnalysis tiltAnalysis;
    private EmotionBehaviorCorrelation behaviorCorrelation;
    private PsychologicalScore psychologicalScore;
    private RecoveryPatternAnalysis recoveryAnalysis;
    private DailyRhythmAnalysis dailyRhythmAnalysis;

    // 1. EmotionTransitionAnalysis
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionTransitionAnalysis {
        private List<EmotionTransition> transitions;
        private List<EmotionTransition> riskyTransitions; // HIGH risk only
        private int totalTransitions;
        private String mostCommonTransition;
    }

    // 2. EmotionTransition - represents Before → After pattern
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionTransition {
        private EmotionState emotionBefore;
        private String emotionBeforeLabel;
        private EmotionState emotionAfter;
        private String emotionAfterLabel;
        private int occurrences;
        private BigDecimal winRate;
        private BigDecimal avgPnl;
        private String riskLevel; // HIGH, MEDIUM, LOW
    }

    // 3. TiltAnalysis
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TiltAnalysis {
        private int tiltScore; // 0-100
        private String tiltLevel; // SEVERE, MODERATE, MILD, NONE
        private List<TiltEvent> recentTiltEvents;
        private TiltScoreBreakdown scoreBreakdown;
        private List<String> recommendations;
    }

    // 4. TiltEvent - individual tilt occurrence
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TiltEvent {
        private LocalDate date;
        private int consecutiveNegativeShifts;
        private List<EmotionTransition> transitionSequence;
        private BigDecimal totalLossDuringTilt;
        private int tradesAffected;
    }

    // 5. TiltScoreBreakdown - how tilt score is calculated
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TiltScoreBreakdown {
        private int consecutiveNegativeScore; // max 40
        private int lossRatioScore; // max 30
        private int emotionDeteriorationScore; // max 20
        private int frequencyScore; // max 10
    }

    // 6. EmotionBehaviorCorrelation
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionBehaviorCorrelation {
        private List<BehaviorCorrelation> correlations;
        private List<BehaviorCorrelation> significantCorrelations; // > 0.3 threshold
    }

    // 7. BehaviorCorrelation - emotion → mistake pattern
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BehaviorCorrelation {
        private EmotionState emotion;
        private String emotionLabel;
        private String mistakeType;
        private int occurrences;
        private BigDecimal correlationStrength; // 0.0 - 1.0
    }

    // 8. PsychologicalScore - composite score
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PsychologicalScore {
        private BigDecimal overallScore; // 0-100
        private String grade; // A, B, C, D, F
        private ScoreComponents components;
        private List<ScoreTrend> historicalTrend;
    }

    // 9. ScoreComponents - breakdown of psychological score
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreComponents {
        private BigDecimal focusScore; // 0-100, weight: 0.25
        private BigDecimal disciplineScore; // 0-100, weight: 0.30
        private BigDecimal emotionalStabilityScore; // 0-100, weight: 0.30
        private BigDecimal resilienceScore; // 0-100, weight: 0.15
    }

    // 10. ScoreTrend - time series data point
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreTrend {
        private LocalDate date;
        private BigDecimal score;
        private String grade;
    }

    // 11. RecoveryPatternAnalysis
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecoveryPatternAnalysis {
        private BigDecimal avgRecoveryDays;
        private String recoveryType; // QUICK_BOUNCER, STEADY_RECOVERER, SLOW_HEALER
        private List<String> helpfulFactors;
        private List<String> harmfulFactors;
        private List<RecoveryEvent> recentRecoveries;
    }

    // 12. RecoveryEvent
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecoveryEvent {
        private LocalDate startDate;
        private LocalDate recoveryDate;
        private int daysToRecover;
        private EmotionState fromEmotion;
        private EmotionState toEmotion;
    }

    // 13. DailyRhythmAnalysis
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyRhythmAnalysis {
        private Map<String, EmotionDistribution> morningEmotions;
        private Map<String, EmotionDistribution> eveningEmotions;
        private Map<String, DayPerformance> dayOfWeekAnalysis;
        private String bestTradingDay;
        private String worstTradingDay;
        private List<String> recommendations;
    }

    // 14. EmotionDistribution
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionDistribution {
        private EmotionState emotion;
        private String emotionLabel;
        private int count;
        private BigDecimal percentage;
    }

    // 15. DayPerformance
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayPerformance {
        private String dayOfWeek;
        private int tradeCount;
        private BigDecimal winRate;
        private BigDecimal avgPnl;
        private EmotionState dominantEmotion;
        private String dominantEmotionLabel;
    }

    // 16. EmotionTrigger - for trigger analysis endpoint
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionTriggerAnalysis {
        private List<EmotionTrigger> triggers;
        private List<EmotionTrigger> topNegativeTriggers;
        private List<EmotionTrigger> topPositiveTriggers;
    }

    // 17. EmotionTrigger
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionTrigger {
        private String trigger; // e.g., "large loss", "consecutive wins"
        private EmotionState resultingEmotion;
        private String resultingEmotionLabel;
        private int occurrences;
        private BigDecimal avgImpactOnPnl;
    }

    // ============================================================
    // 심리-성과 상관관계 분석 (Performance Correlation)
    // ============================================================

    // 18. PsychologyPerformanceCorrelation - 전체 상관관계 분석 결과
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PsychologyPerformanceCorrelation {
        private List<EmotionPerformanceStats> emotionPerformance;
        private ScoreCorrelation focusCorrelation;
        private ScoreCorrelation disciplineCorrelation;
        private OptimalState optimalState;
        private PlanAdherenceStats planAdherence;
        private List<String> recommendations;
    }

    // 19. EmotionPerformanceStats - 감정 상태별 성과
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmotionPerformanceStats {
        private EmotionState emotion;
        private String emotionLabel;
        private int tradeCount;
        private int winCount;
        private BigDecimal winRate;
        private BigDecimal avgPnl;
        private BigDecimal totalPnl;
        private BigDecimal avgRMultiple;
        private String performanceGrade; // A, B, C, D, F
    }

    // 20. ScoreCorrelation - 점수(집중도/규율)와 성과의 상관관계
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreCorrelation {
        private String scoreName;
        private List<ScorePerformanceGroup> groups;
        private BigDecimal correlationCoefficient; // -1 ~ 1
        private String correlationStrength; // STRONG, MODERATE, WEAK, NONE
        private String recommendation;
    }

    // 21. ScorePerformanceGroup - 점수별 성과 그룹
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScorePerformanceGroup {
        private int score;
        private int tradeCount;
        private BigDecimal winRate;
        private BigDecimal avgPnl;
        private BigDecimal totalPnl;
    }

    // 22. OptimalState - 최적 심리 상태 분석
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptimalState {
        private EmotionState bestEmotion;
        private String bestEmotionLabel;
        private Integer optimalFocusMin;
        private Integer optimalFocusMax;
        private Integer optimalDisciplineMin;
        private Integer optimalDisciplineMax;
        private BigDecimal optimalWinRate;
        private BigDecimal optimalAvgPnl;
        private BigDecimal overallWinRate;
        private BigDecimal overallAvgPnl;
        private BigDecimal improvementPercent;
    }

    // 23. PlanAdherenceStats - 매매 계획 준수 vs 미준수 성과 비교
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanAdherenceStats {
        private int followedCount;
        private BigDecimal followedWinRate;
        private BigDecimal followedAvgPnl;
        private int notFollowedCount;
        private BigDecimal notFollowedWinRate;
        private BigDecimal notFollowedAvgPnl;
        private BigDecimal winRateDifference;
        private BigDecimal pnlDifference;
    }
}
