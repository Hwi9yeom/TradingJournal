package com.trading.journal.controller;

import com.trading.journal.dto.AccountDto;
import com.trading.journal.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "계좌 관리", description = "투자 계좌를 관리하는 API")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "계좌 목록 조회", description = "모든 투자 계좌 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "계좌 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @Operation(summary = "계좌 상세 조회", description = "ID로 특정 계좌를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "계좌 조회 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccount(
            @Parameter(description = "계좌 ID", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @Operation(summary = "계좌 생성", description = "새로운 투자 계좌를 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "계좌가 성공적으로 생성됨",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 데이터 또는 중복된 계좌 이름")
    })
    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody AccountDto dto) {
        AccountDto created = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "계좌 수정", description = "기존 계좌 정보를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "계좌 수정 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 데이터")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AccountDto> updateAccount(
            @Parameter(description = "계좌 ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AccountDto dto) {
        return ResponseEntity.ok(accountService.updateAccount(id, dto));
    }

    @Operation(summary = "계좌 삭제", description = "계좌를 삭제합니다. 기본 계좌나 포트폴리오가 있는 계좌는 삭제할 수 없습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "계좌가 성공적으로 삭제됨"),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "400", description = "삭제할 수 없는 계좌 (기본 계좌 또는 포트폴리오 존재)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(description = "계좌 ID", example = "1")
            @PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "기본 계좌 설정", description = "특정 계좌를 기본 계좌로 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "기본 계좌 설정 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음")
    })
    @PutMapping("/{id}/default")
    public ResponseEntity<AccountDto> setDefaultAccount(
            @Parameter(description = "계좌 ID", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(accountService.setDefaultAccount(id));
    }
}
