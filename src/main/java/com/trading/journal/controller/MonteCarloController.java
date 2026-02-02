package com.trading.journal.controller;

import com.trading.journal.dto.MonteCarloRequestDto;
import com.trading.journal.dto.MonteCarloResultDto;
import com.trading.journal.service.MonteCarloSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 몬테카를로 시뮬레이션 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/monte-carlo")
@RequiredArgsConstructor
@Tag(name = "Monte Carlo", description = "Monte Carlo simulation APIs")
public class MonteCarloController {

    private final MonteCarloSimulationService monteCarloService;

    /**
     * 몬테카를로 시뮬레이션 실행
     *
     * @param request 시뮬레이션 요청 정보 (반복 횟수, 기간, 시드 값 등)
     * @return 시뮬레이션 결과 (경로 데이터, 통계, 신뢰도 구간)
     */
    @PostMapping
    @Operation(
            summary = "Run Monte Carlo simulation",
            description = "Run portfolio return distribution simulation")
    public ResponseEntity<MonteCarloResultDto> runSimulation(
            @Valid @RequestBody MonteCarloRequestDto request) {
        log.info(
                "몬테카를로 시뮬레이션 실행: simulations={}, projectionDays={}",
                request.getNumSimulations(),
                request.getProjectionDays());

        MonteCarloResultDto result = monteCarloService.runSimulation(request);
        return ResponseEntity.ok(result);
    }
}
