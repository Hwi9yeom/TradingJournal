package com.trading.journal.controller;

import com.trading.journal.dto.AlertDto;
import com.trading.journal.dto.AlertSummaryDto;
import com.trading.journal.entity.AlertPriority;
import com.trading.journal.entity.AlertType;
import com.trading.journal.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 알림 관리 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "알림 관리 API")
public class AlertController {

    private final AlertService alertService;

    /** 알림 요약 조회 */
    @GetMapping("/summary")
    @Operation(summary = "알림 요약", description = "읽지 않은 알림 수, 긴급 알림 등 요약 정보 조회")
    public ResponseEntity<AlertSummaryDto> getAlertSummary() {
        AlertSummaryDto summary = alertService.getAlertSummary();
        return ResponseEntity.ok(summary);
    }

    /** 읽지 않은 알림 조회 */
    @GetMapping("/unread")
    @Operation(summary = "읽지 않은 알림", description = "읽지 않은 모든 알림 조회")
    public ResponseEntity<List<AlertDto>> getUnreadAlerts() {
        List<AlertDto> alerts = alertService.getUnreadAlerts();
        return ResponseEntity.ok(alerts);
    }

    /** 전체 알림 조회 (페이징) */
    @GetMapping
    @Operation(summary = "전체 알림 조회", description = "모든 알림을 페이징하여 조회")
    public ResponseEntity<Page<AlertDto>> getAllAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AlertDto> alerts = alertService.getAllAlerts(page, size);
        return ResponseEntity.ok(alerts);
    }

    /** 단일 알림 조회 */
    @GetMapping("/{id}")
    @Operation(summary = "알림 상세 조회", description = "특정 알림의 상세 정보 조회")
    public ResponseEntity<AlertDto> getAlert(@PathVariable Long id) {
        AlertDto alert = alertService.getAlert(id);
        return ResponseEntity.ok(alert);
    }

    /** 알림 읽음 처리 */
    @PatchMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음으로 표시")
    public ResponseEntity<AlertDto> markAsRead(@PathVariable Long id) {
        log.info("알림 읽음 처리: id={}", id);
        AlertDto alert = alertService.markAsRead(id);
        return ResponseEntity.ok(alert);
    }

    /** 모든 알림 읽음 처리 */
    @PatchMapping("/read-all")
    @Operation(summary = "모든 알림 읽음 처리", description = "읽지 않은 모든 알림을 읽음으로 표시")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        log.info("모든 알림 읽음 처리 요청");
        int count = alertService.markAllAsRead();
        return ResponseEntity.ok(Map.of("message", "모든 알림이 읽음 처리되었습니다", "count", count));
    }

    /** 알림 무시 처리 */
    @PatchMapping("/{id}/dismiss")
    @Operation(summary = "알림 무시", description = "특정 알림을 무시 처리")
    public ResponseEntity<AlertDto> dismissAlert(@PathVariable Long id) {
        log.info("알림 무시 처리: id={}", id);
        AlertDto alert = alertService.dismissAlert(id);
        return ResponseEntity.ok(alert);
    }

    /** 알림 삭제 */
    @DeleteMapping("/{id}")
    @Operation(summary = "알림 삭제", description = "특정 알림 삭제")
    public ResponseEntity<Map<String, String>> deleteAlert(@PathVariable Long id) {
        log.info("알림 삭제: id={}", id);
        alertService.deleteAlert(id);
        return ResponseEntity.ok(Map.of("message", "알림이 삭제되었습니다", "id", String.valueOf(id)));
    }

    /** 테스트 알림 생성 */
    @PostMapping("/test")
    @Operation(summary = "테스트 알림 생성", description = "테스트용 알림 생성")
    public ResponseEntity<AlertDto> createTestAlert(
            @RequestParam(defaultValue = "SYSTEM_INFO") AlertType type,
            @RequestParam(defaultValue = "MEDIUM") AlertPriority priority,
            @RequestParam(defaultValue = "테스트 알림") String title,
            @RequestParam(defaultValue = "테스트 메시지입니다.") String message) {
        log.info("테스트 알림 생성: type={}, priority={}", type, priority);
        AlertDto alert = alertService.createAlert(type, priority, title, message);
        return ResponseEntity.ok(alert);
    }
}
