package com.trading.journal.exception;

/** 백테스트 실행 중 발생하는 예외 */
public class BacktestExecutionException extends RuntimeException {

    private final String strategyName;
    private final String symbol;

    public BacktestExecutionException(String message) {
        super(message);
        this.strategyName = null;
        this.symbol = null;
    }

    public BacktestExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.strategyName = null;
        this.symbol = null;
    }

    public BacktestExecutionException(String strategyName, String symbol, String message) {
        super(String.format("백테스트 실행 실패 [전략: %s, 종목: %s]: %s", strategyName, symbol, message));
        this.strategyName = strategyName;
        this.symbol = symbol;
    }

    public BacktestExecutionException(
            String strategyName, String symbol, String message, Throwable cause) {
        super(
                String.format("백테스트 실행 실패 [전략: %s, 종목: %s]: %s", strategyName, symbol, message),
                cause);
        this.strategyName = strategyName;
        this.symbol = symbol;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public String getSymbol() {
        return symbol;
    }
}
