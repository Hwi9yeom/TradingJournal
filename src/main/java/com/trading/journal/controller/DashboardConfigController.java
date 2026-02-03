package com.trading.journal.controller;

import com.trading.journal.dto.AdvancedWidgetDto.*;
import com.trading.journal.dto.DashboardConfigDto;
import com.trading.journal.dto.DashboardWidgetDto;
import com.trading.journal.service.AdvancedWidgetService;
import com.trading.journal.service.DashboardConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 대시보드 설정 REST API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard-config")
@RequiredArgsConstructor
public class DashboardConfigController {

    private final DashboardConfigService dashboardConfigService;
    private final AdvancedWidgetService advancedWidgetService;

    // 임시로 userId 1 사용 (추후 인증 연동)
    private static final Long DEFAULT_USER_ID = 1L;

    /** 현재 활성 대시보드 설정 조회 */
    @GetMapping
    public ResponseEntity<DashboardConfigDto> getActiveConfig() {
        log.debug("활성 대시보드 설정 조회 요청");
        DashboardConfigDto config =
                dashboardConfigService.getActiveDashboardConfig(DEFAULT_USER_ID);
        return ResponseEntity.ok(config);
    }

    /** 대시보드 설정 저장 */
    @PostMapping
    public ResponseEntity<DashboardConfigDto> saveConfig(@RequestBody DashboardConfigDto dto) {
        log.debug("대시보드 설정 저장 요청: {}", dto.getConfigName());
        DashboardConfigDto saved = dashboardConfigService.saveDashboardConfig(DEFAULT_USER_ID, dto);
        return ResponseEntity.ok(saved);
    }

    /** 위젯 추가 */
    @PostMapping("/widgets")
    public ResponseEntity<DashboardConfigDto> addWidget(@RequestBody DashboardWidgetDto widgetDto) {
        log.debug("위젯 추가 요청: {}", widgetDto.getWidgetType());
        DashboardConfigDto updated = dashboardConfigService.addWidget(DEFAULT_USER_ID, widgetDto);
        return ResponseEntity.ok(updated);
    }

    /** 위젯 제거 */
    @DeleteMapping("/widgets/{widgetKey}")
    public ResponseEntity<DashboardConfigDto> removeWidget(@PathVariable String widgetKey) {
        log.debug("위젯 제거 요청: {}", widgetKey);
        DashboardConfigDto updated =
                dashboardConfigService.removeWidget(DEFAULT_USER_ID, widgetKey);
        return ResponseEntity.ok(updated);
    }

    /** 위젯 위치/순서 업데이트 (드래그앤드롭 후) */
    @PutMapping("/widgets/positions")
    public ResponseEntity<DashboardConfigDto> updateWidgetPositions(
            @RequestBody List<DashboardWidgetDto> widgets) {
        log.debug("위젯 위치 업데이트 요청: {} 개", widgets.size());
        DashboardConfigDto updated =
                dashboardConfigService.updateWidgetPositions(DEFAULT_USER_ID, widgets);
        return ResponseEntity.ok(updated);
    }

    /** 대시보드 설정 초기화 */
    @PostMapping("/reset")
    public ResponseEntity<DashboardConfigDto> resetToDefault() {
        log.debug("대시보드 설정 초기화 요청");
        DashboardConfigDto config = dashboardConfigService.resetToDefault(DEFAULT_USER_ID);
        return ResponseEntity.ok(config);
    }

    /** 사용 가능한 위젯 목록 */
    @GetMapping("/available-widgets")
    public ResponseEntity<List<DashboardWidgetDto>> getAvailableWidgets() {
        log.debug("사용 가능한 위젯 목록 조회");
        List<DashboardWidgetDto> widgets = dashboardConfigService.getAvailableWidgets();
        return ResponseEntity.ok(widgets);
    }

    /** Monte Carlo 시뮬레이션 요약 위젯 */
    @GetMapping("/widgets/monte-carlo-summary")
    public ResponseEntity<MonteCarloSummaryWidget> getMonteCarloSummaryWidget() {
        log.debug("Monte Carlo 시뮬레이션 요약 위젯 조회");
        return ResponseEntity.ok(advancedWidgetService.getMonteCarloSummary());
    }

    /** 스트레스 테스트 요약 위젯 */
    @GetMapping("/widgets/stress-test-summary")
    public ResponseEntity<StressTestSummaryWidget> getStressTestSummaryWidget() {
        log.debug("스트레스 테스트 요약 위젯 조회");
        return ResponseEntity.ok(advancedWidgetService.getStressTestSummary());
    }

    /** 스트레스 테스트 시나리오 위젯 */
    @GetMapping("/widgets/stress-test-scenarios")
    public ResponseEntity<StressTestScenariosWidget> getStressTestScenariosWidget() {
        log.debug("스트레스 테스트 시나리오 위젯 조회");
        return ResponseEntity.ok(advancedWidgetService.getStressTestScenarios());
    }

    /** 절세 기회 위젯 */
    @GetMapping("/widgets/tax-harvesting")
    public ResponseEntity<TaxHarvestingWidget> getTaxHarvestingWidget() {
        log.debug("절세 기회 위젯 조회");
        return ResponseEntity.ok(advancedWidgetService.getTaxHarvestingOpportunities());
    }
}
