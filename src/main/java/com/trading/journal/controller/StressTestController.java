package com.trading.journal.controller;

import com.trading.journal.dto.StressTestRequestDto;
import com.trading.journal.dto.StressTestResultDto;
import com.trading.journal.entity.StressScenario;
import com.trading.journal.service.StressTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 스트레스 테스트 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/stress-test")
@RequiredArgsConstructor
@Tag(name = "Stress Test", description = "Stress Test APIs")
public class StressTestController {

    private final StressTestService stressTestService;

    /**
     * 사용 가능한 스트레스 테스트 시나리오 목록 조회
     *
     * @return 사용 가능한 시나리오 목록
     */
    @GetMapping("/scenarios")
    @Operation(
            summary = "Get available stress test scenarios",
            description = "Retrieve list of predefined stress test scenarios")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved scenarios"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<List<StressScenario>> getAvailableScenarios() {
        log.info("사용 가능한 스트레스 테스트 시나리오 목록 조회");

        List<StressScenario> scenarios = stressTestService.getAvailableScenarios();
        return ResponseEntity.ok(scenarios);
    }

    /**
     * 스트레스 테스트 실행
     *
     * @param request 스트레스 테스트 요청 정보 (계좌 ID, 시나리오 코드/ID, 커스텀 충격 비율)
     * @return 스트레스 테스트 결과 (포트폴리오 영향, 포지션별 손실, 섹터별 영향)
     */
    @PostMapping("/run")
    @Operation(
            summary = "Run stress test",
            description = "Execute stress test on portfolio with given scenario")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully executed stress test",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StressTestResultDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                @ApiResponse(responseCode = "404", description = "Account or scenario not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<StressTestResultDto> runStressTest(
            @Valid @RequestBody StressTestRequestDto request) {
        log.info(
                "스트레스 테스트 실행 요청: accountId={}, scenarioCode={}, scenarioId={}",
                request.getAccountId(),
                request.getScenarioCode(),
                request.getScenarioId());

        StressTestResultDto result = stressTestService.runStressTest(request);
        return ResponseEntity.ok(result);
    }
}
