package com.trading.journal.config;

import com.trading.journal.entity.Account;
import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Transaction;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.DividendRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import com.trading.journal.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 애플리케이션 시작 시 데이터 초기화 및 마이그레이션 수행
 * - 기본 계좌 생성
 * - 기존 데이터에 account_id 연결
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("DataInitializer 시작...");

        // 1. 기본 계좌 생성
        Account defaultAccount = accountService.ensureDefaultAccount();
        log.info("기본 계좌 확인 완료: {} (ID: {})", defaultAccount.getName(), defaultAccount.getId());

        // 2. 기존 데이터 마이그레이션 (account_id가 NULL인 경우)
        migrateOrphanedData(defaultAccount);

        log.info("DataInitializer 완료");
    }

    private void migrateOrphanedData(Account defaultAccount) {
        // Portfolio 마이그레이션
        List<Portfolio> orphanedPortfolios = portfolioRepository.findByAccountIsNull();
        if (!orphanedPortfolios.isEmpty()) {
            log.info("마이그레이션 대상 Portfolio: {}개", orphanedPortfolios.size());
            for (Portfolio portfolio : orphanedPortfolios) {
                portfolio.setAccount(defaultAccount);
                portfolioRepository.save(portfolio);
            }
            log.info("Portfolio 마이그레이션 완료");
        }

        // Transaction 마이그레이션
        List<Transaction> orphanedTransactions = transactionRepository.findByAccountIsNull();
        if (!orphanedTransactions.isEmpty()) {
            log.info("마이그레이션 대상 Transaction: {}개", orphanedTransactions.size());
            for (Transaction transaction : orphanedTransactions) {
                transaction.setAccount(defaultAccount);
                transactionRepository.save(transaction);
            }
            log.info("Transaction 마이그레이션 완료");
        }

        // Dividend 마이그레이션
        List<Dividend> orphanedDividends = dividendRepository.findByAccountIsNull();
        if (!orphanedDividends.isEmpty()) {
            log.info("마이그레이션 대상 Dividend: {}개", orphanedDividends.size());
            for (Dividend dividend : orphanedDividends) {
                dividend.setAccount(defaultAccount);
                dividendRepository.save(dividend);
            }
            log.info("Dividend 마이그레이션 완료");
        }
    }
}
