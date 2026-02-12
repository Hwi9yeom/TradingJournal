package com.trading.journal.controller;

import com.trading.journal.dto.TaxLossHarvestingDto;
import com.trading.journal.service.TaxLossHarvestingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Tax-Loss Harvesting 기회 분석 API 컨트롤러 */
@Slf4j
@RestController
@RequestMapping("/api/tax/harvesting-opportunities")
@RequiredArgsConstructor
@Tag(name = "Tax Harvesting", description = "세금 최적화 - Tax-Loss Harvesting 기회 분석 API")
public class TaxHarvestingController {

    private final TaxLossHarvestingService taxLossHarvestingService;

    /**
     * 모든 계좌의 Tax-Loss Harvesting 기회 조회
     *
     * @return 모든 계좌의 Tax-Loss Harvesting 분석 결과
     */
    @GetMapping
    @Operation(
            summary = "모든 계좌의 Tax-Loss Harvesting 기회 조회",
            description =
                    "전체 계좌에 대해 현재 보유 중인 모든 손실 포지션의 Tax-Loss Harvesting 기회를 분석합니다. "
                            + "각 포지션의 미실현 손실, 잠재적 세금 절감액, Wash Sale 위험을 포함합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tax-Loss Harvesting 기회 조회 성공",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TaxLossHarvestingDto.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<TaxLossHarvestingDto> getAllHarvestingOpportunities() {
        log.info("Fetching all tax-loss harvesting opportunities");
        TaxLossHarvestingDto result =
                taxLossHarvestingService.analyzeAllHarvestingOpportunitiesForCurrentUser();
        return ResponseEntity.ok(result);
    }

    /**
     * 특정 계좌의 Tax-Loss Harvesting 기회 조회
     *
     * @param accountId 계좌 ID
     * @return 해당 계좌의 Tax-Loss Harvesting 분석 결과
     */
    @GetMapping("/{accountId}")
    @Operation(
            summary = "계좌별 Tax-Loss Harvesting 기회 조회",
            description =
                    "지정된 계좌에 대해 현재 보유 중인 모든 손실 포지션의 Tax-Loss Harvesting 기회를 분석합니다. "
                            + "각 포지션의 미실현 손실, 잠재적 세금 절감액, Wash Sale 위험, 보유 기간 등을 포함합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tax-Loss Harvesting 기회 조회 성공",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TaxLossHarvestingDto.class))),
        @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<TaxLossHarvestingDto> getHarvestingOpportunitiesByAccount(
            @Parameter(description = "계좌 ID", required = true, example = "1") @PathVariable
                    Long accountId) {
        log.info("Fetching tax-loss harvesting opportunities for account: {}", accountId);

        TaxLossHarvestingDto result =
                taxLossHarvestingService.analyzeTaxLossHarvestingOpportunities(accountId);

        return ResponseEntity.ok(result);
    }
}
