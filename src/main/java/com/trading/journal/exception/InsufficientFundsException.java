package com.trading.journal.exception;

import java.math.BigDecimal;

/**
 * 잔고 부족 시 발생하는 예외
 */
public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal required;
    private final BigDecimal available;
    private final String accountName;

    public InsufficientFundsException(String message) {
        super(message);
        this.required = null;
        this.available = null;
        this.accountName = null;
    }

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super(String.format("잔고 부족: 필요 %s, 가용 %s", required, available));
        this.required = required;
        this.available = available;
        this.accountName = null;
    }

    public InsufficientFundsException(String accountName, BigDecimal required, BigDecimal available) {
        super(String.format("계좌 '%s' 잔고 부족: 필요 %s, 가용 %s", accountName, required, available));
        this.required = required;
        this.available = available;
        this.accountName = accountName;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public String getAccountName() {
        return accountName;
    }
}