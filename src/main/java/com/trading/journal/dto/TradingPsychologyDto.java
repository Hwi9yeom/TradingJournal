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
}
