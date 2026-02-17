# Trading Journal - 데이터 복구 가이드

## 개요

Trading Journal은 PostgreSQL 데이터베이스를 사용하며, 일일 자동 백업을 수행합니다.

## 백업 정보

- **위치**: `./backups/` 디렉토리
- **주기**: 매일 03:00 KST
- **보관 기간**: 7일
- **형식**: gzip 압축된 SQL 덤프 (`tradingjournal_YYYYMMDD_HHMMSS.sql.gz`)

## 백업 확인

```bash
# 백업 파일 목록 확인
ls -lh ./backups/

# 최신 백업 확인
ls -t ./backups/tradingjournal_*.sql.gz | head -1
```

## 수동 백업 실행

```bash
# Docker Compose 환경에서
docker exec -it tradingjournal-backup /scripts/backup.sh

# 또는 직접 실행 (PostgreSQL 클라이언트 필요)
export DB_PASSWORD="your_password"
./scripts/backup.sh
```

## 복구 절차

### 1. Docker Compose 환경에서 복구

```bash
# 앱 서비스 중지 (선택사항 - 데이터 일관성 보장)
docker-compose stop app

# 복구 스크립트 실행
docker exec -it tradingjournal-backup /scripts/restore.sh

# 또는 특정 백업 파일로 복구
docker exec -it tradingjournal-backup /scripts/restore.sh /backups/tradingjournal_20250217_030000.sql.gz

# 앱 서비스 재시작
docker-compose start app
```

### 2. 직접 복구 (PostgreSQL 클라이언트 사용)

```bash
# 환경 변수 설정
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=journal
export DB_PASSWORD="your_password"
export DB_NAME=tradingjournal

# 복구 스크립트 실행
./scripts/restore.sh ./backups/tradingjournal_20250217_030000.sql.gz
```

### 3. 수동 복구 (단계별)

```bash
# 1. 기존 연결 종료
psql -h $DB_HOST -U $DB_USER -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='tradingjournal';"

# 2. 데이터베이스 재생성
psql -h $DB_HOST -U $DB_USER -d postgres -c \
  "DROP DATABASE IF EXISTS tradingjournal; CREATE DATABASE tradingjournal OWNER journal;"

# 3. 백업 복원
gunzip -c ./backups/tradingjournal_YYYYMMDD_HHMMSS.sql.gz | \
  psql -h $DB_HOST -U $DB_USER -d tradingjournal

# 4. Flyway 마이그레이션 확인 (앱 재시작 시 자동 실행)
```

## 복구 후 확인 사항

1. **앱 정상 기동 확인**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **데이터 무결성 확인**
   ```bash
   docker exec -it tradingjournal-postgres psql -U journal -d tradingjournal -c \
     "SELECT COUNT(*) FROM transactions;"
   ```

3. **Flyway 마이그레이션 상태**
   ```bash
   docker exec -it tradingjournal-postgres psql -U journal -d tradingjournal -c \
     "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
   ```

## 주의사항

### 암호화 키 관리

- 데이터 복구 후에도 **동일한 `ENCRYPTION_KEY`**를 사용해야 합니다
- 암호화 키가 변경되면 기존 암호화된 데이터를 복호화할 수 없습니다
- 암호화 키는 반드시 안전한 곳에 별도 보관하세요

### 백업 실패 시

1. 디스크 공간 확인: `df -h ./backups/`
2. PostgreSQL 상태 확인: `docker-compose ps postgres`
3. 로그 확인: `docker-compose logs backup`

### 롤백이 필요한 경우

최근 백업으로 복구 후 앱을 `local` 프로파일로 실행하면 H2 데이터베이스를 사용합니다:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 지원

문제 발생 시 로그를 포함하여 이슈를 생성해 주세요:
- PostgreSQL 로그: `docker-compose logs postgres`
- 백업 서비스 로그: `docker-compose logs backup`
- 앱 로그: `docker-compose logs app`
