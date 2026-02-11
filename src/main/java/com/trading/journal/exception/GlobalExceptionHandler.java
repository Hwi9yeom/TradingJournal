package com.trading.journal.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== Helper Methods ====================

    /** 공통 에러 응답 빌더 */
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String error, String message, WebRequest request) {
        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(status.value())
                        .error(error)
                        .message(message)
                        .path(getPath(request))
                        .build();
        return ResponseEntity.status(status).body(errorResponse);
    }

    /** 검증 에러 추출 */
    private Map<String, String> extractValidationErrors(
            org.springframework.validation.BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();
        bindingResult
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return errors;
    }

    // ==================== Not Found Exceptions (404) ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFoundException(
            TransactionNotFoundException ex, WebRequest request) {
        log.warn("Transaction not found: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND, "Transaction Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStockNotFoundException(
            StockNotFoundException ex, WebRequest request) {
        log.warn("Stock not found: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND, "Stock Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(
            AccountNotFoundException ex, WebRequest request) {
        log.warn("Account not found: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND, "Account Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccessException(
            UnauthorizedAccessException ex, WebRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to access this resource.",
                request);
    }

    // ==================== Business Logic Exceptions (400/422) ====================

    @ExceptionHandler(InvalidTradeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTradeException(
            InvalidTradeException ex, WebRequest request) {
        log.warn("Invalid trade: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST, "Invalid Trade", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex, WebRequest request) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Funds", ex.getMessage(), request);
    }

    // ==================== Data Processing Exceptions (500) ====================

    @ExceptionHandler(DataImportException.class)
    public ResponseEntity<ErrorResponse> handleDataImportException(
            DataImportException ex, WebRequest request) {
        log.error("Data import failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Data Import Failed", ex.getMessage(), request);
    }

    @ExceptionHandler(ExportException.class)
    public ResponseEntity<ErrorResponse> handleExportException(
            ExportException ex, WebRequest request) {
        log.error("Export failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Export Failed", ex.getMessage(), request);
    }

    // ==================== External Service Exceptions (502/503) ====================

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(
            ExternalApiException ex, WebRequest request) {
        log.error("External API call failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY, "External API Error", ex.getMessage(), request);
    }

    @ExceptionHandler(PriceDataException.class)
    public ResponseEntity<ErrorResponse> handlePriceDataException(
            PriceDataException ex, WebRequest request) {
        log.error("Price data retrieval failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE, "Price Data Unavailable", ex.getMessage(), request);
    }

    // ==================== Analysis & Backtest Exceptions (500) ====================

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisException(
            AnalysisException ex, WebRequest request) {
        log.error("Analysis failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Analysis Failed", ex.getMessage(), request);
    }

    @ExceptionHandler(BacktestExecutionException.class)
    public ResponseEntity<ErrorResponse> handleBacktestExecutionException(
            BacktestExecutionException ex, WebRequest request) {
        log.error("Backtest execution failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Backtest Execution Failed",
                ex.getMessage(),
                request);
    }

    // ==================== General Exceptions ====================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument exception: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = extractValidationErrors(ex.getBindingResult());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Validation Failed")
                        .message("입력 데이터 검증에 실패했습니다.")
                        .path(getPath(request))
                        .validationErrors(errors)
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex, WebRequest request) {
        log.warn("Bind exception: {}", ex.getMessage());

        Map<String, String> errors = extractValidationErrors(ex.getBindingResult());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Binding Failed")
                        .message("데이터 바인딩에 실패했습니다.")
                        .path(getPath(request))
                        .validationErrors(errors)
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.warn("Type mismatch exception: {}", ex.getMessage());

        String typeName =
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "알 수 없는 타입";

        String message =
                String.format(
                        "파라미터 '%s'의 값 '%s'이 올바르지 않습니다. %s 타입이어야 합니다.",
                        ex.getName(), ex.getValue(), typeName);

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Type Mismatch", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST, "Malformed JSON", "요청 데이터가 올바른 JSON 형식이 아닙니다.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "예상치 못한 오류가 발생했습니다.",
                request);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
