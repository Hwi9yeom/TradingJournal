/**
 * Theme Module — design-system의 테마 관리 + Chart.js 테마 연동
 * 구 theme-toggle 모듈을 대체한다. localStorage 키/전역 API/이벤트는 기존과 호환.
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

    // 구 theme-toggle 모듈과 호환되는 전역 API (init 이전에 노출)
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
