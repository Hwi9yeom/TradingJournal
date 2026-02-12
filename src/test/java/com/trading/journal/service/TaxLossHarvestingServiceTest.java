package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.trading.journal.dto.TaxLossHarvestingDto;
import com.trading.journal.entity.Account;
import com.trading.journal.exception.UnauthorizedAccessException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxLossHarvestingServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private StockPriceService stockPriceService;
    @Mock private SecurityContextService securityContextService;

    @InjectMocks private TaxLossHarvestingService taxLossHarvestingService;

    @Test
    @DisplayName("현재 사용자 계좌가 없으면 전체 계좌 분석은 빈 결과를 반환한다")
    void analyzeAllHarvestingOpportunitiesForCurrentUser_NoAccounts() {
        when(securityContextService.getCurrentUserId()).thenReturn(Optional.of(1L));
        when(accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(1L))
                .thenReturn(List.of());

        TaxLossHarvestingDto result =
                taxLossHarvestingService.analyzeAllHarvestingOpportunitiesForCurrentUser();

        assertThat(result.getAccountId()).isNull();
        assertThat(result.getTotalOpportunities()).isZero();
        assertThat(result.getRecommendation()).isEqualTo("계좌 정보가 없습니다.");
    }

    @Test
    @DisplayName("계좌 소유자가 아니면 계좌별 분석 시 접근 거부 예외를 반환한다")
    void analyzeTaxLossHarvestingOpportunities_UnauthorizedAccount() {
        Account account = Account.builder().id(10L).userId(2L).build();

        when(securityContextService.getCurrentUserId()).thenReturn(Optional.of(1L));
        when(securityContextService.getCurrentUsername()).thenReturn(Optional.of("test-user"));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        assertThatThrownBy(
                        () -> taxLossHarvestingService.analyzeTaxLossHarvestingOpportunities(10L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("현재 사용자 계좌를 합산해 전체 계좌 분석 결과를 반환한다")
    void analyzeAllHarvestingOpportunitiesForCurrentUser_AggregatesAccounts() {
        Account account1 = Account.builder().id(10L).userId(1L).build();
        Account account2 = Account.builder().id(20L).userId(1L).build();

        when(securityContextService.getCurrentUserId()).thenReturn(Optional.of(1L));
        when(accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(1L))
                .thenReturn(List.of(account1, account2));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account1));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account2));
        when(portfolioRepository.findByAccountIdWithStock(10L)).thenReturn(List.of());
        when(portfolioRepository.findByAccountIdWithStock(20L)).thenReturn(List.of());

        TaxLossHarvestingDto result =
                taxLossHarvestingService.analyzeAllHarvestingOpportunitiesForCurrentUser();

        assertThat(result.getAccountId()).isNull();
        assertThat(result.getTotalOpportunities()).isZero();
        assertThat(result.getRecommendation()).contains("총 2개 계좌 기준 분석 결과입니다.");
    }
}
