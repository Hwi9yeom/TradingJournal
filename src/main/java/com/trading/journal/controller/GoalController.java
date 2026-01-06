package com.trading.journal.controller;

import com.trading.journal.dto.GoalDto;
import com.trading.journal.dto.GoalSummaryDto;
import com.trading.journal.entity.GoalStatus;
import com.trading.journal.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 투자 목표 관리 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "투자 목표 관리 API")
public class GoalController {

    private final GoalService goalService;

    /**
     * 새 목표 생성
     */
    @PostMapping
    @Operation(summary = "목표 생성", description = "새로운 투자 목표를 생성합니다")
    public ResponseEntity<GoalDto> createGoal(@RequestBody GoalDto goalDto) {
        log.info("목표 생성 요청: {}", goalDto.getName());
        GoalDto created = goalService.createGoal(goalDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 목표 수정
     */
    @PutMapping("/{id}")
    @Operation(summary = "목표 수정", description = "기존 목표를 수정합니다")
    public ResponseEntity<GoalDto> updateGoal(@PathVariable Long id, @RequestBody GoalDto goalDto) {
        log.info("목표 수정 요청: id={}", id);
        GoalDto updated = goalService.updateGoal(id, goalDto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 목표 삭제
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "목표 삭제", description = "목표를 삭제합니다")
    public ResponseEntity<Map<String, String>> deleteGoal(@PathVariable Long id) {
        log.info("목표 삭제 요청: id={}", id);
        goalService.deleteGoal(id);
        return ResponseEntity.ok(Map.of("message", "목표가 삭제되었습니다", "id", String.valueOf(id)));
    }

    /**
     * 단일 목표 조회
     */
    @GetMapping("/{id}")
    @Operation(summary = "목표 상세 조회", description = "특정 목표의 상세 정보를 조회합니다")
    public ResponseEntity<GoalDto> getGoal(@PathVariable Long id) {
        GoalDto goal = goalService.getGoal(id);
        return ResponseEntity.ok(goal);
    }

    /**
     * 전체 목표 조회
     */
    @GetMapping
    @Operation(summary = "전체 목표 조회", description = "모든 목표를 조회합니다")
    public ResponseEntity<List<GoalDto>> getAllGoals() {
        List<GoalDto> goals = goalService.getAllGoals();
        return ResponseEntity.ok(goals);
    }

    /**
     * 활성 목표 조회
     */
    @GetMapping("/active")
    @Operation(summary = "활성 목표 조회", description = "진행 중인 목표만 조회합니다")
    public ResponseEntity<List<GoalDto>> getActiveGoals() {
        List<GoalDto> goals = goalService.getActiveGoals();
        return ResponseEntity.ok(goals);
    }

    /**
     * 상태별 목표 조회
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "상태별 목표 조회", description = "특정 상태의 목표만 조회합니다")
    public ResponseEntity<List<GoalDto>> getGoalsByStatus(@PathVariable GoalStatus status) {
        List<GoalDto> goals = goalService.getGoalsByStatus(status);
        return ResponseEntity.ok(goals);
    }

    /**
     * 목표 요약 정보 조회
     */
    @GetMapping("/summary")
    @Operation(summary = "목표 요약 조회", description = "전체 목표에 대한 요약 통계를 조회합니다")
    public ResponseEntity<GoalSummaryDto> getGoalSummary() {
        GoalSummaryDto summary = goalService.getGoalSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * 목표 상태 변경
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "목표 상태 변경", description = "목표의 상태를 변경합니다 (일시중지, 재개, 취소 등)")
    public ResponseEntity<GoalDto> updateGoalStatus(
            @PathVariable Long id,
            @RequestParam GoalStatus status) {
        log.info("목표 상태 변경 요청: id={}, status={}", id, status);

        GoalDto goal = goalService.getGoal(id);
        goal.setStatus(status);
        GoalDto updated = goalService.updateGoal(id, goal);

        return ResponseEntity.ok(updated);
    }

    /**
     * 목표 진행률 수동 갱신
     */
    @PostMapping("/refresh")
    @Operation(summary = "진행률 갱신", description = "모든 활성 목표의 진행률을 수동으로 갱신합니다")
    public ResponseEntity<Map<String, String>> refreshGoalsProgress() {
        log.info("목표 진행률 수동 갱신 요청");
        goalService.updateAllGoalsProgress();
        return ResponseEntity.ok(Map.of("message", "목표 진행률이 갱신되었습니다"));
    }
}
