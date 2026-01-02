package com.trading.journal.service;

import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockAnalysisService {
    
    private final StockRepository stockRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockPriceService stockPriceService;
    
    public StockAnalysisDto analyzeStock(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found: " + symbol));
                
        List<Transaction> transactions = transactionRepository.findByStockSymbol(symbol);
        
        if (transactions.isEmpty()) {
            return buildEmptyAnalysis(stock);
        }
        
        // 매수/매도 거래 분리
        List<Transaction> buyTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .collect(Collectors.toList());
                
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .collect(Collectors.toList());
        
        // 기본 통계
        BigDecimal totalBuyQuantity = sumQuantity(buyTransactions);
        BigDecimal totalSellQuantity = sumQuantity(sellTransactions);
        BigDecimal totalBuyAmount = sumAmount(buyTransactions);
        BigDecimal totalSellAmount = sumAmount(sellTransactions);
        
        // 평균 가격
        BigDecimal averageBuyPrice = calculateAveragePrice(buyTransactions);
        BigDecimal averageSellPrice = calculateAveragePrice(sellTransactions);
        
        // 실현 손익
        BigDecimal realizedProfit = calculateRealizedProfit(buyTransactions, sellTransactions);
        BigDecimal realizedProfitRate = calculateProfitRate(totalBuyAmount, realizedProfit);
        
        // 현재 보유 및 미실현 손익
        Optional<Portfolio> portfolio = portfolioRepository.findByStockId(stock.getId());
        BigDecimal currentHolding = portfolio.map(Portfolio::getQuantity).orElse(BigDecimal.ZERO);
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal unrealizedProfit = BigDecimal.ZERO;
        BigDecimal unrealizedProfitRate = BigDecimal.ZERO;
        
        if (currentHolding.compareTo(BigDecimal.ZERO) > 0) {
            try {
                currentPrice = stockPriceService.getCurrentPrice(symbol);
                currentValue = currentPrice.multiply(currentHolding);
                BigDecimal holdingCost = portfolio.get().getTotalInvestment();
                unrealizedProfit = currentValue.subtract(holdingCost);
                unrealizedProfitRate = calculateProfitRate(holdingCost, unrealizedProfit);
            } catch (Exception e) {
                log.error("Failed to get current price for {}", symbol, e);
            }
        }
        
        // 거래 날짜 정보
        LocalDateTime firstTransaction = transactions.stream()
                .map(Transaction::getTransactionDate)
                .min(LocalDateTime::compareTo)
                .orElse(null);
                
        LocalDateTime lastTransaction = transactions.stream()
                .map(Transaction::getTransactionDate)
                .max(LocalDateTime::compareTo)
                .orElse(null);
                
        int holdingDays = firstTransaction != null && lastTransaction != null ? 
                (int) ChronoUnit.DAYS.between(firstTransaction.toLocalDate(), LocalDateTime.now().toLocalDate()) : 0;
        
        // 매매 패턴 분석
        List<StockAnalysisDto.TradingPatternDto> patterns = analyzeTradingPatterns(transactions);
        
        return StockAnalysisDto.builder()
                .stockSymbol(stock.getSymbol())
                .stockName(stock.getName())
                .totalBuyCount(buyTransactions.size())
                .totalSellCount(sellTransactions.size())
                .totalBuyQuantity(totalBuyQuantity)
                .totalSellQuantity(totalSellQuantity)
                .averageBuyPrice(averageBuyPrice)
                .averageSellPrice(averageSellPrice)
                .realizedProfit(realizedProfit)
                .realizedProfitRate(realizedProfitRate)
                .currentHolding(currentHolding)
                .currentValue(currentValue)
                .unrealizedProfit(unrealizedProfit)
                .unrealizedProfitRate(unrealizedProfitRate)
                .firstTransactionDate(firstTransaction)
                .lastTransactionDate(lastTransaction)
                .holdingDays(holdingDays)
                .tradingPatterns(patterns)
                .build();
    }
    
    private List<StockAnalysisDto.TradingPatternDto> analyzeTradingPatterns(List<Transaction> transactions) {
        List<StockAnalysisDto.TradingPatternDto> patterns = new ArrayList<>();
        
        // 평균 보유 기간 분석
        patterns.add(analyzeHoldingPeriod(transactions));
        
        // 손절/익절 패턴 분석
        patterns.add(analyzeProfitLossPattern(transactions));
        
        // 거래 빈도 분석
        patterns.add(analyzeTradeFrequency(transactions));
        
        // 거래 규모 패턴
        patterns.add(analyzeTradeSize(transactions));
        
        return patterns;
    }
    
    private StockAnalysisDto.TradingPatternDto analyzeHoldingPeriod(List<Transaction> transactions) {
        // FIFO 방식으로 매수-매도 매칭하여 평균 보유 기간 계산
        List<Integer> holdingPeriods = new ArrayList<>();
        
        List<Transaction> buys = new ArrayList<>(transactions.stream()
                .filter(t -> t.getType() == TransactionType.BUY)
                .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
                .collect(Collectors.toList()));
                
        List<Transaction> sells = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
                .collect(Collectors.toList());
        
        for (Transaction sell : sells) {
            if (!buys.isEmpty()) {
                Transaction matchedBuy = buys.get(0);
                int days = (int) ChronoUnit.DAYS.between(
                        matchedBuy.getTransactionDate().toLocalDate(),
                        sell.getTransactionDate().toLocalDate());
                holdingPeriods.add(days);
                buys.remove(0);
            }
        }
        
        double avgHoldingDays = holdingPeriods.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        
        return StockAnalysisDto.TradingPatternDto.builder()
                .pattern("평균 보유 기간")
                .value(String.format("%.1f일", avgHoldingDays))
                .description("매수 후 매도까지의 평균 기간")
                .build();
    }
    
    private StockAnalysisDto.TradingPatternDto analyzeProfitLossPattern(List<Transaction> transactions) {
        int profitCount = 0;
        int lossCount = 0;

        // FIFO 기반 realizedPnl로 익절/손절 분석
        List<Transaction> sells = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .collect(Collectors.toList());

        for (Transaction sell : sells) {
            BigDecimal realizedPnl = sell.getRealizedPnl();
            if (realizedPnl != null) {
                if (realizedPnl.compareTo(BigDecimal.ZERO) > 0) {
                    profitCount++;
                } else if (realizedPnl.compareTo(BigDecimal.ZERO) < 0) {
                    lossCount++;
                }
            }
        }

        int total = profitCount + lossCount;
        double winRate = total > 0 ? (double) profitCount / total * 100 : 0;

        return StockAnalysisDto.TradingPatternDto.builder()
                .pattern("승률")
                .value(String.format("%.1f%% (%d승 %d패)", winRate, profitCount, lossCount))
                .description("익절 vs 손절 비율 (FIFO 기반)")
                .build();
    }
    
    private StockAnalysisDto.TradingPatternDto analyzeTradeFrequency(List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return StockAnalysisDto.TradingPatternDto.builder()
                    .pattern("거래 빈도")
                    .value("데이터 부족")
                    .description("거래 횟수가 부족합니다")
                    .build();
        }
        
        LocalDateTime first = transactions.stream()
                .map(Transaction::getTransactionDate)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
                
        LocalDateTime last = transactions.stream()
                .map(Transaction::getTransactionDate)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
                
        long totalDays = ChronoUnit.DAYS.between(first.toLocalDate(), last.toLocalDate());
        if (totalDays == 0) totalDays = 1;
        
        double tradesPerMonth = (double) transactions.size() / totalDays * 30;
        
        return StockAnalysisDto.TradingPatternDto.builder()
                .pattern("거래 빈도")
                .value(String.format("월 평균 %.1f회", tradesPerMonth))
                .description("월 평균 거래 횟수")
                .build();
    }
    
    private StockAnalysisDto.TradingPatternDto analyzeTradeSize(List<Transaction> transactions) {
        List<BigDecimal> tradeSizes = transactions.stream()
                .map(Transaction::getTotalAmount)
                .collect(Collectors.toList());
                
        BigDecimal avgSize = tradeSizes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(tradeSizes.size()), 0, RoundingMode.HALF_UP);
                
        BigDecimal maxSize = tradeSizes.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
                
        return StockAnalysisDto.TradingPatternDto.builder()
                .pattern("평균 거래 규모")
                .value(String.format("₩%s (최대: ₩%s)", 
                        formatNumber(avgSize), formatNumber(maxSize)))
                .description("거래당 평균 금액 및 최대 거래 금액")
                .build();
    }
    
    private String formatNumber(BigDecimal value) {
        return String.format("%,.0f", value);
    }
    
    private BigDecimal sumQuantity(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal sumAmount(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateAveragePrice(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalAmount = sumAmount(transactions);
        BigDecimal totalQuantity = sumQuantity(transactions);
        
        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalAmount.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateRealizedProfit(List<Transaction> buyTransactions,
                                              List<Transaction> sellTransactions) {
        // FIFO 방식으로 계산된 realizedPnl 합계 사용
        return sellTransactions.stream()
                .map(Transaction::getRealizedPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateProfitRate(BigDecimal cost, BigDecimal profit) {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return profit.divide(cost, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    private StockAnalysisDto buildEmptyAnalysis(Stock stock) {
        return StockAnalysisDto.builder()
                .stockSymbol(stock.getSymbol())
                .stockName(stock.getName())
                .totalBuyCount(0)
                .totalSellCount(0)
                .totalBuyQuantity(BigDecimal.ZERO)
                .totalSellQuantity(BigDecimal.ZERO)
                .averageBuyPrice(BigDecimal.ZERO)
                .averageSellPrice(BigDecimal.ZERO)
                .realizedProfit(BigDecimal.ZERO)
                .realizedProfitRate(BigDecimal.ZERO)
                .currentHolding(BigDecimal.ZERO)
                .currentValue(BigDecimal.ZERO)
                .unrealizedProfit(BigDecimal.ZERO)
                .unrealizedProfitRate(BigDecimal.ZERO)
                .holdingDays(0)
                .tradingPatterns(new ArrayList<>())
                .build();
    }
}