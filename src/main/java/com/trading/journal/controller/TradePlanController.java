package com.trading.journal.controller;

import com.trading.journal.dto.PositionSizingResultDto;
import com.trading.journal.dto.TradePlanDto;
import com.trading.journal.dto.TradePlanDto.*;
import com.trading.journal.entity.TradePlanStatus;
import com.trading.journal.entity.TradePlanType;
import com.trading.journal.entity.TradeStrategy;
import com.trading.journal.service.TradePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trade Plan", description = "트레이드 플랜 관리 API")
public class TradePlanController {

    private final TradePlanService planService;

    /**
     * 플랜 생성
     */
    @PostMapping
    @Operation(summary = "트레이드 플랜 생성", description = "새로운 거래 계획을 생성합니다")
    public ResponseEntity<TradePlanDto> createPlan(@Valid @RequestBody TradePlanDto dto) {
        log.info("Creating trade plan for {}", dto.getStockSymbol());
        TradePlanDto created = planService.createPlan(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 플랜 수정
     */
    @PutMapping("/{id}")
    @Operation(summary = "트레이드 플랜 수정", description = "기존 거래 계획을 수정합니다 (PLANNED 상태만)")
    public ResponseEntity<TradePlanDto> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody TradePlanDto dto) {
        log.info("Updating trade plan {}", id);
        return ResponseEntity.ok(planService.updatePlan(id, dto));
    }

    /**
     * 플랜 실행 (거래로 변환)
     */
    @PostMapping("/{id}/execute")
    @Operation(summary = "플랜 실행", description = "계획을 실제 거래로 변환합니다")
    public ResponseEntity<TradePlanDto> executePlan(
            @PathVariable Long id,
            @Valid @RequestBody ExecuteRequest request) {
        log.info("Executing trade plan {}", id);
        return ResponseEntity.ok(planService.executePlan(id, request));
    }

    /**
     * 플랜 취소
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "플랜 취소", description = "거래 계획을 취소합니다")
    public ResponseEntity<TradePlanDto> cancelPlan(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        log.info("Cancelling trade plan {}", id);
        return ResponseEntity.ok(planService.cancelPlan(id, reason));
    }

    /**
     * 플랜 결과 업데이트
     */
    @PatchMapping("/{id}/result")
    @Operation(summary = "플랜 결과 업데이트", description = "거래 청산 후 결과를 기록합니다")
    public ResponseEntity<TradePlanDto> updatePlanResult(
            @PathVariable Long id,
            @RequestParam Long resultTransactionId,
            @RequestParam(required = false) Boolean followedPlan) {
        return ResponseEntity.ok(planService.updatePlanResult(id, resultTransactionId, followedPlan));
    }

    /**
     * 플랜 조회
     */
    @GetMapping("/{id}")
    @Operation(summary = "플랜 조회", description = "특정 플랜의 상세 정보를 조회합니다")
    public ResponseEntity<TradePlanDto> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(planService.getPlan(id));
    }

    /**
     * 대기 중인 플랜 조회
     */
    @GetMapping("/pending")
    @Operation(summary = "대기 중 플랜 조회", description = "아직 실행되지 않은 플랜 목록 (만료 임박 순)")
    public ResponseEntity<List<TradePlanDto>> getPendingPlans() {
        return ResponseEntity.ok(planService.getPendingPlans());
    }

    /**
     * 상태별 플랜 조회
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "상태별 플랜 조회", description = "특정 상태의 플랜 목록을 조회합니다")
    public ResponseEntity<List<TradePlanDto>> getPlansByStatus(@PathVariable TradePlanStatus status) {
        return ResponseEntity.ok(planService.getPlansByStatus(status));
    }

    /**
     * 계좌별 플랜 조회
     */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "계좌별 플랜 조회", description = "특정 계좌의 플랜 목록을 조회합니다")
    public ResponseEntity<List<TradePlanDto>> getPlansByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(planService.getPlansByAccount(accountId));
    }

    /**
     * 최근 플랜 조회 (페이징)
     */
    @GetMapping
    @Operation(summary = "전체 플랜 조회", description = "모든 플랜을 페이징하여 조회합니다")
    public ResponseEntity<Page<TradePlanDto>> getRecentPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(planService.getRecentPlans(page, size));
    }

    /**
     * 플랜 삭제
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "플랜 삭제", description = "플랜을 삭제합니다 (EXECUTED 상태 제외)")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable Long id) {
        log.info("Deleting trade plan {}", id);
        planService.deletePlan(id);
        return ResponseEntity.ok(Map.of(
            "message", "플랜이 삭제되었습니다",
            "id", id
        ));
    }

    /**
     * 플랜 통계
     */
    @GetMapping("/statistics")
    @Operation(summary = "플랜 통계", description = "플랜 실행률, 준수율, 전략별 통계 등")
    public ResponseEntity<PlanStatisticsDto> getStatistics() {
        return ResponseEntity.ok(planService.getStatistics());
    }

    /**
     * 포지션 사이징 계산
     */
    @GetMapping("/calculate-position")
    @Operation(summary = "포지션 사이징 계산", description = "진입가/손절가 기반 추천 수량 계산")
    public ResponseEntity<PositionSizingResultDto> calculatePositionSize(
            @RequestParam(required = false) Long accountId,
            @RequestParam BigDecimal entryPrice,
            @RequestParam BigDecimal stopLossPrice,
            @RequestParam(required = false) BigDecimal takeProfitPrice,
            @RequestParam(required = false) BigDecimal riskPercent) {
        return ResponseEntity.ok(planService.calculatePositionSize(
            accountId, entryPrice, stopLossPrice, takeProfitPrice, riskPercent));
    }

    /**
     * 플랜 상태 목록
     */
    @GetMapping("/statuses")
    @Operation(summary = "플랜 상태 목록", description = "사용 가능한 플랜 상태 목록")
    public ResponseEntity<List<Map<String, String>>> getStatuses() {
        List<Map<String, String>> statuses = Arrays.stream(TradePlanStatus.values())
                .map(s -> Map.of(
                        "value", s.name(),
                        "label", s.getLabel(),
                        "description", s.getDescription()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(statuses);
    }

    /**
     * 플랜 유형 목록
     */
    @GetMapping("/types")
    @Operation(summary = "플랜 유형 목록", description = "LONG/SHORT 유형 목록")
    public ResponseEntity<List<Map<String, String>>> getTypes() {
        List<Map<String, String>> types = Arrays.stream(TradePlanType.values())
                .map(t -> Map.of(
                        "value", t.name(),
                        "label", t.getLabel(),
                        "description", t.getDescription()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    /**
     * 전략 목록
     */
    @GetMapping("/strategies")
    @Operation(summary = "전략 목록", description = "사용 가능한 거래 전략 목록")
    public ResponseEntity<List<Map<String, String>>> getStrategies() {
        List<Map<String, String>> strategies = Arrays.stream(TradeStrategy.values())
                .map(s -> Map.of(
                        "value", s.name(),
                        "label", s.getLabel(),
                        "description", s.getDescription()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(strategies);
    }
}
