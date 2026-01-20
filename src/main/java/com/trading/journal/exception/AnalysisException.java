package com.trading.journal.exception;

/** 분석 처리 중 발생하는 예외 */
public class AnalysisException extends RuntimeException {

    private final String analysisType;
    private final String symbol;

    public AnalysisException(String message) {
        super(message);
        this.analysisType = null;
        this.symbol = null;
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
        this.analysisType = null;
        this.symbol = null;
    }

    public AnalysisException(String analysisType, String message) {
        super(String.format("%s 분석 실패: %s", analysisType, message));
        this.analysisType = analysisType;
        this.symbol = null;
    }

    public AnalysisException(String analysisType, String symbol, String message) {
        super(String.format("%s 분석 실패 [%s]: %s", analysisType, symbol, message));
        this.analysisType = analysisType;
        this.symbol = symbol;
    }

    public AnalysisException(String analysisType, String symbol, String message, Throwable cause) {
        super(String.format("%s 분석 실패 [%s]: %s", analysisType, symbol, message), cause);
        this.analysisType = analysisType;
        this.symbol = symbol;
    }

    public String getAnalysisType() {
        return analysisType;
    }

    public String getSymbol() {
        return symbol;
    }
}
