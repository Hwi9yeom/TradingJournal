package com.trading.journal.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 일별 캘린더 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCalendarDto {
    /** 날짜 */
    private LocalDate date;

    /** 요일 */
    private String dayOfWeek;

    /** 해당 날짜의 이벤트 목록 */
    private List<EconomicEventDto> events;

    /** 고중요도 이벤트 수 */
    private Integer highImportanceCount;

    /** 실적발표 수 */
    private Integer earningsCount;

    /** 해당일 공휴일/휴장 여부 */
    private Boolean isHoliday;

    /** 공휴일 이름 */
    private String holidayName;
}
