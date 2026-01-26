package com.trading.journal.service;

import com.trading.journal.dto.*;
import com.trading.journal.entity.EconomicEvent;
import com.trading.journal.entity.EconomicEventType;
import com.trading.journal.entity.EventImportance;
import com.trading.journal.repository.EconomicEventRepository;
import com.trading.journal.repository.PortfolioRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** 경제 캘린더 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EconomicCalendarService {

    private final EconomicEventRepository eventRepository;
    private final PortfolioRepository portfolioRepository;
    private final FinnhubApiService finnhubApiService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, String> COUNTRY_FLAGS =
            Map.ofEntries(
                    Map.entry("US", "🇺🇸"),
                    Map.entry("EU", "🇪🇺"),
                    Map.entry("DE", "🇩🇪"),
                    Map.entry("FR", "🇫🇷"),
                    Map.entry("GB", "🇬🇧"),
                    Map.entry("UK", "🇬🇧"),
                    Map.entry("JP", "🇯🇵"),
                    Map.entry("CN", "🇨🇳"),
                    Map.entry("KR", "🇰🇷"),
                    Map.entry("AU", "🇦🇺"),
                    Map.entry("CA", "🇨🇦"),
                    Map.entry("CH", "🇨🇭"));

    /** 기간별 이벤트 조회 */
    public List<EconomicEventDto> getEvents(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        List<EconomicEvent> events =
                eventRepository.findByEventTimeBetweenOrderByEventTimeAsc(start, end);
        Set<String> portfolioSymbols = getPortfolioSymbols();

        return events.stream().map(e -> toDto(e, portfolioSymbols)).toList();
    }

    /** 필터링된 이벤트 조회 */
    public List<EconomicEventDto> getEventsWithFilter(CalendarFilterDto filter) {
        LocalDateTime start = filter.getStartDate().atStartOfDay();
        LocalDateTime end = filter.getEndDate().atTime(LocalTime.MAX);
        Set<String> portfolioSymbols = getPortfolioSymbols();

        // 단일 값 필터 추출
        String country = getSingleOrNull(filter.getCountries());
        EconomicEventType eventType = getSingleOrNull(filter.getEventTypes());
        EventImportance importance = getSingleOrNull(filter.getImportanceLevels());

        List<EconomicEvent> events;
        if (Boolean.TRUE.equals(filter.getHighImportanceOnly())) {
            events = eventRepository.findHighImportanceEvents(start, end);
        } else if (country != null || eventType != null || importance != null) {
            events = eventRepository.findWithFilters(country, eventType, importance, start, end);
        } else {
            events = eventRepository.findByEventTimeBetweenOrderByEventTimeAsc(start, end);
        }

        // 보유종목만 필터
        if (Boolean.TRUE.equals(filter.getPortfolioOnly())) {
            events =
                    events.stream()
                            .filter(
                                    e ->
                                            e.getSymbol() != null
                                                    && portfolioSymbols.contains(e.getSymbol()))
                            .toList();
        }

        return events.stream().map(e -> toDto(e, portfolioSymbols)).toList();
    }

    private <T> T getSingleOrNull(List<T> list) {
        return (list != null && list.size() == 1) ? list.get(0) : null;
    }

    /** 오늘 이벤트 조회 */
    public List<EconomicEventDto> getTodayEvents() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<EconomicEvent> events = eventRepository.findTodayEvents(startOfDay, endOfDay);
        Set<String> portfolioSymbols = getPortfolioSymbols();
        return events.stream().map(e -> toDto(e, portfolioSymbols)).toList();
    }

    /** 이번 주 이벤트 조회 */
    public List<DailyCalendarDto> getThisWeekEvents() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<EconomicEvent> events =
                eventRepository.findByEventTimeBetweenOrderByEventTimeAsc(
                        weekStart.atStartOfDay(), weekEnd.atTime(LocalTime.MAX));
        Set<String> portfolioSymbols = getPortfolioSymbols();

        Map<LocalDate, List<EconomicEvent>> eventsByDate =
                events.stream().collect(Collectors.groupingBy(e -> e.getEventTime().toLocalDate()));

        List<DailyCalendarDto> weekCalendar = new ArrayList<>();
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            List<EconomicEvent> dayEvents = eventsByDate.getOrDefault(date, List.of());
            weekCalendar.add(buildDailyCalendar(date, dayEvents, portfolioSymbols));
        }
        return weekCalendar;
    }

    private DailyCalendarDto buildDailyCalendar(
            LocalDate date, List<EconomicEvent> dayEvents, Set<String> portfolioSymbols) {
        return DailyCalendarDto.builder()
                .date(date)
                .dayOfWeek(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN))
                .events(dayEvents.stream().map(e -> toDto(e, portfolioSymbols)).toList())
                .highImportanceCount(
                        (int)
                                dayEvents.stream()
                                        .filter(e -> e.getImportance() == EventImportance.HIGH)
                                        .count())
                .earningsCount(
                        (int)
                                dayEvents.stream()
                                        .filter(e -> e.getEventType() == EconomicEventType.EARNINGS)
                                        .count())
                .isHoliday(
                        dayEvents.stream()
                                .anyMatch(e -> e.getEventType() == EconomicEventType.HOLIDAY))
                .holidayName(
                        dayEvents.stream()
                                .filter(e -> e.getEventType() == EconomicEventType.HOLIDAY)
                                .findFirst()
                                .map(EconomicEvent::getEventName)
                                .orElse(null))
                .build();
    }

    /** 보유종목 실적발표 조회 */
    public List<EconomicEventDto> getPortfolioEarnings(LocalDate from, LocalDate to) {
        Set<String> portfolioSymbols = getPortfolioSymbols();
        if (portfolioSymbols.isEmpty()) {
            return List.of();
        }

        List<EconomicEvent> events =
                eventRepository.findEarningsForSymbols(
                        new ArrayList<>(portfolioSymbols),
                        from.atStartOfDay(),
                        to.atTime(LocalTime.MAX));

        return events.stream().map(e -> toDto(e, portfolioSymbols)).toList();
    }

    /** 다가오는 실적발표 조회 */
    public List<EconomicEventDto> getUpcomingEarnings(int limit) {
        List<EconomicEvent> events =
                eventRepository.findUpcomingEarnings(LocalDateTime.now(), PageRequest.of(0, limit));
        Set<String> portfolioSymbols = getPortfolioSymbols();
        return events.stream().map(e -> toDto(e, portfolioSymbols)).toList();
    }

    /** 캘린더 요약 정보 조회 */
    public CalendarSummaryDto getSummary(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        Set<String> portfolioSymbols = getPortfolioSymbols();

        List<EconomicEvent> allEvents =
                eventRepository.findByEventTimeBetweenOrderByEventTimeAsc(start, end);

        // 통계 계산
        long highCount =
                allEvents.stream().filter(e -> e.getImportance() == EventImportance.HIGH).count();
        long earningsCount =
                allEvents.stream()
                        .filter(e -> e.getEventType() == EconomicEventType.EARNINGS)
                        .count();
        long portfolioCount =
                allEvents.stream()
                        .filter(
                                e ->
                                        e.getSymbol() != null
                                                && portfolioSymbols.contains(e.getSymbol()))
                        .count();

        // 국가별 집계
        Map<String, Long> byCountry =
                allEvents.stream()
                        .collect(
                                Collectors.groupingBy(
                                        EconomicEvent::getCountry, Collectors.counting()));

        // 유형별 집계
        Map<String, Long> byType =
                allEvents.stream()
                        .collect(
                                Collectors.groupingBy(
                                        e -> e.getEventType().name(), Collectors.counting()));

        // 오늘 이벤트
        List<EconomicEventDto> todayEvents = getTodayEvents();

        // 고중요도 이벤트 (다가오는 5개)
        List<EconomicEventDto> upcomingHigh =
                allEvents.stream()
                        .filter(e -> e.getImportance() == EventImportance.HIGH)
                        .filter(e -> e.getEventTime().isAfter(LocalDateTime.now()))
                        .limit(5)
                        .map(e -> toDto(e, portfolioSymbols))
                        .toList();

        // 보유종목 실적
        List<EconomicEventDto> portfolioEarnings =
                allEvents.stream()
                        .filter(e -> e.getEventType() == EconomicEventType.EARNINGS)
                        .filter(
                                e ->
                                        e.getSymbol() != null
                                                && portfolioSymbols.contains(e.getSymbol()))
                        .map(e -> toDto(e, portfolioSymbols))
                        .toList();

        return CalendarSummaryDto.builder()
                .startDate(from)
                .endDate(to)
                .totalEvents(allEvents.size())
                .highImportanceCount((int) highCount)
                .earningsCount((int) earningsCount)
                .portfolioRelatedCount((int) portfolioCount)
                .eventsByCountry(byCountry)
                .eventsByType(byType)
                .todayEvents(todayEvents)
                .upcomingHighImportanceEvents(upcomingHigh)
                .portfolioEarnings(portfolioEarnings)
                .build();
    }

    /** 이벤트 상세 조회 */
    public Optional<EconomicEventDto> getEvent(Long id) {
        Set<String> portfolioSymbols = getPortfolioSymbols();
        return eventRepository.findById(id).map(e -> toDto(e, portfolioSymbols));
    }

    /** 이벤트 알림 설정 */
    @Transactional
    public EconomicEventDto setAlertEnabled(Long id, boolean enabled) {
        EconomicEvent event =
                eventRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("이벤트를 찾을 수 없습니다: " + id));

        event.setAlertEnabled(enabled);
        EconomicEvent saved = eventRepository.save(event);

        return toDto(saved, getPortfolioSymbols());
    }

    /** 이벤트 메모 업데이트 */
    @Transactional
    public EconomicEventDto updateNotes(Long id, String notes) {
        EconomicEvent event =
                eventRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("이벤트를 찾을 수 없습니다: " + id));

        event.setNotes(notes);
        EconomicEvent saved = eventRepository.save(event);

        return toDto(saved, getPortfolioSymbols());
    }

    /** Finnhub에서 데이터 동기화 */
    @Transactional
    public Mono<Integer> syncFromFinnhub(LocalDate from, LocalDate to) {
        log.info("Finnhub 동기화 시작: {} ~ {}", from, to);

        return Mono.zip(
                        finnhubApiService.getEconomicCalendar(from, to),
                        finnhubApiService.getEarningsCalendar(from, to),
                        finnhubApiService.getIpoCalendar(from, to))
                .map(
                        tuple -> {
                            List<EconomicEvent> allEvents = new ArrayList<>();
                            allEvents.addAll(tuple.getT1());
                            allEvents.addAll(tuple.getT2());
                            allEvents.addAll(tuple.getT3());

                            int savedCount = 0;
                            for (EconomicEvent event : allEvents) {
                                try {
                                    // 중복 체크
                                    Optional<EconomicEvent> existing =
                                            eventRepository.findByExternalIdAndSource(
                                                    event.getExternalId(), event.getSource());

                                    if (existing.isEmpty()) {
                                        eventRepository.save(event);
                                        savedCount++;
                                    } else {
                                        // 기존 이벤트 업데이트 (actual 값 등)
                                        EconomicEvent existingEvent = existing.get();
                                        if (event.getActual() != null) {
                                            existingEvent.setActual(event.getActual());
                                        }
                                        if (event.getEpsActual() != null) {
                                            existingEvent.setEpsActual(event.getEpsActual());
                                        }
                                        if (event.getRevenueActual() != null) {
                                            existingEvent.setRevenueActual(
                                                    event.getRevenueActual());
                                        }
                                        eventRepository.save(existingEvent);
                                    }
                                } catch (Exception e) {
                                    log.warn("이벤트 저장 실패: {}", e.getMessage());
                                }
                            }

                            log.info("Finnhub 동기화 완료: {} 건 저장", savedCount);
                            return savedCount;
                        })
                .doOnError(error -> log.error("Finnhub 동기화 실패: {}", error.getMessage()))
                .onErrorReturn(0);
    }

    /** 오래된 이벤트 정리 */
    @Transactional
    public int cleanupOldEvents(int daysToKeep) {
        LocalDateTime before = LocalDateTime.now().minusDays(daysToKeep);
        int deleted = eventRepository.deleteOldEvents(before);
        log.info("오래된 이벤트 {} 건 삭제", deleted);
        return deleted;
    }

    /** API 상태 확인 */
    public Mono<Boolean> checkApiHealth() {
        return finnhubApiService.healthCheck();
    }

    private Set<String> getPortfolioSymbols() {
        try {
            return portfolioRepository.findAllWithStockAndAccount().stream()
                    .filter(p -> p.getStock() != null)
                    .filter(p -> p.getQuantity() != null && p.getQuantity().signum() > 0)
                    .map(p -> p.getStock().getSymbol())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("포트폴리오 조회 실패: {}", e.getMessage());
            return Set.of();
        }
    }

    private EconomicEventDto toDto(EconomicEvent entity, Set<String> portfolioSymbols) {
        boolean isInPortfolio =
                entity.getSymbol() != null && portfolioSymbols.contains(entity.getSymbol());

        return EconomicEventDto.builder()
                .id(entity.getId())
                .eventTime(entity.getEventTime())
                .country(entity.getCountry())
                .eventName(entity.getEventName())
                .eventType(entity.getEventType())
                .importance(entity.getImportance())
                .actual(entity.getActual())
                .forecast(entity.getForecast())
                .previous(entity.getPrevious())
                .unit(entity.getUnit())
                .currency(entity.getCurrency())
                .symbol(entity.getSymbol())
                .epsEstimate(entity.getEpsEstimate())
                .epsActual(entity.getEpsActual())
                .revenueEstimate(entity.getRevenueEstimate())
                .revenueActual(entity.getRevenueActual())
                .notes(entity.getNotes())
                .alertEnabled(entity.getAlertEnabled())
                .createdAt(entity.getCreatedAt())
                // 추가 표시 필드
                .eventTypeLabel(getEventTypeLabel(entity.getEventType()))
                .importanceLabel(getImportanceLabel(entity.getImportance()))
                .countryFlag(COUNTRY_FLAGS.getOrDefault(entity.getCountry(), "🌐"))
                .timeFormatted(
                        entity.getEventTime() != null
                                ? entity.getEventTime().format(TIME_FORMATTER)
                                : "")
                .isPast(entity.isPast())
                .isToday(entity.isToday())
                .epsSurprise(entity.getEpsSurprise())
                .revenueSurprise(entity.getRevenueSurprise())
                .isInPortfolio(isInPortfolio)
                .build();
    }

    private String getEventTypeLabel(EconomicEventType type) {
        return switch (type) {
            case ECONOMIC_INDICATOR -> "경제지표";
            case CENTRAL_BANK -> "중앙은행";
            case EARNINGS -> "실적발표";
            case DIVIDEND -> "배당";
            case IPO -> "IPO";
            case HOLIDAY -> "휴장";
            case OTHER -> "기타";
        };
    }

    private String getImportanceLabel(EventImportance importance) {
        return switch (importance) {
            case HIGH -> "높음";
            case MEDIUM -> "보통";
            case LOW -> "낮음";
        };
    }
}
