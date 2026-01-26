package com.trading.journal.controller;

import com.trading.journal.dto.TradingPsychologyDto;
import com.trading.journal.dto.TradingPsychologyDto.*;
import com.trading.journal.service.TradingPsychologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/psychology")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trading Psychology", description = "트레이딩 심리 분석 API")
public class TradingPsychologyController {

    private final TradingPsychologyService psychologyService;

    // 1. Full analysis
    @GetMapping
    @Operation(summary = "전체 심리 분석", description = "모든 심리 분석 데이터를 종합하여 반환합니다.")
    public ResponseEntity<TradingPsychologyDto> getFullAnalysis(
            @RequestParam Long accountId,
            @Parameter(description = "시작일 (yyyy-MM-dd)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @Parameter(description = "종료일 (yyyy-MM-dd)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        log.info(
                "Full psychology analysis requested for account: {} from {} to {}",
                accountId,
                startDate,
                endDate);
        return ResponseEntity.ok(psychologyService.getFullAnalysis(accountId, startDate, endDate));
    }

    // 2. Emotion transitions
    @GetMapping("/transitions")
    @Operation(summary = "감정 전환 분석", description = "Before → After 감정 패턴과 위험도를 분석합니다.")
    public ResponseEntity<EmotionTransitionAnalysis> getEmotionTransitions(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Emotion transition analysis requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.analyzeEmotionTransitions(accountId, startDate, endDate));
    }

    // 3. Tilt detection
    @GetMapping("/tilt")
    @Operation(summary = "틸트 감지", description = "지정 기간의 틸트 점수와 이벤트를 분석합니다.")
    public ResponseEntity<TiltAnalysis> getTiltAnalysis(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Tilt analysis requested for account: {}", accountId);
        return ResponseEntity.ok(psychologyService.detectTilt(accountId, startDate, endDate));
    }

    // 4. Current tilt status (last 7 days)
    @GetMapping("/tilt/current")
    @Operation(summary = "현재 틸트 상태", description = "최근 7일간의 틸트 상태를 확인합니다.")
    public ResponseEntity<TiltAnalysis> getCurrentTiltStatus(@RequestParam Long accountId) {
        log.info("Current tilt status requested for account: {}", accountId);
        return ResponseEntity.ok(psychologyService.getCurrentTiltStatus(accountId));
    }

    // 5. Emotion-behavior correlation
    @GetMapping("/behavior-correlation")
    @Operation(summary = "감정-행동 상관관계", description = "각 감정 상태와 실수 패턴의 상관관계를 분석합니다.")
    public ResponseEntity<EmotionBehaviorCorrelation> getBehaviorCorrelation(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Behavior correlation analysis requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.analyzeEmotionBehaviorCorrelation(accountId, startDate, endDate));
    }

    // 6. Psychological score
    @GetMapping("/score")
    @Operation(summary = "심리 종합 점수", description = "집중력, 규율, 감정안정성, 회복력을 종합한 점수를 계산합니다.")
    public ResponseEntity<PsychologicalScore> getPsychologicalScore(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Psychological score requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.calculatePsychologicalScore(accountId, startDate, endDate));
    }

    // 7. Recovery patterns
    @GetMapping("/recovery")
    @Operation(summary = "회복 패턴 분석", description = "부정적 감정에서 회복하는 패턴을 분석합니다.")
    public ResponseEntity<RecoveryPatternAnalysis> getRecoveryPatterns(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Recovery pattern analysis requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.analyzeRecoveryPatterns(accountId, startDate, endDate));
    }

    // 8. Emotion triggers
    @GetMapping("/triggers")
    @Operation(summary = "감정 트리거 분석", description = "특정 감정을 유발하는 트리거를 식별합니다.")
    public ResponseEntity<EmotionTriggerAnalysis> getEmotionTriggers(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Emotion trigger analysis requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.analyzeEmotionTriggers(accountId, startDate, endDate));
    }

    // 9. Daily rhythm
    @GetMapping("/daily-rhythm")
    @Operation(summary = "일일 리듬 분석", description = "요일별, 시간대별 감정 패턴과 수익률을 분석합니다.")
    public ResponseEntity<DailyRhythmAnalysis> getDailyRhythm(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Daily rhythm analysis requested for account: {}", accountId);
        return ResponseEntity.ok(
                psychologyService.analyzeDailyRhythm(accountId, startDate, endDate));
    }
}
