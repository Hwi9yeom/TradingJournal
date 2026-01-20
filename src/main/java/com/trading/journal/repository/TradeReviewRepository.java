package com.trading.journal.repository;

import com.trading.journal.entity.TradeReview;
import com.trading.journal.entity.TradeStrategy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeReviewRepository extends JpaRepository<TradeReview, Long> {

    /** 거래 ID로 복기 조회 */
    Optional<TradeReview> findByTransactionId(Long transactionId);

    /** 거래 ID로 복기 존재 여부 확인 */
    boolean existsByTransactionId(Long transactionId);

    /** 전략별 복기 조회 */
    List<TradeReview> findByStrategyOrderByCreatedAtDesc(TradeStrategy strategy);

    /** 평점별 복기 조회 */
    List<TradeReview> findByRatingScoreOrderByCreatedAtDesc(Integer ratingScore);

    /** 기간별 복기 조회 */
    @Query(
            "SELECT r FROM TradeReview r WHERE r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt DESC")
    List<TradeReview> findByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 최근 복기 조회 (페이징) */
    Page<TradeReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 전략별 복기 개수 */
    @Query("SELECT r.strategy, COUNT(r) FROM TradeReview r GROUP BY r.strategy")
    List<Object[]> countByStrategy();

    /** 평균 평점 */
    @Query("SELECT AVG(r.ratingScore) FROM TradeReview r WHERE r.ratingScore IS NOT NULL")
    Double getAverageRating();

    /** 태그 검색 */
    @Query("SELECT r FROM TradeReview r WHERE r.tags LIKE %:tag% ORDER BY r.createdAt DESC")
    List<TradeReview> findByTagContaining(@Param("tag") String tag);

    /** 계획 준수 여부별 통계 */
    @Query(
            "SELECT r.followedPlan, COUNT(r) FROM TradeReview r WHERE r.followedPlan IS NOT NULL GROUP BY r.followedPlan")
    List<Object[]> countByFollowedPlan();

    /** 감정 상태별 통계 (거래 전) */
    @Query(
            "SELECT r.emotionBefore, COUNT(r), "
                    + "AVG(CASE WHEN r.transaction.realizedPnl > 0 THEN 1.0 ELSE 0.0 END) * 100 "
                    + "FROM TradeReview r WHERE r.emotionBefore IS NOT NULL AND r.transaction.realizedPnl IS NOT NULL "
                    + "GROUP BY r.emotionBefore")
    List<Object[]> getEmotionBeforeStatistics();
}
