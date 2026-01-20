package com.trading.journal.controller;

import com.trading.journal.dto.TradingJournalDto;
import com.trading.journal.dto.TradingJournalDto.*;
import com.trading.journal.entity.EmotionState;
import com.trading.journal.service.TradingJournalService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 트레이딩 일지 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class TradingJournalController {

    private final TradingJournalService journalService;

    /** 일지 생성 */
    @PostMapping
    public ResponseEntity<TradingJournalDto> createJournal(@RequestBody JournalRequest request) {
        log.info("일지 생성 요청: date={}", request.getJournalDate());
        TradingJournalDto journal = journalService.createJournal(request);
        return ResponseEntity.ok(journal);
    }

    /** 일지 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<TradingJournalDto> updateJournal(
            @PathVariable Long id, @RequestBody JournalRequest request) {
        log.info("일지 수정 요청: id={}", id);
        TradingJournalDto journal = journalService.updateJournal(id, request);
        return ResponseEntity.ok(journal);
    }

    /** 날짜별 일지 조회 */
    @GetMapping("/date/{date}")
    public ResponseEntity<TradingJournalDto> getJournalByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long accountId) {
        log.info("날짜별 일지 조회: date={}, accountId={}", date, accountId);
        TradingJournalDto journal = journalService.getJournalByDate(accountId, date);
        return ResponseEntity.ok(journal);
    }

    /** 일지 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable Long id) {
        log.info("일지 삭제 요청: id={}", id);
        journalService.deleteJournal(id);
        return ResponseEntity.noContent().build();
    }

    /** 기간별 일지 조회 */
    @GetMapping("/range")
    public ResponseEntity<List<TradingJournalDto>> getJournalRange(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("기간별 일지 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<TradingJournalDto> journals =
                journalService.getJournalRange(accountId, startDate, endDate);
        return ResponseEntity.ok(journals);
    }

    /** 일지 목록 (캘린더용) */
    @GetMapping("/list")
    public ResponseEntity<List<JournalListItem>> getJournalList(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("일지 목록 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        List<JournalListItem> journals =
                journalService.getJournalList(accountId, startDate, endDate);
        return ResponseEntity.ok(journals);
    }

    /** 일지 통계 조회 */
    @GetMapping("/statistics")
    public ResponseEntity<JournalStatistics> getStatistics(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("일지 통계 조회: accountId={}, period={} ~ {}", accountId, startDate, endDate);
        JournalStatistics statistics = journalService.getStatistics(accountId, startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /** 감정 상태 목록 조회 */
    @GetMapping("/emotions")
    public ResponseEntity<List<Map<String, String>>> getEmotions() {
        List<Map<String, String>> emotions =
                Arrays.stream(EmotionState.values())
                        .map(
                                e ->
                                        Map.of(
                                                "value", e.name(),
                                                "label", e.getLabel(),
                                                "description", e.getDescription()))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(emotions);
    }
}
