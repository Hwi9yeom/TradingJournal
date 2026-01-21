package com.trading.journal.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 캘린더 요약 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarSummaryDto {
    /** 조회 기간 시작 */
    private LocalDate startDate;

    /** 조회 기간 종료 */
    private LocalDate endDate;

    /** 총 이벤트 수 */
    private Integer totalEvents;

    /** 고중요도 이벤트 수 */
    private Integer highImportanceCount;

    /** 실적발표 수 */
    private Integer earningsCount;

    /** 보유종목 관련 이벤트 수 */
    private Integer portfolioRelatedCount;

    /** 국가별 이벤트 수 */
    private Map<String, Long> eventsByCountry;

    /** 유형별 이벤트 수 */
    private Map<String, Long> eventsByType;

    /** 오늘 이벤트 목록 */
    private List<EconomicEventDto> todayEvents;

    /** 다가오는 고중요도 이벤트 */
    private List<EconomicEventDto> upcomingHighImportanceEvents;

    /** 보유종목 실적발표 */
    private List<EconomicEventDto> portfolioEarnings;
}
