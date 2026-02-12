# TradingJournal

Spring Boot 기반 트레이딩 저널/포트폴리오 관리 서비스입니다.

## Tech Stack
- Java 21
- Spring Boot 3.5.x
- Spring Data JPA / Spring Security
- Flyway
- H2 / PostgreSQL
- Redis (optional)
- Gradle

## Quick Start
1. 로컬 설정 파일 준비
   - `src/main/resources/application-local.properties.example`를 참고해 `src/main/resources/application-local.properties` 생성
2. 필수 환경 변수 설정
   - `JWT_SECRET`
   - `ADMIN_PASSWORD`
3. 실행
   - `./gradlew bootRun -Dspring.profiles.active=local`

기본 포트는 `8080`입니다.

## Configuration

### 기본 정책
- 스키마 변경은 `spring.jpa.hibernate.ddl-auto=validate`를 기본으로 사용
- 마이그레이션은 Flyway(`spring.flyway.enabled=true`)로 관리
- H2 콘솔은 기본 비활성화(`spring.h2.console.enabled=false`)

### 주요 설정 파일
- 공통 인프라 설정: `src/main/resources/application.yml`
- 도메인/외부 API 설정: `src/main/resources/application.properties`
- 테스트 설정: `src/test/resources/application.yml`
- 로컬 템플릿: `src/main/resources/application-local.properties.example`

## Database Migration
- 마이그레이션 파일 위치: `src/main/resources/db/migration`
- 신규 스키마 변경 시 Flyway 스크립트를 추가하고 애플리케이션 시작으로 반영

## Testing & Quality Gate
- 테스트 실행: `./gradlew test`
- 전체 검증: `./gradlew check`

`check`에는 아래가 포함됩니다.
- 테스트 통과
- Spotless 포맷 검증
- JaCoCo 커버리지 검증(`LINE COVEREDRATIO >= 0.20`)

## API 문서
- Swagger UI: `/swagger-ui/index.html`

## Updating DART Corporation Codes
기업 코드 매핑은 `src/main/resources/corp-codes.yml`에서 관리합니다.

예시:
```yaml
corp:
  codes:
    삼성전자: "00126380"
    새로운회사: "00999999"
```
