package com.trading.journal.exception;

/** 종목을 찾을 수 없을 때 발생하는 예외 */
public class StockNotFoundException extends RuntimeException {

    private final String symbol;
    private final Long stockId;

    public StockNotFoundException(String message) {
        super(message);
        this.symbol = null;
        this.stockId = null;
    }

    public StockNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.symbol = null;
        this.stockId = null;
    }

    public StockNotFoundException(Long stockId) {
        super(String.format("종목을 찾을 수 없습니다. ID: %d", stockId));
        this.stockId = stockId;
        this.symbol = null;
    }

    public static StockNotFoundException bySymbol(String symbol) {
        return new StockNotFoundException(symbol, true);
    }

    private StockNotFoundException(String symbol, boolean bySymbol) {
        super(String.format("종목을 찾을 수 없습니다. 심볼: %s", symbol));
        this.symbol = symbol;
        this.stockId = null;
    }

    public StockNotFoundException(String symbol, Long stockId) {
        super(String.format("종목을 찾을 수 없습니다. 심볼: %s, ID: %d", symbol, stockId));
        this.symbol = symbol;
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Long getStockId() {
        return stockId;
    }
}
