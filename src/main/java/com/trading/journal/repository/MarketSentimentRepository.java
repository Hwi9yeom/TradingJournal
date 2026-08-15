package com.trading.journal.repository;

import com.trading.journal.entity.MarketSentiment;
import com.trading.journal.entity.SentimentIndicator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 시장 심리 지표 Repository */
@Repository
public interface MarketSentimentRepository extends JpaRepository<MarketSentiment, Long> {

    /** 지표 + 일자로 단건 조회 (하루 한 스냅샷) */
    Optional<MarketSentiment> findByIndicatorAndRecordedDate(
            SentimentIndicator indicator, LocalDate recordedDate);

    /** 지표별 최신순 기록 */
    List<MarketSentiment> findByIndicatorOrderByRecordedDateDesc(SentimentIndicator indicator);

    /** 기간 내 전체 기록 (최신순) */
    List<MarketSentiment> findByRecordedDateBetweenOrderByRecordedDateDesc(
            LocalDate startDate, LocalDate endDate);

    /** 전체 기록 최신순 */
    List<MarketSentiment> findAllByOrderByRecordedDateDesc();

    /** 지표별 기간 기록 (오래된 순 - 추이 차트용) */
    List<MarketSentiment> findByIndicatorAndRecordedDateBetweenOrderByRecordedDateAsc(
            SentimentIndicator indicator, LocalDate startDate, LocalDate endDate);
}
