package com.trading.journal.controller;

import com.trading.journal.dto.MarketSentimentDto;
import com.trading.journal.dto.SentimentDashboardDto;
import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.service.MarketSentimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 시장 심리 지표 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
@Tag(name = "Market Sentiment", description = "시장 심리 지표 기록 및 종합 판정 API")
public class MarketSentimentController {

    private final MarketSentimentService marketSentimentService;

    /** 지표 값 기록 */
    @PostMapping
    @Operation(summary = "심리 지표 기록", description = "같은 지표를 같은 날짜에 다시 기록하면 덮어씁니다")
    public ResponseEntity<MarketSentimentDto> record(@RequestBody MarketSentimentDto dto) {
        return ResponseEntity.ok(marketSentimentService.record(dto));
    }

    /** 종합 대시보드 */
    @GetMapping("/dashboard")
    @Operation(summary = "종합 심리 조회", description = "지표별 최신값을 정규화해 시장 전체의 공포/탐욕 정도를 반환합니다")
    public ResponseEntity<SentimentDashboardDto> getDashboard() {
        return ResponseEntity.ok(marketSentimentService.getDashboard());
    }

    /** 지표 카탈로그 */
    @GetMapping("/catalog")
    @Operation(summary = "지표 목록 조회", description = "지표 종류, 단위, 출처, 해석 기준을 반환합니다")
    public ResponseEntity<List<MarketSentimentDto>> getCatalog() {
        return ResponseEntity.ok(marketSentimentService.getCatalog());
    }

    /** 전체 기록 */
    @GetMapping
    @Operation(summary = "전체 기록 조회")
    public ResponseEntity<List<MarketSentimentDto>> getAll() {
        return ResponseEntity.ok(marketSentimentService.getAll());
    }

    /** 지표별 기록 */
    @GetMapping("/indicator/{indicator}")
    @Operation(summary = "지표별 기록 조회")
    public ResponseEntity<List<MarketSentimentDto>> getByIndicator(
            @PathVariable SentimentIndicator indicator) {
        return ResponseEntity.ok(marketSentimentService.getByIndicator(indicator));
    }

    /** 지표별 추이 */
    @GetMapping("/indicator/{indicator}/trend")
    @Operation(summary = "지표별 추이 조회", description = "기간 내 기록을 오래된 순으로 반환합니다")
    public ResponseEntity<List<MarketSentimentDto>> getTrend(
            @PathVariable SentimentIndicator indicator,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(marketSentimentService.getTrend(indicator, startDate, endDate));
    }

    /** 기록 삭제 */
    @DeleteMapping("/{id}")
    @Operation(summary = "심리 지표 기록 삭제")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        marketSentimentService.delete(id);
        return ResponseEntity.ok(Map.of("message", "심리 지표 기록이 삭제되었습니다", "id", String.valueOf(id)));
    }
}
