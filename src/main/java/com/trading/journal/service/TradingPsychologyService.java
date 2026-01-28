package com.trading.journal.service;

import com.trading.journal.dto.TradingPsychologyDto.*;
import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradeReview;
import com.trading.journal.entity.TradingJournal;
import com.trading.journal.repository.TradeReviewRepository;
import com.trading.journal.repository.TradingJournalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 트레이딩 심리 분석 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TradingPsychologyService {

    private final TradeReviewRepository tradeReviewRepository;
    private final TradingJournalRepository tradingJournalRepository;

    // Constants
    private static final int TILT_THRESHOLD_SEVERE = 80;
    private static final int TILT_THRESHOLD_MODERATE = 60;
    private static final int TILT_THRESHOLD_MILD = 40;
    private static final int CONSECUTIVE_NEGATIVE_SHIFT_THRESHOLD = 3;
    private static final BigDecimal CORRELATION_SIGNIFICANCE_THRESHOLD = new BigDecimal("0.3");

    // Negative emotions for tilt detection
    private static final Set<EmotionState> NEGATIVE_EMOTIONS =
            Set.of(
                    EmotionState.FEARFUL,
                    EmotionState.ANXIOUS,
                    EmotionState.FRUSTRATED,
                    EmotionState.GREEDY);

    // Positive/neutral emotions
    private static final Set<EmotionState> POSITIVE_EMOTIONS =
            Set.of(
                    EmotionState.CONFIDENT,
                    EmotionState.CALM,
                    EmotionState.NEUTRAL,
                    EmotionState.EXCITED);

    /**
     * 종합 심리 분석 조회
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 종합 심리 분석 결과
     */
    public com.trading.journal.dto.TradingPsychologyDto getFullAnalysis(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info(
                "Generating full psychology analysis for account: {} from {} to {}",
                accountId,
                startDate,
                endDate);

        return com.trading.journal.dto.TradingPsychologyDto.builder()
                .transitionAnalysis(analyzeEmotionTransitions(accountId, startDate, endDate))
                .tiltAnalysis(detectTilt(accountId, startDate, endDate))
                .behaviorCorrelation(
                        analyzeEmotionBehaviorCorrelation(accountId, startDate, endDate))
                .psychologicalScore(calculatePsychologicalScore(accountId, startDate, endDate))
                .recoveryAnalysis(analyzeRecoveryPatterns(accountId, startDate, endDate))
                .dailyRhythmAnalysis(analyzeDailyRhythm(accountId, startDate, endDate))
                .build();
    }

    /**
     * 감정 전환 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 감정 전환 분석 결과
     */
    public EmotionTransitionAnalysis analyzeEmotionTransitions(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing emotion transitions for account: {}", accountId);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<Object[]> stats =
                tradeReviewRepository.getEmotionTransitionStats(
                        accountId, startDateTime, endDateTime);

        List<EmotionTransition> transitions =
                stats.stream()
                        .map(
                                row -> {
                                    EmotionState before = (EmotionState) row[0];
                                    EmotionState after = (EmotionState) row[1];
                                    Long count = (Long) row[2];
                                    Double winRate = row[3] != null ? (Double) row[3] : 0.0;
                                    Double avgPnl = row[4] != null ? (Double) row[4] : 0.0;

                                    return EmotionTransition.builder()
                                            .emotionBefore(before)
                                            .emotionBeforeLabel(before.getLabel())
                                            .emotionAfter(after)
                                            .emotionAfterLabel(after.getLabel())
                                            .occurrences(count.intValue())
                                            .winRate(
                                                    BigDecimal.valueOf(winRate)
                                                            .setScale(2, RoundingMode.HALF_UP))
                                            .avgPnl(
                                                    BigDecimal.valueOf(avgPnl)
                                                            .setScale(2, RoundingMode.HALF_UP))
                                            .riskLevel(
                                                    calculateTransitionRisk(
                                                            before,
                                                            after,
                                                            BigDecimal.valueOf(winRate)))
                                            .build();
                                })
                        .collect(Collectors.toList());

        List<EmotionTransition> riskyTransitions =
                transitions.stream()
                        .filter(t -> "HIGH".equals(t.getRiskLevel()))
                        .collect(Collectors.toList());

        String mostCommon =
                transitions.isEmpty()
                        ? "없음"
                        : transitions.get(0).getEmotionBeforeLabel()
                                + " -> "
                                + transitions.get(0).getEmotionAfterLabel();

        return EmotionTransitionAnalysis.builder()
                .transitions(transitions)
                .riskyTransitions(riskyTransitions)
                .totalTransitions(
                        transitions.stream().mapToInt(EmotionTransition::getOccurrences).sum())
                .mostCommonTransition(mostCommon)
                .build();
    }

    /**
     * 틸트 감지
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 틸트 분석 결과
     */
    public TiltAnalysis detectTilt(Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Detecting tilt patterns for account: {}", accountId);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<TradeReview> trades =
                tradeReviewRepository.findTradesForTiltAnalysis(
                        accountId, startDateTime, endDateTime);

        if (trades.isEmpty()) {
            return TiltAnalysis.builder()
                    .tiltScore(0)
                    .tiltLevel("NONE")
                    .recentTiltEvents(Collections.emptyList())
                    .scoreBreakdown(
                            TiltScoreBreakdown.builder()
                                    .consecutiveNegativeScore(0)
                                    .lossRatioScore(0)
                                    .emotionDeteriorationScore(0)
                                    .frequencyScore(0)
                                    .build())
                    .recommendations(List.of("분석할 충분한 데이터가 없습니다."))
                    .build();
        }

        // Detect tilt events
        List<TiltEvent> tiltEvents = detectTiltEvents(trades);

        // Calculate tilt score components
        TiltScoreBreakdown breakdown = calculateTiltScoreBreakdown(trades, tiltEvents);

        int totalScore =
                breakdown.getConsecutiveNegativeScore()
                        + breakdown.getLossRatioScore()
                        + breakdown.getEmotionDeteriorationScore()
                        + breakdown.getFrequencyScore();

        String tiltLevel = determineTiltLevel(totalScore);
        List<String> recommendations = generateTiltRecommendations(tiltLevel, breakdown);

        return TiltAnalysis.builder()
                .tiltScore(totalScore)
                .tiltLevel(tiltLevel)
                .recentTiltEvents(tiltEvents.stream().limit(5).collect(Collectors.toList()))
                .scoreBreakdown(breakdown)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 현재 틸트 상태 조회 (최근 7일)
     *
     * @param accountId 계좌 ID
     * @return 틸트 분석 결과
     */
    public TiltAnalysis getCurrentTiltStatus(Long accountId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        return detectTilt(accountId, startDate, endDate);
    }

    /**
     * 감정-행동 상관관계 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 감정-행동 상관관계 분석 결과
     */
    public EmotionBehaviorCorrelation analyzeEmotionBehaviorCorrelation(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing emotion-behavior correlation for account: {}", accountId);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<Object[]> correlationData =
                tradeReviewRepository.getEmotionMistakeCorrelation(
                        accountId, startDateTime, endDateTime);
        List<Object[]> emotionCounts =
                tradeReviewRepository.countTradesByEmotion(accountId, startDateTime, endDateTime);

        // Map emotion to total count
        Map<EmotionState, Long> emotionTotals =
                emotionCounts.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> (EmotionState) row[0], row -> (Long) row[1]));

        List<BehaviorCorrelation> correlations =
                correlationData.stream()
                        .map(
                                row -> {
                                    EmotionState emotion = (EmotionState) row[0];
                                    String mistakeType = (String) row[1];
                                    Long count = (Long) row[2];
                                    Long total = emotionTotals.getOrDefault(emotion, 1L);

                                    BigDecimal strength =
                                            BigDecimal.valueOf(count)
                                                    .divide(
                                                            BigDecimal.valueOf(total),
                                                            4,
                                                            RoundingMode.HALF_UP);

                                    return BehaviorCorrelation.builder()
                                            .emotion(emotion)
                                            .emotionLabel(emotion.getLabel())
                                            .mistakeType(mistakeType)
                                            .occurrences(count.intValue())
                                            .correlationStrength(
                                                    strength.setScale(2, RoundingMode.HALF_UP))
                                            .build();
                                })
                        .collect(Collectors.toList());

        List<BehaviorCorrelation> significant =
                correlations.stream()
                        .filter(
                                c ->
                                        c.getCorrelationStrength()
                                                        .compareTo(
                                                                CORRELATION_SIGNIFICANCE_THRESHOLD)
                                                > 0)
                        .collect(Collectors.toList());

        return EmotionBehaviorCorrelation.builder()
                .correlations(correlations)
                .significantCorrelations(significant)
                .build();
    }

    /**
     * 심리 점수 계산
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 심리 점수
     */
    public PsychologicalScore calculatePsychologicalScore(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Calculating psychological score for account: {}", accountId);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<TradeReview> trades =
                tradeReviewRepository.findTradesForTiltAnalysis(
                        accountId, startDateTime, endDateTime);
        List<TradingJournal> journals =
                tradingJournalRepository.findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                        accountId, startDate, endDate);

        ScoreComponents components = calculateScoreComponents(trades, journals);

        // Weighted formula: focus(0.25) + discipline(0.30) + emotionalStability(0.30) +
        // resilience(0.15)
        BigDecimal overallScore =
                components
                        .getFocusScore()
                        .multiply(new BigDecimal("0.25"))
                        .add(components.getDisciplineScore().multiply(new BigDecimal("0.30")))
                        .add(
                                components
                                        .getEmotionalStabilityScore()
                                        .multiply(new BigDecimal("0.30")))
                        .add(components.getResilienceScore().multiply(new BigDecimal("0.15")))
                        .setScale(2, RoundingMode.HALF_UP);

        String grade = calculateGrade(overallScore);

        // Historical trend (weekly aggregation)
        List<ScoreTrend> trend = calculateScoreTrend(accountId, startDate, endDate);

        return PsychologicalScore.builder()
                .overallScore(overallScore)
                .grade(grade)
                .components(components)
                .historicalTrend(trend)
                .build();
    }

    /**
     * 회복 패턴 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 회복 패턴 분석 결과
     */
    public RecoveryPatternAnalysis analyzeRecoveryPatterns(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing recovery patterns for account: {}", accountId);

        List<TradingJournal> journals =
                tradingJournalRepository.findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                        accountId, startDate, endDate);

        // Sort by date ascending for recovery tracking
        journals.sort(Comparator.comparing(TradingJournal::getJournalDate));

        List<RecoveryEvent> recoveries = findRecoveryEvents(journals);

        BigDecimal avgRecoveryDays =
                recoveries.isEmpty()
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(
                                        recoveries.stream()
                                                .mapToInt(RecoveryEvent::getDaysToRecover)
                                                .average()
                                                .orElse(0))
                                .setScale(2, RoundingMode.HALF_UP);

        String recoveryType = determineRecoveryType(avgRecoveryDays);

        // Analyze factors (from trade reviews during recovery periods)
        List<String> helpful = identifyHelpfulFactors(journals);
        List<String> harmful = identifyHarmfulFactors(journals);

        return RecoveryPatternAnalysis.builder()
                .avgRecoveryDays(avgRecoveryDays)
                .recoveryType(recoveryType)
                .helpfulFactors(helpful)
                .harmfulFactors(harmful)
                .recentRecoveries(recoveries.stream().limit(5).collect(Collectors.toList()))
                .build();
    }

    /**
     * 일일 리듬 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 일일 리듬 분석 결과
     */
    public DailyRhythmAnalysis analyzeDailyRhythm(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing daily rhythm for account: {}", accountId);

        List<TradingJournal> journals =
                tradingJournalRepository.findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                        accountId, startDate, endDate);

        // Morning emotions
        Map<String, EmotionDistribution> morningEmotions =
                analyzeEmotionDistribution(
                        journals.stream()
                                .map(TradingJournal::getMorningEmotion)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()));

        // Evening emotions
        Map<String, EmotionDistribution> eveningEmotions =
                analyzeEmotionDistribution(
                        journals.stream()
                                .map(TradingJournal::getEveningEmotion)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()));

        // Day of week analysis
        Map<String, DayPerformance> dayAnalysis =
                analyzeDayOfWeekPerformance(accountId, startDate, endDate);

        // Find best/worst days
        String bestDay =
                dayAnalysis.entrySet().stream()
                        .filter(e -> e.getValue().getTradeCount() > 0)
                        .max(Comparator.comparing(e -> e.getValue().getWinRate()))
                        .map(Map.Entry::getKey)
                        .orElse("해당없음");

        String worstDay =
                dayAnalysis.entrySet().stream()
                        .filter(e -> e.getValue().getTradeCount() > 0)
                        .min(Comparator.comparing(e -> e.getValue().getWinRate()))
                        .map(Map.Entry::getKey)
                        .orElse("해당없음");

        List<String> recommendations =
                generateRhythmRecommendations(dayAnalysis, morningEmotions, eveningEmotions);

        return DailyRhythmAnalysis.builder()
                .morningEmotions(morningEmotions)
                .eveningEmotions(eveningEmotions)
                .dayOfWeekAnalysis(dayAnalysis)
                .bestTradingDay(bestDay)
                .worstTradingDay(worstDay)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 감정 트리거 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 감정 트리거 분석 결과
     */
    public EmotionTriggerAnalysis analyzeEmotionTriggers(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing emotion triggers for account: {}", accountId);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<TradeReview> trades =
                tradeReviewRepository.findTradesForTiltAnalysis(
                        accountId, startDateTime, endDateTime);

        List<EmotionTrigger> triggers = identifyTriggers(trades);

        List<EmotionTrigger> negativeTriggers =
                triggers.stream()
                        .filter(t -> NEGATIVE_EMOTIONS.contains(t.getResultingEmotion()))
                        .sorted(Comparator.comparing(EmotionTrigger::getOccurrences).reversed())
                        .limit(5)
                        .collect(Collectors.toList());

        List<EmotionTrigger> positiveTriggers =
                triggers.stream()
                        .filter(t -> POSITIVE_EMOTIONS.contains(t.getResultingEmotion()))
                        .sorted(Comparator.comparing(EmotionTrigger::getOccurrences).reversed())
                        .limit(5)
                        .collect(Collectors.toList());

        return EmotionTriggerAnalysis.builder()
                .triggers(triggers)
                .topNegativeTriggers(negativeTriggers)
                .topPositiveTriggers(positiveTriggers)
                .build();
    }

    // ============ HELPER METHODS ============

    private String calculateTransitionRisk(
            EmotionState before, EmotionState after, BigDecimal winRate) {
        // HIGH risk: positive -> negative with low win rate
        if (POSITIVE_EMOTIONS.contains(before)
                && NEGATIVE_EMOTIONS.contains(after)
                && winRate.compareTo(new BigDecimal("50")) < 0) {
            return "HIGH";
        }
        // MEDIUM risk: any -> negative
        if (NEGATIVE_EMOTIONS.contains(after)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<TiltEvent> detectTiltEvents(List<TradeReview> trades) {
        List<TiltEvent> events = new ArrayList<>();
        int consecutiveNegative = 0;
        List<EmotionTransition> sequence = new ArrayList<>();
        BigDecimal tiltLoss = BigDecimal.ZERO;
        LocalDate tiltStartDate = null;
        int tradesAffected = 0;

        for (TradeReview trade : trades) {
            EmotionState before = trade.getEmotionBefore();
            EmotionState after = trade.getEmotionAfter();

            if (before == null || after == null) continue;

            // Check for negative shift
            boolean isNegativeShift =
                    !NEGATIVE_EMOTIONS.contains(before) && NEGATIVE_EMOTIONS.contains(after);
            boolean staysNegative =
                    NEGATIVE_EMOTIONS.contains(before) && NEGATIVE_EMOTIONS.contains(after);

            if (isNegativeShift || staysNegative) {
                consecutiveNegative++;
                if (tiltStartDate == null && trade.getTransaction() != null) {
                    tiltStartDate = trade.getTransaction().getTransactionDate().toLocalDate();
                }

                sequence.add(
                        EmotionTransition.builder()
                                .emotionBefore(before)
                                .emotionBeforeLabel(before.getLabel())
                                .emotionAfter(after)
                                .emotionAfterLabel(after.getLabel())
                                .build());

                BigDecimal pnl = getTradeReviewPnl(trade);
                if (pnl != null && pnl.compareTo(BigDecimal.ZERO) < 0) {
                    tiltLoss = tiltLoss.add(pnl.abs());
                }
                tradesAffected++;

                // Record tilt event when threshold reached
                if (consecutiveNegative >= CONSECUTIVE_NEGATIVE_SHIFT_THRESHOLD) {
                    events.add(
                            TiltEvent.builder()
                                    .date(tiltStartDate)
                                    .consecutiveNegativeShifts(consecutiveNegative)
                                    .transitionSequence(new ArrayList<>(sequence))
                                    .totalLossDuringTilt(tiltLoss.setScale(2, RoundingMode.HALF_UP))
                                    .tradesAffected(tradesAffected)
                                    .build());
                }
            } else {
                // Reset on positive transition
                consecutiveNegative = 0;
                sequence.clear();
                tiltLoss = BigDecimal.ZERO;
                tiltStartDate = null;
                tradesAffected = 0;
            }
        }

        return events;
    }

    private TiltScoreBreakdown calculateTiltScoreBreakdown(
            List<TradeReview> trades, List<TiltEvent> tiltEvents) {
        // 1. Consecutive negative score (max 40)
        int maxConsecutive =
                tiltEvents.stream()
                        .mapToInt(TiltEvent::getConsecutiveNegativeShifts)
                        .max()
                        .orElse(0);
        int consecutiveScore = Math.min(40, maxConsecutive * 10);

        // 2. Loss ratio during tilt (max 30)
        BigDecimal totalLoss =
                trades.stream()
                        .map(this::getTradeReviewPnl)
                        .filter(pnl -> pnl != null && pnl.compareTo(BigDecimal.ZERO) < 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .abs();

        BigDecimal tiltLoss =
                tiltEvents.stream()
                        .map(TiltEvent::getTotalLossDuringTilt)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        int lossRatioScore =
                totalLoss.compareTo(BigDecimal.ZERO) == 0
                        ? 0
                        : Math.min(
                                30,
                                tiltLoss.multiply(new BigDecimal("100"))
                                                .divide(totalLoss, 0, RoundingMode.HALF_UP)
                                                .intValue()
                                        * 30
                                        / 100);

        // 3. Emotion deterioration speed (max 20)
        long rapidDeteriorations =
                tiltEvents.stream().filter(e -> e.getTradesAffected() <= 3).count();
        int deteriorationScore = Math.min(20, (int) rapidDeteriorations * 5);

        // 4. Frequency score (max 10)
        int frequencyScore = Math.min(10, tiltEvents.size() * 2);

        return TiltScoreBreakdown.builder()
                .consecutiveNegativeScore(consecutiveScore)
                .lossRatioScore(lossRatioScore)
                .emotionDeteriorationScore(deteriorationScore)
                .frequencyScore(frequencyScore)
                .build();
    }

    private String determineTiltLevel(int score) {
        if (score >= TILT_THRESHOLD_SEVERE) return "SEVERE";
        if (score >= TILT_THRESHOLD_MODERATE) return "MODERATE";
        if (score >= TILT_THRESHOLD_MILD) return "MILD";
        return "NONE";
    }

    private List<String> generateTiltRecommendations(String level, TiltScoreBreakdown breakdown) {
        List<String> recommendations = new ArrayList<>();

        switch (level) {
            case "SEVERE":
                recommendations.add("즉시 거래를 중단하고 휴식을 취하세요.");
                recommendations.add("손실 한도를 설정하고 도달 시 자동으로 거래를 멈추세요.");
                break;
            case "MODERATE":
                recommendations.add("포지션 크기를 줄이고 신중하게 거래하세요.");
                recommendations.add("거래 전 감정 상태를 체크하는 습관을 들이세요.");
                break;
            case "MILD":
                recommendations.add("감정 일기를 작성하여 패턴을 파악하세요.");
                recommendations.add("스트레스 관리 기법을 연습하세요.");
                break;
            default:
                recommendations.add("현재 감정 관리가 잘 되고 있습니다. 유지하세요!");
        }

        if (breakdown.getConsecutiveNegativeScore() > 20) {
            recommendations.add("연속 손실 후 쉬어가는 규칙을 만드세요.");
        }

        return recommendations;
    }

    private ScoreComponents calculateScoreComponents(
            List<TradeReview> trades, List<TradingJournal> journals) {
        if (trades.isEmpty() && journals.isEmpty()) {
            return ScoreComponents.builder()
                    .focusScore(BigDecimal.valueOf(50))
                    .disciplineScore(BigDecimal.valueOf(50))
                    .emotionalStabilityScore(BigDecimal.valueOf(50))
                    .resilienceScore(BigDecimal.valueOf(50))
                    .build();
        }

        // Focus: based on trade plan adherence (from rating if available)
        BigDecimal focusScore =
                trades.stream()
                        .map(TradeReview::getRatingScore)
                        .filter(Objects::nonNull)
                        .map(rating -> BigDecimal.valueOf(rating * 20L)) // 1-5 -> 20-100
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(Math.max(1, trades.size())),
                                2,
                                RoundingMode.HALF_UP);

        // Discipline: inverse of mistake frequency (based on followedPlan)
        long notFollowedCount =
                trades.stream().filter(t -> Boolean.FALSE.equals(t.getFollowedPlan())).count();
        BigDecimal disciplineScore =
                BigDecimal.valueOf(100)
                        .subtract(
                                BigDecimal.valueOf(
                                        notFollowedCount * 100 / Math.max(1, trades.size())))
                        .max(BigDecimal.ZERO);

        // Emotional stability: ratio of stable emotions
        long stableEmotions =
                trades.stream().filter(t -> t.getEmotionBefore() == t.getEmotionAfter()).count();
        BigDecimal stabilityScore =
                BigDecimal.valueOf(stableEmotions * 100 / Math.max(1, trades.size()));

        // Resilience: recovery from negative emotions
        long recoveriesFromNegative =
                trades.stream()
                        .filter(
                                t ->
                                        NEGATIVE_EMOTIONS.contains(t.getEmotionBefore())
                                                && POSITIVE_EMOTIONS.contains(t.getEmotionAfter()))
                        .count();
        long totalNegative =
                trades.stream()
                        .filter(t -> NEGATIVE_EMOTIONS.contains(t.getEmotionBefore()))
                        .count();
        BigDecimal resilienceScore =
                totalNegative == 0
                        ? BigDecimal.valueOf(70)
                        : BigDecimal.valueOf(recoveriesFromNegative * 100 / totalNegative);

        return ScoreComponents.builder()
                .focusScore(focusScore.setScale(2, RoundingMode.HALF_UP))
                .disciplineScore(disciplineScore.setScale(2, RoundingMode.HALF_UP))
                .emotionalStabilityScore(stabilityScore.setScale(2, RoundingMode.HALF_UP))
                .resilienceScore(resilienceScore.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private String calculateGrade(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (score.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (score.compareTo(new BigDecimal("70")) >= 0) return "C";
        if (score.compareTo(new BigDecimal("60")) >= 0) return "D";
        return "F";
    }

    private List<ScoreTrend> calculateScoreTrend(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        // Weekly aggregation for trend
        List<ScoreTrend> trend = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            LocalDate weekEnd = current.plusDays(6);
            if (weekEnd.isAfter(endDate)) weekEnd = endDate;

            LocalDateTime weekStartDateTime = current.atStartOfDay();
            LocalDateTime weekEndDateTime = weekEnd.atTime(23, 59, 59);

            List<TradeReview> weekTrades =
                    tradeReviewRepository.findTradesForTiltAnalysis(
                            accountId, weekStartDateTime, weekEndDateTime);
            List<TradingJournal> weekJournals =
                    tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    accountId, current, weekEnd);

            ScoreComponents components = calculateScoreComponents(weekTrades, weekJournals);
            BigDecimal weekScore =
                    components
                            .getFocusScore()
                            .multiply(new BigDecimal("0.25"))
                            .add(components.getDisciplineScore().multiply(new BigDecimal("0.30")))
                            .add(
                                    components
                                            .getEmotionalStabilityScore()
                                            .multiply(new BigDecimal("0.30")))
                            .add(components.getResilienceScore().multiply(new BigDecimal("0.15")))
                            .setScale(2, RoundingMode.HALF_UP);

            trend.add(
                    ScoreTrend.builder()
                            .date(current)
                            .score(weekScore)
                            .grade(calculateGrade(weekScore))
                            .build());

            current = current.plusDays(7);
        }

        return trend;
    }

    private List<RecoveryEvent> findRecoveryEvents(List<TradingJournal> journals) {
        List<RecoveryEvent> events = new ArrayList<>();
        LocalDate negativeStart = null;
        EmotionState negativeEmotion = null;

        for (TradingJournal journal : journals) {
            EmotionState morning = journal.getMorningEmotion();
            EmotionState evening = journal.getEveningEmotion();

            if (morning == null && evening == null) continue;

            EmotionState dominantEmotion = evening != null ? evening : morning;

            if (NEGATIVE_EMOTIONS.contains(dominantEmotion)) {
                if (negativeStart == null) {
                    negativeStart = journal.getJournalDate();
                    negativeEmotion = dominantEmotion;
                }
            } else if (negativeStart != null) {
                // Recovery happened
                int daysToRecover =
                        (int)
                                java.time.temporal.ChronoUnit.DAYS.between(
                                        negativeStart, journal.getJournalDate());

                events.add(
                        RecoveryEvent.builder()
                                .startDate(negativeStart)
                                .recoveryDate(journal.getJournalDate())
                                .daysToRecover(daysToRecover)
                                .fromEmotion(negativeEmotion)
                                .toEmotion(dominantEmotion)
                                .build());

                negativeStart = null;
                negativeEmotion = null;
            }
        }

        return events;
    }

    private String determineRecoveryType(BigDecimal avgDays) {
        if (avgDays.compareTo(BigDecimal.ONE) <= 0) return "QUICK_BOUNCER";
        if (avgDays.compareTo(new BigDecimal("3")) <= 0) return "STEADY_RECOVERER";
        return "SLOW_HEALER";
    }

    private List<String> identifyHelpfulFactors(List<TradingJournal> journals) {
        // Analyze what appears during recovery
        List<String> factors = new ArrayList<>();
        factors.add("규칙적인 거래 일지 작성");
        factors.add("작은 포지션으로 재진입");
        factors.add("휴식 후 거래 재개");
        return factors;
    }

    private List<String> identifyHarmfulFactors(List<TradingJournal> journals) {
        List<String> factors = new ArrayList<>();
        factors.add("즉시 복구 시도");
        factors.add("포지션 크기 증가");
        factors.add("감정적 거래");
        return factors;
    }

    private Map<String, EmotionDistribution> analyzeEmotionDistribution(
            List<EmotionState> emotions) {
        Map<EmotionState, Long> counts =
                emotions.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        int total = emotions.size();

        return counts.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey().name(),
                                e ->
                                        EmotionDistribution.builder()
                                                .emotion(e.getKey())
                                                .emotionLabel(e.getKey().getLabel())
                                                .count(e.getValue().intValue())
                                                .percentage(
                                                        BigDecimal.valueOf(
                                                                        e.getValue()
                                                                                * 100.0
                                                                                / Math.max(
                                                                                        1, total))
                                                                .setScale(2, RoundingMode.HALF_UP))
                                                .build()));
    }

    private Map<String, DayPerformance> analyzeDayOfWeekPerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<Object[]> stats =
                tradeReviewRepository.getDayOfWeekStats(accountId, startDateTime, endDateTime);

        String[] dayNames = {"", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        Map<String, DayPerformance> result = new LinkedHashMap<>();

        // Group by day of week
        Map<Integer, List<Object[]>> groupedByDay =
                stats.stream().collect(Collectors.groupingBy(row -> ((Number) row[0]).intValue()));

        for (int day = 1; day <= 7; day++) {
            List<Object[]> dayStats = groupedByDay.getOrDefault(day, Collections.emptyList());

            if (dayStats.isEmpty()) {
                result.put(
                        dayNames[day],
                        DayPerformance.builder()
                                .dayOfWeek(dayNames[day])
                                .tradeCount(0)
                                .winRate(BigDecimal.ZERO)
                                .avgPnl(BigDecimal.ZERO)
                                .dominantEmotion(null)
                                .dominantEmotionLabel("데이터 없음")
                                .build());
            } else {
                int totalTrades =
                        dayStats.stream().mapToInt(row -> ((Number) row[1]).intValue()).sum();
                double avgWinRate =
                        dayStats.stream()
                                .mapToDouble(row -> ((Number) row[2]).doubleValue())
                                .average()
                                .orElse(0);
                double avgPnl =
                        dayStats.stream()
                                .mapToDouble(
                                        row ->
                                                row[3] != null
                                                        ? ((Number) row[3]).doubleValue()
                                                        : 0.0)
                                .average()
                                .orElse(0);

                // Find dominant emotion
                EmotionState dominant =
                        dayStats.stream()
                                .max(Comparator.comparing(row -> ((Number) row[1]).intValue()))
                                .map(row -> (EmotionState) row[4])
                                .orElse(null);

                result.put(
                        dayNames[day],
                        DayPerformance.builder()
                                .dayOfWeek(dayNames[day])
                                .tradeCount(totalTrades)
                                .winRate(
                                        BigDecimal.valueOf(avgWinRate)
                                                .setScale(2, RoundingMode.HALF_UP))
                                .avgPnl(
                                        BigDecimal.valueOf(avgPnl)
                                                .setScale(2, RoundingMode.HALF_UP))
                                .dominantEmotion(dominant)
                                .dominantEmotionLabel(
                                        dominant != null ? dominant.getLabel() : "데이터 없음")
                                .build());
            }
        }

        return result;
    }

    private List<String> generateRhythmRecommendations(
            Map<String, DayPerformance> dayAnalysis,
            Map<String, EmotionDistribution> morningEmotions,
            Map<String, EmotionDistribution> eveningEmotions) {

        List<String> recommendations = new ArrayList<>();

        // Find best performing day
        DayPerformance bestDay =
                dayAnalysis.values().stream()
                        .filter(d -> d.getTradeCount() > 0)
                        .max(Comparator.comparing(DayPerformance::getWinRate))
                        .orElse(null);

        if (bestDay != null && bestDay.getWinRate().compareTo(BigDecimal.valueOf(60)) >= 0) {
            recommendations.add(bestDay.getDayOfWeek() + "에 가장 좋은 성과를 보이고 있습니다. 이 날 더 집중하세요.");
        }

        // Check for negative morning emotions
        long negativeMornings =
                morningEmotions.values().stream()
                        .filter(e -> NEGATIVE_EMOTIONS.contains(e.getEmotion()))
                        .mapToLong(EmotionDistribution::getCount)
                        .sum();

        long totalMornings =
                morningEmotions.values().stream().mapToLong(EmotionDistribution::getCount).sum();

        if (totalMornings > 0 && negativeMornings > totalMornings / 2) {
            recommendations.add("아침에 부정적인 감정이 많습니다. 거래 전 명상이나 운동을 시도해보세요.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("전반적으로 균형 잡힌 감정 리듬을 유지하고 있습니다.");
        }

        return recommendations;
    }

    private List<EmotionTrigger> identifyTriggers(List<TradeReview> trades) {
        // Simplified trigger identification based on PnL patterns
        Map<String, Map<EmotionState, Integer>> triggerCounts = new HashMap<>();
        Map<String, Map<EmotionState, BigDecimal>> triggerPnl = new HashMap<>();

        for (TradeReview trade : trades) {
            if (trade.getEmotionAfter() == null) continue;

            String trigger = identifyTriggerFromTrade(trade);
            if (trigger == null) continue;

            triggerCounts
                    .computeIfAbsent(trigger, k -> new HashMap<>())
                    .merge(trade.getEmotionAfter(), 1, Integer::sum);

            BigDecimal pnl = getTradeReviewPnl(trade);
            triggerPnl
                    .computeIfAbsent(trigger, k -> new HashMap<>())
                    .merge(
                            trade.getEmotionAfter(),
                            pnl != null ? pnl : BigDecimal.ZERO,
                            BigDecimal::add);
        }

        List<EmotionTrigger> triggers = new ArrayList<>();

        for (Map.Entry<String, Map<EmotionState, Integer>> entry : triggerCounts.entrySet()) {
            for (Map.Entry<EmotionState, Integer> emotionEntry : entry.getValue().entrySet()) {
                BigDecimal totalPnl =
                        triggerPnl
                                .get(entry.getKey())
                                .getOrDefault(emotionEntry.getKey(), BigDecimal.ZERO);
                BigDecimal avgPnl =
                        totalPnl.divide(
                                BigDecimal.valueOf(emotionEntry.getValue()),
                                2,
                                RoundingMode.HALF_UP);

                triggers.add(
                        EmotionTrigger.builder()
                                .trigger(entry.getKey())
                                .resultingEmotion(emotionEntry.getKey())
                                .resultingEmotionLabel(emotionEntry.getKey().getLabel())
                                .occurrences(emotionEntry.getValue())
                                .avgImpactOnPnl(avgPnl)
                                .build());
            }
        }

        return triggers;
    }

    private String identifyTriggerFromTrade(TradeReview trade) {
        BigDecimal pnl = getTradeReviewPnl(trade);
        if (pnl == null) return null;

        if (pnl.compareTo(BigDecimal.valueOf(-1000)) < 0) {
            return "큰 손실";
        } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            return "손실";
        } else if (pnl.compareTo(BigDecimal.valueOf(1000)) > 0) {
            return "큰 수익";
        } else if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            return "수익";
        }
        return "본전";
    }

    /**
     * TradeReview에서 PnL 추출 (Transaction 관계 통해)
     *
     * @param trade TradeReview
     * @return PnL 값 (없으면 null)
     */
    private BigDecimal getTradeReviewPnl(TradeReview trade) {
        if (trade.getTransaction() != null) {
            return trade.getTransaction().getRealizedPnl();
        }
        return null;
    }

    /**
     * 심리-성과 상관관계 분석
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 심리-성과 상관관계 분석 결과
     */
    public PsychologyPerformanceCorrelation analyzePerformanceCorrelation(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info(
                "Analyzing psychology-performance correlation for account: {} from {} to {}",
                accountId,
                startDate,
                endDate);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        // 1. 감정별 성과 통계
        List<EmotionPerformanceStats> emotionPerformance =
                calculateEmotionPerformanceStats(accountId, startDateTime, endDateTime);

        // 2. 집중도 점수 상관관계
        ScoreCorrelation focusCorrelation =
                calculateScoreCorrelation(accountId, startDate, endDate, "focusScore", "집중도");

        // 3. 규율 점수 상관관계
        ScoreCorrelation disciplineCorrelation =
                calculateScoreCorrelation(accountId, startDate, endDate, "disciplineScore", "규율");

        // 4. 최적 상태 분석
        OptimalState optimalState =
                calculateOptimalState(
                        emotionPerformance,
                        focusCorrelation.getGroups(),
                        disciplineCorrelation.getGroups());

        // 5. 계획 준수 통계
        PlanAdherenceStats planAdherence =
                calculatePlanAdherenceStats(accountId, startDateTime, endDateTime);

        // 6. 추천사항 생성
        List<String> recommendations =
                generatePerformanceRecommendations(
                        emotionPerformance,
                        focusCorrelation,
                        disciplineCorrelation,
                        optimalState,
                        planAdherence);

        return PsychologyPerformanceCorrelation.builder()
                .emotionPerformance(emotionPerformance)
                .focusCorrelation(focusCorrelation)
                .disciplineCorrelation(disciplineCorrelation)
                .optimalState(optimalState)
                .planAdherence(planAdherence)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 감정별 성과 통계 계산
     *
     * @param accountId 계좌 ID
     * @param startDateTime 시작 일시
     * @param endDateTime 종료 일시
     * @return 감정별 성과 통계 목록
     */
    private List<EmotionPerformanceStats> calculateEmotionPerformanceStats(
            Long accountId, LocalDateTime startDateTime, LocalDateTime endDateTime) {

        List<Object[]> stats =
                tradeReviewRepository.getEmotionPerformanceStats(
                        accountId, startDateTime, endDateTime);

        return stats.stream()
                .map(
                        row -> {
                            EmotionState emotion = (EmotionState) row[0];
                            Long count = (Long) row[1];
                            Long winCount = (Long) row[2];
                            Double avgPnl = row[3] != null ? (Double) row[3] : 0.0;
                            Double totalPnl = row[4] != null ? (Double) row[4] : 0.0;
                            Double avgRMultiple = row[5] != null ? (Double) row[5] : 0.0;

                            BigDecimal winRate =
                                    count > 0
                                            ? BigDecimal.valueOf(winCount * 100.0 / count)
                                                    .setScale(2, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO;

                            // 승률 기반 등급: 60%+ → A, 55%+ → B, 50%+ → C, 45%+ → D, else → F
                            String performanceGrade = calculateWinRateGrade(winRate);

                            return EmotionPerformanceStats.builder()
                                    .emotion(emotion)
                                    .emotionLabel(emotion.getLabel())
                                    .tradeCount(count.intValue())
                                    .winCount(winCount.intValue())
                                    .winRate(winRate)
                                    .avgPnl(
                                            BigDecimal.valueOf(avgPnl)
                                                    .setScale(2, RoundingMode.HALF_UP))
                                    .totalPnl(
                                            BigDecimal.valueOf(totalPnl)
                                                    .setScale(2, RoundingMode.HALF_UP))
                                    .avgRMultiple(
                                            BigDecimal.valueOf(avgRMultiple)
                                                    .setScale(2, RoundingMode.HALF_UP))
                                    .performanceGrade(performanceGrade)
                                    .build();
                        })
                .sorted(Comparator.comparing(EmotionPerformanceStats::getTotalPnl).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 승률 기반 등급 계산
     *
     * @param winRate 승률 (%)
     * @return 등급 (A/B/C/D/F)
     */
    private String calculateWinRateGrade(BigDecimal winRate) {
        if (winRate.compareTo(new BigDecimal("60")) >= 0) return "A";
        if (winRate.compareTo(new BigDecimal("55")) >= 0) return "B";
        if (winRate.compareTo(new BigDecimal("50")) >= 0) return "C";
        if (winRate.compareTo(new BigDecimal("45")) >= 0) return "D";
        return "F";
    }

    /**
     * 점수별 상관관계 계산 (집중도 또는 규율)
     *
     * @param accountId 계좌 ID
     * @param startDate 시작일
     * @param endDate 종료일
     * @param scoreField 점수 필드명 ("focusScore" or "disciplineScore")
     * @param scoreName 점수 표시명
     * @return 점수 상관관계
     */
    private ScoreCorrelation calculateScoreCorrelation(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            String scoreField,
            String scoreName) {

        List<TradingJournal> journals =
                tradingJournalRepository.findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                        accountId, startDate, endDate);

        // 점수별 그룹화 (1-5)
        Map<Integer, List<TradingJournal>> groupedByScore = new HashMap<>();
        for (int score = 1; score <= 5; score++) {
            groupedByScore.put(score, new ArrayList<>());
        }

        for (TradingJournal journal : journals) {
            Integer score =
                    "focusScore".equals(scoreField)
                            ? journal.getFocusScore()
                            : journal.getDisciplineScore();
            if (score != null && score >= 1 && score <= 5) {
                groupedByScore.get(score).add(journal);
            }
        }

        // 각 그룹의 성과 계산
        List<ScorePerformanceGroup> groups = new ArrayList<>();
        for (int score = 1; score <= 5; score++) {
            List<TradingJournal> scoreJournals = groupedByScore.get(score);

            if (scoreJournals.isEmpty()) {
                groups.add(
                        ScorePerformanceGroup.builder()
                                .score(score)
                                .tradeCount(0)
                                .winRate(BigDecimal.ZERO)
                                .avgPnl(BigDecimal.ZERO)
                                .totalPnl(BigDecimal.ZERO)
                                .build());
            } else {
                int totalTrades =
                        scoreJournals.stream()
                                .mapToInt(
                                        j ->
                                                j.getTradeSummaryCount() != null
                                                        ? j.getTradeSummaryCount()
                                                        : 0)
                                .sum();

                BigDecimal avgWinRate =
                        scoreJournals.stream()
                                .map(TradingJournal::getTradeSummaryWinRate)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(
                                        BigDecimal.valueOf(Math.max(1, scoreJournals.size())),
                                        2,
                                        RoundingMode.HALF_UP);

                BigDecimal totalPnl =
                        scoreJournals.stream()
                                .map(TradingJournal::getTradeSummaryProfit)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP);

                BigDecimal avgPnl =
                        totalTrades > 0
                                ? totalPnl.divide(
                                        BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                groups.add(
                        ScorePerformanceGroup.builder()
                                .score(score)
                                .tradeCount(totalTrades)
                                .winRate(avgWinRate)
                                .avgPnl(avgPnl)
                                .totalPnl(totalPnl)
                                .build());
            }
        }

        // Pearson 상관계수 계산
        BigDecimal correlationCoefficient = calculatePearsonCorrelation(groups);

        // 상관 강도 결정
        String correlationStrength = determineCorrelationStrength(correlationCoefficient);

        // 추천사항 생성
        String recommendation = generateScoreRecommendation(scoreName, correlationStrength);

        return ScoreCorrelation.builder()
                .scoreName(scoreName)
                .groups(groups)
                .correlationCoefficient(correlationCoefficient)
                .correlationStrength(correlationStrength)
                .recommendation(recommendation)
                .build();
    }

    /**
     * Pearson 상관계수 계산 (점수와 평균 PnL 간)
     *
     * @param groups 점수별 성과 그룹
     * @return 상관계수
     */
    private BigDecimal calculatePearsonCorrelation(List<ScorePerformanceGroup> groups) {
        // 유효한 데이터만 필터링 (거래 수 > 0)
        List<ScorePerformanceGroup> validGroups =
                groups.stream().filter(g -> g.getTradeCount() > 0).collect(Collectors.toList());

        if (validGroups.size() < 2) {
            return BigDecimal.ZERO;
        }

        // 평균 계산
        double meanScore =
                validGroups.stream().mapToInt(ScorePerformanceGroup::getScore).average().orElse(0);
        double meanPnl =
                validGroups.stream()
                        .mapToDouble(g -> g.getAvgPnl().doubleValue())
                        .average()
                        .orElse(0);

        // 공분산 및 표준편차 계산
        double covariance = 0.0;
        double scoreVariance = 0.0;
        double pnlVariance = 0.0;

        for (ScorePerformanceGroup group : validGroups) {
            double scoreDiff = group.getScore() - meanScore;
            double pnlDiff = group.getAvgPnl().doubleValue() - meanPnl;

            covariance += scoreDiff * pnlDiff;
            scoreVariance += scoreDiff * scoreDiff;
            pnlVariance += pnlDiff * pnlDiff;
        }

        // 상관계수 = 공분산 / (score 표준편차 * pnl 표준편차)
        double denominator = Math.sqrt(scoreVariance * pnlVariance);
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }

        double correlation = covariance / denominator;
        return BigDecimal.valueOf(correlation).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 상관 강도 결정
     *
     * @param coefficient 상관계수
     * @return 상관 강도 (STRONG/MODERATE/WEAK/NONE)
     */
    private String determineCorrelationStrength(BigDecimal coefficient) {
        BigDecimal absCoefficient = coefficient.abs();

        if (absCoefficient.compareTo(new BigDecimal("0.7")) >= 0) return "STRONG";
        if (absCoefficient.compareTo(new BigDecimal("0.4")) >= 0) return "MODERATE";
        if (absCoefficient.compareTo(new BigDecimal("0.2")) >= 0) return "WEAK";
        return "NONE";
    }

    /**
     * 점수 상관관계 추천사항 생성
     *
     * @param scoreName 점수명
     * @param strength 상관 강도
     * @return 추천사항
     */
    private String generateScoreRecommendation(String scoreName, String strength) {
        switch (strength) {
            case "STRONG":
                return scoreName + " 점수가 성과에 큰 영향을 미칩니다. " + scoreName + " 향상에 집중하세요.";
            case "MODERATE":
                return scoreName + " 점수가 성과와 어느 정도 연관이 있습니다. " + scoreName + " 관리가 도움이 될 수 있습니다.";
            case "WEAK":
                return scoreName + " 점수가 성과에 미치는 영향이 미미합니다.";
            default:
                return scoreName + " 점수와 성과 간 명확한 상관관계가 없습니다. 다른 요인을 고려하세요.";
        }
    }

    /**
     * 최적 상태 계산
     *
     * @param emotionPerformance 감정별 성과
     * @param focusGroups 집중도 그룹
     * @param disciplineGroups 규율 그룹
     * @return 최적 상태
     */
    private OptimalState calculateOptimalState(
            List<EmotionPerformanceStats> emotionPerformance,
            List<ScorePerformanceGroup> focusGroups,
            List<ScorePerformanceGroup> disciplineGroups) {

        // 최고 승률 감정 찾기
        EmotionPerformanceStats bestEmotion =
                emotionPerformance.stream()
                        .max(Comparator.comparing(EmotionPerformanceStats::getWinRate))
                        .orElse(null);

        // 집중도 최적 범위 (평균 PnL 기준)
        ScorePerformanceGroup bestFocus =
                focusGroups.stream()
                        .filter(g -> g.getTradeCount() > 0)
                        .max(Comparator.comparing(ScorePerformanceGroup::getAvgPnl))
                        .orElse(null);

        // 규율 최적 범위
        ScorePerformanceGroup bestDiscipline =
                disciplineGroups.stream()
                        .filter(g -> g.getTradeCount() > 0)
                        .max(Comparator.comparing(ScorePerformanceGroup::getAvgPnl))
                        .orElse(null);

        // 전체 평균 승률 및 PnL 계산
        BigDecimal overallWinRate =
                emotionPerformance.stream()
                        .map(e -> e.getWinRate().multiply(BigDecimal.valueOf(e.getTradeCount())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(
                                        Math.max(
                                                1,
                                                emotionPerformance.stream()
                                                        .mapToInt(
                                                                EmotionPerformanceStats
                                                                        ::getTradeCount)
                                                        .sum())),
                                2,
                                RoundingMode.HALF_UP);

        BigDecimal overallAvgPnl =
                emotionPerformance.stream()
                        .map(EmotionPerformanceStats::getTotalPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(
                                        Math.max(
                                                1,
                                                emotionPerformance.stream()
                                                        .mapToInt(
                                                                EmotionPerformanceStats
                                                                        ::getTradeCount)
                                                        .sum())),
                                2,
                                RoundingMode.HALF_UP);

        // 최적 상태 대비 개선 가능 퍼센트
        BigDecimal optimalWinRate = bestEmotion != null ? bestEmotion.getWinRate() : overallWinRate;
        BigDecimal optimalAvgPnl = bestEmotion != null ? bestEmotion.getAvgPnl() : overallAvgPnl;

        BigDecimal improvementPercent =
                overallWinRate.compareTo(BigDecimal.ZERO) > 0
                        ? optimalWinRate
                                .subtract(overallWinRate)
                                .multiply(new BigDecimal("100"))
                                .divide(overallWinRate, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return OptimalState.builder()
                .bestEmotion(bestEmotion != null ? bestEmotion.getEmotion() : null)
                .bestEmotionLabel(bestEmotion != null ? bestEmotion.getEmotionLabel() : "데이터 없음")
                .optimalFocusMin(bestFocus != null ? bestFocus.getScore() : null)
                .optimalFocusMax(bestFocus != null ? bestFocus.getScore() : null)
                .optimalDisciplineMin(bestDiscipline != null ? bestDiscipline.getScore() : null)
                .optimalDisciplineMax(bestDiscipline != null ? bestDiscipline.getScore() : null)
                .optimalWinRate(optimalWinRate)
                .optimalAvgPnl(optimalAvgPnl)
                .overallWinRate(overallWinRate)
                .overallAvgPnl(overallAvgPnl)
                .improvementPercent(improvementPercent)
                .build();
    }

    /**
     * 계획 준수 통계 계산
     *
     * @param accountId 계좌 ID
     * @param startDateTime 시작 일시
     * @param endDateTime 종료 일시
     * @return 계획 준수 통계
     */
    private PlanAdherenceStats calculatePlanAdherenceStats(
            Long accountId, LocalDateTime startDateTime, LocalDateTime endDateTime) {

        List<Object[]> stats =
                tradeReviewRepository.getPlanAdherencePerformance(
                        accountId, startDateTime, endDateTime);

        // 준수/미준수 데이터 분리
        Long followedCount = 0L;
        BigDecimal followedWinRate = BigDecimal.ZERO;
        BigDecimal followedAvgPnl = BigDecimal.ZERO;

        Long notFollowedCount = 0L;
        BigDecimal notFollowedWinRate = BigDecimal.ZERO;
        BigDecimal notFollowedAvgPnl = BigDecimal.ZERO;

        for (Object[] row : stats) {
            Boolean followedPlan = (Boolean) row[0];
            Long count = (Long) row[1];
            Long winCount = (Long) row[2];
            Double avgPnl = row[3] != null ? (Double) row[3] : 0.0;

            BigDecimal winRate =
                    count > 0
                            ? BigDecimal.valueOf(winCount * 100.0 / count)
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            if (Boolean.TRUE.equals(followedPlan)) {
                followedCount = count;
                followedWinRate = winRate;
                followedAvgPnl = BigDecimal.valueOf(avgPnl).setScale(2, RoundingMode.HALF_UP);
            } else {
                notFollowedCount = count;
                notFollowedWinRate = winRate;
                notFollowedAvgPnl = BigDecimal.valueOf(avgPnl).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // 차이 계산
        BigDecimal winRateDifference = followedWinRate.subtract(notFollowedWinRate);
        BigDecimal pnlDifference = followedAvgPnl.subtract(notFollowedAvgPnl);

        return PlanAdherenceStats.builder()
                .followedCount(followedCount.intValue())
                .followedWinRate(followedWinRate)
                .followedAvgPnl(followedAvgPnl)
                .notFollowedCount(notFollowedCount.intValue())
                .notFollowedWinRate(notFollowedWinRate)
                .notFollowedAvgPnl(notFollowedAvgPnl)
                .winRateDifference(winRateDifference)
                .pnlDifference(pnlDifference)
                .build();
    }

    /**
     * 성과 상관관계 추천사항 생성
     *
     * @param emotionPerformance 감정별 성과
     * @param focusCorrelation 집중도 상관관계
     * @param disciplineCorrelation 규율 상관관계
     * @param optimalState 최적 상태
     * @param planAdherence 계획 준수 통계
     * @return 추천사항 목록
     */
    private List<String> generatePerformanceRecommendations(
            List<EmotionPerformanceStats> emotionPerformance,
            ScoreCorrelation focusCorrelation,
            ScoreCorrelation disciplineCorrelation,
            OptimalState optimalState,
            PlanAdherenceStats planAdherence) {

        List<String> recommendations = new ArrayList<>();

        // 1. 최고 성과 감정 추천
        if (optimalState.getBestEmotion() != null) {
            recommendations.add(
                    String.format(
                            "%s 감정 상태에서 가장 좋은 성과(승률: %s%%)를 보입니다. 이 상태를 유지하도록 노력하세요.",
                            optimalState.getBestEmotionLabel(), optimalState.getOptimalWinRate()));
        }

        // 2. 집중도 상관관계 추천
        if ("STRONG".equals(focusCorrelation.getCorrelationStrength())
                || "MODERATE".equals(focusCorrelation.getCorrelationStrength())) {
            recommendations.add(focusCorrelation.getRecommendation());
        }

        // 3. 규율 상관관계 추천
        if ("STRONG".equals(disciplineCorrelation.getCorrelationStrength())
                || "MODERATE".equals(disciplineCorrelation.getCorrelationStrength())) {
            recommendations.add(disciplineCorrelation.getRecommendation());
        }

        // 4. 계획 준수 추천
        if (planAdherence.getWinRateDifference().compareTo(new BigDecimal("5")) > 0) {
            recommendations.add(
                    String.format(
                            "계획을 준수한 거래의 승률이 %s%% 포인트 더 높습니다. 거래 계획을 철저히 따르세요.",
                            planAdherence.getWinRateDifference()));
        } else if (planAdherence.getWinRateDifference().compareTo(new BigDecimal("-5")) < 0) {
            recommendations.add("계획 미준수 시 오히려 성과가 더 좋습니다. 계획을 재검토하고 유연성을 고려하세요.");
        }

        // 5. 개선 가능성 추천
        if (optimalState.getImprovementPercent().compareTo(new BigDecimal("10")) > 0) {
            recommendations.add(
                    String.format(
                            "최적 상태를 유지하면 현재 대비 승률을 %s%% 개선할 수 있습니다.",
                            optimalState.getImprovementPercent()));
        }

        // 기본 추천
        if (recommendations.isEmpty()) {
            recommendations.add("현재 심리 상태와 성과 간 명확한 패턴이 발견되지 않았습니다. 더 많은 데이터가 필요합니다.");
        }

        return recommendations;
    }
}
