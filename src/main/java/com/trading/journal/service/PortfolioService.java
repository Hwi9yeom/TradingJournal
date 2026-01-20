package com.trading.journal.service;

import com.trading.journal.entity.Account;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;

    @CacheEvict(value = "portfolio", allEntries = true)
    public void updatePortfolio(Transaction transaction) {
        Account account = transaction.getAccount();
        Long stockId = transaction.getStock().getId();

        // Account 기반으로 Portfolio 조회 (같은 계좌 + 같은 종목)
        Portfolio portfolio;
        if (account != null) {
            portfolio =
                    portfolioRepository
                            .findByAccountIdAndStockId(account.getId(), stockId)
                            .orElse(
                                    Portfolio.builder()
                                            .account(account)
                                            .stock(transaction.getStock())
                                            .quantity(BigDecimal.ZERO)
                                            .averagePrice(BigDecimal.ZERO)
                                            .totalInvestment(BigDecimal.ZERO)
                                            .build());
        } else {
            // 하위 호환성: account가 없으면 기존 방식
            portfolio =
                    portfolioRepository
                            .findByStockId(stockId)
                            .orElse(
                                    Portfolio.builder()
                                            .stock(transaction.getStock())
                                            .quantity(BigDecimal.ZERO)
                                            .averagePrice(BigDecimal.ZERO)
                                            .totalInvestment(BigDecimal.ZERO)
                                            .build());
        }

        if (transaction.getType() == TransactionType.BUY) {
            BigDecimal newTotalQuantity = portfolio.getQuantity().add(transaction.getQuantity());
            BigDecimal newTotalInvestment =
                    portfolio
                            .getTotalInvestment()
                            .add(transaction.getPrice().multiply(transaction.getQuantity()))
                            .add(
                                    transaction.getCommission() != null
                                            ? transaction.getCommission()
                                            : BigDecimal.ZERO);

            portfolio.setQuantity(newTotalQuantity);
            portfolio.setTotalInvestment(newTotalInvestment);

            if (newTotalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                portfolio.setAveragePrice(
                        newTotalInvestment.divide(newTotalQuantity, 2, RoundingMode.HALF_UP));
            }
        } else {
            BigDecimal newTotalQuantity =
                    portfolio.getQuantity().subtract(transaction.getQuantity());

            if (newTotalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                if (portfolio.getId() != null) {
                    portfolioRepository.delete(portfolio);
                }
                return;
            }

            BigDecimal soldRatio =
                    transaction
                            .getQuantity()
                            .divide(portfolio.getQuantity(), 6, RoundingMode.HALF_UP);
            BigDecimal soldInvestment = portfolio.getTotalInvestment().multiply(soldRatio);
            BigDecimal newTotalInvestment = portfolio.getTotalInvestment().subtract(soldInvestment);

            portfolio.setQuantity(newTotalQuantity);
            portfolio.setTotalInvestment(newTotalInvestment);
        }

        portfolioRepository.save(portfolio);
    }

    @CacheEvict(value = "portfolio", allEntries = true)
    public void recalculatePortfolio(Long accountId, Long stockId) {
        List<Transaction> transactions;

        if (accountId != null) {
            transactions =
                    transactionRepository.findByAccountIdAndStockIdOrderByTransactionDateDesc(
                            accountId, stockId);
        } else {
            transactions = transactionRepository.findByStockIdOrderByTransactionDateDesc(stockId);
        }

        if (transactions.isEmpty()) {
            if (accountId != null) {
                portfolioRepository
                        .findByAccountIdAndStockId(accountId, stockId)
                        .ifPresent(portfolioRepository::delete);
            } else {
                portfolioRepository.findByStockId(stockId).ifPresent(portfolioRepository::delete);
            }
            return;
        }

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalInvestment = BigDecimal.ZERO;

        // 거래를 날짜 오름차순으로 처리 (리스트가 DESC이므로 역순으로)
        for (int i = transactions.size() - 1; i >= 0; i--) {
            Transaction transaction = transactions.get(i);
            if (transaction.getType() == TransactionType.BUY) {
                totalQuantity = totalQuantity.add(transaction.getQuantity());
                totalInvestment =
                        totalInvestment
                                .add(transaction.getPrice().multiply(transaction.getQuantity()))
                                .add(
                                        transaction.getCommission() != null
                                                ? transaction.getCommission()
                                                : BigDecimal.ZERO);
            } else {
                if (totalQuantity.compareTo(BigDecimal.ZERO) > 0
                        && totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal soldRatio =
                            transaction
                                    .getQuantity()
                                    .divide(totalQuantity, 6, RoundingMode.HALF_UP);
                    BigDecimal soldInvestment = totalInvestment.multiply(soldRatio);
                    totalInvestment = totalInvestment.subtract(soldInvestment);
                }
                totalQuantity = totalQuantity.subtract(transaction.getQuantity());
            }
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            if (accountId != null) {
                portfolioRepository
                        .findByAccountIdAndStockId(accountId, stockId)
                        .ifPresent(portfolioRepository::delete);
            } else {
                portfolioRepository.findByStockId(stockId).ifPresent(portfolioRepository::delete);
            }
            return;
        }

        Portfolio portfolio;
        if (accountId != null) {
            portfolio =
                    portfolioRepository
                            .findByAccountIdAndStockId(accountId, stockId)
                            .orElse(
                                    Portfolio.builder()
                                            .account(transactions.get(0).getAccount())
                                            .stock(transactions.get(0).getStock())
                                            .build());
        } else {
            portfolio =
                    portfolioRepository
                            .findByStockId(stockId)
                            .orElse(
                                    Portfolio.builder()
                                            .stock(transactions.get(0).getStock())
                                            .build());
        }

        portfolio.setQuantity(totalQuantity);
        portfolio.setTotalInvestment(totalInvestment);
        portfolio.setAveragePrice(totalInvestment.divide(totalQuantity, 2, RoundingMode.HALF_UP));

        portfolioRepository.save(portfolio);
    }

    /** 하위 호환성을 위한 메서드 (accountId 없이 호출) */
    @CacheEvict(value = "portfolio", allEntries = true)
    public void recalculatePortfolio(Long stockId) {
        recalculatePortfolio(null, stockId);
    }
}
