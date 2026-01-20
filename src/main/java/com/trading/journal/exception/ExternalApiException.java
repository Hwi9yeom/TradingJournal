package com.trading.journal.exception;

/**
 * 외부 API 호출 중 발생하는 예외
 */
public class ExternalApiException extends RuntimeException {

    private final String apiName;
    private final Integer statusCode;
    private final String endpoint;

    public ExternalApiException(String message) {
        super(message);
        this.apiName = null;
        this.statusCode = null;
        this.endpoint = null;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.apiName = null;
        this.statusCode = null;
        this.endpoint = null;
    }

    public ExternalApiException(String apiName, String endpoint, String message) {
        super(String.format("%s API 호출 실패 [%s]: %s", apiName, endpoint, message));
        this.apiName = apiName;
        this.statusCode = null;
        this.endpoint = endpoint;
    }

    public ExternalApiException(String apiName, String endpoint, Integer statusCode, String message) {
        super(String.format("%s API 호출 실패 [%s, HTTP %d]: %s", apiName, endpoint, statusCode, message));
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }

    public ExternalApiException(String apiName, String endpoint, String message, Throwable cause) {
        super(String.format("%s API 호출 실패 [%s]: %s", apiName, endpoint, message), cause);
        this.apiName = apiName;
        this.statusCode = null;
        this.endpoint = endpoint;
    }

    public String getApiName() {
        return apiName;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getEndpoint() {
        return endpoint;
    }
}