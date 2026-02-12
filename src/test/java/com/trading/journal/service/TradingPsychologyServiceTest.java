package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.trading.journal.dto.TradingPsychologyDto;
import com.trading.journal.dto.TradingPsychologyDto.*;
import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradeReview;
import com.trading.journal.entity.TradingJournal;
import com.trading.journal.entity.Transaction;
import com.trading.journal.repository.TradeReviewRepository;
import com.trading.journal.repository.TradingJournalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingPsychologyServiceTest {

    @Mock private TradeReviewRepository tradeReviewRepository;
    @Mock private TradingJournalRepository tradingJournalRepository;

    @InjectMocks private TradingPsychologyService tradingPsychologyService;

    private Long accountId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        accountId = 1L;
        startDate = LocalDate.of(2024, 1, 1);
        endDate = LocalDate.of(2024, 3, 31);
    }

    @Nested
    @DisplayName("종합 심리 분석 테스트")
    class GetFullAnalysisTests {

        @Test
        @DisplayName("종합 심리 분석 조회 성공")
        void getFullAnalysis_WithData_ReturnsCompleteAnalysis() {
            // Given
            List<TradeReview> tradeReviews = createSampleTradeReviews();
            List<TradingJournal> journals = createSampleJournals();

            when(tradeReviewRepository.getEmotionTransitionStats(any(), any(), any()))
                    .thenReturn(createEmotionTransitionStats());
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(tradeReviews);
            when(tradeReviewRepository.getEmotionMistakeCorrelation(any(), any(), any()))
                    .thenReturn(createEmotionMistakeCorrelation());
            when(tradeReviewRepository.countTradesByEmotion(any(), any(), any()))
                    .thenReturn(createEmotionCounts());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);
            when(tradeReviewRepository.getDayOfWeekStats(any(), any(), any()))
                    .thenReturn(createDayOfWeekStats());

            // When
            TradingPsychologyDto result =
                    tradingPsychologyService.getFullAnalysis(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTransitionAnalysis()).isNotNull();
            assertThat(result.getTiltAnalysis()).isNotNull();
            assertThat(result.getBehaviorCorrelation()).isNotNull();
            assertThat(result.getPsychologicalScore()).isNotNull();
            assertThat(result.getRecoveryAnalysis()).isNotNull();
            assertThat(result.getDailyRhythmAnalysis()).isNotNull();
        }
    }

    @Nested
    @DisplayName("감정 전환 분석 테스트")
    class AnalyzeEmotionTransitionsTests {

        @Test
        @DisplayName("감정 전환 패턴 분석 성공")
        void analyzeEmotionTransitions_WithData_ReturnsTransitionAnalysis() {
            // Given
            List<Object[]> stats = createEmotionTransitionStats();
            when(tradeReviewRepository.getEmotionTransitionStats(any(), any(), any()))
                    .thenReturn(stats);

            // When
            EmotionTransitionAnalysis result =
                    tradingPsychologyService.analyzeEmotionTransitions(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTransitions()).isNotEmpty();
            assertThat(result.getTotalTransitions()).isGreaterThan(0);
            assertThat(result.getMostCommonTransition()).isNotBlank();

            // 첫 번째 전환 검증
            EmotionTransition firstTransition = result.getTransitions().get(0);
            assertThat(firstTransition.getEmotionBefore()).isEqualTo(EmotionState.CONFIDENT);
            assertThat(firstTransition.getEmotionAfter()).isEqualTo(EmotionState.CALM);
            assertThat(firstTransition.getOccurrences()).isEqualTo(10);
            assertThat(firstTransition.getWinRate()).isEqualByComparingTo(new BigDecimal("70.00"));
            assertThat(firstTransition.getRiskLevel()).isEqualTo("LOW");
        }

        @Test
        @DisplayName("데이터 없을 때 빈 결과 반환")
        void analyzeEmotionTransitions_NoData_ReturnsEmptyAnalysis() {
            // Given
            when(tradeReviewRepository.getEmotionTransitionStats(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            EmotionTransitionAnalysis result =
                    tradingPsychologyService.analyzeEmotionTransitions(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTransitions()).isEmpty();
            assertThat(result.getTotalTransitions()).isZero();
            assertThat(result.getMostCommonTransition()).isEqualTo("없음");
        }

        @Test
        @DisplayName("위험한 감정 전환 패턴 감지")
        void analyzeEmotionTransitions_DetectsRiskyTransitions() {
            // Given - 긍정→부정 전환 (낮은 승률)
            List<Object[]> stats = new ArrayList<>();
            stats.add(
                    new Object[] {EmotionState.CONFIDENT, EmotionState.FEARFUL, 5L, 30.0, -500.0});
            when(tradeReviewRepository.getEmotionTransitionStats(any(), any(), any()))
                    .thenReturn(stats);

            // When
            EmotionTransitionAnalysis result =
                    tradingPsychologyService.analyzeEmotionTransitions(
                            accountId, startDate, endDate);

            // Then
            assertThat(result.getRiskyTransitions()).hasSize(1);
            EmotionTransition riskyTransition = result.getRiskyTransitions().get(0);
            assertThat(riskyTransition.getRiskLevel()).isEqualTo("HIGH");
            assertThat(riskyTransition.getEmotionBefore()).isEqualTo(EmotionState.CONFIDENT);
            assertThat(riskyTransition.getEmotionAfter()).isEqualTo(EmotionState.FEARFUL);
        }
    }

    @Nested
    @DisplayName("틸트 감지 테스트")
    class DetectTiltTests {

        @Test
        @DisplayName("틸트 감지 - 심각한 수준")
        void detectTilt_SevereLevel_ReturnsHighTiltScore() {
            // Given - 연속 부정 감정, 큰 손실
            List<TradeReview> trades = createTiltTradeReviews();
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);

            // When
            TiltAnalysis result =
                    tradingPsychologyService.detectTilt(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTiltScore()).isGreaterThan(0);
            assertThat(result.getTiltLevel()).isNotBlank();
            assertThat(result.getScoreBreakdown()).isNotNull();
            assertThat(result.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("틸트 감지 - 데이터 없을 때")
        void detectTilt_NoData_ReturnsNoneTiltLevel() {
            // Given
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            TiltAnalysis result =
                    tradingPsychologyService.detectTilt(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTiltScore()).isZero();
            assertThat(result.getTiltLevel()).isEqualTo("NONE");
            assertThat(result.getRecentTiltEvents()).isEmpty();
            assertThat(result.getScoreBreakdown().getConsecutiveNegativeScore()).isZero();
            assertThat(result.getScoreBreakdown().getLossRatioScore()).isZero();
            assertThat(result.getRecommendations()).contains("분석할 충분한 데이터가 없습니다.");
        }

        @Test
        @DisplayName("틸트 이벤트 감지 - 연속 부정 감정 전환")
        void detectTilt_ConsecutiveNegativeShifts_DetectsTiltEvent() {
            // Given - 4번 연속 긍정→부정 감정 전환
            List<TradeReview> trades = new ArrayList<>();
            LocalDate baseDate = LocalDate.of(2024, 1, 1);

            for (int i = 0; i < 4; i++) {
                Transaction tx = createTransaction(baseDate.plusDays(i), new BigDecimal("-200"));
                TradeReview review =
                        TradeReview.builder()
                                .transaction(tx)
                                .emotionBefore(EmotionState.CONFIDENT)
                                .emotionAfter(EmotionState.FEARFUL)
                                .reviewedAt(baseDate.plusDays(i).atTime(15, 0))
                                .createdAt(baseDate.plusDays(i).atTime(15, 0))
                                .build();
                trades.add(review);
            }

            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);

            // When
            TiltAnalysis result =
                    tradingPsychologyService.detectTilt(accountId, startDate, endDate);

            // Then
            assertThat(result.getRecentTiltEvents()).isNotEmpty();
            TiltEvent tiltEvent = result.getRecentTiltEvents().get(0);
            assertThat(tiltEvent.getConsecutiveNegativeShifts()).isGreaterThanOrEqualTo(3);
            assertThat(tiltEvent.getTotalLossDuringTilt()).isGreaterThan(BigDecimal.ZERO);
            assertThat(tiltEvent.getTradesAffected()).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("현재 틸트 상태 조회 테스트")
    class GetCurrentTiltStatusTests {

        @Test
        @DisplayName("현재 틸트 상태 조회 - 최근 7일 데이터")
        void getCurrentTiltStatus_ReturnsLast7DaysAnalysis() {
            // Given
            List<TradeReview> recentTrades = createRecentTradeReviews();
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(recentTrades);

            // When
            TiltAnalysis result = tradingPsychologyService.getCurrentTiltStatus(accountId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTiltLevel()).isNotNull();
            assertThat(result.getScoreBreakdown()).isNotNull();
        }
    }

    @Nested
    @DisplayName("감정-행동 상관관계 분석 테스트")
    class AnalyzeEmotionBehaviorCorrelationTests {

        @Test
        @DisplayName("감정-행동 상관관계 분석 성공")
        void analyzeEmotionBehaviorCorrelation_WithData_ReturnsCorrelation() {
            // Given
            when(tradeReviewRepository.getEmotionMistakeCorrelation(any(), any(), any()))
                    .thenReturn(createEmotionMistakeCorrelation());
            when(tradeReviewRepository.countTradesByEmotion(any(), any(), any()))
                    .thenReturn(createEmotionCounts());

            // When
            EmotionBehaviorCorrelation result =
                    tradingPsychologyService.analyzeEmotionBehaviorCorrelation(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCorrelations()).isNotEmpty();

            // 첫 번째 상관관계 검증
            BehaviorCorrelation correlation = result.getCorrelations().get(0);
            assertThat(correlation.getEmotion()).isEqualTo(EmotionState.FEARFUL);
            assertThat(correlation.getMistakeType()).isEqualTo("조기 청산");
            assertThat(correlation.getOccurrences()).isEqualTo(8);
            assertThat(correlation.getCorrelationStrength())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("유의미한 상관관계 필터링")
        void analyzeEmotionBehaviorCorrelation_FiltersSignificantCorrelations() {
            // Given
            when(tradeReviewRepository.getEmotionMistakeCorrelation(any(), any(), any()))
                    .thenReturn(createEmotionMistakeCorrelation());
            when(tradeReviewRepository.countTradesByEmotion(any(), any(), any()))
                    .thenReturn(createEmotionCounts());

            // When
            EmotionBehaviorCorrelation result =
                    tradingPsychologyService.analyzeEmotionBehaviorCorrelation(
                            accountId, startDate, endDate);

            // Then
            assertThat(result.getSignificantCorrelations()).isNotNull();
            // 상관 강도가 0.3 이상인 것만 필터링
            result.getSignificantCorrelations()
                    .forEach(
                            c ->
                                    assertThat(c.getCorrelationStrength())
                                            .isGreaterThanOrEqualTo(new BigDecimal("0.30")));
        }
    }

    @Nested
    @DisplayName("심리 점수 계산 테스트")
    class CalculatePsychologicalScoreTests {

        @Test
        @DisplayName("심리 점수 계산 성공")
        void calculatePsychologicalScore_WithData_ReturnsScore() {
            // Given
            List<TradeReview> trades = createSampleTradeReviews();
            List<TradingJournal> journals = createSampleJournals();

            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);

            // When
            PsychologicalScore result =
                    tradingPsychologyService.calculatePsychologicalScore(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOverallScore()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(result.getOverallScore()).isLessThanOrEqualTo(new BigDecimal("100.00"));
            assertThat(result.getGrade()).isIn("A", "B", "C", "D", "F");
            assertThat(result.getComponents()).isNotNull();
            assertThat(result.getComponents().getFocusScore()).isNotNull();
            assertThat(result.getComponents().getDisciplineScore()).isNotNull();
            assertThat(result.getComponents().getEmotionalStabilityScore()).isNotNull();
            assertThat(result.getComponents().getResilienceScore()).isNotNull();
        }

        @Test
        @DisplayName("심리 점수 계산 - 데이터 없을 때 기본값 50점")
        void calculatePsychologicalScore_NoData_ReturnsDefaultScore() {
            // Given
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            PsychologicalScore result =
                    tradingPsychologyService.calculatePsychologicalScore(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getComponents().getFocusScore())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.getComponents().getDisciplineScore())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.getComponents().getEmotionalStabilityScore())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.getComponents().getResilienceScore())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("점수 등급 계산 검증")
        void calculatePsychologicalScore_CalculatesGradeCorrectly() {
            // Given
            List<TradeReview> highQualityTrades = createHighQualityTradeReviews();
            List<TradingJournal> journals = createSampleJournals();

            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(highQualityTrades);
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);

            // When
            PsychologicalScore result =
                    tradingPsychologyService.calculatePsychologicalScore(
                            accountId, startDate, endDate);

            // Then
            BigDecimal score = result.getOverallScore();
            String grade = result.getGrade();

            if (score.compareTo(new BigDecimal("90")) >= 0) {
                assertThat(grade).isEqualTo("A");
            } else if (score.compareTo(new BigDecimal("80")) >= 0) {
                assertThat(grade).isEqualTo("B");
            } else if (score.compareTo(new BigDecimal("70")) >= 0) {
                assertThat(grade).isEqualTo("C");
            } else if (score.compareTo(new BigDecimal("60")) >= 0) {
                assertThat(grade).isEqualTo("D");
            } else {
                assertThat(grade).isEqualTo("F");
            }
        }
    }

    @Nested
    @DisplayName("회복 패턴 분석 테스트")
    class AnalyzeRecoveryPatternsTests {

        @Test
        @DisplayName("회복 패턴 분석 성공")
        void analyzeRecoveryPatterns_WithData_ReturnsRecoveryAnalysis() {
            // Given
            List<TradingJournal> journals = createJournalsWithRecoveryPattern();
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);

            // When
            RecoveryPatternAnalysis result =
                    tradingPsychologyService.analyzeRecoveryPatterns(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAvgRecoveryDays()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(result.getRecoveryType())
                    .isIn("QUICK_BOUNCER", "STEADY_RECOVERER", "SLOW_HEALER");
            assertThat(result.getHelpfulFactors()).isNotEmpty();
            assertThat(result.getHarmfulFactors()).isNotEmpty();
        }

        @Test
        @DisplayName("회복 이벤트 감지")
        void analyzeRecoveryPatterns_DetectsRecoveryEvents() {
            // Given - 부정→긍정 감정 회복 패턴
            List<TradingJournal> journals = createJournalsWithRecoveryPattern();
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);

            // When
            RecoveryPatternAnalysis result =
                    tradingPsychologyService.analyzeRecoveryPatterns(accountId, startDate, endDate);

            // Then
            assertThat(result.getRecentRecoveries()).isNotEmpty();
            RecoveryEvent recovery = result.getRecentRecoveries().get(0);
            assertThat(recovery.getStartDate()).isNotNull();
            assertThat(recovery.getRecoveryDate()).isAfter(recovery.getStartDate());
            assertThat(recovery.getDaysToRecover()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("일일 리듬 분석 테스트")
    class AnalyzeDailyRhythmTests {

        @Test
        @DisplayName("일일 리듬 분석 성공")
        void analyzeDailyRhythm_WithData_ReturnsDailyRhythmAnalysis() {
            // Given
            List<TradingJournal> journals = createSampleJournals();
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);
            when(tradeReviewRepository.getDayOfWeekStats(any(), any(), any()))
                    .thenReturn(createDayOfWeekStats());

            // When
            DailyRhythmAnalysis result =
                    tradingPsychologyService.analyzeDailyRhythm(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMorningEmotions()).isNotEmpty();
            assertThat(result.getEveningEmotions()).isNotEmpty();
            assertThat(result.getDayOfWeekAnalysis()).isNotEmpty();
            assertThat(result.getBestTradingDay()).isNotBlank();
            assertThat(result.getWorstTradingDay()).isNotBlank();
            assertThat(result.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("요일별 성과 분석")
        void analyzeDailyRhythm_AnalyzesDayOfWeekPerformance() {
            // Given
            List<TradingJournal> journals = createSampleJournals();
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(journals);
            when(tradeReviewRepository.getDayOfWeekStats(any(), any(), any()))
                    .thenReturn(createDayOfWeekStats());

            // When
            DailyRhythmAnalysis result =
                    tradingPsychologyService.analyzeDailyRhythm(accountId, startDate, endDate);

            // Then
            Map<String, DayPerformance> dayAnalysis = result.getDayOfWeekAnalysis();
            assertThat(dayAnalysis).hasSize(7); // 일~토

            dayAnalysis
                    .values()
                    .forEach(
                            day -> {
                                assertThat(day.getDayOfWeek()).isNotBlank();
                                assertThat(day.getWinRate())
                                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                            });
        }
    }

    @Nested
    @DisplayName("감정 트리거 분석 테스트")
    class AnalyzeEmotionTriggersTests {

        @Test
        @DisplayName("감정 트리거 분석 성공")
        void analyzeEmotionTriggers_WithData_ReturnsEmotionTriggerAnalysis() {
            // Given
            List<TradeReview> trades = createTradeReviewsWithTriggers();
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);

            // When
            EmotionTriggerAnalysis result =
                    tradingPsychologyService.analyzeEmotionTriggers(accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTriggers()).isNotEmpty();
            assertThat(result.getTopNegativeTriggers()).isNotNull();
            assertThat(result.getTopPositiveTriggers()).isNotNull();
        }

        @Test
        @DisplayName("부정적 트리거 감지")
        void analyzeEmotionTriggers_IdentifiesNegativeTriggers() {
            // Given - 큰 손실로 인한 부정 감정
            List<TradeReview> trades = createTradeReviewsWithTriggers();
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);

            // When
            EmotionTriggerAnalysis result =
                    tradingPsychologyService.analyzeEmotionTriggers(accountId, startDate, endDate);

            // Then
            assertThat(result.getTopNegativeTriggers()).isNotEmpty();
            result.getTopNegativeTriggers()
                    .forEach(
                            trigger -> {
                                assertThat(trigger.getResultingEmotion())
                                        .isIn(
                                                EmotionState.FEARFUL,
                                                EmotionState.ANXIOUS,
                                                EmotionState.FRUSTRATED,
                                                EmotionState.GREEDY);
                            });
        }

        @Test
        @DisplayName("긍정적 트리거 감지")
        void analyzeEmotionTriggers_IdentifiesPositiveTriggers() {
            // Given
            List<TradeReview> trades = createTradeReviewsWithTriggers();
            when(tradeReviewRepository.findTradesForTiltAnalysis(any(), any(), any()))
                    .thenReturn(trades);

            // When
            EmotionTriggerAnalysis result =
                    tradingPsychologyService.analyzeEmotionTriggers(accountId, startDate, endDate);

            // Then
            assertThat(result.getTopPositiveTriggers()).isNotNull();
            result.getTopPositiveTriggers()
                    .forEach(
                            trigger -> {
                                assertThat(trigger.getResultingEmotion())
                                        .isIn(
                                                EmotionState.CONFIDENT,
                                                EmotionState.CALM,
                                                EmotionState.NEUTRAL,
                                                EmotionState.EXCITED);
                            });
        }
    }

    @Nested
    @DisplayName("심리-성과 상관관계 분석 테스트")
    class AnalyzePerformanceCorrelationTests {

        @Test
        @DisplayName("심리-성과 상관관계 분석 성공")
        void analyzePerformanceCorrelation_WithData_ReturnsCorrelationAnalysis() {
            // Given
            when(tradeReviewRepository.getEmotionPerformanceStats(any(), any(), any()))
                    .thenReturn(createEmotionPerformanceStats());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(createSampleJournals());
            when(tradeReviewRepository.getPlanAdherencePerformance(any(), any(), any()))
                    .thenReturn(createPlanAdherenceStats());

            // When
            PsychologyPerformanceCorrelation result =
                    tradingPsychologyService.analyzePerformanceCorrelation(
                            accountId, startDate, endDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmotionPerformance()).isNotEmpty();
            assertThat(result.getFocusCorrelation()).isNotNull();
            assertThat(result.getDisciplineCorrelation()).isNotNull();
            assertThat(result.getOptimalState()).isNotNull();
            assertThat(result.getPlanAdherence()).isNotNull();
            assertThat(result.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("감정별 성과 통계 계산")
        void analyzePerformanceCorrelation_CalculatesEmotionPerformanceStats() {
            // Given
            when(tradeReviewRepository.getEmotionPerformanceStats(any(), any(), any()))
                    .thenReturn(createEmotionPerformanceStats());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(createSampleJournals());
            when(tradeReviewRepository.getPlanAdherencePerformance(any(), any(), any()))
                    .thenReturn(createPlanAdherenceStats());

            // When
            PsychologyPerformanceCorrelation result =
                    tradingPsychologyService.analyzePerformanceCorrelation(
                            accountId, startDate, endDate);

            // Then
            List<EmotionPerformanceStats> emotionStats = result.getEmotionPerformance();
            assertThat(emotionStats).isNotEmpty();

            emotionStats.forEach(
                    stat -> {
                        assertThat(stat.getEmotion()).isNotNull();
                        assertThat(stat.getEmotionLabel()).isNotBlank();
                        assertThat(stat.getTradeCount()).isGreaterThanOrEqualTo(0);
                        assertThat(stat.getWinRate()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                        assertThat(stat.getPerformanceGrade()).isIn("A", "B", "C", "D", "F");
                    });
        }

        @Test
        @DisplayName("집중도 상관관계 계산")
        void analyzePerformanceCorrelation_CalculatesFocusCorrelation() {
            // Given
            when(tradeReviewRepository.getEmotionPerformanceStats(any(), any(), any()))
                    .thenReturn(createEmotionPerformanceStats());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(createSampleJournals());
            when(tradeReviewRepository.getPlanAdherencePerformance(any(), any(), any()))
                    .thenReturn(createPlanAdherenceStats());

            // When
            PsychologyPerformanceCorrelation result =
                    tradingPsychologyService.analyzePerformanceCorrelation(
                            accountId, startDate, endDate);

            // Then
            ScoreCorrelation focusCorrelation = result.getFocusCorrelation();
            assertThat(focusCorrelation.getScoreName()).isEqualTo("집중도");
            assertThat(focusCorrelation.getGroups()).hasSize(5); // 1-5점
            assertThat(focusCorrelation.getCorrelationCoefficient()).isNotNull();
            assertThat(focusCorrelation.getCorrelationStrength())
                    .isIn("STRONG", "MODERATE", "WEAK", "NONE");
            assertThat(focusCorrelation.getRecommendation()).isNotBlank();
        }

        @Test
        @DisplayName("계획 준수 통계 분석")
        void analyzePerformanceCorrelation_AnalyzesPlanAdherence() {
            // Given
            when(tradeReviewRepository.getEmotionPerformanceStats(any(), any(), any()))
                    .thenReturn(createEmotionPerformanceStats());
            when(tradingJournalRepository
                            .findByAccountIdAndJournalDateBetweenOrderByJournalDateDesc(
                                    any(), any(), any()))
                    .thenReturn(createSampleJournals());
            when(tradeReviewRepository.getPlanAdherencePerformance(any(), any(), any()))
                    .thenReturn(createPlanAdherenceStats());

            // When
            PsychologyPerformanceCorrelation result =
                    tradingPsychologyService.analyzePerformanceCorrelation(
                            accountId, startDate, endDate);

            // Then
            PlanAdherenceStats adherence = result.getPlanAdherence();
            assertThat(adherence.getFollowedCount()).isGreaterThanOrEqualTo(0);
            assertThat(adherence.getNotFollowedCount()).isGreaterThanOrEqualTo(0);
            assertThat(adherence.getFollowedWinRate()).isNotNull();
            assertThat(adherence.getNotFollowedWinRate()).isNotNull();
            assertThat(adherence.getWinRateDifference()).isNotNull();
            assertThat(adherence.getPnlDifference()).isNotNull();
        }
    }

    // ==================== Helper Methods ====================

    private List<TradeReview> createSampleTradeReviews() {
        List<TradeReview> reviews = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < 10; i++) {
            Transaction tx = createTransaction(baseDate.plusDays(i), new BigDecimal("100"));
            TradeReview review =
                    TradeReview.builder()
                            .transaction(tx)
                            .emotionBefore(EmotionState.CONFIDENT)
                            .emotionAfter(EmotionState.CALM)
                            .followedPlan(true)
                            .ratingScore(4)
                            .reviewedAt(baseDate.plusDays(i).atTime(15, 0))
                            .createdAt(baseDate.plusDays(i).atTime(15, 0))
                            .build();
            reviews.add(review);
        }

        return reviews;
    }

    private List<TradeReview> createHighQualityTradeReviews() {
        List<TradeReview> reviews = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < 10; i++) {
            Transaction tx = createTransaction(baseDate.plusDays(i), new BigDecimal("200"));
            TradeReview review =
                    TradeReview.builder()
                            .transaction(tx)
                            .emotionBefore(EmotionState.CONFIDENT)
                            .emotionAfter(EmotionState.CONFIDENT)
                            .followedPlan(true)
                            .ratingScore(5)
                            .reviewedAt(baseDate.plusDays(i).atTime(15, 0))
                            .createdAt(baseDate.plusDays(i).atTime(15, 0))
                            .build();
            reviews.add(review);
        }

        return reviews;
    }

    private List<TradeReview> createTiltTradeReviews() {
        List<TradeReview> reviews = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        // 5번 연속 부정 감정 전환
        for (int i = 0; i < 5; i++) {
            Transaction tx = createTransaction(baseDate.plusDays(i), new BigDecimal("-150"));
            TradeReview review =
                    TradeReview.builder()
                            .transaction(tx)
                            .emotionBefore(EmotionState.CONFIDENT)
                            .emotionAfter(EmotionState.FEARFUL)
                            .followedPlan(false)
                            .ratingScore(2)
                            .reviewedAt(baseDate.plusDays(i).atTime(15, 0))
                            .createdAt(baseDate.plusDays(i).atTime(15, 0))
                            .build();
            reviews.add(review);
        }

        return reviews;
    }

    private List<TradeReview> createRecentTradeReviews() {
        List<TradeReview> reviews = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 5; i++) {
            Transaction tx = createTransaction(today.minusDays(i), new BigDecimal("50"));
            TradeReview review =
                    TradeReview.builder()
                            .transaction(tx)
                            .emotionBefore(EmotionState.NEUTRAL)
                            .emotionAfter(EmotionState.CALM)
                            .followedPlan(true)
                            .ratingScore(3)
                            .reviewedAt(today.minusDays(i).atTime(15, 0))
                            .createdAt(today.minusDays(i).atTime(15, 0))
                            .build();
            reviews.add(review);
        }

        return reviews;
    }

    private List<TradeReview> createTradeReviewsWithTriggers() {
        List<TradeReview> reviews = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        // 큰 손실 → 부정 감정
        Transaction txLoss = createTransaction(baseDate, new BigDecimal("-1500"));
        reviews.add(
                TradeReview.builder()
                        .transaction(txLoss)
                        .emotionBefore(EmotionState.CONFIDENT)
                        .emotionAfter(EmotionState.FEARFUL)
                        .reviewedAt(baseDate.atTime(15, 0))
                        .createdAt(baseDate.atTime(15, 0))
                        .build());

        // 큰 수익 → 긍정 감정
        Transaction txProfit = createTransaction(baseDate.plusDays(1), new BigDecimal("1200"));
        reviews.add(
                TradeReview.builder()
                        .transaction(txProfit)
                        .emotionBefore(EmotionState.NEUTRAL)
                        .emotionAfter(EmotionState.CONFIDENT)
                        .reviewedAt(baseDate.plusDays(1).atTime(15, 0))
                        .createdAt(baseDate.plusDays(1).atTime(15, 0))
                        .build());

        // 손실 → 불안
        Transaction txSmallLoss = createTransaction(baseDate.plusDays(2), new BigDecimal("-300"));
        reviews.add(
                TradeReview.builder()
                        .transaction(txSmallLoss)
                        .emotionBefore(EmotionState.CALM)
                        .emotionAfter(EmotionState.ANXIOUS)
                        .reviewedAt(baseDate.plusDays(2).atTime(15, 0))
                        .createdAt(baseDate.plusDays(2).atTime(15, 0))
                        .build());

        return reviews;
    }

    private Transaction createTransaction(LocalDate date, BigDecimal pnl) {
        return Transaction.builder()
                .transactionDate(date.atTime(10, 0))
                .realizedPnl(pnl)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("50000"))
                .build();
    }

    private List<TradingJournal> createSampleJournals() {
        List<TradingJournal> journals = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < 10; i++) {
            journals.add(
                    TradingJournal.builder()
                            .accountId(accountId)
                            .journalDate(baseDate.plusDays(i))
                            .morningEmotion(EmotionState.CONFIDENT)
                            .eveningEmotion(EmotionState.CALM)
                            .focusScore(4)
                            .disciplineScore(4)
                            .tradeSummaryCount(5)
                            .tradeSummaryProfit(new BigDecimal("500"))
                            .tradeSummaryWinRate(new BigDecimal("60.00"))
                            .createdAt(baseDate.plusDays(i).atTime(18, 0))
                            .updatedAt(baseDate.plusDays(i).atTime(18, 0))
                            .build());
        }

        return journals;
    }

    private List<TradingJournal> createJournalsWithRecoveryPattern() {
        List<TradingJournal> journals = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2024, 1, 1);

        // 부정 감정 3일
        for (int i = 0; i < 3; i++) {
            journals.add(
                    TradingJournal.builder()
                            .accountId(accountId)
                            .journalDate(baseDate.plusDays(i))
                            .morningEmotion(EmotionState.FEARFUL)
                            .eveningEmotion(EmotionState.ANXIOUS)
                            .focusScore(2)
                            .disciplineScore(2)
                            .createdAt(baseDate.plusDays(i).atTime(18, 0))
                            .updatedAt(baseDate.plusDays(i).atTime(18, 0))
                            .build());
        }

        // 회복 - 긍정 감정
        journals.add(
                TradingJournal.builder()
                        .accountId(accountId)
                        .journalDate(baseDate.plusDays(3))
                        .morningEmotion(EmotionState.CALM)
                        .eveningEmotion(EmotionState.CONFIDENT)
                        .focusScore(4)
                        .disciplineScore(4)
                        .createdAt(baseDate.plusDays(3).atTime(18, 0))
                        .updatedAt(baseDate.plusDays(3).atTime(18, 0))
                        .build());

        return journals;
    }

    private List<Object[]> createEmotionTransitionStats() {
        List<Object[]> stats = new ArrayList<>();
        // EmotionState before, EmotionState after, Long count, Double winRate, Double avgPnl
        stats.add(new Object[] {EmotionState.CONFIDENT, EmotionState.CALM, 10L, 70.0, 150.0});
        stats.add(new Object[] {EmotionState.CALM, EmotionState.CONFIDENT, 8L, 65.0, 120.0});
        stats.add(new Object[] {EmotionState.CONFIDENT, EmotionState.FEARFUL, 3L, 30.0, -200.0});
        return stats;
    }

    private List<Object[]> createEmotionMistakeCorrelation() {
        List<Object[]> correlation = new ArrayList<>();
        // EmotionState, String mistakeType, Long count
        correlation.add(new Object[] {EmotionState.FEARFUL, "조기 청산", 8L});
        correlation.add(new Object[] {EmotionState.GREEDY, "과도한 레버리지", 5L});
        correlation.add(new Object[] {EmotionState.ANXIOUS, "손절 미준수", 6L});
        return correlation;
    }

    private List<Object[]> createEmotionCounts() {
        List<Object[]> counts = new ArrayList<>();
        // EmotionState, Long count
        counts.add(new Object[] {EmotionState.FEARFUL, 20L});
        counts.add(new Object[] {EmotionState.GREEDY, 15L});
        counts.add(new Object[] {EmotionState.ANXIOUS, 18L});
        counts.add(new Object[] {EmotionState.CONFIDENT, 25L});
        return counts;
    }

    private List<Object[]> createDayOfWeekStats() {
        List<Object[]> stats = new ArrayList<>();
        // Integer dayOfWeek (1=일, 7=토), Long count, Double winRate, Double avgPnl, EmotionState
        // emotion
        stats.add(new Object[] {1, 5L, 60.0, 100.0, EmotionState.CALM});
        stats.add(new Object[] {2, 10L, 65.0, 120.0, EmotionState.CONFIDENT});
        stats.add(new Object[] {3, 8L, 55.0, 80.0, EmotionState.NEUTRAL});
        stats.add(new Object[] {4, 12L, 70.0, 150.0, EmotionState.CONFIDENT});
        stats.add(new Object[] {5, 9L, 50.0, 50.0, EmotionState.CALM});
        stats.add(new Object[] {6, 6L, 45.0, 30.0, EmotionState.ANXIOUS});
        stats.add(new Object[] {7, 3L, 40.0, 20.0, EmotionState.FEARFUL});
        return stats;
    }

    private List<Object[]> createEmotionPerformanceStats() {
        List<Object[]> stats = new ArrayList<>();
        // EmotionState, Long count, Long winCount, Double avgPnl, Double totalPnl, Double
        // avgRMultiple
        stats.add(new Object[] {EmotionState.CONFIDENT, 20L, 14L, 100.0, 2000.0, 1.5}); // 70% 승률
        stats.add(new Object[] {EmotionState.CALM, 15L, 9L, 80.0, 1200.0, 1.3}); // 60% 승률
        stats.add(new Object[] {EmotionState.FEARFUL, 10L, 3L, -50.0, -500.0, 0.7}); // 30% 승률
        return stats;
    }

    private List<Object[]> createPlanAdherenceStats() {
        List<Object[]> stats = new ArrayList<>();
        // Boolean followedPlan, Long count, Long winCount, Double avgPnl
        stats.add(new Object[] {true, 30L, 20L, 100.0}); // 66.7% 승률
        stats.add(new Object[] {false, 15L, 6L, -20.0}); // 40% 승률
        return stats;
    }
}
