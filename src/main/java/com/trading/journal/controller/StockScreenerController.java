package com.trading.journal.controller;

import com.trading.journal.dto.SavedScreenDto;
import com.trading.journal.dto.ScreenerRequestDto;
import com.trading.journal.dto.ScreenerResultDto;
import com.trading.journal.entity.SavedScreen;
import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import com.trading.journal.service.StockScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Stock Screener API Controller */
@Slf4j
@RestController
@RequestMapping("/api/screener")
@RequiredArgsConstructor
@Tag(name = "Stock Screener", description = "Stock screening and filtering API")
public class StockScreenerController {

    private final StockScreenerService stockScreenerService;
    private final UserRepository userRepository;

    /**
     * Run stock screener with provided filters
     *
     * @param request Screening criteria with filters
     * @return Screening results with matched stocks
     */
    @PostMapping("/search")
    @Operation(
            summary = "Run stock screen",
            description = "Execute stock screen with specified filters and return matching results")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully screened stocks",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(implementation = ScreenerResultDto.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid screening criteria provided"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<ScreenerResultDto> searchStocks(
            @RequestBody(required = false) ScreenerRequestDto request) {
        log.info("Executing stock screen request");

        // Use empty request if not provided
        if (request == null) {
            request = new ScreenerRequestDto();
        }

        ScreenerResultDto result = stockScreenerService.screenStocks(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Save current screening criteria for later use
     *
     * @param payload Save request containing name, description, criteria, and visibility
     * @return Saved screen details
     */
    @PostMapping("/save")
    @Operation(
            summary = "Save screen criteria",
            description = "Save current screening criteria for reuse and sharing")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Screen criteria saved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SavedScreenDto.class))),
                @ApiResponse(responseCode = "400", description = "Invalid save request"),
                @ApiResponse(responseCode = "401", description = "User not authenticated"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<SavedScreenDto> saveScreen(@RequestBody SaveScreenRequest payload) {
        log.info("Saving screen: name={}", payload.getName());

        User currentUser = getCurrentUser();
        SavedScreen savedScreen =
                stockScreenerService.saveScreen(
                        currentUser,
                        payload.getName(),
                        payload.getDescription(),
                        payload.getCriteria(),
                        payload.isPublic());

        SavedScreenDto response = toSavedScreenDto(savedScreen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all saved screens for current user
     *
     * @return List of saved screens
     */
    @GetMapping("/saved")
    @Operation(
            summary = "List saved screens",
            description = "Retrieve all saved screening criteria for current user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved saved screens",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SavedScreenDto.class))),
                @ApiResponse(responseCode = "401", description = "User not authenticated"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<List<SavedScreenDto>> getSavedScreens() {
        log.info("Retrieving saved screens for user");

        User currentUser = getCurrentUser();
        List<SavedScreen> screens = stockScreenerService.getUserScreens(currentUser.getId());

        List<SavedScreenDto> response =
                screens.stream().map(this::toSavedScreenDto).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get specific saved screen by ID
     *
     * @param id Screen ID
     * @return Saved screen details
     */
    @GetMapping("/saved/{id}")
    @Operation(
            summary = "Get saved screen",
            description = "Retrieve specific saved screening criteria by ID")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved screen",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = SavedScreenDto.class))),
                @ApiResponse(responseCode = "404", description = "Screen not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<SavedScreenDto> getSavedScreen(@PathVariable Long id) {
        log.info("Retrieving saved screen: id={}", id);

        SavedScreen screen =
                stockScreenerService
                        .loadScreen(id)
                        .orElseThrow(() -> new IllegalArgumentException("Screen not found: " + id));

        SavedScreenDto response = toSavedScreenDto(screen);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete saved screen
     *
     * @param id Screen ID
     * @return Success message
     */
    @DeleteMapping("/saved/{id}")
    @Operation(
            summary = "Delete saved screen",
            description = "Delete a saved screening criteria by ID")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "Screen deleted successfully"),
                @ApiResponse(responseCode = "404", description = "Screen not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<Map<String, String>> deleteSavedScreen(@PathVariable Long id) {
        log.info("Deleting saved screen: id={}", id);

        User currentUser = getCurrentUser();
        stockScreenerService.deleteScreen(id, currentUser.getId());

        return ResponseEntity.ok(
                Map.of("message", "Screen deleted successfully", "id", String.valueOf(id)));
    }

    /**
     * Get current authenticated user
     *
     * @return User entity
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    /**
     * Convert SavedScreen entity to DTO
     *
     * @param screen SavedScreen entity
     * @return SavedScreenDto
     */
    private SavedScreenDto toSavedScreenDto(SavedScreen screen) {
        return SavedScreenDto.builder()
                .id(screen.getId())
                .name(screen.getName())
                .description(screen.getDescription())
                .criteria(stockScreenerService.parseCriteria(screen))
                .createdAt(screen.getCreatedAt())
                .updatedAt(screen.getUpdatedAt())
                .build();
    }

    /** Request payload for saving screen */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SaveScreenRequest {
        private String name;
        private String description;
        private ScreenerRequestDto criteria;
        private boolean isPublic;
    }
}
