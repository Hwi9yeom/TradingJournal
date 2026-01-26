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

    /** FETCH JOIN으로 Transaction 함께 로딩 (N+1 방지) */
    @Query("SELECT r FROM TradeReview r LEFT JOIN FETCH r.transaction")
    List<TradeReview> findAllWithTransaction();

    /** 심리 분석: 감정 전환 통계 */
    @Query(
            "SELECT tr.emotionBefore, tr.emotionAfter, COUNT(tr), "
                    + "SUM(CASE WHEN t.realizedPnl >= 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(tr), "
                    + "AVG(t.realizedPnl) "
                    + "FROM TradeReview tr JOIN tr.transaction t "
                    + "WHERE t.account.id = :accountId "
                    + "AND tr.emotionBefore IS NOT NULL AND tr.emotionAfter IS NOT NULL "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY tr.emotionBefore, tr.emotionAfter "
                    + "ORDER BY COUNT(tr) DESC")
    List<Object[]> getEmotionTransitionStats(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 심리 분석: 틸트 분석용 거래 조회 */
    @Query(
            "SELECT tr FROM TradeReview tr JOIN FETCH tr.transaction t "
                    + "WHERE t.account.id = :accountId "
                    + "AND tr.emotionBefore IS NOT NULL AND tr.emotionAfter IS NOT NULL "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY t.transactionDate ASC, tr.createdAt ASC")
    List<TradeReview> findTradesForTiltAnalysis(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 심리 분석: 감정-실수 상관관계 (tags를 실수 유형으로 사용) */
    @Query(
            "SELECT tr.emotionBefore, tr.tags, COUNT(tr) "
                    + "FROM TradeReview tr JOIN tr.transaction t "
                    + "WHERE t.account.id = :accountId "
                    + "AND tr.emotionBefore IS NOT NULL "
                    + "AND tr.tags IS NOT NULL "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY tr.emotionBefore, tr.tags")
    List<Object[]> getEmotionMistakeCorrelation(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 심리 분석: 감정별 거래 수 */
    @Query(
            "SELECT tr.emotionBefore, COUNT(tr) "
                    + "FROM TradeReview tr JOIN tr.transaction t "
                    + "WHERE t.account.id = :accountId "
                    + "AND tr.emotionBefore IS NOT NULL "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY tr.emotionBefore")
    List<Object[]> countTradesByEmotion(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** 심리 분석: 요일별 통계 */
    @Query(
            "SELECT FUNCTION('DAYOFWEEK', t.transactionDate), COUNT(tr), "
                    + "SUM(CASE WHEN t.realizedPnl >= 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(tr), "
                    + "AVG(t.realizedPnl), tr.emotionBefore "
                    + "FROM TradeReview tr JOIN tr.transaction t "
                    + "WHERE t.account.id = :accountId "
                    + "AND t.transactionDate BETWEEN :startDate AND :endDate "
                    + "GROUP BY FUNCTION('DAYOFWEEK', t.transactionDate), tr.emotionBefore")
    List<Object[]> getDayOfWeekStats(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
