package com.trading.journal.controller;

import com.trading.journal.dto.TransactionDto;
import com.trading.journal.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "거래 관리", description = "주식 거래 내역을 관리하는 API")
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @Operation(summary = "거래 내역 생성", description = "새로운 주식 거래 내역을 생성합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "거래 내역이 성공적으로 생성됨",
                content = @Content(schema = @Schema(implementation = TransactionDto.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 입력 데이터")
    })
    @PostMapping
    public ResponseEntity<TransactionDto> createTransaction(@Valid @RequestBody TransactionDto dto) {
        TransactionDto created = transactionService.createTransaction(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @Operation(summary = "거래 내역 목록 조회 (페이지네이션)", description = "페이지네이션을 적용하여 거래 내역 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "거래 내역 목록 조회 성공")
    @GetMapping
    public ResponseEntity<Page<TransactionDto>> getAllTransactions(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "정렬 기준 필드", example = "transactionDate")
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @Parameter(description = "정렬 방향 (asc/desc)", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TransactionDto> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<TransactionDto>> getAllTransactionsAsList() {
        List<TransactionDto> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(transactions);
    }
    
    @Operation(summary = "거래 내역 상세 조회", description = "ID로 특정 거래 내역을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "거래 내역 조회 성공",
                content = @Content(schema = @Schema(implementation = TransactionDto.class))),
        @ApiResponse(responseCode = "404", description = "거래 내역을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransactionById(
            @Parameter(description = "거래 ID", example = "1")
            @PathVariable Long id) {
        TransactionDto transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }
    
    @Operation(summary = "종목별 거래 내역 조회", description = "특정 종목의 모든 거래 내역을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "종목별 거래 내역 조회 성공")
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<TransactionDto>> getTransactionsBySymbol(
            @Parameter(description = "종목 심볼", example = "AAPL")
            @PathVariable String symbol) {
        List<TransactionDto> transactions = transactionService.getTransactionsBySymbol(symbol);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/date-range")
    public ResponseEntity<List<TransactionDto>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<TransactionDto> transactions = transactionService.getTransactionsByDateRange(startDate, endDate);
        return ResponseEntity.ok(transactions);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TransactionDto> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionDto dto) {
        TransactionDto updated = transactionService.updateTransaction(id, dto);
        return ResponseEntity.ok(updated);
    }
    
    @Operation(summary = "거래 내역 삭제", description = "ID로 특정 거래 내역을 삭제합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "거래 내역이 성공적으로 삭제됨"),
        @ApiResponse(responseCode = "404", description = "거래 내역을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @Parameter(description = "거래 ID", example = "1")
            @PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}