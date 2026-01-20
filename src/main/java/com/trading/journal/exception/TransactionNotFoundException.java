package com.trading.journal.exception;

/**
 * 거래(트랜잭션)를 찾을 수 없을 때 발생하는 예외
 */
public class TransactionNotFoundException extends RuntimeException {

    private final Long transactionId;
    private final String transactionType;

    public TransactionNotFoundException(String message) {
        super(message);
        this.transactionId = null;
        this.transactionType = null;
    }

    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.transactionId = null;
        this.transactionType = null;
    }

    public TransactionNotFoundException(Long transactionId) {
        super(String.format("거래를 찾을 수 없습니다. ID: %d", transactionId));
        this.transactionId = transactionId;
        this.transactionType = null;
    }

    public TransactionNotFoundException(Long transactionId, String transactionType) {
        super(String.format("%s 거래를 찾을 수 없습니다. ID: %d", transactionType, transactionId));
        this.transactionId = transactionId;
        this.transactionType = transactionType;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }
}
