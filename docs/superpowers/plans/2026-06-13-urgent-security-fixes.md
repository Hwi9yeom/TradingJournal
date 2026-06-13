# 긴급 보안/버그 수정 3건 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 트랜잭션 목록 API의 유저 간 데이터 노출, Goal 캐시 이름 불일치 런타임 버그, WebSocket 무인증 브로드캐스트 — 3건의 긴급 이슈를 수정한다.

**Architecture:** (1) `TransactionRepository`에 유저 스코프 쿼리를 추가하고 `TransactionService` 목록 메서드를 현재 유저로 필터링한다. (2) `RedisConfig` 캐시 상수 한 줄을 고쳐 Goal 캐시 이름을 일치시킨다. (3) `JwtHandshakeInterceptor`로 WebSocket 핸드셰이크에서 JWT를 검증하고 세션에 userId를 바인딩한 뒤, 가격 알림을 소유자 세션에만 전송한다.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, Spring WebSocket, JUnit 5, Mockito, AssertJ, Gradle.

스펙: `docs/superpowers/specs/2026-06-13-urgent-security-fixes-design.md`

빌드/테스트 명령은 `./gradlew test --tests "<FQCN>"` 형태를 사용한다. 단일 테스트 메서드는 `--tests "<FQCN>.<method>"`.

---

## Task 1: Goal 캐시 이름 불일치 수정

가장 작고 독립적인 버그부터 처리한다. `GoalService`는 `"goalSummary"` 캐시를 쓰지만 `RedisConfig`는 `"goals"`만 등록해 런타임 예외가 발생한다.

**Files:**
- Modify: `src/main/java/com/trading/journal/config/RedisConfig.java:46`
- Test: `src/test/java/com/trading/journal/config/GoalCacheConfigTest.java` (Create)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/trading/journal/config/GoalCacheConfigTest.java` 생성:

```java
package com.trading.journal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest
@DisplayName("Goal 캐시 설정")
class GoalCacheConfigTest {

    @Autowired private CacheManager cacheManager;

    @Test
    @DisplayName("GoalService가 사용하는 goalSummary 캐시가 등록되어 있어야 한다")
    void goalSummaryCacheIsRegistered() {
        assertThat(cacheManager.getCache("goalSummary")).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.trading.journal.config.GoalCacheConfigTest"`
Expected: FAIL — `getCache("goalSummary")`가 null (현재 `"goals"`로 등록됨).

- [ ] **Step 3: Apply the fix**

`src/main/java/com/trading/journal/config/RedisConfig.java:46`을 수정한다:

```java
    private static final String CACHE_GOALS = "goalSummary";
```

(상수명 `CACHE_GOALS`는 그대로 두고 값만 `"goals"` → `"goalSummary"`로 변경. Simple/Redis 캐시 매니저가 모두 이 상수를 참조하므로 한 줄로 양쪽 해결.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.trading.journal.config.GoalCacheConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/trading/journal/config/RedisConfig.java src/test/java/com/trading/journal/config/GoalCacheConfigTest.java
git commit -m "fix: align Goal cache name to goalSummary"
```

---

## Task 2: TransactionRepository 유저 스코프 쿼리 추가

`TransactionService`가 사용할 유저 스코프 쿼리 4개를 리포지토리에 추가한다. 기존 쿼리는 다른 서비스가 사용하므로 변경하지 않는다.

**Files:**
- Modify: `src/main/java/com/trading/journal/repository/TransactionRepository.java`
- Test: `src/test/java/com/trading/journal/repository/TransactionRepositoryUserScopeTest.java` (Create)

WHERE 절 공통 정책: `(t.account IS NULL OR t.account.userId = :userId)` — 단건 조회의 `validateTransactionOwnership`이 계좌 없는 레거시 거래를 허용하는 것과 동일.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/trading/journal/repository/TransactionRepositoryUserScopeTest.java` 생성:

```java
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
        Account user1Account = accountRepository.save(
                Account.builder().name("u1").accountType(AccountType.GENERAL)
                        .isDefault(true).userId(1L).build());
        Account user2Account = accountRepository.save(
                Account.builder().name("u2").accountType(AccountType.GENERAL)
                        .isDefault(true).userId(2L).build());
        saveTx(user1Account, "100.00", LocalDateTime.now().minusDays(1));
        saveTx(user2Account, "200.00", LocalDateTime.now().minusDays(1));
    }

    private void saveTx(Account account, String price, LocalDateTime date) {
        transactionRepository.save(
                Transaction.builder().account(account).stock(stock)
                        .type(TransactionType.BUY).quantity(new BigDecimal("10"))
                        .price(new BigDecimal(price)).commission(BigDecimal.ZERO)
                        .transactionDate(date).build());
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
        List<Transaction> result =
                transactionRepository.findBySymbolWithStockAndUserId("AAPL", 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccount().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findByDateRangeAndUserId는 유저+기간으로 필터한다")
    void findByDateRangeAndUserId_scopesToUser() {
        List<Transaction> result = transactionRepository.findByDateRangeAndUserId(
                LocalDateTime.now().minusDays(2), LocalDateTime.now(), 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccount().getUserId()).isEqualTo(1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.trading.journal.repository.TransactionRepositoryUserScopeTest"`
Expected: FAIL (컴파일 에러) — `findAllWithStockByUserId` 등 메서드 미존재.

- [ ] **Step 3: Add the repository queries**

`src/main/java/com/trading/journal/repository/TransactionRepository.java`의 `findByAccountIsNull()` 메서드(라인 67 부근) 바로 다음에 추가한다:

```java
    // ===== 유저 스코프 쿼리 (목록 조회 접근제어) =====

    @Query(
            "SELECT t FROM Transaction t LEFT JOIN FETCH t.stock LEFT JOIN FETCH t.account "
                    + "WHERE (t.account IS NULL OR t.account.userId = :userId) "
                    + "ORDER BY t.transactionDate DESC")
    Page<Transaction> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(
            "SELECT t FROM Transaction t LEFT JOIN FETCH t.stock LEFT JOIN FETCH t.account "
                    + "WHERE (t.account IS NULL OR t.account.userId = :userId) "
                    + "ORDER BY t.transactionDate DESC")
    List<Transaction> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock LEFT JOIN FETCH t.account "
                    + "WHERE t.stock.symbol = :symbol "
                    + "AND (t.account IS NULL OR t.account.userId = :userId) "
                    + "ORDER BY t.transactionDate DESC")
    List<Transaction> findBySymbolWithStockAndUserId(
            @Param("symbol") String symbol, @Param("userId") Long userId);

    @Query(
            "SELECT t FROM Transaction t JOIN FETCH t.stock LEFT JOIN FETCH t.account "
                    + "WHERE t.transactionDate BETWEEN :startDate AND :endDate "
                    + "AND (t.account IS NULL OR t.account.userId = :userId) "
                    + "ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRangeAndUserId(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userId") Long userId);
```

참고: `findByUserId`의 페이징 쿼리는 `JOIN FETCH` + `Pageable` 조합이다. 컬렉션이 아닌 `@ManyToOne`(stock/account) fetch이므로 Hibernate가 메모리 페이징 경고 없이 처리한다 — 정상.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.trading.journal.repository.TransactionRepositoryUserScopeTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/trading/journal/repository/TransactionRepository.java src/test/java/com/trading/journal/repository/TransactionRepositoryUserScopeTest.java
git commit -m "feat: add user-scoped transaction queries"
```

---

## Task 3: TransactionService 목록 메서드 유저 스코핑

목록 조회 메서드들이 현재 유저로 필터링하도록 수정한다. 미인증이면 `UnauthorizedAccessException`. 계좌 단위 메서드는 `accountService.getAccountEntity()`로 소유권을 검증한다.

**Files:**
- Modify: `src/main/java/com/trading/journal/service/TransactionService.java`
- Test: `src/test/java/com/trading/journal/service/TransactionServiceTest.java` (기존 파일에 추가)

먼저 `UnauthorizedAccessException` 생성자 시그니처를 확인한다.

- [ ] **Step 0: Confirm exception constructor**

Run: `grep -n "public UnauthorizedAccessException" src/main/java/com/trading/journal/exception/UnauthorizedAccessException.java`
Expected: `UnauthorizedAccessException(String resourceType, Long resourceId, String username)` 생성자 존재 (TransactionService에서 이미 사용 중). 미인증용으로 단일 String 메시지 생성자가 없으면 Step 3에서 기존 3-arg 형태(`"Transaction", null, username`)를 사용한다.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/trading/journal/service/TransactionServiceTest.java`의 클래스 마지막 `}` 직전에 추가한다 (필요 import: `org.springframework.data.domain.Page`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Pageable`):

```java
    @Test
    @DisplayName("getAllTransactions(리스트)는 현재 유저 ID로 스코프 쿼리를 호출한다")
    void getAllTransactionsList_scopesToCurrentUser() {
        when(transactionRepository.findAllWithStockByUserId(100L))
                .thenReturn(List.of(mockTransaction));

        List<TransactionDto> result = transactionService.getAllTransactions();

        assertThat(result).hasSize(1);
        verify(transactionRepository).findAllWithStockByUserId(100L);
        verify(transactionRepository, never()).findAllWithStock();
    }

    @Test
    @DisplayName("getAllTransactions(페이징)는 현재 유저 ID로 스코프 쿼리를 호출한다")
    void getAllTransactionsPaged_scopesToCurrentUser() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByUserId(100L, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(mockTransaction)));

        Page<TransactionDto> result = transactionService.getAllTransactions(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(transactionRepository).findByUserId(100L, pageable);
    }

    @Test
    @DisplayName("getTransactionsBySymbol은 현재 유저 ID로 스코프 쿼리를 호출한다")
    void getTransactionsBySymbol_scopesToCurrentUser() {
        when(transactionRepository.findBySymbolWithStockAndUserId("AAPL", 100L))
                .thenReturn(List.of(mockTransaction));

        List<TransactionDto> result = transactionService.getTransactionsBySymbol("AAPL");

        assertThat(result).hasSize(1);
        verify(transactionRepository).findBySymbolWithStockAndUserId("AAPL", 100L);
    }

    @Test
    @DisplayName("getTransactionsByDateRange은 현재 유저 ID로 스코프 쿼리를 호출한다")
    void getTransactionsByDateRange_scopesToCurrentUser() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(transactionRepository.findByDateRangeAndUserId(start, end, 100L))
                .thenReturn(List.of(mockTransaction));

        List<TransactionDto> result = transactionService.getTransactionsByDateRange(start, end);

        assertThat(result).hasSize(1);
        verify(transactionRepository).findByDateRangeAndUserId(start, end, 100L);
    }

    @Test
    @DisplayName("미인증 상태에서 목록 조회 시 UnauthorizedAccessException")
    void getAllTransactionsList_unauthenticated_throws() {
        when(securityContextService.getCurrentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getAllTransactions())
                .isInstanceOf(com.trading.journal.exception.UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("getTransactionsByAccount은 계좌 소유권을 검증한다")
    void getTransactionsByAccount_validatesOwnership() {
        when(accountService.getAccountEntity(1L)).thenReturn(mockAccount);
        when(transactionRepository.findByAccountIdWithStock(1L))
                .thenReturn(List.of(mockTransaction));

        List<TransactionDto> result = transactionService.getTransactionsByAccount(1L);

        assertThat(result).hasSize(1);
        verify(accountService).getAccountEntity(1L);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.trading.journal.service.TransactionServiceTest"`
Expected: FAIL (컴파일 에러 또는 기존 stub 미스매치) — 서비스가 아직 스코프 쿼리를 호출하지 않음.

- [ ] **Step 3: Update the service methods**

`src/main/java/com/trading/journal/service/TransactionService.java`에서 아래 메서드들을 교체한다.

먼저 현재 유저 ID 헬퍼를 클래스 하단(`validateTransactionOwnership` 메서드 위)에 추가:

```java
    /** 현재 인증된 유저 ID 반환. 미인증이면 예외. */
    private Long requireCurrentUserId() {
        return securityContextService
                .getCurrentUserId()
                .orElseThrow(
                        () ->
                                new UnauthorizedAccessException(
                                        "Transaction",
                                        null,
                                        securityContextService
                                                .getCurrentUsername()
                                                .orElse("anonymous")));
    }
```

`getAllTransactions(Pageable)` (라인 97-100) 교체:

```java
    @Transactional(readOnly = true)
    public Page<TransactionDto> getAllTransactions(Pageable pageable) {
        Long userId = requireCurrentUserId();
        return transactionRepository.findByUserId(userId, pageable).map(this::convertToDto);
    }
```

`getAllTransactions()` (라인 102-107) 교체:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getAllTransactions() {
        Long userId = requireCurrentUserId();
        return transactionRepository.findAllWithStockByUserId(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

`getTransactionsByAccount(Long accountId, Pageable)` (라인 109-112) 교체 — 소유권 검증 추가:

```java
    @Transactional(readOnly = true)
    public Page<TransactionDto> getTransactionsByAccount(Long accountId, Pageable pageable) {
        accountService.getAccountEntity(accountId); // 소유권 검증
        return transactionRepository.findByAccountId(accountId, pageable).map(this::convertToDto);
    }
```

`getTransactionsByAccount(Long accountId)` (라인 114-119) 교체:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByAccount(Long accountId) {
        accountService.getAccountEntity(accountId); // 소유권 검증
        return transactionRepository.findByAccountIdWithStock(accountId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

`getTransactionsByAccountAndSymbol(Long accountId, String symbol)` (라인 121-126) 교체:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByAccountAndSymbol(Long accountId, String symbol) {
        accountService.getAccountEntity(accountId); // 소유권 검증
        return transactionRepository.findByAccountIdAndSymbol(accountId, symbol).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

`getTransactionsBySymbol(String symbol)` (라인 128-133) 교체:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsBySymbol(String symbol) {
        Long userId = requireCurrentUserId();
        return transactionRepository.findBySymbolWithStockAndUserId(symbol, userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

`getTransactionsByDateRange(...)` (라인 135-141) 교체:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByDateRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        Long userId = requireCurrentUserId();
        return transactionRepository.findByDateRangeAndUserId(startDate, endDate, userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

`getTransactionsByAccountAndDateRange(...)` (라인 143-151) 교체 — 소유권 검증 추가:

```java
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByAccountAndDateRange(
            Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        accountService.getAccountEntity(accountId); // 소유권 검증
        return transactionRepository
                .findByAccountIdAndDateRange(accountId, startDate, endDate)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
```

- [ ] **Step 4: Run the full TransactionServiceTest**

Run: `./gradlew test --tests "com.trading.journal.service.TransactionServiceTest"`
Expected: PASS (신규 6 + 기존 테스트 전부). 기존 테스트가 `findAllWithStock` 등을 stub했다면 깨질 수 있으니 깨진 테스트는 새 스코프 메서드 stub으로 수정한다.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/trading/journal/service/TransactionService.java src/test/java/com/trading/journal/service/TransactionServiceTest.java
git commit -m "fix: scope transaction list queries to current user"
```

---

## Task 4: WebSocketSessionRegistry 유저별 전송

세션에 바인딩된 userId로 특정 유저에게만 전송하는 `sendToUser`를 추가한다. 핸드셰이크 인터셉터(Task 5)가 `attributes`에 userId를 넣어주므로 여기서는 그 값을 읽는다.

**Files:**
- Modify: `src/main/java/com/trading/journal/websocket/WebSocketSessionRegistry.java`
- Test: `src/test/java/com/trading/journal/websocket/WebSocketSessionRegistryTest.java` (Create)

세션의 userId는 `session.getAttributes().get("userId")`로 접근한다 (Spring이 핸드셰이크 attributes를 세션 attributes로 복사).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/trading/journal/websocket/WebSocketSessionRegistryTest.java` 생성:

```java
package com.trading.journal.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("WebSocketSessionRegistry 유저별 전송")
class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    private WebSocketSession mockSession(String id, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }

    @Test
    @DisplayName("sendToUser는 해당 userId 세션에만 전송한다")
    void sendToUser_onlyToMatchingUser() throws Exception {
        WebSocketSession s1 = mockSession("s1", 1L);
        WebSocketSession s2 = mockSession("s2", 2L);
        registry.addSession(s1);
        registry.addSession(s2);

        registry.sendToUser(1L, "hello");

        verify(s1, times(1)).sendMessage(any(TextMessage.class));
        verify(s2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendToUser는 닫힌 세션에 전송하지 않는다")
    void sendToUser_skipsClosedSession() throws Exception {
        WebSocketSession s1 = mockSession("s1", 1L);
        when(s1.isOpen()).thenReturn(false);
        registry.addSession(s1);

        registry.sendToUser(1L, "hello");

        verify(s1, never()).sendMessage(any(TextMessage.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.trading.journal.websocket.WebSocketSessionRegistryTest"`
Expected: FAIL (컴파일 에러) — `sendToUser` 미존재.

- [ ] **Step 3: Add sendToUser method**

`src/main/java/com/trading/journal/websocket/WebSocketSessionRegistry.java`의 `broadcast` 메서드 다음에 추가한다:

```java
    /** 특정 유저의 열린 세션에만 메시지를 전송한다. */
    public void sendToUser(Long userId, String message) {
        if (userId == null) {
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        sessions.values().stream()
                .filter(session -> userId.equals(session.getAttributes().get("userId")))
                .forEach(
                        session -> {
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(textMessage);
                                }
                            } catch (IOException e) {
                                log.error(
                                        "Error sending message to user {} session {}: {}",
                                        userId,
                                        session.getId(),
                                        e.getMessage());
                            }
                        });
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.trading.journal.websocket.WebSocketSessionRegistryTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/trading/journal/websocket/WebSocketSessionRegistry.java src/test/java/com/trading/journal/websocket/WebSocketSessionRegistryTest.java
git commit -m "feat: add per-user WebSocket message delivery"
```

---

## Task 5: JwtHandshakeInterceptor — WebSocket 인증

WebSocket 핸드셰이크에서 `?token=<JWT>` 쿼리 파라미터의 JWT를 검증하고 세션 attributes에 userId를 바인딩한다. 실패 시 핸드셰이크 거부.

**Files:**
- Create: `src/main/java/com/trading/journal/websocket/JwtHandshakeInterceptor.java`
- Modify: `src/main/java/com/trading/journal/config/WebSocketConfig.java`
- Test: `src/test/java/com/trading/journal/websocket/JwtHandshakeInterceptorTest.java` (Create)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/trading/journal/websocket/JwtHandshakeInterceptorTest.java` 생성:

```java
package com.trading.journal.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import com.trading.journal.security.JwtTokenProvider;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

@DisplayName("JwtHandshakeInterceptor")
class JwtHandshakeInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;
    private JwtHandshakeInterceptor interceptor;
    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userRepository = mock(UserRepository.class);
        interceptor = new JwtHandshakeInterceptor(jwtTokenProvider, userRepository);
        handler = mock(WebSocketHandler.class);
    }

    private ServerHttpRequest requestWithQuery(String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/alerts" + query));
        return request;
    }

    @Test
    @DisplayName("유효한 토큰이면 핸드셰이크를 허용하고 userId를 바인딩한다")
    void validToken_allowsAndBindsUserId() throws Exception {
        when(jwtTokenProvider.validateToken("good")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("good")).thenReturn("alice");
        User user = User.builder().id(42L).username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWithQuery("?token=good"), null, handler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(42L);
    }

    @Test
    @DisplayName("토큰이 없으면 핸드셰이크를 거부한다")
    void missingToken_rejects() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWithQuery(""), null, handler, attributes);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 핸드셰이크를 거부한다")
    void invalidToken_rejects() throws Exception {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWithQuery("?token=bad"), null, handler, attributes);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 핸드셰이크를 거부한다")
    void userNotFound_rejects() throws Exception {
        when(jwtTokenProvider.validateToken("good")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("good")).thenReturn("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWithQuery("?token=good"), null, handler, attributes);
        assertThat(result).isFalse();
    }
}
```

먼저 `User` 엔티티에 `builder()`와 `id`, `username` 필드가 있는지 확인한다:

Run: `grep -n "@Builder\|class User\|private Long id\|private String username" src/main/java/com/trading/journal/entity/User.java`
빌더가 없으면 테스트에서 setter 또는 생성자로 대체한다.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.trading.journal.websocket.JwtHandshakeInterceptorTest"`
Expected: FAIL (컴파일 에러) — `JwtHandshakeInterceptor` 미존재.

- [ ] **Step 3: Create the interceptor**

`src/main/java/com/trading/journal/websocket/JwtHandshakeInterceptor.java` 생성:

```java
package com.trading.journal.websocket;

import com.trading.journal.repository.UserRepository;
import com.trading.journal.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** WebSocket 핸드셰이크에서 JWT를 검증하고 세션에 userId를 바인딩한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("token");

        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket handshake rejected: missing or invalid token");
            return false;
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        return userRepository
                .findByUsername(username)
                .map(
                        user -> {
                            attributes.put("userId", user.getId());
                            return true;
                        })
                .orElseGet(
                        () -> {
                            log.warn("WebSocket handshake rejected: user not found");
                            return false;
                        });
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
```

- [ ] **Step 4: Register the interceptor in WebSocketConfig**

`src/main/java/com/trading/journal/config/WebSocketConfig.java` 전체를 교체한다:

```java
package com.trading.journal.config;

import com.trading.journal.websocket.AlertWebSocketHandler;
import com.trading.journal.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*");
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.trading.journal.websocket.JwtHandshakeInterceptorTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/trading/journal/websocket/JwtHandshakeInterceptor.java src/main/java/com/trading/journal/config/WebSocketConfig.java src/test/java/com/trading/journal/websocket/JwtHandshakeInterceptorTest.java
git commit -m "feat: authenticate WebSocket handshake with JWT"
```

---

## Task 6: 가격 알림을 소유자에게만 전송

`AlertBroadcastService.broadcastPriceAlert`이 전체 방송 대신 알림 소유자(userId)에게만 전송하도록 변경한다. 시스템/커스텀 메시지는 전체 방송 유지.

**Files:**
- Modify: `src/main/java/com/trading/journal/service/AlertBroadcastService.java`
- Test: `src/test/java/com/trading/journal/service/AlertBroadcastServiceTest.java` (Create)

- [ ] **Step 1: Write the failing test**

먼저 `PriceAlert` 빌더/필드를 확인한다:

Run: `grep -n "@Builder\|getUserId\|getAlertType\|getCondition\|getSymbol" src/main/java/com/trading/journal/entity/PriceAlert.java | head`

`src/test/java/com/trading/journal/service/AlertBroadcastServiceTest.java` 생성 (PriceAlert 빌더 필드는 위 grep 결과에 맞춰 조정):

```java
package com.trading.journal.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.entity.AlertCondition;
import com.trading.journal.entity.AlertType;
import com.trading.journal.entity.PriceAlert;
import com.trading.journal.websocket.WebSocketSessionRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AlertBroadcastService")
class AlertBroadcastServiceTest {

    private WebSocketSessionRegistry sessionRegistry;
    private AlertBroadcastService service;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(WebSocketSessionRegistry.class);
        service = new AlertBroadcastService(sessionRegistry, new ObjectMapper());
    }

    @Test
    @DisplayName("가격 알림은 소유자 userId에게만 전송한다")
    void broadcastPriceAlert_sendsToOwnerOnly() {
        PriceAlert alert =
                PriceAlert.builder()
                        .id(1L)
                        .userId(42L)
                        .symbol("AAPL")
                        .alertType(AlertType.PRICE_ABOVE)
                        .condition(AlertCondition.GREATER_THAN)
                        .thresholdPrice(new BigDecimal("150"))
                        .currentPrice(new BigDecimal("155"))
                        .triggeredAt(LocalDateTime.now())
                        .build();

        service.broadcastPriceAlert(alert);

        verify(sessionRegistry).sendToUser(eq(42L), anyString());
        verify(sessionRegistry, never()).broadcast(anyString());
    }
}
```

주의: `AlertType`/`AlertCondition` enum 상수명은 실제 코드에 맞춰야 한다. Step 1 실행 전 확인:
Run: `grep -rn "enum AlertType\|enum AlertCondition\|GREATER_THAN\|PRICE_ABOVE" src/main/java/com/trading/journal/entity/Alert*.java`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.trading.journal.service.AlertBroadcastServiceTest"`
Expected: FAIL — 현재 `broadcastPriceAlert`이 `sendToUser`가 아닌 `broadcast`를 호출.

- [ ] **Step 3: Update broadcastPriceAlert**

`src/main/java/com/trading/journal/service/AlertBroadcastService.java`의 `broadcastPriceAlert` 메서드에서 `sessionRegistry.broadcast(json);` 한 줄을 교체한다:

```java
            sessionRegistry.sendToUser(alert.getUserId(), json);
```

(메서드의 나머지 — 메시지 생성, 로깅 — 는 그대로 유지.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.trading.journal.service.AlertBroadcastServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/trading/journal/service/AlertBroadcastService.java src/test/java/com/trading/journal/service/AlertBroadcastServiceTest.java
git commit -m "fix: deliver price alerts only to alert owner"
```

---

## Task 7: 전체 검증

모든 변경을 합쳐 전체 빌드/품질 게이트를 통과시킨다.

- [ ] **Step 1: Run full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — 전체 테스트 통과, Spotless 포맷 검증 통과, JaCoCo 커버리지(LINE >= 0.20) 통과.

- [ ] **Step 2: Spotless 포맷 자동 적용 (필요 시)**

Spotless 실패 시:
Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit (포맷 변경이 있었다면)**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```

---

## 검증 체크리스트 (스펙 성공 기준 매핑)

- [ ] Task 3 → 다른 유저로 로그인 시 `GET /api/transactions`(전 계열)에서 내 거래만 반환
- [ ] Task 3 → 타 유저 accountId로 `/api/transactions/account/{id}` 호출 시 예외(소유권 검증)
- [ ] Task 1 → 목표 페이지 CRUD/요약이 예외 없이 동작 (`goalSummary` 캐시 등록)
- [ ] Task 5 → 토큰 없이 `/ws/alerts` 연결 시 핸드셰이크 거부
- [ ] Task 6 → 가격 알림이 알림 소유자 세션에만 전송
- [ ] Task 7 → `./gradlew check` 통과

## 범위 외 (스펙과 동일 — 후속 작업 후보)

- GoalService 자체의 유저 스코핑 부재 (Goal 엔티티에 user 필드 없음, 데이터 모델 변경 필요)
- 분석/통계 서비스의 전역 조회 누출 (`TradingStatisticsService` 등)
- 예외 메시지 노출, CORS, JWT type 클레임, docker-compose 기본 패스워드
