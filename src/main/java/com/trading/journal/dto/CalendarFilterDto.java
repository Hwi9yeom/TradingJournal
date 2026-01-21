package com.trading.journal.dto;

import com.trading.journal.entity.EconomicEventType;
import com.trading.journal.entity.EventImportance;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 캘린더 필터 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarFilterDto {
    /** 시작 날짜 */
    private LocalDate startDate;

    /** 종료 날짜 */
    private LocalDate endDate;

    /** 국가 필터 (US, KR, EU 등) */
    private List<String> countries;

    /** 이벤트 유형 필터 */
    private List<EconomicEventType> eventTypes;

    /** 중요도 필터 */
    private List<EventImportance> importanceLevels;

    /** 보유 종목만 표시 */
    @Builder.Default private Boolean portfolioOnly = false;

    /** 고중요도만 표시 */
    @Builder.Default private Boolean highImportanceOnly = false;
}
