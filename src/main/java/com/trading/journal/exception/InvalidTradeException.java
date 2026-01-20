package com.trading.journal.exception;

/** 잘못된 거래 요청 시 발생하는 예외 */
public class InvalidTradeException extends RuntimeException {

    private final String tradeType;
    private final String reason;

    public InvalidTradeException(String message) {
        super(message);
        this.tradeType = null;
        this.reason = message;
    }

    public InvalidTradeException(String tradeType, String reason) {
        super(String.format("잘못된 %s 거래: %s", tradeType, reason));
        this.tradeType = tradeType;
        this.reason = reason;
    }

    public String getTradeType() {
        return tradeType;
    }

    public String getReason() {
        return reason;
    }
}
