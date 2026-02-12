package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.AccountDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.exception.AccountNotFoundException;
import com.trading.journal.exception.UnauthorizedAccessException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private SecurityContextService securityContextService;

    @InjectMocks private AccountService accountService;

    private static final Long TEST_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String TEST_USERNAME = "testuser";

    private Account testAccount;
    private Account defaultAccount;
    private AccountDto createAccountDto;

    @BeforeEach
    void setUp() {
        testAccount =
                Account.builder()
                        .id(1L)
                        .name("테스트 계좌")
                        .accountType(AccountType.GENERAL)
                        .description("테스트 설명")
                        .isDefault(false)
                        .userId(TEST_USER_ID)
                        .build();

        defaultAccount =
                Account.builder()
                        .id(2L)
                        .name("기본 계좌")
                        .accountType(AccountType.GENERAL)
                        .description("기본 계좌 설명")
                        .isDefault(true)
                        .userId(TEST_USER_ID)
                        .build();

        createAccountDto =
                AccountDto.builder()
                        .name("신규 계좌")
                        .accountType(AccountType.ISA)
                        .description("신규 계좌 설명")
                        .build();

        // 기본적으로 현재 사용자 ID를 TEST_USER_ID로 설정 (lenient로 변경하여 사용되지 않아도 에러 방지)
        lenient()
                .when(securityContextService.getCurrentUserId())
                .thenReturn(Optional.of(TEST_USER_ID));
        lenient()
                .when(securityContextService.getCurrentUsername())
                .thenReturn(Optional.of(TEST_USERNAME));
    }

    @Nested
    @DisplayName("계좌 생성 테스트")
    class CreateAccountTests {

        @Test
        @DisplayName("계좌 생성 성공 - 현재 사용자 ID로 생성됨")
        void createAccount_Success() {
            when(accountRepository.existsByNameAndUserId(createAccountDto.getName(), TEST_USER_ID))
                    .thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            AccountDto result = accountService.createAccount(createAccountDto);

            assertThat(result).isNotNull();
            verify(accountRepository)
                    .existsByNameAndUserId(createAccountDto.getName(), TEST_USER_ID);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("계좌 생성 실패 - 동일한 이름이 이미 존재함")
        void createAccount_ThrowsWhenNameExists() {
            when(accountRepository.existsByNameAndUserId(createAccountDto.getName(), TEST_USER_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(createAccountDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 존재하는 계좌 이름입니다");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("계좌 생성 시 isDefault는 false로 설정됨")
        void createAccount_IsDefaultSetToFalse() {
            when(accountRepository.existsByNameAndUserId(any(), any())).thenReturn(false);
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            accountService.createAccount(createAccountDto);

            verify(accountRepository)
                    .save(
                            argThat(
                                    account ->
                                            !account.getIsDefault()
                                                    && account.getUserId().equals(TEST_USER_ID)));
        }
    }

    @Nested
    @DisplayName("전체 계좌 조회 테스트")
    class GetAllAccountsTests {

        @Test
        @DisplayName("현재 사용자의 계좌만 반환됨")
        void getAllAccounts_ReturnsOnlyCurrentUserAccounts() {
            List<Account> userAccounts = Arrays.asList(testAccount, defaultAccount);
            when(accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(TEST_USER_ID))
                    .thenReturn(userAccounts);

            List<AccountDto> result = accountService.getAllAccounts();

            assertThat(result).hasSize(2);
            verify(accountRepository).findByUserIdOrderByIsDefaultDescCreatedAtAsc(TEST_USER_ID);
        }

        @Test
        @DisplayName("계좌가 없는 경우 빈 리스트 반환")
        void getAllAccounts_ReturnsEmptyListWhenNoAccounts() {
            when(accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(TEST_USER_ID))
                    .thenReturn(Collections.emptyList());

            List<AccountDto> result = accountService.getAllAccounts();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("계좌 조회 테스트")
    class GetAccountTests {

        @Test
        @DisplayName("계좌 조회 성공 - 사용자가 소유한 계좌")
        void getAccount_Success_WhenUserOwnsAccount() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            AccountDto result = accountService.getAccount(1L);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("테스트 계좌");
            assertThat(result.getAccountType()).isEqualTo(AccountType.GENERAL);
            verify(accountRepository).findById(1L);
        }

        @Test
        @DisplayName("계좌 조회 실패 - 사용자가 소유하지 않은 계좌")
        void getAccount_ThrowsUnauthorized_WhenUserDoesNotOwnAccount() {
            Account otherUserAccount =
                    Account.builder()
                            .id(3L)
                            .name("다른 사용자 계좌")
                            .accountType(AccountType.GENERAL)
                            .userId(OTHER_USER_ID)
                            .build();

            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherUserAccount));

            assertThatThrownBy(() -> accountService.getAccount(3L))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("does not have access to Account");
        }

        @Test
        @DisplayName("계좌 조회 실패 - 계좌가 존재하지 않음")
        void getAccount_ThrowsNotFound_WhenAccountDoesNotExist() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccount(999L))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("계좌를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("기본 계좌 조회 테스트")
    class GetDefaultAccountTests {

        @Test
        @DisplayName("기본 계좌 ID 조회 성공")
        void getDefaultAccountId_Success() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.of(defaultAccount));

            Long result = accountService.getDefaultAccountId();

            assertThat(result).isEqualTo(2L);
            verify(accountRepository).findByUserIdAndIsDefaultTrue(TEST_USER_ID);
        }

        @Test
        @DisplayName("기본 계좌 ID 조회 실패 - 기본 계좌가 없음")
        void getDefaultAccountId_ThrowsWhenNoDefaultAccount() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getDefaultAccountId())
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("기본 계좌가 설정되지 않았습니다");
        }

        @Test
        @DisplayName("기본 계좌 엔티티 조회 성공")
        void getDefaultAccount_Success() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.of(defaultAccount));

            Account result = accountService.getDefaultAccount();

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(2L);
            assertThat(result.getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("기본 계좌 엔티티 조회 실패 - 기본 계좌가 없음")
        void getDefaultAccount_ThrowsWhenNoDefaultAccount() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getDefaultAccount())
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("기본 계좌가 설정되지 않았습니다");
        }
    }

    @Nested
    @DisplayName("계좌 엔티티 조회 테스트")
    class GetAccountEntityTests {

        @Test
        @DisplayName("계좌 엔티티 조회 성공")
        void getAccountEntity_Success() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Account result = accountService.getAccountEntity(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("테스트 계좌");
        }

        @Test
        @DisplayName("계좌 엔티티 조회 실패 - 소유권 없음")
        void getAccountEntity_ThrowsUnauthorized() {
            Account otherUserAccount =
                    Account.builder()
                            .id(3L)
                            .name("다른 사용자 계좌")
                            .accountType(AccountType.GENERAL)
                            .userId(OTHER_USER_ID)
                            .build();

            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherUserAccount));

            assertThatThrownBy(() -> accountService.getAccountEntity(3L))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    @Nested
    @DisplayName("계좌 수정 테스트")
    class UpdateAccountTests {

        @Test
        @DisplayName("계좌 수정 성공 - 사용자가 소유한 계좌")
        void updateAccount_Success_WhenUserOwnsAccount() {
            AccountDto updateDto =
                    AccountDto.builder()
                            .name("수정된 계좌")
                            .accountType(AccountType.PENSION)
                            .description("수정된 설명")
                            .build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.existsByNameAndUserId("수정된 계좌", TEST_USER_ID)).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            AccountDto result = accountService.updateAccount(1L, updateDto);

            assertThat(result).isNotNull();
            verify(accountRepository).findById(1L);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("계좌 수정 실패 - 사용자가 소유하지 않은 계좌")
        void updateAccount_ThrowsUnauthorized_WhenUserDoesNotOwnAccount() {
            Account otherUserAccount =
                    Account.builder()
                            .id(3L)
                            .name("다른 사용자 계좌")
                            .accountType(AccountType.GENERAL)
                            .userId(OTHER_USER_ID)
                            .build();

            AccountDto updateDto =
                    AccountDto.builder().name("수정 시도").accountType(AccountType.GENERAL).build();

            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherUserAccount));

            assertThatThrownBy(() -> accountService.updateAccount(3L, updateDto))
                    .isInstanceOf(UnauthorizedAccessException.class);

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("계좌 수정 실패 - 중복된 이름")
        void updateAccount_ThrowsWhenNameAlreadyExists() {
            AccountDto updateDto =
                    AccountDto.builder().name("중복된 이름").accountType(AccountType.GENERAL).build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.existsByNameAndUserId("중복된 이름", TEST_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> accountService.updateAccount(1L, updateDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 존재하는 계좌 이름입니다");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("계좌 수정 성공 - 같은 이름으로 수정")
        void updateAccount_Success_WhenSameName() {
            AccountDto updateDto =
                    AccountDto.builder()
                            .name("테스트 계좌") // 기존과 같은 이름
                            .accountType(AccountType.PENSION)
                            .description("수정된 설명")
                            .build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            AccountDto result = accountService.updateAccount(1L, updateDto);

            assertThat(result).isNotNull();
            verify(accountRepository).save(any(Account.class));
            // 같은 이름이므로 중복 체크하지 않음
            verify(accountRepository, never()).existsByNameAndUserId(any(), any());
        }
    }

    @Nested
    @DisplayName("계좌 삭제 테스트")
    class DeleteAccountTests {

        @Test
        @DisplayName("계좌 삭제 성공 - 사용자가 소유한 계좌")
        void deleteAccount_Success_WhenUserOwnsAccount() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(portfolioRepository.findByAccountId(1L)).thenReturn(Collections.emptyList());

            accountService.deleteAccount(1L);

            verify(accountRepository).findById(1L);
            verify(portfolioRepository).findByAccountId(1L);
            verify(accountRepository).delete(testAccount);
        }

        @Test
        @DisplayName("계좌 삭제 실패 - 기본 계좌는 삭제 불가")
        void deleteAccount_ThrowsWhenDeletingDefaultAccount() {
            when(accountRepository.findById(2L)).thenReturn(Optional.of(defaultAccount));

            assertThatThrownBy(() -> accountService.deleteAccount(2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("기본 계좌는 삭제할 수 없습니다");

            verify(accountRepository, never()).delete(any());
        }

        @Test
        @DisplayName("계좌 삭제 실패 - 포트폴리오가 존재함")
        void deleteAccount_ThrowsWhenPortfolioExists() {
            Portfolio mockPortfolio = Portfolio.builder().id(1L).build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(portfolioRepository.findByAccountId(1L))
                    .thenReturn(Collections.singletonList(mockPortfolio)); // 포트폴리오가 존재

            assertThatThrownBy(() -> accountService.deleteAccount(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("포트폴리오가 있는 계좌는 삭제할 수 없습니다");

            verify(accountRepository, never()).delete(any());
        }

        @Test
        @DisplayName("계좌 삭제 실패 - 사용자가 소유하지 않은 계좌")
        void deleteAccount_ThrowsUnauthorized_WhenUserDoesNotOwnAccount() {
            Account otherUserAccount =
                    Account.builder()
                            .id(3L)
                            .name("다른 사용자 계좌")
                            .accountType(AccountType.GENERAL)
                            .userId(OTHER_USER_ID)
                            .isDefault(false)
                            .build();

            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherUserAccount));

            assertThatThrownBy(() -> accountService.deleteAccount(3L))
                    .isInstanceOf(UnauthorizedAccessException.class);

            verify(accountRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("기본 계좌 설정 테스트")
    class SetDefaultAccountTests {

        @Test
        @DisplayName("기본 계좌 설정 성공 - 기존 기본 계좌 해제됨")
        void setDefaultAccount_Success_UnsetsExistingDefault() {
            Account existingDefault =
                    Account.builder()
                            .id(4L)
                            .name("기존 기본 계좌")
                            .accountType(AccountType.GENERAL)
                            .isDefault(true)
                            .userId(TEST_USER_ID)
                            .build();

            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.of(existingDefault));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AccountDto result = accountService.setDefaultAccount(1L);

            assertThat(result).isNotNull();
            verify(accountRepository, times(2)).save(any(Account.class));
            assertThat(existingDefault.getIsDefault()).isFalse();
        }

        @Test
        @DisplayName("기본 계좌 설정 성공 - 기존 기본 계좌가 없음")
        void setDefaultAccount_Success_WhenNoExistingDefault() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.empty());
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AccountDto result = accountService.setDefaultAccount(1L);

            assertThat(result).isNotNull();
            verify(accountRepository, times(1)).save(any(Account.class));
        }

        @Test
        @DisplayName("기본 계좌 설정 실패 - 사용자가 소유하지 않은 계좌")
        void setDefaultAccount_ThrowsUnauthorized_WhenUserDoesNotOwnAccount() {
            Account otherUserAccount =
                    Account.builder()
                            .id(3L)
                            .name("다른 사용자 계좌")
                            .accountType(AccountType.GENERAL)
                            .userId(OTHER_USER_ID)
                            .build();

            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.empty());
            when(accountRepository.findById(3L)).thenReturn(Optional.of(otherUserAccount));

            assertThatThrownBy(() -> accountService.setDefaultAccount(3L))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }

        @Test
        @DisplayName("기본 계좌 설정 실패 - 계좌가 존재하지 않음")
        void setDefaultAccount_ThrowsNotFound_WhenAccountDoesNotExist() {
            when(accountRepository.findByUserIdAndIsDefaultTrue(TEST_USER_ID))
                    .thenReturn(Optional.empty());
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.setDefaultAccount(999L))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("기본 계좌 자동 생성 테스트")
    class EnsureDefaultAccountTests {

        @Test
        @DisplayName("기본 계좌가 이미 존재하면 반환")
        void ensureDefaultAccount_ReturnsExisting_WhenDefaultExists() {
            when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultAccount));

            Account result = accountService.ensureDefaultAccount();

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(2L);
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("기본 계좌가 없으면 자동 생성")
        void ensureDefaultAccount_CreatesNew_WhenNoDefaultExists() {
            Account newDefaultAccount =
                    Account.builder()
                            .id(5L)
                            .name("기본 계좌")
                            .accountType(AccountType.GENERAL)
                            .description("자동 생성된 기본 계좌")
                            .isDefault(true)
                            .build();

            when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenReturn(newDefaultAccount);

            Account result = accountService.ensureDefaultAccount();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("기본 계좌");
            assertThat(result.getIsDefault()).isTrue();
            verify(accountRepository).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("DTO 변환 테스트")
    class ConvertToDtoTests {

        @Test
        @DisplayName("DTO 변환 시 모든 필드가 올바르게 매핑됨")
        void convertToDto_AllFieldsMappedCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            Account accountWithAllFields =
                    Account.builder()
                            .id(1L)
                            .name("완전한 계좌")
                            .accountType(AccountType.PENSION)
                            .description("모든 필드 포함")
                            .isDefault(true)
                            .userId(TEST_USER_ID)
                            .build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(accountWithAllFields));

            AccountDto result = accountService.getAccount(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("완전한 계좌");
            assertThat(result.getAccountType()).isEqualTo(AccountType.PENSION);
            assertThat(result.getDescription()).isEqualTo("모든 필드 포함");
            assertThat(result.getIsDefault()).isTrue();
        }
    }
}
