package com.trading.journal.service;

import com.trading.journal.dto.TradeReviewDto;
import com.trading.journal.dto.TradeReviewDto.*;
import com.trading.journal.entity.*;
import com.trading.journal.repository.TradeReviewRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래 복기/일지 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TradeReviewService {

    private final TradeReviewRepository reviewRepository;
    private final TransactionRepository transactionRepository;

    /** 복기 생성 */
    public TradeReviewDto createReview(Long transactionId, TradeReviewDto dto) {
        Transaction transaction =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "거래를 찾을 수 없습니다: " + transactionId));

        if (reviewRepository.existsByTransactionId(transactionId)) {
            throw new IllegalStateException("이미 복기가 존재합니다. 수정을 사용해주세요.");
        }

        TradeReview review =
                TradeReview.builder()
                        .transaction(transaction)
                        .strategy(dto.getStrategy())
                        .entryReason(dto.getEntryReason())
                        .exitReason(dto.getExitReason())
                        .emotionBefore(dto.getEmotionBefore())
                        .emotionAfter(dto.getEmotionAfter())
                        .reviewNote(dto.getReviewNote())
                        .lessonsLearned(dto.getLessonsLearned())
                        .ratingScore(dto.getRatingScore())
                        .tags(dto.getTags())
                        .followedPlan(dto.getFollowedPlan())
                        .screenshotPath(dto.getScreenshotPath())
                        .reviewedAt(LocalDateTime.now())
                        .build();

        TradeReview saved = reviewRepository.save(review);
        log.info("Created review {} for transaction {}", saved.getId(), transactionId);

        return toDto(saved);
    }

    /** 복기 수정 */
    public TradeReviewDto updateReview(Long reviewId, TradeReviewDto dto) {
        TradeReview review =
                reviewRepository
                        .findById(reviewId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("복기를 찾을 수 없습니다: " + reviewId));

        review.setStrategy(dto.getStrategy());
        review.setEntryReason(dto.getEntryReason());
        review.setExitReason(dto.getExitReason());
        review.setEmotionBefore(dto.getEmotionBefore());
        review.setEmotionAfter(dto.getEmotionAfter());
        review.setReviewNote(dto.getReviewNote());
        review.setLessonsLearned(dto.getLessonsLearned());
        review.setRatingScore(dto.getRatingScore());
        review.setTags(dto.getTags());
        review.setFollowedPlan(dto.getFollowedPlan());
        review.setScreenshotPath(dto.getScreenshotPath());
        review.setReviewedAt(LocalDateTime.now());

        TradeReview saved = reviewRepository.save(review);
        log.info("Updated review {}", reviewId);

        return toDto(saved);
    }

    /** 복기 조회 (ID) */
    @Transactional(readOnly = true)
    public TradeReviewDto getReview(Long reviewId) {
        TradeReview review =
                reviewRepository
                        .findById(reviewId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("복기를 찾을 수 없습니다: " + reviewId));
        return toDto(review);
    }

    /** 거래별 복기 조회 */
    @Transactional(readOnly = true)
    public TradeReviewDto getReviewByTransaction(Long transactionId) {
        return reviewRepository.findByTransactionId(transactionId).map(this::toDto).orElse(null);
    }

    /** 전략별 복기 조회 */
    @Transactional(readOnly = true)
    public List<TradeReviewDto> getReviewsByStrategy(TradeStrategy strategy) {
        return reviewRepository.findByStrategyOrderByCreatedAtDesc(strategy).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 최근 복기 조회 (페이징) */
    @Transactional(readOnly = true)
    public Page<TradeReviewDto> getRecentReviews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reviewRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    /** 복기 삭제 */
    public void deleteReview(Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new IllegalArgumentException("복기를 찾을 수 없습니다: " + reviewId);
        }
        reviewRepository.deleteById(reviewId);
        log.info("Deleted review {}", reviewId);
    }

    /** 복기 통계 조회 */
    @Transactional(readOnly = true)
    public ReviewStatisticsDto getStatistics() {
        // FETCH JOIN으로 Transaction 함께 로딩하여 N+1 쿼리 방지
        List<TradeReview> allReviews = reviewRepository.findAllWithTransaction();
        long totalTransactions = transactionRepository.count();

        // 전략별 통계
        Map<TradeStrategy, StrategyStats> strategyStats = new HashMap<>();
        List<Object[]> strategyCounts = reviewRepository.countByStrategy();
        for (Object[] row : strategyCounts) {
            TradeStrategy strategy = (TradeStrategy) row[0];
            Long count = (Long) row[1];

            List<TradeReview> strategyReviews =
                    allReviews.stream()
                            .filter(r -> r.getStrategy() == strategy)
                            .collect(Collectors.toList());

            long winCount =
                    strategyReviews.stream()
                            .filter(
                                    r ->
                                            r.getTransaction() != null
                                                    && r.getTransaction().getRealizedPnl() != null
                                                    && r.getTransaction()
                                                                    .getRealizedPnl()
                                                                    .compareTo(BigDecimal.ZERO)
                                                            > 0)
                            .count();

            BigDecimal winRate =
                    count > 0
                            ? BigDecimal.valueOf((double) winCount / count * 100)
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            BigDecimal totalProfit =
                    strategyReviews.stream()
                            .filter(
                                    r ->
                                            r.getTransaction() != null
                                                    && r.getTransaction().getRealizedPnl() != null)
                            .map(r -> r.getTransaction().getRealizedPnl())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            strategyStats.put(
                    strategy,
                    StrategyStats.builder()
                            .strategy(strategy)
                            .strategyLabel(strategy.getLabel())
                            .count(count.intValue())
                            .winRate(winRate)
                            .avgProfit(
                                    count > 0
                                            ? totalProfit.divide(
                                                    new BigDecimal(count), 2, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .totalProfit(totalProfit)
                            .build());
        }

        // 감정 통계
        Map<EmotionState, EmotionStats> emotionStats = new HashMap<>();
        List<Object[]> emotionData = reviewRepository.getEmotionBeforeStatistics();
        for (Object[] row : emotionData) {
            EmotionState emotion = (EmotionState) row[0];
            Long count = (Long) row[1];
            Double winRate = (Double) row[2];

            emotionStats.put(
                    emotion,
                    EmotionStats.builder()
                            .emotion(emotion)
                            .emotionLabel(emotion.getLabel())
                            .count(count.intValue())
                            .winRate(BigDecimal.valueOf(winRate).setScale(2, RoundingMode.HALF_UP))
                            .build());
        }

        // 계획 준수 통계
        List<Object[]> planStats = reviewRepository.countByFollowedPlan();
        int followedCount = 0;
        int notFollowedCount = 0;
        for (Object[] row : planStats) {
            Boolean followed = (Boolean) row[0];
            Long count = (Long) row[1];
            if (Boolean.TRUE.equals(followed)) {
                followedCount = count.intValue();
            } else {
                notFollowedCount = count.intValue();
            }
        }

        // 최근 교훈
        List<LessonSummary> recentLessons =
                allReviews.stream()
                        .filter(
                                r ->
                                        r.getLessonsLearned() != null
                                                && !r.getLessonsLearned().isEmpty())
                        .sorted(Comparator.comparing(TradeReview::getReviewedAt).reversed())
                        .limit(5)
                        .map(
                                r ->
                                        LessonSummary.builder()
                                                .reviewId(r.getId())
                                                .stockName(
                                                        r.getTransaction() != null
                                                                ? r.getTransaction()
                                                                        .getStock()
                                                                        .getName()
                                                                : "")
                                                .lessonsLearned(r.getLessonsLearned())
                                                .reviewedAt(r.getReviewedAt())
                                                .isWin(
                                                        r.getTransaction() != null
                                                                && r.getTransaction()
                                                                                .getRealizedPnl()
                                                                        != null
                                                                && r.getTransaction()
                                                                                .getRealizedPnl()
                                                                                .compareTo(
                                                                                        BigDecimal
                                                                                                .ZERO)
                                                                        > 0)
                                                .build())
                        .collect(Collectors.toList());

        // 인기 태그
        Map<String, Integer> tagCounts = new HashMap<>();
        for (TradeReview r : allReviews) {
            if (r.getTags() != null && !r.getTags().isEmpty()) {
                for (String tag : r.getTags().split(",")) {
                    String trimmed = tag.trim();
                    tagCounts.put(trimmed, tagCounts.getOrDefault(trimmed, 0) + 1);
                }
            }
        }
        List<String> topTags =
                tagCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        int totalReviews = allReviews.size();
        BigDecimal reviewRate =
                totalTransactions > 0
                        ? BigDecimal.valueOf((double) totalReviews / totalTransactions * 100)
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return ReviewStatisticsDto.builder()
                .totalReviews(totalReviews)
                .totalTransactions((int) totalTransactions)
                .reviewRate(reviewRate)
                .averageRating(reviewRepository.getAverageRating())
                .strategyStats(strategyStats)
                .emotionStats(emotionStats)
                .followedPlanCount(followedCount)
                .notFollowedPlanCount(notFollowedCount)
                .topTags(topTags)
                .recentLessons(recentLessons)
                .build();
    }

    /** Entity -> DTO 변환 */
    private TradeReviewDto toDto(TradeReview review) {
        Transaction tx = review.getTransaction();

        BigDecimal profitPercent = BigDecimal.ZERO;
        if (tx != null
                && tx.getCostBasis() != null
                && tx.getCostBasis().compareTo(BigDecimal.ZERO) > 0
                && tx.getRealizedPnl() != null) {
            profitPercent =
                    tx.getRealizedPnl()
                            .divide(tx.getCostBasis(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
        }

        return TradeReviewDto.builder()
                .id(review.getId())
                .transactionId(tx != null ? tx.getId() : null)
                .stockSymbol(tx != null ? tx.getStock().getSymbol() : null)
                .stockName(tx != null ? tx.getStock().getName() : null)
                .transactionDate(tx != null ? tx.getTransactionDate() : null)
                .realizedPnl(tx != null ? tx.getRealizedPnl() : null)
                .profitPercent(profitPercent)
                .strategy(review.getStrategy())
                .strategyLabel(
                        review.getStrategy() != null ? review.getStrategy().getLabel() : null)
                .entryReason(review.getEntryReason())
                .exitReason(review.getExitReason())
                .emotionBefore(review.getEmotionBefore())
                .emotionBeforeLabel(
                        review.getEmotionBefore() != null
                                ? review.getEmotionBefore().getLabel()
                                : null)
                .emotionAfter(review.getEmotionAfter())
                .emotionAfterLabel(
                        review.getEmotionAfter() != null
                                ? review.getEmotionAfter().getLabel()
                                : null)
                .reviewNote(review.getReviewNote())
                .lessonsLearned(review.getLessonsLearned())
                .ratingScore(review.getRatingScore())
                .tags(review.getTags())
                .followedPlan(review.getFollowedPlan())
                .screenshotPath(review.getScreenshotPath())
                .reviewedAt(review.getReviewedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
