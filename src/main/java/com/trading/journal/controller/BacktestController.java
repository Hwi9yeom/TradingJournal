package com.trading.journal.controller;

import com.trading.journal.dto.BacktestRequestDto;
import com.trading.journal.dto.BacktestResultDto;
import com.trading.journal.service.BacktestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 백테스트 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * 백테스트 실행
     */
    @PostMapping("/run")
    public ResponseEntity<BacktestResultDto> runBacktest(@Valid @RequestBody BacktestRequestDto request) {
        log.info("백테스트 실행 요청: symbol={}, strategy={}, period={} ~ {}",
                request.getSymbol(), request.getStrategyType(),
                request.getStartDate(), request.getEndDate());

        BacktestResultDto result = backtestService.runBacktest(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 백테스트 히스토리 조회
     */
    @GetMapping("/history")
    public ResponseEntity<List<BacktestResultDto>> getHistory() {
        return ResponseEntity.ok(backtestService.getHistory());
    }

    /**
     * 백테스트 결과 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<BacktestResultDto> getResult(@PathVariable Long id) {
        return ResponseEntity.ok(backtestService.getResult(id));
    }

    /**
     * 사용 가능한 전략 목록 조회
     */
    @GetMapping("/strategies")
    public ResponseEntity<List<Map<String, Object>>> getAvailableStrategies() {
        return ResponseEntity.ok(backtestService.getAvailableStrategies());
    }
}
