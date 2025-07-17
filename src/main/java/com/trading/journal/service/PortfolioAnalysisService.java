package com.trading.journal.service;

import com.trading.journal.dto.PortfolioDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioAnalysisService {
    
    private final PortfolioRepository portfolioRepository;
    private final StockPriceService stockPriceService;
    
    public PortfolioSummaryDto getPortfolioSummary() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<PortfolioDto> holdings = new ArrayList<>();
        
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalDayChange = BigDecimal.ZERO;
        
        for (Portfolio portfolio : portfolios) {
            PortfolioDto dto = calculatePortfolioMetrics(portfolio);
            holdings.add(dto);
            
            totalInvestment = totalInvestment.add(portfolio.getTotalInvestment());
            totalCurrentValue = totalCurrentValue.add(dto.getCurrentValue());
            totalDayChange = totalDayChange.add(dto.getDayChange());
        }
        
        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvestment);
        BigDecimal totalProfitLossPercent = BigDecimal.ZERO;
        BigDecimal totalDayChangePercent = BigDecimal.ZERO;
        
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitLossPercent = totalProfitLoss
                    .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        if (totalCurrentValue.subtract(totalDayChange).compareTo(BigDecimal.ZERO) > 0) {
            totalDayChangePercent = totalDayChange
                    .divide(totalCurrentValue.subtract(totalDayChange), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        
        return PortfolioSummaryDto.builder()
                .totalInvestment(totalInvestment)
                .totalCurrentValue(totalCurrentValue)
                .totalProfitLoss(totalProfitLoss)
                .totalProfitLossPercent(totalProfitLossPercent)
                .totalDayChange(totalDayChange)
                .totalDayChangePercent(totalDayChangePercent)
                .holdings(holdings)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    public PortfolioDto getPortfolioBySymbol(String symbol) {
        Portfolio portfolio = portfolioRepository.findByStockSymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Portfolio not found for symbol: " + symbol));
        return calculatePortfolioMetrics(portfolio);
    }
    
    private PortfolioDto calculatePortfolioMetrics(Portfolio portfolio) {
        try {
            BigDecimal currentPrice = stockPriceService.getCurrentPrice(portfolio.getStock().getSymbol());
            BigDecimal previousClose = stockPriceService.getPreviousClose(portfolio.getStock().getSymbol());
            
            BigDecimal currentValue = currentPrice.multiply(portfolio.getQuantity());
            BigDecimal profitLoss = currentValue.subtract(portfolio.getTotalInvestment());
            BigDecimal profitLossPercent = BigDecimal.ZERO;
            
            if (portfolio.getTotalInvestment().compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .divide(portfolio.getTotalInvestment(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            BigDecimal dayChange = currentPrice.subtract(previousClose).multiply(portfolio.getQuantity());
            BigDecimal dayChangePercent = BigDecimal.ZERO;
            
            if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
                dayChangePercent = currentPrice.subtract(previousClose)
                        .divide(previousClose, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            return PortfolioDto.builder()
                    .id(portfolio.getId())
                    .stockSymbol(portfolio.getStock().getSymbol())
                    .stockName(portfolio.getStock().getName())
                    .quantity(portfolio.getQuantity())
                    .averagePrice(portfolio.getAveragePrice())
                    .totalInvestment(portfolio.getTotalInvestment())
                    .currentPrice(currentPrice)
                    .currentValue(currentValue)
                    .profitLoss(profitLoss)
                    .profitLossPercent(profitLossPercent)
                    .dayChange(dayChange)
                    .dayChangePercent(dayChangePercent)
                    .lastUpdated(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to calculate portfolio metrics for symbol: {}", 
                    portfolio.getStock().getSymbol(), e);
            
            return PortfolioDto.builder()
                    .id(portfolio.getId())
                    .stockSymbol(portfolio.getStock().getSymbol())
                    .stockName(portfolio.getStock().getName())
                    .quantity(portfolio.getQuantity())
                    .averagePrice(portfolio.getAveragePrice())
                    .totalInvestment(portfolio.getTotalInvestment())
                    .currentPrice(BigDecimal.ZERO)
                    .currentValue(BigDecimal.ZERO)
                    .profitLoss(BigDecimal.ZERO)
                    .profitLossPercent(BigDecimal.ZERO)
                    .dayChange(BigDecimal.ZERO)
                    .dayChangePercent(BigDecimal.ZERO)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }
}