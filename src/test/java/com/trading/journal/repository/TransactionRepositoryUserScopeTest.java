package com.trading.journal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@DisplayName("TransactionRepository 유저 스코프 쿼리")
class TransactionRepositoryUserScopeTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private AccountRepository accountRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(Stock.builder().symbol("AAPL").name("Apple").build());
        Account user1Account =
                accountRepository.save(
                        Account.builder()
                                .name("u1")
                                .accountType(AccountType.GENERAL)
                                .isDefault(true)
                                .userId(1L)
                                .build());
        Account user2Account =
                accountRepository.save(
                        Account.builder()
                                .name("u2")
                                .accountType(AccountType.GENERAL)
                                .isDefault(true)
                                .userId(2L)
                                .build());
        saveTx(user1Account, "100.00", LocalDateTime.now().minusDays(1));
        saveTx(user2Account, "200.00", LocalDateTime.now().minusDays(1));
    }

    private void saveTx(Account account, String price, LocalDateTime date) {
        transactionRepository.save(
                Transaction.builder()
                        .account(account)
                        .stock(stock)
                        .type(TransactionType.BUY)
                        .quantity(new BigDecimal("10"))
                        .price(new BigDecimal(price))
                        .commission(BigDecimal.ZERO)
                        .transactionDate(date)
                        .build());
    }

    @Test
    @DisplayName("findAllWithStockByUserId는 해당 유저의 거래만 반환한다")
    void findAllWithStockByUserId_scopesToUser() {
        List<Transaction> result = transactionRepository.findAllWithStockByUserId(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccount().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByUserId(Pageable)는 해당 유저의 거래만 반환한다")
    void findByUserId_paged_scopesToUser() {
        var page = transactionRepository.findByUserId(2L, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAccount().getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findBySymbolWithStockAndUserId는 유저+심볼로 필터한다")
    void findBySymbolWithStockAndUserId_scopesToUser() {
        List<Transaction> result = transactionRepository.findBySymbolWithStockAndUserId("AAPL", 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccount().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByDateRangeAndUserId는 유저+기간으로 필터한다")
    void findByDateRangeAndUserId_scopesToUser() {
        List<Transaction> result =
                transactionRepository.findByDateRangeAndUserId(
                        LocalDateTime.now().minusDays(2), LocalDateTime.now(), 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccount().getUserId()).isEqualTo(1L);
    }
}
