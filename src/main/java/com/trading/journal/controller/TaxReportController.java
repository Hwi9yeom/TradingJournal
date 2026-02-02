package com.trading.journal.controller;

import com.trading.journal.service.TaxReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Tax Report Export API Controller */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Tax Report Export", description = "Tax report PDF export API")
public class TaxReportController {

    private final TaxReportExportService taxReportExportService;

    /**
     * Export tax report as PDF for a specific year
     *
     * @param year Tax year (e.g., 2024)
     * @param accountId Account ID (optional, null for all accounts)
     * @return PDF file as downloadable attachment
     */
    @GetMapping("/tax-report/{year}")
    @Operation(
            summary = "Export tax report as PDF",
            description =
                    "Generates a comprehensive tax report PDF for the specified year. "
                            + "Includes tax calculations, transaction details, gains/losses summary, and estimated tax. "
                            + "Optionally filter by account ID.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tax report PDF generated successfully",
                content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "400", description = "Invalid year parameter"),
        @ApiResponse(responseCode = "500", description = "Failed to generate PDF report")
    })
    public ResponseEntity<byte[]> exportTaxReport(
            @Parameter(description = "Tax year", required = true, example = "2024") @PathVariable
                    int year,
            @Parameter(description = "Account ID (optional)", required = false, example = "1")
                    @RequestParam(required = false)
                    Long accountId) {

        log.info("Generating tax report PDF for year: {}, accountId: {}", year, accountId);

        try {
            // Validate year
            if (year < 1900 || year > 2100) {
                log.warn("Invalid year parameter: {}", year);
                return ResponseEntity.badRequest().build();
            }

            // Generate PDF
            byte[] pdfBytes = taxReportExportService.generateTaxReport(accountId, year);

            // Set HTTP headers for PDF download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData(
                    "attachment", String.format("tax-report-%d.pdf", year));
            headers.setContentLength(pdfBytes.length);

            log.info(
                    "Tax report PDF generated successfully for year: {}, size: {} bytes",
                    year,
                    pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Failed to generate tax report PDF for year: {}", year, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
