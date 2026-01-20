package com.trading.journal.controller;

import com.trading.journal.dto.TradeReviewDto;
import com.trading.journal.dto.TradeReviewDto.ReviewStatisticsDto;
import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradeStrategy;
import com.trading.journal.service.TradeReviewService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 거래 복기/일지 컨트롤러 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class TradeReviewController {

    private final TradeReviewService reviewService;

    /** 복기 생성 */
    @PostMapping("/transaction/{transactionId}")
    public ResponseEntity<TradeReviewDto> createReview(
            @PathVariable Long transactionId, @RequestBody TradeReviewDto dto) {
        log.info("Creating review for transaction {}", transactionId);
        TradeReviewDto created = reviewService.createReview(transactionId, dto);
        return ResponseEntity.ok(created);
    }

    /** 복기 수정 */
    @PutMapping("/{reviewId}")
    public ResponseEntity<TradeReviewDto> updateReview(
            @PathVariable Long reviewId, @RequestBody TradeReviewDto dto) {
        log.info("Updating review {}", reviewId);
        TradeReviewDto updated = reviewService.updateReview(reviewId, dto);
        return ResponseEntity.ok(updated);
    }

    /** 복기 조회 (ID) */
    @GetMapping("/{reviewId}")
    public ResponseEntity<TradeReviewDto> getReview(@PathVariable Long reviewId) {
        TradeReviewDto review = reviewService.getReview(reviewId);
        return ResponseEntity.ok(review);
    }

    /** 거래별 복기 조회 */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<TradeReviewDto> getReviewByTransaction(@PathVariable Long transactionId) {
        TradeReviewDto review = reviewService.getReviewByTransaction(transactionId);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(review);
    }

    /** 전략별 복기 조회 */
    @GetMapping("/strategy/{strategy}")
    public ResponseEntity<List<TradeReviewDto>> getReviewsByStrategy(
            @PathVariable TradeStrategy strategy) {
        List<TradeReviewDto> reviews = reviewService.getReviewsByStrategy(strategy);
        return ResponseEntity.ok(reviews);
    }

    /** 최근 복기 조회 (페이징) */
    @GetMapping
    public ResponseEntity<Page<TradeReviewDto>> getRecentReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TradeReviewDto> reviews = reviewService.getRecentReviews(page, size);
        return ResponseEntity.ok(reviews);
    }

    /** 복기 삭제 */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        log.info("Deleting review {}", reviewId);
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    /** 복기 통계 */
    @GetMapping("/statistics")
    public ResponseEntity<ReviewStatisticsDto> getStatistics() {
        ReviewStatisticsDto stats = reviewService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /** 거래 전략 목록 */
    @GetMapping("/strategies")
    public ResponseEntity<List<Map<String, String>>> getStrategies() {
        List<Map<String, String>> strategies =
                Arrays.stream(TradeStrategy.values())
                        .map(
                                s ->
                                        Map.of(
                                                "value", s.name(),
                                                "label", s.getLabel(),
                                                "description", s.getDescription()))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(strategies);
    }

    /** 감정 상태 목록 */
    @GetMapping("/emotions")
    public ResponseEntity<List<Map<String, String>>> getEmotions() {
        List<Map<String, String>> emotions =
                Arrays.stream(EmotionState.values())
                        .map(
                                e ->
                                        Map.of(
                                                "value", e.name(),
                                                "label", e.getLabel(),
                                                "description", e.getDescription()))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(emotions);
    }
}
