/**
 * Sidebar Navigation Module — 구 navigation-glass 모듈을 대체.
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
                { href: 'budget.html', icon: 'bi-piggy-bank', label: '가계·저축' },
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
            <button class="sidebar-toggle-btn" aria-label="메뉴 열기" aria-expanded="false"><i class="bi bi-list"></i></button>
            <div class="sidebar-overlay"></div>
            <nav class="sidebar-glass" aria-label="주 메뉴">
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
            </nav>`;
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

        const open = () => {
            sidebar.classList.add('open');
            overlay.classList.add('active');
            toggle.setAttribute('aria-expanded', 'true');
            document.body.style.overflow = 'hidden';
        };
        const close = () => {
            sidebar.classList.remove('open');
            overlay.classList.remove('active');
            toggle.setAttribute('aria-expanded', 'false');
            document.body.style.overflow = '';
        };

        toggle.addEventListener('click', () => sidebar.classList.contains('open') ? close() : open());
        overlay.addEventListener('click', close);
        document.addEventListener('keydown', e => { if (e.key === 'Escape' && sidebar.classList.contains('open')) close(); });
        sidebar.querySelectorAll('a.nav-link').forEach(a =>
            a.addEventListener('click', () => setTimeout(close, 100)));
    }

    function init() {
        if (document.querySelector('.sidebar-glass')) return; // 중복 주입 방지
        injectBackgroundOrbs();
        document.body.insertAdjacentHTML('afterbegin', buildSidebarHTML());
        setupDrawer();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
