package com.trading.journal.controller;

import com.trading.journal.dto.*;
import com.trading.journal.service.EconomicCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** 경제 캘린더 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Tag(name = "Economic Calendar", description = "경제 캘린더 API - 경제지표, 실적발표, FOMC 등")
public class CalendarController {

    private final EconomicCalendarService calendarService;

    private static final List<Map<String, String>> EVENT_TYPES =
            List.of(
                    Map.of("value", "ECONOMIC_INDICATOR", "label", "경제지표"),
                    Map.of("value", "CENTRAL_BANK", "label", "중앙은행"),
                    Map.of("value", "EARNINGS", "label", "실적발표"),
                    Map.of("value", "DIVIDEND", "label", "배당"),
                    Map.of("value", "IPO", "label", "IPO"),
                    Map.of("value", "HOLIDAY", "label", "휴장"),
                    Map.of("value", "OTHER", "label", "기타"));

    private static final List<Map<String, String>> IMPORTANCE_LEVELS =
            List.of(
                    Map.of("value", "HIGH", "label", "높음", "color", "red"),
                    Map.of("value", "MEDIUM", "label", "보통", "color", "yellow"),
                    Map.of("value", "LOW", "label", "낮음", "color", "green"));

    private static final List<Map<String, String>> COUNTRIES =
            List.of(
                    Map.of("code", "US", "name", "미국", "flag", "🇺🇸"),
                    Map.of("code", "EU", "name", "유럽연합", "flag", "🇪🇺"),
                    Map.of("code", "GB", "name", "영국", "flag", "🇬🇧"),
                    Map.of("code", "JP", "name", "일본", "flag", "🇯🇵"),
                    Map.of("code", "CN", "name", "중국", "flag", "🇨🇳"),
                    Map.of("code", "KR", "name", "대한민국", "flag", "🇰🇷"),
                    Map.of("code", "DE", "name", "독일", "flag", "🇩🇪"),
                    Map.of("code", "AU", "name", "호주", "flag", "🇦🇺"),
                    Map.of("code", "CA", "name", "캐나다", "flag", "🇨🇦"),
                    Map.of("code", "CH", "name", "스위스", "flag", "🇨🇭"));

    /** 캘린더 요약 조회 */
    @GetMapping("/summary")
    @Operation(summary = "캘린더 요약", description = "지정 기간의 경제 이벤트 요약 정보 조회")
    public ResponseEntity<CalendarSummaryDto> getSummary(
            @Parameter(description = "시작일 (기본: 오늘)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "종료일 (기본: 7일 후)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {

        LocalDate startDate = from != null ? from : LocalDate.now();
        LocalDate endDate = to != null ? to : startDate.plusDays(7);

        CalendarSummaryDto summary = calendarService.getSummary(startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    /** 기간별 이벤트 조회 */
    @GetMapping("/events")
    @Operation(summary = "이벤트 목록 조회", description = "지정 기간의 모든 경제 이벤트 조회")
    public ResponseEntity<List<EconomicEventDto>> getEvents(
            @Parameter(description = "시작일")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "종료일")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {

        List<EconomicEventDto> events = calendarService.getEvents(from, to);
        return ResponseEntity.ok(events);
    }

    /** 필터링된 이벤트 조회 */
    @PostMapping("/events/filter")
    @Operation(summary = "이벤트 필터 조회", description = "조건에 맞는 경제 이벤트 필터링 조회")
    public ResponseEntity<List<EconomicEventDto>> getEventsWithFilter(
            @RequestBody CalendarFilterDto filter) {

        // 기본값 설정
        if (filter.getStartDate() == null) {
            filter.setStartDate(LocalDate.now());
        }
        if (filter.getEndDate() == null) {
            filter.setEndDate(filter.getStartDate().plusDays(7));
        }

        List<EconomicEventDto> events = calendarService.getEventsWithFilter(filter);
        return ResponseEntity.ok(events);
    }

    /** 오늘 이벤트 조회 */
    @GetMapping("/today")
    @Operation(summary = "오늘 이벤트", description = "오늘의 경제 이벤트 목록 조회")
    public ResponseEntity<List<EconomicEventDto>> getTodayEvents() {
        List<EconomicEventDto> events = calendarService.getTodayEvents();
        return ResponseEntity.ok(events);
    }

    /** 이번 주 이벤트 조회 */
    @GetMapping("/week")
    @Operation(summary = "이번 주 이벤트", description = "이번 주 일별 경제 이벤트 조회")
    public ResponseEntity<List<DailyCalendarDto>> getThisWeekEvents() {
        List<DailyCalendarDto> weekEvents = calendarService.getThisWeekEvents();
        return ResponseEntity.ok(weekEvents);
    }

    /** 보유종목 실적발표 조회 */
    @GetMapping("/earnings/portfolio")
    @Operation(summary = "보유종목 실적발표", description = "포트폴리오 종목의 실적발표 일정 조회")
    public ResponseEntity<List<EconomicEventDto>> getPortfolioEarnings(
            @Parameter(description = "시작일 (기본: 오늘)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "종료일 (기본: 30일 후)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {

        LocalDate startDate = from != null ? from : LocalDate.now();
        LocalDate endDate = to != null ? to : startDate.plusDays(30);

        List<EconomicEventDto> earnings = calendarService.getPortfolioEarnings(startDate, endDate);
        return ResponseEntity.ok(earnings);
    }

    /** 다가오는 실적발표 조회 */
    @GetMapping("/earnings/upcoming")
    @Operation(summary = "다가오는 실적발표", description = "다가오는 실적발표 일정 조회 (인기 종목)")
    public ResponseEntity<List<EconomicEventDto>> getUpcomingEarnings(
            @Parameter(description = "조회 개수 (기본: 20)") @RequestParam(defaultValue = "20")
                    int limit) {

        List<EconomicEventDto> earnings = calendarService.getUpcomingEarnings(limit);
        return ResponseEntity.ok(earnings);
    }

    /** 이벤트 상세 조회 */
    @GetMapping("/events/{id}")
    @Operation(summary = "이벤트 상세", description = "특정 경제 이벤트 상세 정보 조회")
    public ResponseEntity<EconomicEventDto> getEvent(@PathVariable Long id) {
        return calendarService
                .getEvent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 이벤트 알림 설정 */
    @PatchMapping("/events/{id}/alert")
    @Operation(summary = "알림 설정", description = "특정 이벤트의 알림 활성화/비활성화")
    public ResponseEntity<EconomicEventDto> setAlertEnabled(
            @PathVariable Long id, @RequestParam boolean enabled) {

        log.info("이벤트 알림 설정: id={}, enabled={}", id, enabled);
        EconomicEventDto event = calendarService.setAlertEnabled(id, enabled);
        return ResponseEntity.ok(event);
    }

    /** 이벤트 메모 업데이트 */
    @PatchMapping("/events/{id}/notes")
    @Operation(summary = "메모 업데이트", description = "특정 이벤트에 메모 추가/수정")
    public ResponseEntity<EconomicEventDto> updateNotes(
            @PathVariable Long id, @RequestBody Map<String, String> body) {

        String notes = body.get("notes");
        log.info("이벤트 메모 업데이트: id={}", id);
        EconomicEventDto event = calendarService.updateNotes(id, notes);
        return ResponseEntity.ok(event);
    }

    /** Finnhub 데이터 동기화 */
    @PostMapping("/sync")
    @Operation(summary = "데이터 동기화", description = "Finnhub에서 경제 캘린더 데이터 동기화")
    public Mono<ResponseEntity<Map<String, Object>>> syncFromFinnhub(
            @Parameter(description = "시작일 (기본: 오늘)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "종료일 (기본: 14일 후)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {

        LocalDate startDate = from != null ? from : LocalDate.now();
        LocalDate endDate = to != null ? to : startDate.plusDays(14);

        log.info("경제 캘린더 동기화 요청: {} ~ {}", startDate, endDate);

        return calendarService
                .syncFromFinnhub(startDate, endDate)
                .map(
                        count ->
                                ResponseEntity.ok(
                                        Map.of(
                                                "message",
                                                "동기화가 완료되었습니다",
                                                "syncedCount",
                                                count,
                                                "from",
                                                startDate.toString(),
                                                "to",
                                                endDate.toString())));
    }

    /** API 상태 확인 */
    @GetMapping("/health")
    @Operation(summary = "API 상태 확인", description = "Finnhub API 연결 상태 확인")
    public Mono<ResponseEntity<Map<String, Object>>> checkApiHealth() {
        return calendarService
                .checkApiHealth()
                .map(
                        isHealthy ->
                                ResponseEntity.ok(
                                        Map.of(
                                                "status",
                                                isHealthy ? "healthy" : "unhealthy",
                                                "finnhubApi",
                                                isHealthy ? "connected" : "disconnected")));
    }

    /** 이벤트 유형 목록 */
    @GetMapping("/event-types")
    @Operation(summary = "이벤트 유형 목록", description = "지원하는 이벤트 유형 목록 조회")
    public ResponseEntity<List<Map<String, String>>> getEventTypes() {
        return ResponseEntity.ok(EVENT_TYPES);
    }

    /** 중요도 목록 */
    @GetMapping("/importance-levels")
    @Operation(summary = "중요도 목록", description = "이벤트 중요도 레벨 목록 조회")
    public ResponseEntity<List<Map<String, String>>> getImportanceLevels() {
        return ResponseEntity.ok(IMPORTANCE_LEVELS);
    }

    /** 지원 국가 목록 */
    @GetMapping("/countries")
    @Operation(summary = "국가 목록", description = "지원하는 국가/지역 목록 조회")
    public ResponseEntity<List<Map<String, String>>> getCountries() {
        return ResponseEntity.ok(COUNTRIES);
    }
}
