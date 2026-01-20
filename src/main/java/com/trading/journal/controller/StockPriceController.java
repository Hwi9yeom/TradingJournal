package com.trading.journal.controller;

import com.trading.journal.dto.StockPriceDto;
import com.trading.journal.service.StockPriceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yahoofinance.Stock;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockPriceController {

    private final StockPriceService stockPriceService;

    @GetMapping("/price/{symbol}")
    public ResponseEntity<StockPriceDto> getStockPrice(@PathVariable String symbol) {
        try {
            Stock stock = stockPriceService.getStockInfo(symbol);

            if (stock == null || stock.getQuote() == null) {
                return ResponseEntity.notFound().build();
            }

            BigDecimal currentPrice = stock.getQuote().getPrice();
            BigDecimal previousClose = stock.getQuote().getPreviousClose();

            if (currentPrice == null || previousClose == null) {
                return ResponseEntity.notFound().build();
            }

            BigDecimal change = currentPrice.subtract(previousClose);
            BigDecimal changePercent = BigDecimal.ZERO;

            if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
                changePercent =
                        change.divide(previousClose, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
            }

            StockPriceDto dto =
                    StockPriceDto.builder()
                            .symbol(stock.getSymbol() != null ? stock.getSymbol() : symbol)
                            .name(stock.getName() != null ? stock.getName() : symbol)
                            .currentPrice(currentPrice)
                            .previousClose(previousClose)
                            .changeAmount(change)
                            .changePercent(changePercent)
                            .timestamp(LocalDateTime.now())
                            .build();

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
