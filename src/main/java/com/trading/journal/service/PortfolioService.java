package com.trading.journal.service;

import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    
    public void updatePortfolio(Transaction transaction) {
        Portfolio portfolio = portfolioRepository.findByStockId(transaction.getStock().getId())
                .orElse(Portfolio.builder()
                        .stock(transaction.getStock())
                        .quantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .totalInvestment(BigDecimal.ZERO)
                        .build());
        
        if (transaction.getType() == TransactionType.BUY) {
            BigDecimal newTotalQuantity = portfolio.getQuantity().add(transaction.getQuantity());
            BigDecimal newTotalInvestment = portfolio.getTotalInvestment()
                    .add(transaction.getPrice().multiply(transaction.getQuantity()))
                    .add(transaction.getCommission() != null ? transaction.getCommission() : BigDecimal.ZERO);
            
            portfolio.setQuantity(newTotalQuantity);
            portfolio.setTotalInvestment(newTotalInvestment);
            
            if (newTotalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                portfolio.setAveragePrice(newTotalInvestment.divide(newTotalQuantity, 2, RoundingMode.HALF_UP));
            }
        } else {
            BigDecimal newTotalQuantity = portfolio.getQuantity().subtract(transaction.getQuantity());
            
            if (newTotalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                portfolioRepository.delete(portfolio);
                return;
            }
            
            BigDecimal soldRatio = transaction.getQuantity().divide(portfolio.getQuantity(), 6, RoundingMode.HALF_UP);
            BigDecimal soldInvestment = portfolio.getTotalInvestment().multiply(soldRatio);
            BigDecimal newTotalInvestment = portfolio.getTotalInvestment().subtract(soldInvestment);
            
            portfolio.setQuantity(newTotalQuantity);
            portfolio.setTotalInvestment(newTotalInvestment);
        }
        
        portfolioRepository.save(portfolio);
    }
    
    public void recalculatePortfolio(Long stockId) {
        List<Transaction> transactions = transactionRepository.findByStockIdOrderByTransactionDateDesc(stockId);
        
        if (transactions.isEmpty()) {
            portfolioRepository.findByStockId(stockId).ifPresent(portfolioRepository::delete);
            return;
        }
        
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalInvestment = BigDecimal.ZERO;
        
        for (Transaction transaction : transactions) {
            if (transaction.getType() == TransactionType.BUY) {
                totalQuantity = totalQuantity.add(transaction.getQuantity());
                totalInvestment = totalInvestment
                        .add(transaction.getPrice().multiply(transaction.getQuantity()))
                        .add(transaction.getCommission() != null ? transaction.getCommission() : BigDecimal.ZERO);
            } else {
                totalQuantity = totalQuantity.subtract(transaction.getQuantity());
                
                if (totalQuantity.compareTo(BigDecimal.ZERO) > 0 && totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal soldRatio = transaction.getQuantity().divide(
                            totalQuantity.add(transaction.getQuantity()), 6, RoundingMode.HALF_UP);
                    BigDecimal soldInvestment = totalInvestment.multiply(soldRatio);
                    totalInvestment = totalInvestment.subtract(soldInvestment);
                }
            }
        }
        
        if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            portfolioRepository.findByStockId(stockId).ifPresent(portfolioRepository::delete);
            return;
        }
        
        Portfolio portfolio = portfolioRepository.findByStockId(stockId)
                .orElse(Portfolio.builder()
                        .stock(transactions.get(0).getStock())
                        .build());
        
        portfolio.setQuantity(totalQuantity);
        portfolio.setTotalInvestment(totalInvestment);
        portfolio.setAveragePrice(totalInvestment.divide(totalQuantity, 2, RoundingMode.HALF_UP));
        
        portfolioRepository.save(portfolio);
    }
}