package com.trading.journal.scheduler;

import com.trading.journal.service.EconomicCalendarService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 경제 캘린더 동기화 스케줄러 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EconomicCalendarScheduler {

    private final EconomicCalendarService calendarService;

    @Value("${finnhub.api.key:}")
    private String apiKey;

    @Value("${economic-calendar.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${economic-calendar.cleanup.days-to-keep:90}")
    private int daysToKeep;

    /** 매일 오전 6시에 경제 캘린더 동기화 */
    @Scheduled(cron = "0 0 6 * * *")
    public void syncDailyCalendar() {
        if (isSyncDisabled()) return;

        log.info("경제 캘린더 일일 동기화 시작");
        syncCalendar(LocalDate.now(), LocalDate.now().plusDays(7), "일일");
    }

    /** 매주 일요일 자정에 주간 캘린더 동기화 */
    @Scheduled(cron = "0 0 0 * * SUN")
    public void syncWeeklyCalendar() {
        if (isSyncDisabled()) return;

        log.info("경제 캘린더 주간 동기화 시작");
        syncCalendar(LocalDate.now(), LocalDate.now().plusWeeks(2), "주간");
    }

    /** 매월 1일 오전 1시에 오래된 이벤트 정리 및 월간 동기화 */
    @Scheduled(cron = "0 0 1 1 * *")
    public void monthlyCleanupAndSync() {
        log.info("경제 캘린더 월간 정리 시작");

        try {
            int deleted = calendarService.cleanupOldEvents(daysToKeep);
            log.info("오래된 경제 이벤트 {} 건 삭제", deleted);

            if (!isSyncDisabled()) {
                syncCalendar(LocalDate.now(), LocalDate.now().plusMonths(1), "월간");
            }
        } catch (Exception e) {
            log.error("경제 캘린더 월간 정리 실패: {}", e.getMessage(), e);
        }
    }

    /** 평일 장 시작 30분 전 당일 이벤트 업데이트 확인 */
    @Scheduled(cron = "0 30 8 * * MON-FRI")
    public void updateTodayEvents() {
        if (isSyncDisabled()) return;

        LocalDate today = LocalDate.now();
        calendarService
                .syncFromFinnhub(today, today)
                .subscribe(
                        count -> {
                            if (count > 0) log.info("오늘 경제 이벤트 {} 건 업데이트", count);
                        },
                        error -> log.debug("오늘 이벤트 업데이트 실패: {}", error.getMessage()));
    }

    private boolean isSyncDisabled() {
        return !syncEnabled || apiKey == null || apiKey.isBlank();
    }

    private void syncCalendar(LocalDate from, LocalDate to, String type) {
        try {
            calendarService
                    .syncFromFinnhub(from, to)
                    .subscribe(
                            count -> log.info("경제 캘린더 {} 동기화 완료: {} 건", type, count),
                            error -> log.error("경제 캘린더 {} 동기화 실패: {}", type, error.getMessage()));
        } catch (Exception e) {
            log.error("경제 캘린더 {} 동기화 실패: {}", type, e.getMessage(), e);
        }
    }
}
