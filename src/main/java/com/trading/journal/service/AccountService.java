package com.trading.journal.service;

import com.trading.journal.dto.AccountDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import com.trading.journal.exception.AccountNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    public AccountDto createAccount(AccountDto dto) {
        if (accountRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("이미 존재하는 계좌 이름입니다: " + dto.getName());
        }

        Account account = Account.builder()
                .name(dto.getName())
                .accountType(dto.getAccountType())
                .description(dto.getDescription())
                .isDefault(false)
                .build();

        Account saved = accountRepository.save(account);
        log.info("계좌 생성 완료: {}", saved.getName());
        return convertToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAllByOrderByIsDefaultDescCreatedAtAsc().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return convertToDto(account);
    }

    @Transactional(readOnly = true)
    public Long getDefaultAccountId() {
        return accountRepository.findByIsDefaultTrue()
                .map(Account::getId)
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌가 설정되지 않았습니다"));
    }

    @Transactional(readOnly = true)
    public Account getDefaultAccount() {
        return accountRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌가 설정되지 않았습니다"));
    }

    @Transactional(readOnly = true)
    public Account getAccountEntity(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public AccountDto updateAccount(Long id, AccountDto dto) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        // 이름 중복 체크 (자신 제외)
        if (!account.getName().equals(dto.getName()) && accountRepository.existsByName(dto.getName())) {
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
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

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
        // 기존 기본 계좌 해제
        accountRepository.findByIsDefaultTrue().ifPresent(existingDefault -> {
            existingDefault.setIsDefault(false);
            accountRepository.save(existingDefault);
        });

        // 새 기본 계좌 설정
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setIsDefault(true);
        Account saved = accountRepository.save(account);

        log.info("기본 계좌 변경: {}", saved.getName());
        return convertToDto(saved);
    }

    /**
     * 기본 계좌가 없으면 생성 (마이그레이션용)
     */
    public Account ensureDefaultAccount() {
        return accountRepository.findByIsDefaultTrue()
                .orElseGet(() -> {
                    Account defaultAccount = Account.builder()
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
