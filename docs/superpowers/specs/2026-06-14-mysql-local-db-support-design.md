# MySQL 로컬 DB 연결 지원 — 설계 문서

**작성일**: 2026-06-14
**상태**: 사용자 승인 (구현 진행)

## 배경

현재 DB 구성:
- 기본 프로필: **H2 파일** (`jdbc:h2:file:./data/tradingjournal`)
- 운영 프로필(`prod`): **PostgreSQL**
- 테스트: H2 in-memory (`ddl-auto=create-drop`)
- **MySQL 드라이버 없음**

스키마 관리상의 핵심 사실:
- `ddl-auto=validate` (테스트 제외) → Hibernate가 테이블을 생성하지 않음
- Flyway 마이그레이션 V1~V7이 존재하나 **핵심 테이블(users/accounts/transactions/stocks/portfolios/goals 등)을 CREATE 하는 마이그레이션이 없음** — 기존 H2 파일은 과거 `ddl-auto` 부트스트랩 상태로 추정
- 따라서 **새 MySQL DB에 붙으려면 전체 스키마 CREATE 문이 필요**

### Flyway 마이그레이션이 MySQL에서 동작하지 않는 이유 (확인됨)
- `CREATE INDEX IF NOT EXISTS` (V1, V2, V6) → MySQL 8.0 미지원
- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (V2, V6) → MySQL 미지원
- `BIGINT GENERATED ALWAYS AS IDENTITY` (V3, V4, V5) → MySQL은 `AUTO_INCREMENT`
- `CREATE INDEX ... WHERE ...` 부분 인덱스 (V5) → MySQL 미지원

→ MySQL에서는 **Flyway를 비활성화하고 단일 init 스크립트를 스키마의 단일 소스로** 사용한다.

## 목표

기존 H2/Postgres 동작을 **전혀 변경하지 않고**, 로컬 MySQL 8.0에 연결할 수 있도록 (1) 전체 스키마 DDL, (2) 전용 프로필, (3) 드라이버, (4) docker-compose 서비스를 추가한다.

## 대상 테이블 (26개 엔티티 + 1개 비-엔티티)

엔티티 테이블 (`@Table` 명시):
accounts, account_risk_settings, alerts, backtest_results, backtest_trades,
benchmark_prices, dashboard_configs, dashboard_widgets, disclosures, dividends,
economic_events, goals, historical_prices, portfolios, price_alert, saved_screen,
stocks, stock_fundamentals, strategy_templates, stress_scenario, target_allocations,
trade_plans, trade_reviews, trading_journals, transactions, users

비-엔티티 테이블 (네이티브 사용, V4에서 생성): **stress_test_result**

## 구성 요소 (모두 신규 파일, 기존 파일은 build.gradle/docker-compose만 추가)

### 1. `src/main/resources/db/mysql/schema-mysql.sql` (핵심 산출물)
- 위 27개 테이블 전체의 `CREATE TABLE`
- MySQL 8.0 문법:
  - PK: `BIGINT AUTO_INCREMENT PRIMARY KEY`
  - 테이블 옵션: `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
  - 타입 매핑: `BigDecimal(p,s)`→`DECIMAL(p,s)`, `LocalDateTime`→`DATETIME(6)`, `LocalDate`→`DATE`, `Boolean`→`BIT(1)` 또는 `TINYINT(1)`(Hibernate 기본에 맞춤), `String(len)`→`VARCHAR(len)`, `@Lob`/대용량 JSON→`LONGTEXT` 또는 `JSON`, enum(`@Enumerated(STRING)`)→`VARCHAR`
  - `@Column(columnDefinition=...)`가 있으면 그 정의를 우선
- **FK 의존성 순서**로 CREATE (참조 대상 먼저). 엔티티의 실제 `@ManyToOne`/`@JoinColumn` 관계만 FK로 반영 (V6의 `accounts.user_id`처럼 컬럼만 있고 FK 제약이 없는 경우는 FK 없이 컬럼+인덱스만)
- 인덱스: 엔티티 `@Table(indexes=...)`/`@Index` + `@Column(unique=true)` + V1 성능 인덱스 + V3~V6 인덱스를 통합 (`IF NOT EXISTS`/부분 인덱스는 MySQL 문법으로 변환·제거)
- V4 사전정의 시드 데이터: `stress_scenario` 5행 (`GFC_2008`, `COVID_2020`, `DOT_COM_2000`, `BLACK_MONDAY_1987`, `ASIAN_CRISIS_1997`)
- V5 CHECK 제약(`alert_type`, `condition`)은 MySQL 8.0에서 지원되므로 포함

### 2. `src/main/resources/application-mysql.yml` (전용 프로필)
- `spring.datasource.url`: `${DB_URL:jdbc:mysql://localhost:3306/tradingjournal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8}`
- `driver-class-name: com.mysql.cj.jdbc.Driver`
- `username: ${DB_USERNAME:tradingjournal}`, `password: ${DB_PASSWORD:}`
- `spring.jpa.hibernate.ddl-auto: ${JPA_DDL_AUTO:validate}`
- `spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.MySQLDialect`
- `spring.flyway.enabled: false`
- `spring.h2.console.enabled: false`
- Redis 등 나머지 인프라 설정은 기본(`application.yml`)을 그대로 상속

### 3. `build.gradle`
- `runtimeOnly 'com.mysql:mysql-connector-j'` 추가 (기존 H2/Postgres 의존성 유지)

### 4. `docker-compose.yml`
- `mysql` 서비스 추가 (MySQL 8.0): `MYSQL_DATABASE=tradingjournal`, 자격증명 env, 포트 `3306`
- `schema-mysql.sql`을 `/docker-entrypoint-initdb.d/`로 read-only 마운트 → 컨테이너 최초 기동 시 자동 적용
- 기존 postgres/app 서비스는 변경하지 않음 (별도 서비스로 공존)

## 정확성 검증 (라이브 MySQL 없이 — 검증은 나중 정책)

1. **Hibernate 기준 DDL 대조**: 구현 중 Hibernate schema-generation을 MySQLDialect로 **DDL 파일만 덤프**(실 DB 연결 없이, schema-generation scripts 모드)하여 수작업 `schema-mysql.sql`과 테이블/컬럼 커버리지를 대조한다. 불일치는 조사 후 반영.
2. **커버리지 자동 테스트** (`MysqlSchemaCoverageTest`, MySQL 불필요):
   - 엔티티 패키지의 모든 `@Table(name=...)` 이름에 대해 `schema-mysql.sql`에 대응하는 `CREATE TABLE` 문이 존재하는지 검사
   - `stress_test_result`도 포함 검사
3. **문서화된 수동 스모크** (나중에 MySQL 가용 시): DB 생성 → `schema-mysql.sql` 실행 → `--spring.profiles.active=mysql JPA_DDL_AUTO=validate`로 부팅 → Hibernate validate 통과 시 스키마-엔티티 완전 일치 증명. README/스펙에 절차 기재.

## 에러 처리 / 호환성
- 새 프로필은 명시적으로 활성화해야만 동작 → 기본/테스트/prod 경로 무영향
- MySQL 프로필에서 Flyway off → V1~V7 비호환 문제 회피
- 스키마-엔티티 불일치 시 `ddl-auto=validate`가 부팅 시점에 명확히 실패 (조용한 손상 없음)

## 테스트 계획
- `MysqlSchemaCoverageTest`: 위 검증 2번 (단위 테스트, MySQL 불필요, `./gradlew test`에 포함)
- 기존 전체 테스트가 깨지지 않는지 `./gradlew check` (단, 기존 플래키 `DatabaseIndexPerformanceTest`는 별개)

## 범위 밖 (후속 작업 후보)
- 기존 H2 데이터 → MySQL **데이터 이관** (mysqldump/CSV 경로는 추후 별도 작업)
- Flyway 마이그레이션을 크로스-DB 호환으로 재작성하거나 MySQL용 마이그레이션 세트 신설
- CI에 MySQL 통합 테스트(Testcontainers) 추가
- 운영 프로필을 MySQL로 전환

## 성공 기준
- [ ] `schema-mysql.sql`이 27개 테이블 전체 CREATE + 인덱스 + FK + stress_scenario 시드 포함
- [ ] `MysqlSchemaCoverageTest` 통과 (모든 @Table 커버)
- [ ] Hibernate MySQL DDL 대조에서 누락/타입 불일치 0건
- [ ] `application-mysql.yml`, `build.gradle`, `docker-compose.yml` 추가로 `--spring.profiles.active=mysql` 부팅 경로 구성
- [ ] 기존 H2/Postgres/테스트 동작 무변경 (`./gradlew check`에서 신규 회귀 없음)
