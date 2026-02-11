package com.trading.journal.service;

import com.trading.journal.dto.AccountDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import com.trading.journal.exception.AccountNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityContextService securityContextService;

    public AccountDto createAccount(AccountDto dto) {
        Long userId = securityContextService.getCurrentUserId().orElse(null);

        if (accountRepository.existsByNameAndUserId(dto.getName(), userId)) {
            throw new IllegalArgumentException("이미 존재하는 계좌 이름입니다: " + dto.getName());
        }

        Account account =
                Account.builder()
                        .name(dto.getName())
                        .accountType(dto.getAccountType())
                        .description(dto.getDescription())
                        .isDefault(false)
                        .userId(userId)
                        .build();

        Account saved = accountRepository.save(account);
        log.info("계좌 생성 완료: {}", saved.getName());
        return convertToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        Long userId = securityContextService.getCurrentUserId().orElse(null);
        return accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(Long id) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        validateOwnership(account);
        return convertToDto(account);
    }

    @Transactional(readOnly = true)
    public Long getDefaultAccountId() {
        Long userId = securityContextService.getCurrentUserId().orElse(null);
        return accountRepository
                .findByUserIdAndIsDefaultTrue(userId)
                .map(Account::getId)
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌가 설정되지 않았습니다"));
    }

    @Transactional(readOnly = true)
    public Account getDefaultAccount() {
        Long userId = securityContextService.getCurrentUserId().orElse(null);
        return accountRepository
                .findByUserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌가 설정되지 않았습니다"));
    }

    @Transactional(readOnly = true)
    public Account getAccountEntity(Long id) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        validateOwnership(account);
        return account;
    }

    public AccountDto updateAccount(Long id, AccountDto dto) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        validateOwnership(account);

        Long userId = securityContextService.getCurrentUserId().orElse(null);

        // 이름 중복 체크 (자신 제외)
        if (!account.getName().equals(dto.getName())
                && accountRepository.existsByNameAndUserId(dto.getName(), userId)) {
            throw new IllegalArgumentException("이미 존재하는 계좌 이름입니다: " + dto.getName());
        }

        account.setName(dto.getName());
        account.setAccountType(dto.getAccountType());
        account.setDescription(dto.getDescription());

        Account saved = accountRepository.save(account);
        log.info("계좌 수정 완료: {}", saved.getName());
        return convertToDto(saved);
    }

    public void deleteAccount(Long id) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        validateOwnership(account);

        if (account.getIsDefault()) {
            throw new IllegalArgumentException("기본 계좌는 삭제할 수 없습니다");
        }

        // 해당 계좌에 포트폴리오가 있는지 확인
        if (!portfolioRepository.findByAccountId(id).isEmpty()) {
            throw new IllegalArgumentException("포트폴리오가 있는 계좌는 삭제할 수 없습니다. 먼저 포트폴리오를 정리해주세요.");
        }

        accountRepository.delete(account);
        log.info("계좌 삭제 완료: {}", account.getName());
    }

    public AccountDto setDefaultAccount(Long id) {
        Long userId = securityContextService.getCurrentUserId().orElse(null);

        // 기존 기본 계좌 해제
        accountRepository
                .findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(
                        existingDefault -> {
                            existingDefault.setIsDefault(false);
                            accountRepository.save(existingDefault);
                        });

        // 새 기본 계좌 설정
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        validateOwnership(account);
        account.setIsDefault(true);
        Account saved = accountRepository.save(account);

        log.info("기본 계좌 변경: {}", saved.getName());
        return convertToDto(saved);
    }

    /** 기본 계좌가 없으면 생성 (마이그레이션용) */
    public Account ensureDefaultAccount() {
        return accountRepository
                .findByIsDefaultTrue()
                .orElseGet(
                        () -> {
                            Account defaultAccount =
                                    Account.builder()
                                            .name("기본 계좌")
                                            .accountType(AccountType.GENERAL)
                                            .description("자동 생성된 기본 계좌")
                                            .isDefault(true)
                                            .build();
                            Account saved = accountRepository.save(defaultAccount);
                            log.info("기본 계좌 자동 생성: {}", saved.getName());
                            return saved;
                        });
    }

    /**
     * Validates that the current user owns the given account.
     *
     * @param account The account to validate
     * @throws com.trading.journal.exception.UnauthorizedAccessException if user doesn't own the
     *     account
     */
    private void validateOwnership(Account account) {
        Long currentUserId = securityContextService.getCurrentUserId().orElse(null);
        String currentUsername = securityContextService.getCurrentUsername().orElse("anonymous");

        if (currentUserId == null || !currentUserId.equals(account.getUserId())) {
            throw new com.trading.journal.exception.UnauthorizedAccessException(
                    "Account", account.getId(), currentUsername);
        }
    }

    private AccountDto convertToDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .name(account.getName())
                .accountType(account.getAccountType())
                .description(account.getDescription())
                .isDefault(account.getIsDefault())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
