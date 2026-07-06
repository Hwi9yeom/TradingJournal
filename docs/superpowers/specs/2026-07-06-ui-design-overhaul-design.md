# UI 디자인 전면 개선 (Design Overhaul) — 설계 문서

- **날짜**: 2026-07-06
- **상태**: 사용자 승인 대기
- **범위**: `src/main/resources/static/` 프론트엔드 전체 (백엔드 무변경)

## 1. 배경과 목표

Trading Journal의 프론트엔드는 Spring Boot static으로 서빙되는 약 20개의 바닐라 HTML/CSS/JS 페이지다. 대부분 glassmorphism 다크 테마(`css/dashboard-glass.css`)를 쓰지만 다음 문제가 있다:

- **일관성 부족**: 네비게이션 바가 15개 페이지에 각각 하드코딩되어 있고 페이지마다 메뉴 항목이 다르다. 중앙화 모듈(`js/navigation-glass.js`)은 4개 페이지만 사용한다. 통계·목표·알림·내보내기 등은 어떤 메뉴에도 없다.
- **가독성 부족**: 데이터 카드가 투명도 97%의 유리 효과라 배경이 비쳐 대비가 낮고, 금액에 그라데이션 텍스트가 쓰여 판독성이 떨어진다. 숫자가 가변폭 폰트라 자릿수 정렬이 안 된다. 차트 색이 페이지마다 제각각이다.
- **구조 부채**: 대시보드 변형이 4개(`dashboard`, `-new`, `-original`, `-custom`) 존재하고, 2개 페이지는 Bootstrap/jQuery를 혼용한다.

**목표**: glassmorphism 분위기는 유지하면서 ① 전 페이지 디자인·네비게이션 통일 ② 데이터 가독성 개선 ③ 중복 파일 정리. 스타일 언어 교체나 기능 추가는 하지 않는다.

## 2. 확정된 요구사항

사용자와의 브레인스토밍(Visual Companion 목업 3회)으로 확정:

| 항목 | 결정 |
|---|---|
| 디자인 방향 | glass 테마 유지, 스타일 언어 변경 없음 |
| 네비게이션 | **좌측 사이드바** (4개 그룹 섹션), 상단 바 제거 |
| 데이터 표면 | **데이터 우선 글래스**: 카드 불투명도 상향, 모노스페이스 숫자 |
| 대시보드 | `dashboard.html` 하나로 통합, 나머지 3개 삭제 |
| 대상 페이지 | 17개 콘텐츠 페이지 전부 + login (모두 실사용 중) |
| 환경 | 데스크톱 중심, 모바일은 깨지지 않는 수준 |
| 테마 | 다크/라이트 **동등하게** 지원 (다크 기본) |
| 구현 방식 | 빌드 도구 없이 공유 CSS + 네비 주입 스크립트 (현 구조 유지) |

## 3. 디자인 시스템

### 3.1 토큰 (CSS 변수)

`dashboard-glass.css`의 변수 체계를 계승하되 다음을 변경한다:

- **표면 토큰 신설**: 데이터를 담는 카드·패널·테이블의 배경.
  - 다크: `--surface: rgba(26, 28, 52, 0.75)`, `--surface-border: rgba(255,255,255,0.14)`
  - 라이트: `--surface: rgba(255, 255, 255, 0.9)`, `--surface-border: rgba(0,0,0,0.08)`
  - 기존의 투명 글래스 토큰(`--glass-*`)은 사이드바·배경 장식 등 비데이터 표면에만 사용한다.
- **시맨틱 컬러 라이트 보정**: 라이트 테마에서 수익 `#00915f`, 손실 `#e5484d` (현행 라이트 민트 `#00c781`은 흰 배경 대비 부족). 다크는 현행 유지 (`#00f5a0` / `#ff6b6b`).
- 간격·라운드·트랜지션 변수는 현행 유지.
- 배경 오브(장식 원): 다크에서 현재보다 은은하게(투명도 하향), 라이트에서는 제거하고 그레이 그라데이션 배경만 사용.

### 3.2 타이포그래피

- UI 텍스트: Outfit (현행 유지).
- **숫자 데이터**(금액·수익률·수량·R값 등): JetBrains Mono + `font-variant-numeric: tabular-nums`. `.num` 유틸리티 클래스로 제공하고 모든 데이터 값에 적용한다.
- 그라데이션 텍스트는 로고·브랜드에만 허용. 데이터 값에서는 제거하고 흰색/시맨틱 컬러 단색으로.

### 3.3 공용 컴포넌트

`design-system.css`에 1회 정의, 전 페이지 공유: 사이드바, 페이지 헤더, 통계 카드(stat card), 패널, 테이블, 버튼, 폼(input/select/textarea), 모달, 토스트, 배지, 탭, 빈 상태(empty state), 로딩 스켈레톤. 각 컴포넌트는 다크/라이트 토큰만으로 두 테마를 지원해야 한다 (테마별 컴포넌트 규칙 금지).

### 3.4 차트 테마

- Chart.js 전역 기본값(폰트, 그리드 색, 눈금 색, 툴팁 배경)을 CSS 변수에서 읽어 설정하는 공용 함수를 `js/theme.js`에 둔다.
- 테마 토글 시 등록된 차트 인스턴스를 재테마링한다.
- 페이지별 차트는 시맨틱 팔레트(수익/손실/액센트/정보)만 사용한다.

### 3.5 모션

fade-in/stagger 진입 애니메이션은 유지하되, `prefers-reduced-motion: reduce`에서 비활성화한다.

## 4. 레이아웃과 네비게이션

### 4.1 페이지 골격

모든 콘텐츠 페이지는 동일한 골격을 가진다:

```html
<head>
  <!-- 폰트(Outfit, JetBrains Mono), Bootstrap Icons, design-system.css,
       테마 FOUC 방지 인라인 스니펫, 페이지별 라이브러리(Chart.js 등) -->
</head>
<body>
  <!-- 사이드바: js/navigation.js가 자동 주입 -->
  <main class="page-container">
    <header class="page-header">제목 + 페이지별 컨트롤(계좌 선택 등)</header>
    <!-- 페이지 콘텐츠 -->
  </main>
</body>
```

### 4.2 사이드바

- 폭 240px 고정, 화면 좌측, 은은한 글래스 패널. 상단 로고, 중간 그룹별 링크, 하단 테마 토글·로그아웃.
- 현재 페이지는 액센트(보라) 하이라이트 + 좌측 인디케이터 바.
- 그룹 구성 (17개 페이지, 이 구성이 `js/navigation.js`의 단일 nav 설정):
  - **코어**: 대시보드(dashboard) / 거래관리(index) / 계좌(accounts)
  - **분석**: 통계(statistics) / 패턴분석(patterns) / 섹터(sectors) / 상관관계(correlation) / 백테스트(backtest)
  - **계획·복기**: 트레이드플랜(plans) / 거래복기(reviews) / 목표(goals) / 저널(journal)
  - **보조**: 리스크(risk) / 배당금(dividend) / 알림(alerts) / AI(ai-assistant) / 내보내기(export)
- login.html은 사이드바 없는 독립 레이아웃(중앙 카드)으로 같은 토큰만 공유.

### 4.3 반응형

- 데스크톱(≥1024px): 사이드바 상시 표시.
- 그 미만: 사이드바는 햄버거 버튼으로 여닫는 오버레이 드로어로 전환.
- 넓은 테이블은 `overflow-x: auto` 래퍼로 감싸 본문 가로 스크롤을 방지한다.
- 모바일 최적화는 "깨지지 않는 수준"까지만 한다 (데스크톱 중심 결정).

## 5. 적용 아키텍처

### 5.1 파일 구조

| 파일 | 처리 |
|---|---|
| `css/design-system.css` | **신규**. `dashboard-glass.css` 계승·대체 |
| `js/navigation.js` | **신규**. `navigation-glass.js`의 주입 패턴 계승, 사이드바 주입 + 전체 nav 설정 단일 소스 |
| `js/theme.js` | **신규**. `theme-toggle.js` 계승: `<html data-theme>` + localStorage 저장, FOUC 방지 인라인 스니펫 제공, Chart.js 재테마링 |
| `js/glass-utils.js` | 유지 (필요시 클래스명만 갱신) |
| 페이지별 JS (~29,000줄) | **로직 무변경**, 참조 클래스명·색상 하드코딩만 교체 |

### 5.2 삭제 목록

- `dashboard-new.html`, `dashboard-original.html`, `dashboard-custom.html`, `dashboard-custom.js` — 대시보드는 `dashboard.html`로 단일화. 커스텀 위젯 기능은 제거한다(YAGNI; 필요해지면 별도 스펙으로).
- 마이그레이션 완료 후: `style.css`, `css/dashboard-glass.css`, `js/navigation-glass.js`, `js/theme-toggle.js`
- Bootstrap/jQuery CDN 의존은 위 파일 삭제로 자연 해소된다 (남는 페이지 중 사용처 없음 확인 후 제거).

### 5.3 페이지 마이그레이션

각 페이지에 반복 적용하는 절차 (페이지 단위로 검증 가능):

1. `<head>` 공통 boilerplate로 교체 (design-system.css + 테마 스니펫)
2. 하드코딩 navbar / 배경 오브 마크업 제거 → `navigation.js` 주입으로 대체
3. 콘텐츠 마크업의 클래스명을 design-system 컴포넌트로 교체
4. 숫자 표시 요소에 `.num` 적용 (페이지 JS가 innerHTML로 생성하는 부분 포함)
5. 다크/라이트 시각 확인

## 6. 엣지 케이스

- **테마 FOUC**: `<head>` 인라인 스니펫이 localStorage를 읽어 `data-theme`을 첫 페인트 전에 설정.
- **차트 재테마링**: 토글 시 열린 페이지의 차트가 새 테마 색으로 갱신되어야 한다.
- **사이드바 주입 실패**: JS 로드 실패 시에도 `<main>` 콘텐츠는 정상 표시된다 (네비만 사라짐).
- **로그아웃 등 인증 동작**: 기존 `auth.js` 흐름 유지, 사이드바 하단 버튼에 연결.

## 7. 검증

1. **시각 체크리스트**: 17개 페이지 × 다크/라이트 = 34개 화면을 로컬 서버에서 육안 확인 (네비 표시·현재 페이지 하이라이트·카드 대비·숫자 정렬·차트 색).
2. **참조 무결성**: 삭제 파일에 대한 참조가 남아있지 않은지 grep 검사 (`dashboard-glass`, `navigation-glass`, `theme-toggle`, `dashboard-new` 등).
3. **회귀 없음**: `./gradlew check` 통과 (정적 리소스 변경이라 백엔드 무영향 확인용).

## 8. 범위 외 (Non-goals)

- SPA 전환, 정적 빌드 도구 도입
- 신규 기능·페이지 추가, 백엔드/API 변경
- 대시보드 위젯 커스터마이징 기능 (dashboard-custom 삭제와 함께 제거)
- 모바일 전용 최적화 (깨지지 않는 수준 이상)
