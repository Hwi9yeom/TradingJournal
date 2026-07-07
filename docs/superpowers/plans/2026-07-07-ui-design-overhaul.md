# UI 디자인 전면 개선 (UI Design Overhaul) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 20개 정적 HTML 페이지를 좌측 사이드바 + "데이터 우선 글래스" 디자인 시스템으로 통일하고 중복 파일을 정리한다.

**Architecture:** 빌드 도구 없이 공유 리소스 3개(`css/design-system.css`, `js/navigation.js`, `js/theme.js`)를 만들고, 페이지를 하나씩 마이그레이션한다. 기존 클래스 어휘(glass-card, stat-card, glass-table, modal-glass…)는 **이름을 그대로 유지**하므로 페이지 본문 마크업은 거의 손대지 않는다 — 각 페이지의 작업은 head 교체 + 하드코딩 navbar/orb 제거 + 스크립트 목록 정리로 수렴한다. 페이지별 JS(29,000줄)는 로직 무변경이며, 차트 색 상수만 테마 팔레트로 교체한다.

**Tech Stack:** 바닐라 HTML/CSS/JS, Chart.js 4.4, d3 7 (treemap), Bootstrap Icons 1.11, Spring Boot static 서빙.

**Spec:** `docs/superpowers/specs/2026-07-06-ui-design-overhaul-design.md`

---

## 사전 조건 (Prerequisites)

1. **동시 작업 확인**: 다른 세션이 `fetchWithAuth` 도입 작업으로 static/*.js·html을 수정 중이었다 (auth.js, app.js, 페이지 JS 다수). **이 작업이 커밋/머지되어 `git status`가 깨끗해진 후에 시작할 것.** 미해결이면 사용자에게 확인.
2. superpowers:using-git-worktrees 스킬로 격리 워크트리 생성, 브랜치명 `feature/ui-design-overhaul`.
3. 모든 경로는 저장소 루트(`journal/`) 기준. 정적 리소스 루트는 `src/main/resources/static/` (이하 `static/`).

## 파일 구조 (최종 상태)

| 파일 | 상태 | 책임 |
|---|---|---|
| `static/css/design-system.css` | 신규 (dashboard-glass.css 복사 후 수정) | 토큰 + 전 컴포넌트 + 사이드바 + 호환 심 |
| `static/js/theme.js` | 신규 | 테마 저장/토글/FOUC, Chart.js 전역 테마, 팔레트 API |
| `static/js/navigation.js` | 신규 | 사이드바 + 모바일 드로어 자동 주입, 활성 페이지 표시 |
| `scripts/check-frontend.sh` | 신규 | 페이지 마이그레이션 상태 검증 (회귀 방지) |
| `static/*.html` 17개 + login | 수정 | head/스크립트 통일, navbar/orb 마크업 제거 |
| statistics/patterns/backtest/correlation/dividend`.js` | 수정 | 차트 색 상수를 TJTheme 팔레트로 교체 |
| dashboard-new/original/custom.html, dashboard.js, dashboard-custom.js, charts.js, style.css, css/dashboard-glass.css, js/navigation-glass.js, js/theme-toggle.js | **삭제** | 마이그레이션 완료 후 |

## 감사에서 확인된 사실 (구현 시 참고)

- 테마 localStorage 키는 `trading-journal-theme`, 속성은 `<html data-theme="dark|light">`, 전역 API `window.ThemeToggle` {toggle, setTheme, getTheme, isDark}, 커스텀 이벤트 `themechange`. **모두 호환 유지할 것** (페이지 JS가 참조 가능).
- `js/dashboard-glass.js`는 dashboard.html의 페이지 로직이지만 alerts/journal/reviews.html도 로드한다(Chart 전역 기본값+헬퍼). **이 include는 유지**하고, `js/theme.js`를 그 **뒤에** 로드해 테마 기본값이 이기게 한다.
- `dashboard.js`(1,632줄)는 dashboard-original.html만 로드 → 함께 삭제. `charts.js`는 어떤 페이지도 로드 안 함(죽은 파일) → 삭제.
- utils.js가 `empty-state-description`을 생성하지만 CSS는 `empty-state-desc`만 정의 → 별칭 추가로 수정.
- risk.html이 `stagger-5`를 쓰지만 CSS는 stagger-1..4만 정의 → stagger-5/6 추가.
- 공유 JS(utils.js, app.js, goals.js, sectors.js 등)가 Bootstrap 클래스(btn btn-primary, card, text-end, spinner-border…)를 생성하는데 글래스 페이지에는 Bootstrap CSS가 없어 스타일 없이 렌더됨 → design-system.css에 호환 심(shim) 추가.
- jQuery는 15개 페이지 JS가 사용 → **제거하지 않는다** (auth.js의 ajaxPrefilter 의존).

---

### Task 1: `static/js/theme.js` — 테마 모듈

**Files:**
- Create: `src/main/resources/static/js/theme.js`

- [ ] **Step 1: 파일 생성** — 아래 내용 그대로:

```javascript
/**
 * Theme Module — design-system의 테마 관리 + Chart.js 테마 연동
 * theme-toggle.js를 대체한다. localStorage 키/전역 API/이벤트는 기존과 호환.
 *
 * HTML <head>에는 FOUC 방지용 인라인 스니펫이 별도로 들어간다 (이 파일 참조 전 실행):
 * <script>(function(){var t=null;try{t=localStorage.getItem('trading-journal-theme')}catch(e){}
 *   if(!t){t=(window.matchMedia&&window.matchMedia('(prefers-color-scheme: light)').matches)?'light':'dark';}
 *   document.documentElement.setAttribute('data-theme',t);})();</script>
 */
(function () {
    'use strict';

    if (document.documentElement.hasAttribute('data-tj-theme-bound')) return;
    document.documentElement.setAttribute('data-tj-theme-bound', '');

    const THEME_KEY = 'trading-journal-theme';
    const DARK = 'dark';
    const LIGHT = 'light';
    const themeChangeCallbacks = [];

    function safeGetStoredTheme() {
        try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
    }
    function safeStoreTheme(theme) {
        try { localStorage.setItem(THEME_KEY, theme); } catch (e) { /* storage blocked */ }
    }

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') || DARK;
    }

    function cssVar(name) {
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    /** '#rrggbb' 또는 '#rgb' → 'rgba(r,g,b,a)' */
    function hexToRgba(hex, alpha) {
        if (!/^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(hex.trim())) {
            console.warn('TJTheme.rgba: invalid hex', hex);
            return hex;
        }
        let h = hex.trim().replace('#', '');
        if (h.length === 3) h = h.split('').map(c => c + c).join('');
        const int = parseInt(h, 16);
        return `rgba(${(int >> 16) & 255}, ${(int >> 8) & 255}, ${int & 255}, ${alpha})`;
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        updateToggleIcons(theme);
        applyChartDefaults();
        refreshCharts();
        window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
        themeChangeCallbacks.forEach(cb => { try { cb(theme); } catch (e) { console.error(e); } });
    }

    function updateToggleIcons(theme) {
        document.querySelectorAll('.theme-toggle-icon.sun').forEach(i => {
            i.style.display = theme === DARK ? 'inline' : 'none';
        });
        document.querySelectorAll('.theme-toggle-icon.moon').forEach(i => {
            i.style.display = theme === LIGHT ? 'inline' : 'none';
        });
    }

    function toggleTheme() {
        const next = currentTheme() === DARK ? LIGHT : DARK;
        safeStoreTheme(next);
        applyTheme(next);
    }

    /** Chart.js 전역 기본값을 CSS 변수에서 읽어 설정 (Chart 미로드 페이지에선 no-op) */
    function applyChartDefaults() {
        if (typeof Chart === 'undefined') return;
        Chart.defaults.color = cssVar('--text-secondary');
        Chart.defaults.borderColor = cssVar('--surface-border');
        Chart.defaults.font.family = cssVar('--font-display') || "'Outfit', sans-serif";
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.tooltip.backgroundColor = cssVar('--surface');
        Chart.defaults.plugins.tooltip.borderColor = cssVar('--surface-border');
        Chart.defaults.plugins.tooltip.borderWidth = 1;
        Chart.defaults.plugins.tooltip.titleColor = cssVar('--text-primary');
        Chart.defaults.plugins.tooltip.bodyColor = cssVar('--text-secondary');
        Chart.defaults.plugins.tooltip.padding = 12;
        Chart.defaults.plugins.tooltip.cornerRadius = 8;
    }

    /** 열린 차트의 축/그리드/툴팁을 새 테마로 갱신 (데이터셋 색은 생성 시점 고정) */
    function refreshCharts() {
        if (typeof Chart === 'undefined' || !Chart.instances) return;
        Object.values(Chart.instances).forEach(c => { try { c.update('none'); } catch (e) { /* detached */ } });
    }

    // 토글 버튼: 사이드바가 나중에 주입되므로 이벤트 위임으로 처리
    document.addEventListener('click', function (e) {
        if (e.target.closest('.theme-toggle, [data-theme-toggle]')) toggleTheme();
    });

    function init() {
        // FOUC 인라인 스니펫이 누락된 페이지 방어: data-theme이 없으면 여기서 결정
        if (!document.documentElement.hasAttribute('data-theme')) {
            applyTheme(safeGetStoredTheme() ||
                ((window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) ? LIGHT : DARK));
        }
        // data-theme은 인라인 스니펫이 이미 설정; 아이콘/차트만 동기화
        updateToggleIcons(currentTheme());
        applyChartDefaults();
        try {
            window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)')
                .addEventListener('change', e => {
                    if (!safeGetStoredTheme()) applyTheme(e.matches ? DARK : LIGHT);
                });
        } catch (e) { /* older Safari: MediaQueryList without addEventListener */ }
    }

    // 기존 theme-toggle.js와 호환되는 전역 API (init 이전에 노출)
    window.ThemeToggle = {
        toggle: toggleTheme,
        setTheme: (t) => { if (t === DARK || t === LIGHT) { safeStoreTheme(t); applyTheme(t); } },
        getTheme: currentTheme,
        isDark: () => currentTheme() === DARK
    };

    // 페이지 JS용 테마/팔레트 API
    window.TJTheme = {
        cssVar,
        color: (name) => cssVar('--color-' + name),          // positive|negative|warning|info|accent
        rgba: (name, alpha) => hexToRgba(cssVar('--color-' + name), alpha),
        chartPalette: () => [1, 2, 3, 4, 5, 6, 7, 8].map(i => cssVar('--chart-' + i)),
        onThemeChange: (cb) => themeChangeCallbacks.push(cb),
        applyChartDefaults
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
```

- [ ] **Step 2: 문법 검증**

Run: `node --check src/main/resources/static/js/theme.js`
Expected: 출력 없음 (exit 0)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/theme.js
git commit -m "feat(ui): add theme.js (theme persistence + Chart.js theming, ThemeToggle-compatible)"
```

---

### Task 2: `static/css/design-system.css` — 디자인 시스템

기존 1,775줄 `dashboard-glass.css`를 복사한 뒤 아래 수정을 가한다. 나열되지 않은 규칙은 **그대로 둔다** (기존 클래스 어휘 유지가 마이그레이션 비용을 좌우한다).

**Files:**
- Create: `src/main/resources/static/css/design-system.css` (복사 후 수정)

- [ ] **Step 1: 복사**

Run: `cp src/main/resources/static/css/dashboard-glass.css src/main/resources/static/css/design-system.css`

- [ ] **Step 2 (Edit A): `:root` 블록에 토큰 추가** — `:root {` 블록 끝(`--transition-spring` 줄 뒤)에 추가:

```css
    /* Data-first surfaces (카드/패널/모달 — 판독성 우선) */
    --surface: rgba(26, 28, 52, 0.75);
    --surface-hover: rgba(32, 35, 62, 0.85);
    --surface-border: rgba(255, 255, 255, 0.14);

    /* Layout */
    --sidebar-width: 240px;

    /* Categorical chart palette */
    --chart-1: #667eea;
    --chart-2: #a855f7;
    --chart-3: #f093fb;
    --chart-4: #6bcaff;
    --chart-5: #00f5a0;
    --chart-6: #ffd93d;
    --chart-7: #ff6b6b;
    --chart-8: #764ba2;
```

- [ ] **Step 3 (Edit B): `[data-theme="light"]` 블록에 추가** — 블록 끝에 추가:

```css
    /* Data-first surfaces */
    --surface: rgba(255, 255, 255, 0.9);
    --surface-hover: rgba(255, 255, 255, 1);
    --surface-border: rgba(0, 0, 0, 0.08);

    /* 라이트 배경 대비 보정 (스펙 3.1) */
    --color-positive: #00915f;
    --color-positive-glow: rgba(0, 145, 95, 0.25);
    --color-negative: #e5484d;
    --color-negative-glow: rgba(229, 72, 77, 0.25);

    --chart-1: #5b6ee0;
    --chart-2: #9333ea;
    --chart-3: #c65fd6;
    --chart-4: #2f8fd6;
    --chart-5: #00915f;
    --chart-6: #d99a0b;
    --chart-7: #e5484d;
    --chart-8: #6d4699;
```

- [ ] **Step 4 (Edit C): 배경 오브 완화** — `.bg-orb {` 규칙의 기존 `opacity: 0.4;` 선언을 `opacity: 0.25;`로 교체하고, `[data-theme="light"]` 블록 **뒤에** 다음 규칙 추가:

```css
/* 라이트 테마: 오브 제거, 그라데이션 배경만 (스펙 3.1) */
[data-theme="light"] .bg-orb {
    display: none;
}
```

- [ ] **Step 5 (Edit D): 상단 navbar 섹션 → 사이드바 섹션 교체** — `/* === Navigation === */`부터 `.nav-link.active::after { ... }` 규칙 끝까지(원본 199–283행 상당)를 삭제하고, 그 자리에 아래 전체를 삽입. `.navbar-brand`/`.logo-icon`은 사이드바 브랜드로 계승한다:

```css
/* === Sidebar Navigation === */
.sidebar-glass {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: var(--sidebar-width);
    z-index: 1000;
    display: flex;
    flex-direction: column;
    background: rgba(20, 22, 45, 0.7);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border-right: 1px solid var(--glass-border);
    overflow-y: auto;
    padding: var(--space-5) var(--space-3);
    transition: transform var(--transition-base);
}

[data-theme="light"] .sidebar-glass {
    background: rgba(255, 255, 255, 0.78);
    border-right-color: var(--surface-border);
}

.navbar-brand {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    font-weight: 700;
    font-size: var(--font-size-lg);
    color: var(--text-primary);
    text-decoration: none;
    letter-spacing: -0.02em;
    padding: 0 var(--space-2) var(--space-4);
}

.navbar-brand .logo-icon {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, var(--gradient-start), var(--gradient-mid));
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--font-size-base);
    box-shadow: 0 4px 15px var(--color-accent-glow);
    flex-shrink: 0;
}

.sidebar-group-label {
    font-size: var(--font-size-xs);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--text-muted);
    padding: var(--space-4) var(--space-2) var(--space-1);
}

.sidebar-nav {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.nav-link {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    color: var(--text-secondary);
    text-decoration: none;
    font-size: var(--font-size-sm);
    font-weight: 500;
    border-radius: var(--radius-md);
    transition: var(--transition-base);
    position: relative;
    background: none;
    border: none;
    width: 100%;
    cursor: pointer;
    font-family: inherit;
}

.nav-link i {
    font-size: var(--font-size-base);
    width: 20px;
    text-align: center;
    flex-shrink: 0;
}

.nav-link:hover {
    color: var(--text-primary);
    background: var(--glass-bg-hover);
}

.nav-link.active {
    color: var(--text-primary);
    background: rgba(168, 85, 247, 0.22);
    box-shadow: inset 2px 0 0 var(--color-accent);
}

[data-theme="light"] .nav-link.active {
    color: var(--color-accent);
    background: rgba(147, 51, 234, 0.1);
}

.sidebar-footer {
    margin-top: auto;
    border-top: 1px solid var(--glass-border);
    padding-top: var(--space-3);
    display: flex;
    flex-direction: column;
    gap: 2px;
}

/* 모바일 드로어 제어 (≤1023px) */
.sidebar-toggle-btn {
    display: none;
    position: fixed;
    top: var(--space-3);
    left: var(--space-3);
    z-index: 1100;
    width: 44px;
    height: 44px;
    border-radius: var(--radius-md);
    background: var(--surface);
    border: 1px solid var(--surface-border);
    color: var(--text-primary);
    font-size: var(--font-size-xl);
    cursor: pointer;
    align-items: center;
    justify-content: center;
}

.sidebar-overlay {
    display: none;
    position: fixed;
    inset: 0;
    z-index: 999;
    background: rgba(0, 0, 0, 0.5);
}

@media (max-width: 1023px) {
    .sidebar-glass {
        transform: translateX(-100%);
    }
    .sidebar-glass.open {
        transform: translateX(0);
        box-shadow: var(--glass-shadow);
    }
    .sidebar-toggle-btn {
        display: flex;
    }
    .sidebar-overlay.active {
        display: block;
    }
}
```

참고: `body::before`와 `.bg-orb`에 `z-index: -1;`을 추가하고 `.main-container`의 `z-index: 1`을 제거한다 (모달 스태킹 트랩 방지). 768px 블록의 `.main-container`에 `padding-top: calc(var(--space-4) + 44px + var(--space-2));` 추가. dropdown-menu/toast-glass 배경은 `var(--surface)`로.

- [ ] **Step 6 (Edit E): `.main-container`에 사이드바 오프셋** — 기존 규칙을 다음으로 교체:

```css
.main-container {
    position: relative;
    max-width: 1600px;
    margin: 0 auto;
    margin-left: max(var(--sidebar-width), calc((100vw - 1600px + var(--sidebar-width)) / 2));
    padding: var(--space-8) var(--space-6);
}

@media (max-width: 1023px) {
    .main-container {
        margin-left: 0;
        padding-top: calc(var(--space-8) + 44px); /* 햄버거 버튼 공간 */
    }
}
```

- [ ] **Step 7 (Edit F): 데이터 표면 불투명화** — 다음 4개 규칙에서 `background: var(--glass-bg);` → `background: var(--surface);`, `border: 1px solid var(--glass-border);` → `border: 1px solid var(--surface-border);`로 교체 (그 외 선언 유지). 대상: `.glass-card`, `.stat-card`, `.metric-card`, `.modal-glass-content`. `.glass-card:hover`/`.stat-card:hover`의 `border-color: var(--glass-border-hover)`는 그대로 둔다.

- [ ] **Step 8 (Edit G): 페이지 제목 그라데이션 제거** — `.page-title h1` 규칙을 다음으로 교체 (그라데이션은 로고 전용 — 스펙 3.2):

```css
.page-title h1 {
    font-size: var(--font-size-3xl);
    font-weight: 700;
    letter-spacing: -0.03em;
    color: var(--text-primary);
}
```

- [ ] **Step 9 (Edit H): 유틸리티/버그픽스 추가** — 파일 끝(기존 `@media (prefers-reduced-motion: reduce)` 블록 **앞**)에 추가:

```css
/* === Numeric Display (스펙 3.2) === */
.num {
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.01em;
}

/* === 감사에서 확인된 버그픽스 === */
/* utils.js가 empty-state-description을 생성 (CSS에는 -desc만 있었음) */
.empty-state-description {
    font-size: var(--font-size-sm);
    color: var(--text-muted);
}

/* risk.html이 stagger-5 사용 */
.stagger-5 { animation-delay: 0.5s; }
.stagger-6 { animation-delay: 0.6s; }

/* === Bootstrap-emitting JS 호환 심 ===
   공유 JS(utils.js, app.js, goals.js, sectors.js, backtest.js, plans.js,
   dividend.js)가 Bootstrap 클래스를 생성하지만 글래스 페이지에는 Bootstrap이
   없다. 최다 빈도 클래스만 글래스 스타일로 매핑한다. */
.btn {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-4);
    background: var(--glass-bg-hover);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-family: inherit;
    font-size: var(--font-size-sm);
    font-weight: 500;
    cursor: pointer;
    text-decoration: none;
    transition: var(--transition-base);
}
.btn:hover { border-color: var(--glass-border-hover); }
.btn-primary {
    background: linear-gradient(135deg, var(--gradient-start), var(--gradient-mid));
    border: none;
    color: #fff;
}
.btn-danger { background: var(--color-negative); border: none; color: #fff; }
.btn-success { background: var(--color-positive); border: none; color: var(--text-inverse); }
.btn-sm { padding: var(--space-1) var(--space-3); font-size: var(--font-size-xs); }
.card {
    background: var(--surface);
    border: 1px solid var(--surface-border);
    border-radius: var(--radius-lg);
}
.card-header { padding: var(--space-4); border-bottom: 1px solid var(--surface-border); }
.card-body { padding: var(--space-4); }
.text-end { text-align: right !important; }
.text-danger { color: var(--color-negative) !important; }
.text-success { color: var(--color-positive) !important; }
.d-flex { display: flex !important; }
.justify-content-between { justify-content: space-between !important; }
.align-items-center { align-items: center !important; }
.spinner-border {
    width: 24px;
    height: 24px;
    border: 3px solid var(--glass-border);
    border-top-color: var(--color-accent);
    border-radius: 50%;
    display: inline-block;
    animation: spin 0.8s linear infinite;
}
.visually-hidden { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
small { font-size: var(--font-size-xs); }
.fw-bold { font-weight: 700 !important; }
.d-block { display: block !important; }
.row { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.row > [class*="col-"], .row > .col { flex: 1 1 0; min-width: 0; }
.col-12, .col-md-12 { flex: 0 0 100%; }
.col-6, .col-md-6 { flex: 1 1 calc(50% - var(--space-3)); }
.col-4, .col-md-4 { flex: 1 1 calc(33.333% - var(--space-3)); }
.col-md-3, .col-lg-3 { flex: 1 1 calc(25% - var(--space-3)); }
.me-1 { margin-right: var(--space-1) !important; }
.me-2 { margin-right: var(--space-2) !important; }
.mt-3 { margin-top: var(--space-3) !important; }
.mb-3 { margin-bottom: var(--space-3) !important; }
.p-3 { padding: var(--space-3) !important; }
.pt-3 { padding-top: var(--space-3) !important; }
.border-top { border-top: 1px solid var(--surface-border) !important; }
.badge { display: inline-block; padding: var(--space-1) var(--space-2); border-radius: var(--radius-sm); font-size: var(--font-size-xs); font-weight: 600; background: var(--glass-bg-hover); color: var(--text-secondary); }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { padding: var(--space-2) var(--space-3); border-bottom: 1px solid var(--surface-border); text-align: left; }
.form-control, .form-select {
    width: 100%;
    padding: var(--space-2) var(--space-3);
    background: var(--glass-bg);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-md);
    color: var(--text-primary);
    font-family: inherit;
    font-size: var(--font-size-sm);
}
.form-control-sm, .form-select-sm { padding: var(--space-1) var(--space-2); font-size: var(--font-size-xs); }

/* === 키보드 포커스 표시 (사이드바가 주 내비게이션) === */
.nav-link:focus-visible,
.sidebar-toggle-btn:focus-visible,
.btn:focus-visible,
.btn-glass:focus-visible {
    outline: 2px solid var(--color-accent);
    outline-offset: 2px;
}
```

- [ ] **Step 10: 768px 미디어 블록의 navbar 잔재 제거** — `@media (max-width: 768px)` 블록(원본 1278행 상당) 안에서 `.navbar-nav`, `.mobile-menu-toggle`, `.navbar-glass` 관련 규칙을 삭제한다 (사이드바 미디어 규칙은 Step 5에서 이미 추가됨). 블록 내 다른 규칙(stats-grid 1열 등)은 유지.

- [ ] **Step 11: 검증** — 문법 확인 및 잔여 참조 확인:

Run: `grep -c 'navbar-nav\|mobile-menu-toggle' src/main/resources/static/css/design-system.css`
Expected: `0`

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/static/css/design-system.css
git commit -m "feat(ui): add design-system.css (sidebar, data-first surfaces, palette tokens, compat shim)"
```

---

### Task 3: `static/js/navigation.js` — 사이드바 주입

**Files:**
- Create: `src/main/resources/static/js/navigation.js`

- [ ] **Step 1: 파일 생성** — 아래 내용 그대로:

```javascript
/**
 * Sidebar Navigation Module — navigation-glass.js를 대체.
 * 전체 페이지의 nav 구성이 이 파일 하나에만 존재한다 (스펙 4.2).
 * body 시작 부분에 사이드바 + 배경 오브 + 모바일 드로어 컨트롤을 주입한다.
 */
(function () {
    'use strict';

    const NAV_GROUPS = [
        {
            label: '코어',
            items: [
                { href: 'dashboard.html', icon: 'bi-speedometer2', label: '대시보드' },
                { href: 'index.html', icon: 'bi-list-ul', label: '거래관리' },
                { href: 'accounts.html', icon: 'bi-wallet2', label: '계좌' },
            ],
        },
        {
            label: '분석',
            items: [
                { href: 'statistics.html', icon: 'bi-bar-chart-line', label: '통계' },
                { href: 'patterns.html', icon: 'bi-diagram-3', label: '패턴분석' },
                { href: 'sectors.html', icon: 'bi-pie-chart', label: '섹터' },
                { href: 'correlation.html', icon: 'bi-grid-3x3', label: '상관관계' },
                { href: 'backtest.html', icon: 'bi-clock-history', label: '백테스트' },
            ],
        },
        {
            label: '계획·복기',
            items: [
                { href: 'plans.html', icon: 'bi-journal-bookmark', label: '트레이드플랜' },
                { href: 'reviews.html', icon: 'bi-journal-text', label: '거래복기' },
                { href: 'goals.html', icon: 'bi-bullseye', label: '목표' },
                { href: 'journal.html', icon: 'bi-pencil-square', label: '저널' },
            ],
        },
        {
            label: '보조',
            items: [
                { href: 'risk.html', icon: 'bi-shield-check', label: '리스크' },
                { href: 'dividend.html', icon: 'bi-cash-coin', label: '배당금' },
                { href: 'alerts.html', icon: 'bi-bell', label: '알림' },
                { href: 'ai-assistant.html', icon: 'bi-robot', label: 'AI 어시스턴트' },
                { href: 'export.html', icon: 'bi-download', label: '내보내기' },
            ],
        },
    ];

    function currentPage() {
        const path = window.location.pathname.split('/').pop();
        return path === '' ? 'index.html' : path;
    }

    function buildSidebarHTML() {
        const page = currentPage();
        const groups = NAV_GROUPS.map(group => `
            <div class="sidebar-group-label">${group.label}</div>
            <ul class="sidebar-nav">
                ${group.items.map(item => `
                    <li><a href="${item.href}"
                           class="nav-link${item.href === page ? ' active' : ''}">
                        <i class="bi ${item.icon}"></i> ${item.label}
                    </a></li>`).join('')}
            </ul>`).join('');

        return `
            <button class="sidebar-toggle-btn" aria-label="메뉴 열기"><i class="bi bi-list"></i></button>
            <div class="sidebar-overlay"></div>
            <aside class="sidebar-glass">
                <a href="index.html" class="navbar-brand">
                    <span class="logo-icon"><i class="bi bi-graph-up-arrow"></i></span>
                    Trading Journal
                </a>
                ${groups}
                <div class="sidebar-footer">
                    <button class="nav-link theme-toggle" data-theme-toggle title="테마 변경">
                        <i class="bi bi-sun theme-toggle-icon sun"></i>
                        <i class="bi bi-moon theme-toggle-icon moon"></i>
                        테마 변경
                    </button>
                    <a href="#" class="nav-link" onclick="logout(); return false;">
                        <i class="bi bi-box-arrow-right"></i> 로그아웃
                    </a>
                </div>
            </aside>`;
    }

    function injectBackgroundOrbs() {
        if (document.querySelector('.bg-orb')) return;
        document.body.insertAdjacentHTML('afterbegin',
            '<div class="bg-orb bg-orb-1"></div><div class="bg-orb bg-orb-2"></div><div class="bg-orb bg-orb-3"></div>');
    }

    function setupDrawer() {
        const sidebar = document.querySelector('.sidebar-glass');
        const toggle = document.querySelector('.sidebar-toggle-btn');
        const overlay = document.querySelector('.sidebar-overlay');
        if (!sidebar || !toggle || !overlay) return;

        const open = () => { sidebar.classList.add('open'); overlay.classList.add('active'); };
        const close = () => { sidebar.classList.remove('open'); overlay.classList.remove('active'); };

        toggle.addEventListener('click', open);
        overlay.addEventListener('click', close);
        document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });
        sidebar.querySelectorAll('a.nav-link').forEach(a =>
            a.addEventListener('click', () => setTimeout(close, 100)));
    }

    function init() {
        if (document.querySelector('.sidebar-glass')) return; // 중복 주입 방지
        injectBackgroundOrbs();
        document.body.insertAdjacentHTML('afterbegin', buildSidebarHTML());
        setupDrawer();
        // 테마 아이콘 상태 동기화 (theme.js가 먼저 로드된 경우)
        if (window.ThemeToggle) {
            const t = window.ThemeToggle.getTheme();
            document.querySelectorAll('.theme-toggle-icon.sun').forEach(i => {
                i.style.display = t === 'dark' ? 'inline' : 'none';
            });
            document.querySelectorAll('.theme-toggle-icon.moon').forEach(i => {
                i.style.display = t === 'light' ? 'inline' : 'none';
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
```

- [ ] **Step 2: 문법 검증**

Run: `node --check src/main/resources/static/js/navigation.js`
Expected: 출력 없음 (exit 0)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/navigation.js
git commit -m "feat(ui): add navigation.js (sidebar injection, single nav source for 17 pages)"
```

---

### Task 4: `scripts/check-frontend.sh` — 마이그레이션 검증 스크립트

**Files:**
- Create: `scripts/check-frontend.sh` (chmod +x)

- [ ] **Step 1: 파일 생성** — 아래 내용 그대로:

```bash
#!/usr/bin/env bash
# 페이지가 새 디자인 시스템으로 마이그레이션되었는지 검증한다.
# Usage: check-frontend.sh page.html [page2.html ...]   # 지정 페이지 검사
#        check-frontend.sh --all                        # 전체 페이지 + 삭제 파일 참조 검사
set -u
cd "$(dirname "$0")/../src/main/resources/static" || exit 1

PAGES=(accounts.html ai-assistant.html alerts.html backtest.html correlation.html
       dashboard.html dividend.html export.html goals.html index.html journal.html
       login.html patterns.html plans.html reviews.html risk.html sectors.html statistics.html)
DELETED=(dashboard-new.html dashboard-original.html dashboard-custom.html
         dashboard-custom.js dashboard.js charts.js style.css
         css/dashboard-glass.css js/navigation-glass.js js/theme-toggle.js)
fail=0

err() { echo "FAIL: $1"; fail=1; }

check_page() {
    local p="$1"
    [ -f "$p" ] || { err "$p: 파일 없음"; return; }
    grep -q 'css/design-system.css' "$p"       || err "$p: design-system.css 미포함"
    grep -q 'trading-journal-theme' "$p"       || err "$p: 테마 FOUC 인라인 스니펫 없음"
    grep -q 'js/theme.js' "$p"                 || err "$p: js/theme.js 미포함"
    grep -q 'dashboard-glass.css' "$p"         && err "$p: 구 dashboard-glass.css 참조 잔존"
    grep -q 'navigation-glass.js\|theme-toggle.js' "$p" && err "$p: 구 nav/theme 스크립트 잔존"
    grep -q '<nav class="navbar-glass"' "$p"   && err "$p: 하드코딩 navbar 잔존"
    grep -q '<div class="bg-orb' "$p"          && err "$p: 하드코딩 배경 오브 잔존"
    if [ "$p" != "login.html" ]; then
        grep -q 'js/navigation.js' "$p"        || err "$p: js/navigation.js 미포함"
    fi
}

if [ "${1:-}" = "--all" ]; then
    for p in "${PAGES[@]}"; do check_page "$p"; done
    for d in "${DELETED[@]}"; do
        [ -e "$d" ] && err "$d: 삭제 대상 파일이 아직 존재"
        # shellcheck disable=SC2038
        refs=$(find . -name '*.html' -o -name '*.js' | xargs grep -l "$(basename "$d")" 2>/dev/null | grep -v check-frontend || true)
        [ -n "$refs" ] && err "$d 참조 잔존: $refs"
    done
else
    for p in "$@"; do check_page "$p"; done
fi

[ $fail -eq 0 ] && echo "OK" || exit 1
```

- [ ] **Step 2: 실행 권한 + 레드 확인** (아직 아무 페이지도 마이그레이션 전이므로 실패해야 정상)

Run: `chmod +x scripts/check-frontend.sh && scripts/check-frontend.sh dashboard.html`
Expected: `FAIL: dashboard.html: design-system.css 미포함` 등 여러 줄, exit 1

- [ ] **Step 3: Commit**

```bash
git add scripts/check-frontend.sh
git commit -m "test(ui): add frontend migration checker script"
```

---

## 페이지 마이그레이션 공통 절차 (Task 5–21에서 반복)

모든 페이지 태스크는 같은 5단계다. **각 태스크의 코드 블록이 그 페이지의 완전한 최종 상태를 담는다.**

1. `scripts/check-frontend.sh <페이지>` 실행 → FAIL 확인 (레드)
2. `<head>`에서: 기존 `dashboard-glass.css` link를 `css/design-system.css`로 교체하고, 그 **바로 뒤에** FOUC 스니펫 추가. 폰트 preconnect+link가 없으면 추가, bootstrap-icons는 `1.11.0`으로 통일. 페이지의 Chart.js 등 CDN 스크립트는 기존 버전 그대로 유지. 페이지 인라인 `<style>` 블록은 유지(페이지 전용 컴포넌트).
3. `<body>`에서: `<div class="bg-orb ...">` 3줄과 `<nav class="navbar-glass">…</nav>` 블록 전체 삭제 (grep으로 시작/끝 확인). 스크립트 목록을 태스크에 명시된 최종 목록으로 교체 (`js/theme.js` + `js/navigation.js` 추가, 구 `js/navigation-glass.js`/`js/theme-toggle.js` 제거).
4. `scripts/check-frontend.sh <페이지>` 실행 → `OK` (그린)
5. Commit

**모든 페이지 공통 head 삽입 블록** (각 태스크에서 "표준 head 블록"으로 지칭):

```html
    <!-- Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
    <!-- Icons -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css">
    <!-- Design System -->
    <link rel="stylesheet" href="css/design-system.css">
    <!-- Theme init (FOUC 방지) -->
    <script>(function(){var t=null;try{t=localStorage.getItem('trading-journal-theme')}catch(e){}if(!t){t=(window.matchMedia&&window.matchMedia('(prefers-color-scheme: light)').matches)?'light':'dark';}document.documentElement.setAttribute('data-theme',t);})();</script>
```

---

### Task 5: dashboard.html 마이그레이션 (레퍼런스 페이지)

**Files:**
- Modify: `src/main/resources/static/dashboard.html`

- [ ] **Step 1: 레드 확인** — Run: `scripts/check-frontend.sh dashboard.html` → Expected: FAIL 다수
- [ ] **Step 2: head 교체** — 표준 head 블록 적용. 이 페이지의 CDN 차트 스택은 유지: `chart.umd.min.js@4.4.0`, `chartjs-adapter-date-fns@3.0.0`, `chartjs-plugin-datalabels@2.2.0`, `d3@7.9.0`
- [ ] **Step 3: body 정리** — bg-orb 3줄 + `<nav class="navbar-glass">…</nav>` 삭제. body 끝 스크립트를 다음 **최종 목록**으로:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/dashboard-glass.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
```
(`js/theme.js`가 `js/dashboard-glass.js` **뒤** — Chart 전역 기본값을 테마 모듈이 이긴다)

- [ ] **Step 4: 그린 확인** — Run: `scripts/check-frontend.sh dashboard.html` → Expected: `OK`
- [ ] **Step 5: 영문 h1 한글화** — page-title의 `<h1>Portfolio Dashboard</h1>` → `<h1>대시보드</h1>` (title 태그는 `대시보드 - Trading Journal`)
- [ ] **Step 6: Commit** — `git add src/main/resources/static/dashboard.html && git commit -m "feat(ui): migrate dashboard.html to design system"`

### Task 6: index.html (거래관리)

**Files:** Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh index.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0, adapter-date-fns 3.0.0, datalabels 2.2.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="app.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh index.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate index.html"`

### Task 7: accounts.html

**Files:** Modify: `src/main/resources/static/accounts.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh accounts.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록 (이 페이지는 차트 CDN 없음)
- [ ] **Step 3: body 정리** — orb 삭제 (navbar는 원래 injected — 하드코딩 없음). `js/navigation-glass.js`와 `js/theme-toggle.js` include 제거. 최종 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="accounts.js"></script>
```

- [ ] **Step 4: h1 한글화** — `Account Management` → `계좌 관리`
- [ ] **Step 5: 그린** — `scripts/check-frontend.sh accounts.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate accounts.html"`

### Task 8: statistics.html + statistics.js 차트 색

**Files:** Modify: `src/main/resources/static/statistics.html`, `src/main/resources/static/statistics.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh statistics.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="statistics.js"></script>
```

- [ ] **Step 4: 차트 색 교체** — `grep -n "rgba(\|#[0-9a-fA-F]\{3,6\}" statistics.js`로 상단 색 상수 블록(~24–32행)을 찾아 다음 매핑으로 교체 (상수 이름은 유지, 값만):

| 기존 값 | 새 값 |
|---|---|
| `'rgba(40, 167, 69, 0.7)'` (승률 양수 계열) | `TJTheme.rgba('positive', 0.7)` |
| `'#667eea'` (수익 라인) | `TJTheme.cssVar('--chart-1')` |
| `'#f5576c'` (거래수 라인) | `TJTheme.cssVar('--chart-7')` |
| 그 외 녹색/적색 계열 rgba | `TJTheme.rgba('positive', α)` / `TJTheme.rgba('negative', α)` (α는 기존 값 유지) |

- [ ] **Step 5: 문법+그린** — `node --check statistics.js && scripts/check-frontend.sh statistics.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate statistics page, themed chart colors"`

### Task 9: patterns.html + patterns.js 차트 색

**Files:** Modify: `src/main/resources/static/patterns.html`, `src/main/resources/static/patterns.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh patterns.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="patterns.js"></script>
```

- [ ] **Step 4: 차트 색 교체** — 상단 상수(~19–25행)에서 **Bootstrap 시대 색만** 교체한다: `'#28a745'` → `TJTheme.color('positive')`, `'#dc3545'` → `TJTheme.color('negative')`. 도넛 팔레트 배열(`'#667eea', '#764ba2', '#f093fb', …`)은 `TJTheme.chartPalette()` 호출로 교체. `'rgba(102, 126, 234, 0.7)'`(--chart-1과 동일 색)은 그대로 둔다.
- [ ] **Step 5: 문법+그린** — `node --check patterns.js && scripts/check-frontend.sh patterns.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate patterns page, themed chart colors"`

### Task 10: sectors.html + sectors.js

**Files:** Modify: `src/main/resources/static/sectors.html`, `src/main/resources/static/sectors.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh sectors.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb 삭제, `js/navigation-glass.js`/`js/theme-toggle.js` 제거. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="sectors.js"></script>
```

- [ ] **Step 4: 차트 색** — `SECTOR_COLORS` 배열(~32–36행)을 `TJTheme.chartPalette()`로 교체. 도넛 `borderColor: '#fff'`(~152행) → `TJTheme.cssVar('--surface-border')`
- [ ] **Step 5: 문법+그린** — `node --check sectors.js && scripts/check-frontend.sh sectors.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate sectors page, themed chart colors"`

### Task 11: correlation.html + correlation.js

**Files:** Modify: `src/main/resources/static/correlation.html`, `src/main/resources/static/correlation.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh correlation.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0, adapter-date-fns 3.0.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@3.0.0/dist/chartjs-adapter-date-fns.bundle.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="correlation.js"></script>
```

- [ ] **Step 4: 차트 색** — 상수(~52, 63–65행): `NULL_BACKGROUND '#f8f9fa'` → `TJTheme.cssVar('--surface')`, `ROLLING_LINE_COLOR '#3b82f6'` → `TJTheme.cssVar('--chart-4')`, `COMPARISON_COLOR_1 '#22c55e'` → `TJTheme.color('positive')`
- [ ] **Step 5: 문법+그린** — `node --check correlation.js && scripts/check-frontend.sh correlation.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate correlation page, themed chart colors"`

### Task 12: backtest.html + backtest.js

**Files:** Modify: `src/main/resources/static/backtest.html`, `src/main/resources/static/backtest.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh backtest.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="backtest.js"></script>
```

- [ ] **Step 4: 차트 색** — 상수(~11–15행): `PRIMARY '#0d6efd'` → `TJTheme.cssVar('--chart-1')`, `SUCCESS 'rgba(34, 197, 94, 0.8)'` → `TJTheme.rgba('positive', 0.8)`, `DANGER 'rgba(239, 68, 68, 0.8)'` → `TJTheme.rgba('negative', 0.8)`
- [ ] **Step 5: 문법+그린** — `node --check backtest.js && scripts/check-frontend.sh backtest.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate backtest page, themed chart colors"`

### Task 13: plans.html

**Files:** Modify: `src/main/resources/static/plans.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh plans.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록 (차트 CDN 없음)
- [ ] **Step 3: body 정리** — orb 삭제, `js/navigation-glass.js` 제거. 최종 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="plans.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh plans.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate plans.html"`

### Task 14: reviews.html

**Files:** Modify: `src/main/resources/static/reviews.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh reviews.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0 (있는 경우 기존 그대로)
- [ ] **Step 3: body 정리** — orb/navbar 삭제. `js/dashboard-glass.js`는 유지하고 그 뒤에 theme.js. 최종 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="js/dashboard-glass.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="reviews.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh reviews.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate reviews.html"`

### Task 15: goals.html

**Files:** Modify: `src/main/resources/static/goals.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh goals.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="goals.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh goals.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate goals.html"`

### Task 16: journal.html

**Files:** Modify: `src/main/resources/static/journal.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh journal.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지 (기존에 있으면 그대로)
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트 (utils.js는 원래 없었음 — 추가하지 않는다):

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="js/dashboard-glass.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="journal.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh journal.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate journal.html"`

### Task 17: risk.html

**Files:** Modify: `src/main/resources/static/risk.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh risk.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb 삭제, `js/navigation-glass.js`/`js/theme-toggle.js` 제거. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="risk.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh risk.html` → `OK` (stagger-5는 Task 2에서 정의됨)
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate risk.html"`

### Task 18: dividend.html + dividend.js

**Files:** Modify: `src/main/resources/static/dividend.html`, `src/main/resources/static/dividend.js`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh dividend.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록. CDN 유지: chart.umd 4.4.0
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="dividend.js"></script>
```

- [ ] **Step 4: 차트 색** — 상수(~25–26행): `'rgba(75, 192, 192, 0.8)'` → `TJTheme.rgba('info', 0.8)`, `'rgba(75, 192, 192, 1)'` → `TJTheme.color('info')`
- [ ] **Step 5: 문법+그린** — `node --check dividend.js && scripts/check-frontend.sh dividend.html` → `OK`
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): migrate dividend page, themed chart colors"`

### Task 19: alerts.html

**Files:** Modify: `src/main/resources/static/alerts.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh alerts.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록 (bootstrap-icons 1.7.2 → 1.11.0)
- [ ] **Step 3: body 정리** — orb/navbar 삭제. **주의**: 이 페이지의 인라인 스크립트에 있는 `toggleMobileMenu`/`checkScreenSize` 함수와 그 이벤트 바인딩(구 navbar용)을 삭제한다. 단 같은 인라인 스크립트의 filter-tab 로직(`window.filterAlertsOriginal` 위임)은 **유지**. 최종 외부 스크립트:

```html
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="js/dashboard-glass.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="alerts.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh alerts.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate alerts.html"`

### Task 20: ai-assistant.html

**Files:** Modify: `src/main/resources/static/ai-assistant.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh ai-assistant.html` → FAIL
- [ ] **Step 2: head 교체** — 표준 head 블록 (폰트 link 신규 추가, icons 1.7.2 → 1.11.0). 페이지 인라인 `<style>`(채팅 UI) 유지
- [ ] **Step 3: body 정리** — orb/navbar 삭제. 최종 스크립트:

```html
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
    <script src="ai-assistant.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh ai-assistant.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate ai-assistant.html"`

### Task 21: export.html + login.html

**Files:** Modify: `src/main/resources/static/export.html`, `src/main/resources/static/login.html`

- [ ] **Step 1: 레드** — `scripts/check-frontend.sh export.html login.html` → FAIL
- [ ] **Step 2: export.html** — 표준 head 블록, orb/navbar 삭제. 최종 스크립트:

```html
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/glass-utils.js"></script>
    <script src="js/theme.js"></script>
    <script src="js/navigation.js"></script>
```

- [ ] **Step 3: login.html** — 표준 head 블록 적용. **사이드바 없음**: `js/navigation.js`를 넣지 않는다 (check 스크립트가 login은 예외 처리). orb 마크업이 있으면 유지 가능하나 일관성 위해 삭제. 인라인 login-* 스타일 유지. 최종 스크립트:

```html
    <script src="js/glass-utils.js"></script>
    <script src="utils.js"></script>
    <script src="auth.js"></script>
    <script src="js/theme.js"></script>
```

- [ ] **Step 4: 그린** — `scripts/check-frontend.sh export.html login.html` → `OK`
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): migrate export.html and login.html"`

---

### Task 22: 구 파일 삭제 + 전체 검증

**Files:**
- Delete: `static/dashboard-new.html`, `static/dashboard-original.html`, `static/dashboard-custom.html`, `static/dashboard-custom.js`, `static/dashboard.js`, `static/charts.js`, `static/style.css`, `static/css/dashboard-glass.css`, `static/js/navigation-glass.js`, `static/js/theme-toggle.js`

- [ ] **Step 1: 잔여 참조 확인** (삭제 전 — 참조가 있으면 해당 페이지 태스크가 누락된 것):

Run: `cd src/main/resources/static && grep -rn "dashboard-glass.css\|navigation-glass.js\|theme-toggle.js\|dashboard-new\|dashboard-original\|dashboard-custom\|charts.js\|style.css" --include='*.html' --include='*.js' . | grep -v 'js/dashboard-glass.js\|css/design-system.css'`
Expected: 출력 없음

- [ ] **Step 2: 삭제**

```bash
cd src/main/resources/static
git rm dashboard-new.html dashboard-original.html dashboard-custom.html \
       dashboard-custom.js dashboard.js charts.js style.css \
       css/dashboard-glass.css js/navigation-glass.js js/theme-toggle.js
```

- [ ] **Step 3: 전체 그린** — Run: `scripts/check-frontend.sh --all` → Expected: `OK`
- [ ] **Step 4: Commit** — `git commit -m "chore(ui): remove legacy dashboards, old css/nav/theme modules"`

### Task 23: 최종 검증

- [ ] **Step 1: 백엔드 회귀 없음** — Run: `./gradlew check` → Expected: BUILD SUCCESSFUL
- [ ] **Step 2: 시각 검증** — 앱 실행 (`./gradlew bootRun --args='--spring.profiles.active=local'` 또는 프로젝트 표준 실행법) 후 17개 페이지 × 다크/라이트를 브라우저에서 확인. 페이지마다: ① 사이드바 표시+현재 페이지 하이라이트 ② 카드가 불투명 surface ③ 숫자 모노스페이스 ④ 차트 렌더+테마 색 ⑤ 테마 토글 시 즉시 전환·새로고침 후 유지 ⑥ 1024px 미만에서 드로어 동작. 발견된 문제는 이 계획 파일 하단에 기록하고 수정 커밋.
- [ ] **Step 3: 스펙 상태 갱신** — `docs/superpowers/specs/2026-07-06-ui-design-overhaul-design.md`의 상태를 `구현 완료`로 변경, 커밋.

---

## Self-Review 결과 (작성 시 수행)

- 스펙 커버리지: 3.1 토큰(Task 2 A–D), 3.2 타이포(.num, h1 — Task 2 G/H; 페이지 적용은 기존 stat-card-value가 이미 mono), 3.3 컴포넌트(기존 어휘 유지+심), 3.4 차트(Task 1 defaults + Task 8–12/18 상수 교체), 3.5 모션(기존 reduced-motion 블록 존치 확인), 4.1–4.3 레이아웃/사이드바/반응형(Task 2 E–F, Task 3), 5.2 삭제(Task 22), 6 엣지(FOUC 스니펫, 드로어, 주입 실패 시 본문 표시), 7 검증(Task 4, 22, 23) — 전부 태스크에 매핑됨.
- 알려진 한계 (스펙 6 관련): 테마 토글 시 차트의 축/그리드/툴팁은 즉시 갱신되지만, 생성 시점에 지정된 데이터셋 색(시맨틱)은 페이지 새로고침 시 갱신된다. 필요 시 각 페이지가 `TJTheme.onThemeChange`로 재렌더 가능.
- 타입 일관성: `TJTheme.{cssVar,color,rgba,chartPalette,onThemeChange,applyChartDefaults}` — Task 1 정의와 Task 8–18 사용 일치. `ThemeToggle` API 기존 시그니처 유지.
