package com.trading.journal.controller;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.service.PortfolioAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioAnalysisService portfolioAnalysisService;

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryDto> getPortfolioSummary() {
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<PortfolioDto> getPortfolioBySymbol(@PathVariable String symbol) {
        PortfolioDto portfolio = portfolioAnalysisService.getPortfolioBySymbol(symbol);
        return ResponseEntity.ok(portfolio);
    }
}
