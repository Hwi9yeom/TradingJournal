package com.trading.journal.controller;

import com.trading.journal.dto.SavingsRecordDto;
import com.trading.journal.service.SavingsRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 저축 기록(저축 일지) API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
@Tag(name = "Savings", description = "저축 기록 관리 API")
public class SavingsRecordController {

    private final SavingsRecordService savingsRecordService;

    /** 저축 기록 추가 */
    @PostMapping
    @Operation(summary = "저축 기록 추가")
    public ResponseEntity<SavingsRecordDto> create(@RequestBody SavingsRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsRecordService.create(dto));
    }

    /** 저축 기록 수정 */
    @PutMapping("/{id}")
    @Operation(summary = "저축 기록 수정")
    public ResponseEntity<SavingsRecordDto> update(
            @PathVariable Long id, @RequestBody SavingsRecordDto dto) {
        return ResponseEntity.ok(savingsRecordService.update(id, dto));
    }

    /** 저축 기록 삭제 */
    @DeleteMapping("/{id}")
    @Operation(summary = "저축 기록 삭제")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        savingsRecordService.delete(id);
        return ResponseEntity.ok(Map.of("message", "저축 기록이 삭제되었습니다", "id", String.valueOf(id)));
    }

    /** 단건 조회 */
    @GetMapping("/{id}")
    @Operation(summary = "저축 기록 상세 조회")
    public ResponseEntity<SavingsRecordDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(savingsRecordService.get(id));
    }

    /** 전체 조회 */
    @GetMapping
    @Operation(summary = "전체 저축 기록 조회", description = "모든 저축 기록을 최신순으로 조회합니다")
    public ResponseEntity<List<SavingsRecordDto>> getAll() {
        return ResponseEntity.ok(savingsRecordService.getAll());
    }

    /** 기간 조회 */
    @GetMapping("/range")
    @Operation(summary = "기간별 저축 기록 조회")
    public ResponseEntity<List<SavingsRecordDto>> getRange(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(savingsRecordService.getRange(accountId, startDate, endDate));
    }

    /** 월별 합계 */
    @GetMapping("/monthly-totals")
    @Operation(summary = "월별 저축 합계 조회")
    public ResponseEntity<Map<String, BigDecimal>> getMonthlyTotals(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
                savingsRecordService.getMonthlyTotals(accountId, startDate, endDate));
    }

    /** 분류별 합계 */
    @GetMapping("/category-totals")
    @Operation(summary = "분류별 저축 합계 조회")
    public ResponseEntity<Map<String, BigDecimal>> getCategoryTotals() {
        return ResponseEntity.ok(savingsRecordService.getTotalsByCategory());
    }
}
