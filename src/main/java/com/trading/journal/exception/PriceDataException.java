package com.trading.journal.exception;

import java.time.LocalDate;

/** 가격 데이터 조회/처리 중 발생하는 예외 */
public class PriceDataException extends RuntimeException {

    private final String symbol;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String dataSource;

    public PriceDataException(String message) {
        super(message);
        this.symbol = null;
        this.startDate = null;
        this.endDate = null;
        this.dataSource = null;
    }

    public PriceDataException(String message, Throwable cause) {
        super(message, cause);
        this.symbol = null;
        this.startDate = null;
        this.endDate = null;
        this.dataSource = null;
    }

    public PriceDataException(String symbol, String message) {
        super(String.format("가격 데이터 조회 실패 [%s]: %s", symbol, message));
        this.symbol = symbol;
        this.startDate = null;
        this.endDate = null;
        this.dataSource = null;
    }

    public PriceDataException(
            String symbol, LocalDate startDate, LocalDate endDate, String message) {
        super(String.format("가격 데이터 조회 실패 [%s, %s ~ %s]: %s", symbol, startDate, endDate, message));
        this.symbol = symbol;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dataSource = null;
    }

    public PriceDataException(String symbol, String dataSource, String message, Throwable cause) {
        super(String.format("가격 데이터 조회 실패 [%s, 소스: %s]: %s", symbol, dataSource, message), cause);
        this.symbol = symbol;
        this.startDate = null;
        this.endDate = null;
        this.dataSource = dataSource;
    }

    public PriceDataException(
            String symbol,
            LocalDate startDate,
            LocalDate endDate,
            String dataSource,
            String message,
            Throwable cause) {
        super(
                String.format(
                        "가격 데이터 조회 실패 [%s, %s ~ %s, 소스: %s]: %s",
                        symbol, startDate, endDate, dataSource, message),
                cause);
        this.symbol = symbol;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dataSource = dataSource;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getDataSource() {
        return dataSource;
    }
}
