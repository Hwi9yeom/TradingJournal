package com.trading.journal.controller;

import com.trading.journal.dto.BudgetSummaryDto;
import com.trading.journal.dto.MonthlyBudgetDto;
import com.trading.journal.service.MonthlyBudgetService;
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

/** 월별 소득/지출 점검 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Tag(name = "Monthly Budget", description = "월별 소득/지출 점검 및 저축 여력 API")
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;

    /** 월 기록 생성/갱신 */
    @PostMapping
    @Operation(summary = "월 가계 기록 저장", description = "같은 계좌·월 기록이 있으면 덮어씁니다")
    public ResponseEntity<MonthlyBudgetDto> upsert(@RequestBody MonthlyBudgetDto dto) {
        return ResponseEntity.ok(monthlyBudgetService.upsert(dto));
    }

    /** 전체 기록 조회 */
    @GetMapping
    @Operation(summary = "전체 월 기록 조회", description = "모든 월 기록을 최신순으로 조회합니다")
    public ResponseEntity<List<MonthlyBudgetDto>> getAll() {
        return ResponseEntity.ok(monthlyBudgetService.getAll());
    }

    /** 단건 조회 */
    @GetMapping("/{id}")
    @Operation(summary = "월 기록 상세 조회")
    public ResponseEntity<MonthlyBudgetDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(monthlyBudgetService.get(id));
    }

    /** 특정 월 조회 */
    @GetMapping("/month")
    @Operation(summary = "특정 월 기록 조회", description = "해당 월 기록이 없으면 204를 반환합니다")
    public ResponseEntity<MonthlyBudgetDto> getByMonth(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return monthlyBudgetService
                .getByMonth(accountId, month)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 기간 조회 */
    @GetMapping("/range")
    @Operation(summary = "기간별 월 기록 조회")
    public ResponseEntity<List<MonthlyBudgetDto>> getRange(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startMonth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endMonth) {
        return ResponseEntity.ok(monthlyBudgetService.getRange(accountId, startMonth, endMonth));
    }

    /** 요약 조회 */
    @GetMapping("/summary")
    @Operation(summary = "가계 요약 조회", description = "최근 N개월 평균 소득/지출/저축률과 총자산 변화를 조회합니다")
    public ResponseEntity<BudgetSummaryDto> getSummary(
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(monthlyBudgetService.getSummary(accountId, months));
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    @Operation(summary = "월 기록 삭제")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        monthlyBudgetService.delete(id);
        return ResponseEntity.ok(Map.of("message", "월 가계 기록이 삭제되었습니다", "id", String.valueOf(id)));
    }
}
