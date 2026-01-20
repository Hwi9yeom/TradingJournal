package com.trading.journal.exception;

/**
 * 계좌를 찾을 수 없을 때 발생하는 예외
 */
public class AccountNotFoundException extends RuntimeException {

    private final Long accountId;
    private final String accountName;

    public AccountNotFoundException(String message) {
        super(message);
        this.accountId = null;
        this.accountName = null;
    }

    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.accountId = null;
        this.accountName = null;
    }

    public AccountNotFoundException(Long accountId) {
        super(String.format("계좌를 찾을 수 없습니다. ID: %d", accountId));
        this.accountId = accountId;
        this.accountName = null;
    }

    public static AccountNotFoundException byName(String accountName) {
        return new AccountNotFoundException(accountName, true);
    }

    private AccountNotFoundException(String accountName, boolean byName) {
        super(String.format("계좌를 찾을 수 없습니다. 계좌명: %s", accountName));
        this.accountName = accountName;
        this.accountId = null;
    }

    public AccountNotFoundException(Long accountId, String accountName) {
        super(String.format("계좌를 찾을 수 없습니다. ID: %d, 계좌명: %s", accountId, accountName));
        this.accountId = accountId;
        this.accountName = accountName;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }
}
