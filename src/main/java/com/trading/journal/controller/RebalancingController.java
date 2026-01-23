package com.trading.journal.controller;

import com.trading.journal.dto.RebalancingDashboardDto;
import com.trading.journal.dto.RebalancingDashboardDto.PositionRebalanceAnalysis;
import com.trading.journal.dto.RebalancingDashboardDto.RebalanceRecommendation;
import com.trading.journal.dto.TargetAllocationBatchDto;
import com.trading.journal.dto.TargetAllocationDto;
import com.trading.journal.service.RebalancingService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 포트폴리오 리밸런싱 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/rebalancing")
@RequiredArgsConstructor
public class RebalancingController {

    private final RebalancingService rebalancingService;

    // ===== 리밸런싱 대시보드 =====

    /** 리밸런싱 대시보드 조회 */
    @GetMapping("/dashboard")
    public ResponseEntity<RebalancingDashboardDto> getRebalancingDashboard(
            @RequestParam(required = false) Long accountId) {
        log.info("리밸런싱 대시보드 조회: accountId={}", accountId);
        return ResponseEntity.ok(rebalancingService.getRebalancingDashboard(accountId));
    }

    /** 단일 포지션 분석 */
    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<PositionRebalanceAnalysis> analyzePosition(
            @PathVariable String symbol, @RequestParam(required = false) Long accountId) {
        log.info("포지션 분석: symbol={}, accountId={}", symbol, accountId);
        return ResponseEntity.ok(rebalancingService.analyzePosition(accountId, symbol));
    }

    /** 리밸런싱 추천 조회 */
    @PostMapping("/recommendations")
    public ResponseEntity<List<RebalanceRecommendation>> getRecommendations(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) BigDecimal minTradeAmount) {
        log.info("리밸런싱 추천 조회: accountId={}, minTradeAmount={}", accountId, minTradeAmount);
        return ResponseEntity.ok(
                rebalancingService.calculateRecommendations(accountId, minTradeAmount));
    }

    // ===== 목표 배분 CRUD =====

    /** 목표 배분 목록 조회 */
    @GetMapping("/allocations")
    public ResponseEntity<List<TargetAllocationDto>> getTargetAllocations(
            @RequestParam(required = false) Long accountId) {
        log.info("목표 배분 목록 조회: accountId={}", accountId);
        return ResponseEntity.ok(rebalancingService.getTargetAllocations(accountId));
    }

    /** 목표 배분 단건 조회 */
    @GetMapping("/allocations/{id}")
    public ResponseEntity<TargetAllocationDto> getTargetAllocation(@PathVariable Long id) {
        log.info("목표 배분 조회: id={}", id);
        return ResponseEntity.ok(rebalancingService.getTargetAllocation(id));
    }

    /** 목표 배분 생성 */
    @PostMapping("/allocations")
    public ResponseEntity<TargetAllocationDto> createTargetAllocation(
            @Valid @RequestBody TargetAllocationDto dto) {
        log.info(
                "목표 배분 생성: stockId={}, targetPercent={}", dto.getStockId(), dto.getTargetPercent());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rebalancingService.createTargetAllocation(dto));
    }

    /** 목표 배분 수정 */
    @PutMapping("/allocations/{id}")
    public ResponseEntity<TargetAllocationDto> updateTargetAllocation(
            @PathVariable Long id, @Valid @RequestBody TargetAllocationDto dto) {
        log.info("목표 배분 수정: id={}, targetPercent={}", id, dto.getTargetPercent());
        return ResponseEntity.ok(rebalancingService.updateTargetAllocation(id, dto));
    }

    /** 목표 배분 삭제 */
    @DeleteMapping("/allocations/{id}")
    public ResponseEntity<Void> deleteTargetAllocation(@PathVariable Long id) {
        log.info("목표 배분 삭제: id={}", id);
        rebalancingService.deleteTargetAllocation(id);
        return ResponseEntity.noContent().build();
    }

    /** 배치 설정 */
    @PutMapping("/allocations/batch")
    public ResponseEntity<List<TargetAllocationDto>> batchSetAllocations(
            @Valid @RequestBody TargetAllocationBatchDto batchDto) {
        log.info(
                "배치 목표 배분 설정: accountId={}, count={}",
                batchDto.getAccountId(),
                batchDto.getAllocations() != null ? batchDto.getAllocations().size() : 0);
        return ResponseEntity.ok(rebalancingService.batchSetAllocations(batchDto));
    }

    /** 목표 배분율 합계 검증 */
    @GetMapping("/allocations/validate")
    public ResponseEntity<Map<String, Object>> validateAllocations(
            @RequestParam(required = false) Long accountId) {
        log.info("목표 배분율 검증: accountId={}", accountId);
        return ResponseEntity.ok(rebalancingService.validateAllocations(accountId));
    }
}
