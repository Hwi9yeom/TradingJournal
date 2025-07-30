package com.trading.journal.controller;

import com.trading.journal.dto.DividendDto;
import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.service.DividendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "배당금 관리", description = "배당금 기록을 관리하는 API")
public class DividendController {
    
    private final DividendService dividendService;
    
    @Operation(summary = "배당금 기록 생성", description = "새로운 배당금 기록을 생성합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "배당금 기록이 성공적으로 생성됨",
                content = @Content(schema = @Schema(implementation = DividendDto.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 입력 데이터")
    })
    @PostMapping
    public ResponseEntity<DividendDto> createDividend(@RequestBody DividendDto dividendDto) {
        log.info("Creating dividend for stock: {}", dividendDto.getStockSymbol());
        DividendDto created = dividendService.createDividend(dividendDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<DividendDto> updateDividend(@PathVariable Long id, @RequestBody DividendDto dividendDto) {
        log.info("Updating dividend: {}", id);
        DividendDto updated = dividendService.updateDividend(id, dividendDto);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDividend(@PathVariable Long id) {
        log.info("Deleting dividend: {}", id);
        dividendService.deleteDividend(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DividendDto> getDividend(@PathVariable Long id) {
        DividendDto dividend = dividendService.getDividend(id);
        return ResponseEntity.ok(dividend);
    }
    
    @Operation(summary = "배당금 목록 조회", description = "조건에 따라 배당금 목록을 조회합니다. 조건이 없으면 최근 1년 데이터를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "배당금 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<DividendDto>> getDividends(
            @Parameter(description = "종목 심볼 (옵션)", example = "AAPL")
            @RequestParam(required = false) String symbol,
            @Parameter(description = "시작 날짜 (옵션)", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료 날짜 (옵션)", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<DividendDto> dividends;
        
        if (symbol != null) {
            log.info("Getting dividends for stock: {}", symbol);
            dividends = dividendService.getDividendsByStock(symbol);
        } else if (startDate != null && endDate != null) {
            log.info("Getting dividends between {} and {}", startDate, endDate);
            dividends = dividendService.getDividendsByPeriod(startDate, endDate);
        } else {
            // 기본적으로 최근 1년 배당금 조회
            LocalDate now = LocalDate.now();
            LocalDate oneYearAgo = now.minusYears(1);
            dividends = dividendService.getDividendsByPeriod(oneYearAgo, now);
        }
        
        return ResponseEntity.ok(dividends);
    }
    
    @Operation(summary = "배당금 요약 정보 조회", description = "전체 배당금 요약 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "배당금 요약 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = DividendSummaryDto.class)))
    @GetMapping("/summary")
    public ResponseEntity<DividendSummaryDto> getDividendSummary() {
        log.info("Getting dividend summary");
        DividendSummaryDto summary = dividendService.getDividendSummary();
        return ResponseEntity.ok(summary);
    }
}