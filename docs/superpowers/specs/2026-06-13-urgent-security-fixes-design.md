# 긴급 보안/버그 수정 3건 — 설계 문서

**작성일**: 2026-06-13
**상태**: 사용자 승인 대기

## 배경

전체 코드베이스 스캔(아키텍처/성능/보안/테스트 4개 영역)에서 실제 동작에 영향을 주는
긴급 이슈 3건이 확인되었다. 이 문서는 그 3건의 수정 설계를 정의한다.

1. 트랜잭션 목록 API가 전체 유저의 거래를 노출 (Critical, 접근제어 누락)
2. GoalService 캐시 이름 불일치로 목표 기능이 런타임 예외로 실패 (High, 버그)
3. WebSocket `/ws/alerts` 무인증 + 전체 브로드캐스트 (High, 보안)

## 1. 트랜잭션 목록 유저 스코핑

### 문제

`TransactionService`의 목록 조회 메서드들이 유저 필터 없이 전체 테이블을 조회한다.

- `getAllTransactions()` (페이징/리스트 양쪽) → `findAll` / `findAllWithStock`
- `getTransactionsBySymbol(symbol)` → `findBySymbolWithStock`
- `getTransactionsByDateRange(start, end)` → `findByDateRange`
- `getTransactionsByAccount*(accountId, ...)` → accountId 소유권 검증 없음

단건 조회(`getTransactionById`/`update`/`delete`)는 `validateTransactionOwnership`으로
이미 보호되어 있다. 참고: 감사에서 제기된 "업데이트 시 타 유저 계좌로 재배정" 우려는
오탐 — `AccountService.getAccountEntity()`가 내부에서 `validateOwnership()`을 호출한다.

### 설계

**리포지토리** (`TransactionRepository`): 유저 스코프 쿼리 4개 추가. 기존 쿼리는
다른 서비스(통계 등)가 사용 중이므로 변경하지 않는다.

```java
// 공통 WHERE 절: (t.account IS NULL OR t.account.userId = :userId)
Page<Transaction> findByUserId(Long userId, Pageable pageable);
List<Transaction> findAllWithStockByUserId(Long userId);
List<Transaction> findBySymbolWithStockAndUserId(String symbol, Long userId);
List<Transaction> findByDateRangeAndUserId(LocalDateTime start, LocalDateTime end, Long userId);
```

레거시 `account IS NULL` 행은 포함한다 — 단건 조회의 `validateTransactionOwnership`이
계좌 없는 거래를 허용하는 것과 동일한 정책 (마이그레이션 전 데이터 접근 보장).

**서비스** (`TransactionService`):

- 목록 메서드 4개가 `securityContextService.getCurrentUserId()`로 현재 유저를 받아
  스코프 쿼리를 사용한다. 미인증(userId 없음)이면 `UnauthorizedAccessException`.
- `getTransactionsByAccount*` 계열은 쿼리 전에 `accountService.getAccountEntity(accountId)`를
  호출한다 (소유권 검증 내장 — 타 유저 계좌면 `UnauthorizedAccessException` 발생).

**컨트롤러**: 변경 없음 (시그니처 동일).

## 2. Goal 캐시 이름 불일치

### 문제

`GoalService`는 `@Cacheable`/`@CacheEvict`에 `"goalSummary"`를 사용하지만,
`RedisConfig`에는 `"goals"`만 등록되어 있다. 기본 설정(`REDIS_ENABLED:false`)의
`SimpleCacheManager`는 고정 캐시 목록을 사용하므로, 목표 요약 조회와
생성/수정/삭제가 전부 `Cannot find cache named 'goalSummary'`로 실패한다.

### 설계

`RedisConfig.java:46`의 상수 값을 변경한다:

```java
private static final String CACHE_GOALS = "goalSummary";
```

Simple/Redis 캐시 매니저 모두 이 상수를 사용하므로 한 줄 수정으로 양쪽이 해결된다.
`"goals"` 문자열은 캐시 용도로 다른 곳에서 사용되지 않음을 확인했다
(엔티티 테이블명과 export 파일명은 무관).

## 3. WebSocket JWT 인증 + 유저별 라우팅

### 문제

`/ws/alerts`는 인증 없이 연결 가능하고(`SecurityConfig`는 `/api/**`만 보호),
`AlertBroadcastService.broadcastPriceAlert()`가 모든 세션에 전송하므로
타 유저의 가격 알림(userId, 종목, 가격 포함)을 누구나 수신할 수 있다.
현재 프런트엔드에 WS 클라이언트 코드는 없으므로 호환성 제약은 없다.

### 설계

**`JwtHandshakeInterceptor`** (신규, `websocket` 패키지):

- `HandshakeInterceptor` 구현. 쿼리 파라미터 `?token=<JWT>`에서 토큰을 읽는다
  (브라우저 WebSocket API는 커스텀 헤더를 지원하지 않으므로 쿼리 파라미터 사용).
- `JwtTokenProvider.validateToken()` 검증 → `getUsernameFromToken()` →
  `UserRepository.findByUsername()`으로 userId 해석.
- 성공 시 `attributes.put("userId", userId)` 후 핸드셰이크 허용, 실패 시 거부(`false` 반환).
- 주의: 토큰이 URL에 노출되므로 액세스 로그에 쿼리스트링이 기록되지 않도록 유의
  (현재 액세스 로그 미사용 — 향후 도입 시 마스킹 필요. 스펙 범위 외).

**`WebSocketConfig`**: `addInterceptors(jwtHandshakeInterceptor)` 등록.

**`WebSocketSessionRegistry`**:

- `sendToUser(Long userId, String message)` 추가 — 세션 attribute의 userId가
  일치하는 열린 세션에만 전송. (세션 수가 적으므로 필터 순회로 충분, 별도 인덱스 불필요)
- `broadcast()`는 유지 — 이제 인증된 세션만 존재하므로 시스템 공지 용도로 안전.

**`AlertBroadcastService`**:

- `broadcastPriceAlert(alert)` → `sessionRegistry.sendToUser(alert.getUserId(), json)`.
- `broadcastSystemAlert`/`broadcastCustomMessage`는 전체 방송 유지.
- 메시지 페이로드에서 `activeConnections` 등 불필요한 내부 정보는 유지하되,
  타 유저에게 전송되지 않으므로 userId 포함은 문제없음.

## 에러 처리

- 미인증 목록 조회: `UnauthorizedAccessException` → 기존 `GlobalExceptionHandler` 매핑 사용.
- 타 유저 계좌 조회: `AccountService.validateOwnership`의 기존 예외 흐름 그대로.
- WS 핸드셰이크 실패: 인터셉터가 `false` 반환 → 연결 거부 (별도 에러 바디 없음, 표준 동작).

## 테스트 계획 (TDD)

1. **TransactionService**: 기존 `TransactionServiceTest`에 추가 —
   목록 메서드가 현재 유저 ID로 스코프 쿼리를 호출하는지(Mockito verify),
   미인증 시 예외, 계좌 메서드가 `getAccountEntity`로 소유권을 검증하는지.
2. **JwtHandshakeInterceptor**: 단위 테스트 — 유효 토큰이면 attributes에 userId 저장 후
   허용, 토큰 없음/무효/유저 없음이면 거부.
3. **WebSocketSessionRegistry**: 단위 테스트 — `sendToUser`가 해당 userId 세션에만
   전송하는지 (mock `WebSocketSession` + attributes).
4. **Goal 캐시**: `@SpringBootTest` 또는 기존 컨텍스트 테스트에서 `CacheManager`에
   `goalSummary` 캐시가 존재하는지 단언 (회귀 방지).

## 범위 외 (후속 작업 후보)

- **GoalService 유저 스코핑 부재**: Goal 엔티티에 user 필드 자체가 없어 전 유저가 목표를
  공유하고, 캐시 키도 `'summary'` 단일 키. 데이터 모델 변경(마이그레이션) 필요 — 별도 작업.
- **분석/통계 서비스의 전역 조회**: `TradingStatisticsService` 등이 `findAllWithStock()`으로
  전 유저 데이터를 집계 — 분석 엔드포인트에도 같은 클래스의 누출 존재. 별도 작업.
- 감사에서 나온 나머지 항목들(예외 메시지 노출, CORS, JWT type 클레임, docker-compose
  기본 패스워드 등)은 우선순위 목록에 유지.

## 성공 기준

- [ ] 다른 유저로 로그인 시 `GET /api/transactions`(전 계열)에서 내 거래만 반환
- [ ] 타 유저 accountId로 `/api/transactions/account/{id}` 호출 시 403/401
- [ ] 목표 페이지 CRUD/요약이 예외 없이 동작
- [ ] 토큰 없이 `/ws/alerts` 연결 시 핸드셰이크 거부
- [ ] 가격 알림이 알림 소유자 세션에만 전송
- [ ] `./gradlew check` 통과 (Spotless, JaCoCo 포함)
