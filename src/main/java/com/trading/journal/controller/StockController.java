package com.trading.journal.controller;

import com.trading.journal.entity.Stock;
import com.trading.journal.repository.StockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final StockRepository stockRepository;

    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks() {
        log.info("Getting all stocks");
        List<Stock> stocks = stockRepository.findAll();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Stock> getStock(@PathVariable String symbol) {
        log.info("Getting stock: {}", symbol);
        return stockRepository
                .findBySymbol(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
