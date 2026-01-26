package com.trading.journal.repository;

import com.trading.journal.entity.EconomicEvent;
import com.trading.journal.entity.EconomicEventType;
import com.trading.journal.entity.EventImportance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 경제 캘린더 이벤트 Repository */
@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEvent, Long> {

    /** 기간 내 이벤트 조회 (시간순) */
    List<EconomicEvent> findByEventTimeBetweenOrderByEventTimeAsc(
            LocalDateTime start, LocalDateTime end);

    /** 기간 내 이벤트 조회 (페이징) */
    Page<EconomicEvent> findByEventTimeBetweenOrderByEventTimeAsc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** 오늘 이벤트 조회 */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE e.eventTime BETWEEN :startOfDay AND :endOfDay ORDER BY e.eventTime")
    List<EconomicEvent> findTodayEvents(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    /** 특정 국가의 이벤트 조회 */
    List<EconomicEvent> findByCountryAndEventTimeBetweenOrderByEventTimeAsc(
            String country, LocalDateTime start, LocalDateTime end);

    /** 이벤트 유형별 조회 */
    List<EconomicEvent> findByEventTypeAndEventTimeBetweenOrderByEventTimeAsc(
            EconomicEventType eventType, LocalDateTime start, LocalDateTime end);

    /** 중요도별 이벤트 조회 */
    List<EconomicEvent> findByImportanceAndEventTimeBetweenOrderByEventTimeAsc(
            EventImportance importance, LocalDateTime start, LocalDateTime end);

    /** 고중요도 이벤트만 조회 */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE e.importance = 'HIGH' "
                    + "AND e.eventTime BETWEEN :start AND :end ORDER BY e.eventTime")
    List<EconomicEvent> findHighImportanceEvents(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 특정 종목의 실적발표 조회 */
    List<EconomicEvent> findBySymbolAndEventTypeOrderByEventTimeDesc(
            String symbol, EconomicEventType eventType);

    /** 종목 목록의 실적발표 조회 (보유종목 연동) */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE e.symbol IN :symbols "
                    + "AND e.eventType = 'EARNINGS' AND e.eventTime BETWEEN :start AND :end "
                    + "ORDER BY e.eventTime")
    List<EconomicEvent> findEarningsForSymbols(
            @Param("symbols") List<String> symbols,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 다가오는 실적발표 조회 */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE e.eventType = 'EARNINGS' "
                    + "AND e.eventTime > :now ORDER BY e.eventTime")
    List<EconomicEvent> findUpcomingEarnings(@Param("now") LocalDateTime now, Pageable pageable);

    /** 알림 설정된 이벤트 조회 */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE e.alertEnabled = true "
                    + "AND e.eventTime BETWEEN :start AND :end ORDER BY e.eventTime")
    List<EconomicEvent> findAlertEnabledEvents(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 중복 체크 (동일 이벤트 존재 여부) */
    Optional<EconomicEvent> findByEventTimeAndEventNameAndCountryAndSymbol(
            LocalDateTime eventTime, String eventName, String country, String symbol);

    /** 외부 ID로 조회 */
    Optional<EconomicEvent> findByExternalIdAndSource(String externalId, String source);

    /** 특정 소스의 기간 내 이벤트 삭제 (동기화 전 정리용) */
    @Modifying
    @Query(
            "DELETE FROM EconomicEvent e WHERE e.source = :source "
                    + "AND e.eventTime BETWEEN :start AND :end")
    int deleteBySourceAndEventTimeBetween(
            @Param("source") String source,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 오래된 이벤트 삭제 */
    @Modifying
    @Query("DELETE FROM EconomicEvent e WHERE e.eventTime < :before")
    int deleteOldEvents(@Param("before") LocalDateTime before);

    /** 국가별 이벤트 개수 */
    @Query(
            "SELECT e.country, COUNT(e) FROM EconomicEvent e "
                    + "WHERE e.eventTime BETWEEN :start AND :end GROUP BY e.country")
    List<Object[]> countByCountry(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 이벤트 유형별 개수 */
    @Query(
            "SELECT e.eventType, COUNT(e) FROM EconomicEvent e "
                    + "WHERE e.eventTime BETWEEN :start AND :end GROUP BY e.eventType")
    List<Object[]> countByEventType(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 국가 및 유형별 필터 조회 */
    @Query(
            "SELECT e FROM EconomicEvent e WHERE "
                    + "(:country IS NULL OR e.country = :country) AND "
                    + "(:eventType IS NULL OR e.eventType = :eventType) AND "
                    + "(:importance IS NULL OR e.importance = :importance) AND "
                    + "e.eventTime BETWEEN :start AND :end ORDER BY e.eventTime")
    List<EconomicEvent> findWithFilters(
            @Param("country") String country,
            @Param("eventType") EconomicEventType eventType,
            @Param("importance") EventImportance importance,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
