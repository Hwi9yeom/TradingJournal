package com.trading.journal.controller;

import com.trading.journal.dto.*;
import com.trading.journal.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API")
public class AuthController {

    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @Operation(
            summary = "Auth Config",
            description = "Public endpoint exposing whether login is required")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Auth mode returned")})
    @GetMapping("/config")
    public ResponseEntity<AuthConfigDto> getAuthConfig() {
        return ResponseEntity.ok(new AuthConfigDto(authEnabled));
    }

    @Operation(summary = "Login", description = "Authenticate user and return JWT tokens")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Login successful"),
                @ApiResponse(responseCode = "401", description = "Invalid credentials")
            })
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        JwtResponseDto response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh Token", description = "Get new access token using refresh token")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
                @ApiResponse(responseCode = "401", description = "Invalid refresh token")
            })
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDto> refreshToken(
            @Valid @RequestBody RefreshTokenDto refreshTokenDto) {
        JwtResponseDto response = authService.refreshToken(refreshTokenDto.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get Current User",
            description = "Get information about the authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User info retrieved successfully"),
                @ApiResponse(responseCode = "401", description = "Not authenticated")
            })
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUser() {
        UserInfoDto userInfo = authService.getCurrentUser();
        return ResponseEntity.ok(userInfo);
    }

    @Operation(
            summary = "Change Password",
            description = "Change the password for the authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Password changed successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid current password or validation error"),
                @ApiResponse(responseCode = "401", description = "Not authenticated")
            })
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeDto passwordChangeDto) {
        authService.changePassword(passwordChangeDto);
        return ResponseEntity.ok().build();
    }
}
