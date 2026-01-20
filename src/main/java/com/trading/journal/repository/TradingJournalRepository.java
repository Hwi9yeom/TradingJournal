package com.trading.journal.repository;

import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradingJournal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 트레이딩 일지 Repository */
@Repository
public interface TradingJournalRepository extends JpaRepository<TradingJournal, Long> {

    /** 날짜로 일지 조회 */
    Optional<TradingJournal> findByJournalDate(LocalDate journalDate);

    /** 계좌 + 날짜로 일지 조회 */
    Optional<TradingJournal> findByAccountIdAndJournalDate(Long accountId, LocalDate journalDate);

    /** 계좌 + 날짜 (계좌 null 포함) */
    @Query(
            "SELECT j FROM TradingJournal j WHERE "
                    + "((:accountId IS NULL AND j.accountId IS NULL) OR j.accountId = :accountId) "
                    + "AND j.journalDate = :journalDate")
    Optional<TradingJournal> findByAccountIdOrNullAndJournalDate(
            @Param("accountId") Long accountId, @Param("journalDate") LocalDate journalDate);

    /** 기간별 일지 조회 */
    List<TradingJournal> findByJournalDateBetweenOrderByJournalDateDesc(
            LocalDate startDate, LocalDate endDate);

    /** 계좌별 기간별 일지 조회 */
    @Query(
            "SELECT j FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate "
                    + "ORDER BY j.journalDate DESC")
    List<TradingJournal> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 최근 일지 조회 (페이징) */
    Page<TradingJournal> findByAccountIdOrderByJournalDateDesc(Long accountId, Pageable pageable);

    /** 전체 일지 최근순 조회 */
    List<TradingJournal> findAllByOrderByJournalDateDesc();

    /** 감정 상태별 일지 조회 */
    List<TradingJournal> findByMorningEmotionOrEveningEmotion(
            EmotionState morningEmotion, EmotionState eveningEmotion);

    /** 태그로 일지 검색 */
    @Query("SELECT j FROM TradingJournal j WHERE j.tags LIKE %:tag% ORDER BY j.journalDate DESC")
    List<TradingJournal> findByTagContaining(@Param("tag") String tag);

    /** 평균 집중도 점수 조회 */
    @Query(
            "SELECT AVG(j.focusScore) FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate "
                    + "AND j.focusScore IS NOT NULL")
    Double getAverageFocusScore(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 평균 규율 점수 조회 */
    @Query(
            "SELECT AVG(j.disciplineScore) FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate "
                    + "AND j.disciplineScore IS NOT NULL")
    Double getAverageDisciplineScore(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 감정 상태별 통계 (오전) */
    @Query(
            "SELECT j.morningEmotion, COUNT(j) FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate "
                    + "AND j.morningEmotion IS NOT NULL "
                    + "GROUP BY j.morningEmotion")
    List<Object[]> getMorningEmotionStats(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 감정 상태별 통계 (오후) */
    @Query(
            "SELECT j.eveningEmotion, COUNT(j) FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate "
                    + "AND j.eveningEmotion IS NOT NULL "
                    + "GROUP BY j.eveningEmotion")
    List<Object[]> getEveningEmotionStats(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 일지 개수 조회 */
    @Query(
            "SELECT COUNT(j) FROM TradingJournal j WHERE "
                    + "(:accountId IS NULL OR j.accountId = :accountId) "
                    + "AND j.journalDate BETWEEN :startDate AND :endDate")
    Long countByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
