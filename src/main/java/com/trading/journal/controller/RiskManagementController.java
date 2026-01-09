package com.trading.journal.controller;

import com.trading.journal.dto.AccountRiskSettingsDto;
import com.trading.journal.dto.PositionSizingRequestDto;
import com.trading.journal.dto.PositionSizingResultDto;
import com.trading.journal.dto.RiskDashboardDto;
import com.trading.journal.dto.RiskDashboardDto.*;
import com.trading.journal.service.AccountRiskSettingsService;
import com.trading.journal.service.PositionSizingService;
import com.trading.journal.service.RiskDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 리스크 관리 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskManagementController {

    private final RiskDashboardService riskDashboardService;
    private final PositionSizingService positionSizingService;
    private final AccountRiskSettingsService riskSettingsService;

    // ===== 리스크 대시보드 =====

    /**
     * 종합 리스크 대시보드 조회
     */
    @GetMapping("/dashboard")
    public ResponseEntity<RiskDashboardDto> getRiskDashboard(
            @RequestParam(required = false) Long accountId) {
        log.info("리스크 대시보드 조회: accountId={}", accountId);
        return ResponseEntity.ok(riskDashboardService.getRiskDashboard(accountId));
    }

    /**
     * R-multiple 분석 조회
     */
    @GetMapping("/r-multiple")
    public ResponseEntity<RMultipleAnalysis> getRMultipleAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("R-multiple 분석 조회: accountId={}, {} ~ {}", accountId, startDate, endDate);
        return ResponseEntity.ok(riskDashboardService.analyzeRMultiples(accountId, startDate, endDate));
    }

    /**
     * 포지션별 리스크 요약 조회
     */
    @GetMapping("/positions")
    public ResponseEntity<List<PositionRiskSummary>> getPositionRisks(
            @RequestParam(required = false) Long accountId) {
        log.info("포지션 리스크 조회: accountId={}", accountId);
        return ResponseEntity.ok(riskDashboardService.getPositionRisks(accountId));
    }

    /**
     * 집중도 알림 조회
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<ConcentrationAlert>> getRiskAlerts(
            @RequestParam(required = false) Long accountId) {
        log.info("리스크 알림 조회: accountId={}", accountId);
        return ResponseEntity.ok(riskDashboardService.checkConcentrationLimits(accountId, null));
    }

    /**
     * 섹터 노출 현황 조회
     */
    @GetMapping("/sector-exposure")
    public ResponseEntity<List<SectorExposure>> getSectorExposures(
            @RequestParam(required = false) Long accountId) {
        log.info("섹터 노출 현황 조회: accountId={}", accountId);
        return ResponseEntity.ok(riskDashboardService.getSectorExposures(accountId, null));
    }

    // ===== 포지션 사이징 =====

    /**
     * 포지션 사이징 계산
     */
    @PostMapping("/position-size")
    public ResponseEntity<PositionSizingResultDto> calculatePositionSize(
            @Valid @RequestBody PositionSizingRequestDto request) {
        log.info("포지션 사이징 계산: entry={}, stopLoss={}, method={}",
                request.getEntryPrice(), request.getStopLossPrice(), request.getMethod());
        return ResponseEntity.ok(positionSizingService.calculatePositionSize(request));
    }

    /**
     * Kelly Criterion 계산
     */
    @GetMapping("/kelly")
    public ResponseEntity<Map<String, BigDecimal>> getKellyCriterion(
            @RequestParam(required = false) Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Kelly Criterion 계산: accountId={}, {} ~ {}", accountId, startDate, endDate);

        BigDecimal kellyPct = positionSizingService.calculateKellyPercentage(accountId, startDate, endDate);

        Map<String, BigDecimal> result = new HashMap<>();
        result.put("kellyPercentage", kellyPct != null ? kellyPct : BigDecimal.ZERO);
        if (kellyPct != null && kellyPct.compareTo(BigDecimal.ZERO) > 0) {
            result.put("halfKelly", kellyPct.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP));
            result.put("quarterKelly", kellyPct.divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP));
        } else {
            result.put("halfKelly", BigDecimal.ZERO);
            result.put("quarterKelly", BigDecimal.ZERO);
        }

        return ResponseEntity.ok(result);
    }

    // ===== 리스크 설정 =====

    /**
     * 계정 리스크 설정 조회
     */
    @GetMapping("/settings/{accountId}")
    public ResponseEntity<AccountRiskSettingsDto> getRiskSettings(@PathVariable Long accountId) {
        log.info("리스크 설정 조회: accountId={}", accountId);
        return ResponseEntity.ok(riskSettingsService.getRiskSettings(accountId));
    }

    /**
     * 계정 리스크 설정 조회 (기본 계좌)
     */
    @GetMapping("/settings")
    public ResponseEntity<AccountRiskSettingsDto> getDefaultRiskSettings() {
        log.info("기본 계좌 리스크 설정 조회");
        return ResponseEntity.ok(riskSettingsService.getRiskSettings(null));
    }

    /**
     * 리스크 설정 생성
     */
    @PostMapping("/settings")
    public ResponseEntity<AccountRiskSettingsDto> createRiskSettings(
            @Valid @RequestBody AccountRiskSettingsDto dto) {
        log.info("리스크 설정 생성: accountId={}", dto.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(riskSettingsService.createRiskSettings(dto));
    }

    /**
     * 리스크 설정 업데이트
     */
    @PutMapping("/settings/{accountId}")
    public ResponseEntity<AccountRiskSettingsDto> updateRiskSettings(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountRiskSettingsDto dto) {
        log.info("리스크 설정 업데이트: accountId={}", accountId);
        return ResponseEntity.ok(riskSettingsService.updateRiskSettings(accountId, dto));
    }

    /**
     * 계좌 자본금 업데이트
     */
    @PutMapping("/settings/{accountId}/capital")
    public ResponseEntity<AccountRiskSettingsDto> updateAccountCapital(
            @PathVariable Long accountId,
            @RequestParam BigDecimal capital) {
        log.info("계좌 자본금 업데이트: accountId={}, capital={}", accountId, capital);
        return ResponseEntity.ok(riskSettingsService.updateAccountCapital(accountId, capital));
    }

    // ===== 유틸리티 =====

    /**
     * 일일 손실 한도 상태 체크
     */
    @GetMapping("/check/daily-loss")
    public ResponseEntity<Map<String, Object>> checkDailyLossLimit(
            @RequestParam(required = false) Long accountId) {
        boolean breached = riskSettingsService.isDailyLossLimitBreached(accountId);
        BigDecimal todayPnl = riskSettingsService.getTodayPnl(accountId);

        Map<String, Object> result = new HashMap<>();
        result.put("isBreached", breached);
        result.put("todayPnl", todayPnl);

        return ResponseEntity.ok(result);
    }

    /**
     * 주간 손실 한도 상태 체크
     */
    @GetMapping("/check/weekly-loss")
    public ResponseEntity<Map<String, Object>> checkWeeklyLossLimit(
            @RequestParam(required = false) Long accountId) {
        boolean breached = riskSettingsService.isWeeklyLossLimitBreached(accountId);
        BigDecimal weekPnl = riskSettingsService.getWeekPnl(accountId);

        Map<String, Object> result = new HashMap<>();
        result.put("isBreached", breached);
        result.put("weekPnl", weekPnl);

        return ResponseEntity.ok(result);
    }

    /**
     * 포지션 수 한도 상태 체크
     */
    @GetMapping("/check/position-count")
    public ResponseEntity<Map<String, Object>> checkPositionCountLimit(
            @RequestParam(required = false) Long accountId) {
        boolean breached = riskSettingsService.isPositionCountLimitBreached(accountId);
        int currentPositions = riskSettingsService.getCurrentOpenPositions(accountId);

        Map<String, Object> result = new HashMap<>();
        result.put("isBreached", breached);
        result.put("currentPositions", currentPositions);

        return ResponseEntity.ok(result);
    }
}
