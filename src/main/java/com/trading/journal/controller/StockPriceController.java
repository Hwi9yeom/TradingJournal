package com.trading.journal.controller;

import com.trading.journal.dto.StockPriceDto;
import com.trading.journal.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yahoofinance.Stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StockPriceController {
    
    private final StockPriceService stockPriceService;
    
    @GetMapping("/price/{symbol}")
    public ResponseEntity<StockPriceDto> getStockPrice(@PathVariable String symbol) {
        try {
            Stock stock = stockPriceService.getStockInfo(symbol);
            BigDecimal currentPrice = stock.getQuote().getPrice();
            BigDecimal previousClose = stock.getQuote().getPreviousClose();
            BigDecimal change = currentPrice.subtract(previousClose);
            BigDecimal changePercent = BigDecimal.ZERO;
            
            if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
                changePercent = change.divide(previousClose, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            StockPriceDto dto = StockPriceDto.builder()
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
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