package com.trading.journal.controller;

import com.trading.journal.dto.TradingStatisticsDto;
import com.trading.journal.dto.TradingStatisticsDto.*;
import com.trading.journal.service.TradingStatisticsService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 거래 통계 분석 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class TradingStatisticsController {

    private final TradingStatisticsService tradingStatisticsService;

    /** 전체 거래 통계 조회 */
    @GetMapping
    public ResponseEntity<TradingStatisticsDto> getFullStatistics(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("전체 거래 통계 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        TradingStatisticsDto statistics =
                tradingStatisticsService.getFullStatistics(accountId, startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /** 시간대별 성과 조회 */
    @GetMapping("/time-of-day")
    public ResponseEntity<List<TimeOfDayStats>> getTimeOfDayPerformance(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("시간대별 성과 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<TimeOfDayStats> stats =
                tradingStatisticsService.getTimeOfDayPerformance(accountId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    /** 요일별 성과 조회 */
    @GetMapping("/weekday")
    public ResponseEntity<List<WeekdayStats>> getWeekdayPerformance(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("요일별 성과 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<WeekdayStats> stats =
                tradingStatisticsService.getWeekdayPerformance(accountId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    /** 종목별 성과 조회 */
    @GetMapping("/by-symbol")
    public ResponseEntity<List<SymbolStats>> getSymbolPerformance(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("종목별 성과 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<SymbolStats> stats =
                tradingStatisticsService.getSymbolPerformance(accountId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    /** 실수 패턴 조회 */
    @GetMapping("/mistakes")
    public ResponseEntity<List<MistakePattern>> getMistakePatterns(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("실수 패턴 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<MistakePattern> patterns =
                tradingStatisticsService.getMistakePatterns(accountId, startDate, endDate);
        return ResponseEntity.ok(patterns);
    }

    /** 개선 제안 조회 */
    @GetMapping("/suggestions")
    public ResponseEntity<List<ImprovementSuggestion>> getImprovementSuggestions(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("개선 제안 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<ImprovementSuggestion> suggestions =
                tradingStatisticsService.getImprovementSuggestions(accountId, startDate, endDate);
        return ResponseEntity.ok(suggestions);
    }
}
