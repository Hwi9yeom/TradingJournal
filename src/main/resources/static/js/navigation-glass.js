/**
 * Glass Navigation Module
 * Unified navigation component with glassmorphism design
 * Auto-injects navigation, detects current page, handles mobile menu, and includes theme toggle
 */
(function() {
    'use strict';

    const GlassNavigation = {
        /**
         * Navigation items configuration
         */
        navItems: [
            { href: 'dashboard.html', icon: 'bi-speedometer2', label: '대시보드', page: 'dashboard' },
            { href: 'patterns.html', icon: 'bi-diagram-3', label: '패턴분석', page: 'patterns' },
            { href: 'reviews.html', icon: 'bi-journal-text', label: '거래복기', page: 'reviews' },
            { href: 'risk.html', icon: 'bi-shield-check', label: '리스크', page: 'risk' },
            { href: 'sectors.html', icon: 'bi-pie-chart', label: '섹터', page: 'sectors' },
            { href: 'backtest.html', icon: 'bi-clock-history', label: '백테스트', page: 'backtest' },
            { href: 'ai-assistant.html', icon: 'bi-robot', label: 'AI', page: 'ai-assistant' },
            { href: 'index.html', icon: 'bi-list-ul', label: '거래', page: 'index' }
        ],

        /**
         * Initialize navigation on page load
         */
        init() {
            this.injectBackgroundOrbs();
            this.injectNavigation();
            this.setupEventListeners();
            this.setActivePage();
        },

        /**
         * Inject background orbs for glassmorphism effect
         */
        injectBackgroundOrbs() {
            // Only inject if they don't already exist
            if (document.querySelector('.bg-orb')) {
                return;
            }

            const orbs = `
                <div class="bg-orb bg-orb-1"></div>
                <div class="bg-orb bg-orb-2"></div>
                <div class="bg-orb bg-orb-3"></div>
            `;

            document.body.insertAdjacentHTML('afterbegin', orbs);
        },

        /**
         * Inject navigation HTML into the page
         */
        injectNavigation() {
            // Check if navigation already exists
            if (document.querySelector('.navbar-glass')) {
                return;
            }

            const navHTML = this.buildNavigationHTML();
            document.body.insertAdjacentHTML('afterbegin', navHTML);
        },

        /**
         * Build the complete navigation HTML structure
         */
        buildNavigationHTML() {
            const navItemsHTML = this.navItems.map(item =>
                `<li><a href="${item.href}" class="nav-link" data-page="${item.page}">
                    <i class="bi ${item.icon}"></i> ${item.label}
                </a></li>`
            ).join('');

            return `
                <nav class="navbar-glass">
                    <div class="container">
                        <a href="index.html" class="navbar-brand">
                            <span class="logo-icon"><i class="bi bi-graph-up-arrow"></i></span>
                            Trading Journal
                        </a>

                        <!-- Desktop Navigation -->
                        <ul class="navbar-nav desktop-nav">
                            ${navItemsHTML}
                            <li>
                                <button class="nav-link theme-toggle" data-theme-toggle title="테마 변경">
                                    <i class="bi bi-sun theme-toggle-icon sun"></i>
                                    <i class="bi bi-moon theme-toggle-icon moon"></i>
                                </button>
                            </li>
                            <li>
                                <a href="#" class="nav-link" onclick="logout()">
                                    <i class="bi bi-box-arrow-right"></i>
                                </a>
                            </li>
                        </ul>

                        <!-- Mobile Menu Toggle -->
                        <button class="mobile-menu-toggle" aria-label="메뉴 열기">
                            <i class="bi bi-list"></i>
                        </button>
                    </div>

                    <!-- Mobile Navigation -->
                    <div class="mobile-nav-overlay">
                        <div class="mobile-nav">
                            <div class="mobile-nav-header">
                                <span class="navbar-brand">
                                    <span class="logo-icon"><i class="bi bi-graph-up-arrow"></i></span>
                                    Trading Journal
                                </span>
                                <button class="mobile-nav-close" aria-label="메뉴 닫기">
                                    <i class="bi bi-x-lg"></i>
                                </button>
                            </div>
                            <ul class="mobile-nav-items">
                                ${navItemsHTML}
                                <li>
                                    <button class="nav-link theme-toggle" data-theme-toggle>
                                        <i class="bi bi-sun theme-toggle-icon sun"></i>
                                        <i class="bi bi-moon theme-toggle-icon moon"></i>
                                        <span>테마 변경</span>
                                    </button>
                                </li>
                                <li>
                                    <a href="#" class="nav-link" onclick="logout()">
                                        <i class="bi bi-box-arrow-right"></i> 로그아웃
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </div>
                </nav>
            `;
        },

        /**
         * Setup event listeners for mobile menu and theme toggle
         */
        setupEventListeners() {
            // Mobile menu toggle
            const menuToggle = document.querySelector('.mobile-menu-toggle');
            const menuClose = document.querySelector('.mobile-nav-close');
            const mobileOverlay = document.querySelector('.mobile-nav-overlay');

            if (menuToggle) {
                menuToggle.addEventListener('click', () => this.openMobileMenu());
            }

            if (menuClose) {
                menuClose.addEventListener('click', () => this.closeMobileMenu());
            }

            if (mobileOverlay) {
                mobileOverlay.addEventListener('click', (e) => {
                    if (e.target === mobileOverlay) {
                        this.closeMobileMenu();
                    }
                });
            }

            // Close mobile menu when a nav link is clicked
            document.querySelectorAll('.mobile-nav-items .nav-link').forEach(link => {
                link.addEventListener('click', () => {
                    setTimeout(() => this.closeMobileMenu(), 100);
                });
            });

            // Keyboard accessibility
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && mobileOverlay?.classList.contains('active')) {
                    this.closeMobileMenu();
                }
            });

            // Theme toggle is handled by theme-toggle.js
            // It automatically attaches to elements with .theme-toggle class
        },

        /**
         * Open mobile menu
         */
        openMobileMenu() {
            const overlay = document.querySelector('.mobile-nav-overlay');
            if (overlay) {
                overlay.classList.add('active');
                document.body.style.overflow = 'hidden';
            }
        },

        /**
         * Close mobile menu
         */
        closeMobileMenu() {
            const overlay = document.querySelector('.mobile-nav-overlay');
            if (overlay) {
                overlay.classList.remove('active');
                document.body.style.overflow = '';
            }
        },

        /**
         * Detect current page and set active class on the correct nav link
         */
        setActivePage() {
            const currentPage = this.getCurrentPage();

            // Remove any existing active classes
            document.querySelectorAll('.nav-link').forEach(link => {
                link.classList.remove('active');
            });

            // Add active class to matching nav links (both desktop and mobile)
            document.querySelectorAll(`.nav-link[data-page="${currentPage}"]`).forEach(link => {
                link.classList.add('active');
            });
        },

        /**
         * Get the current page identifier from the URL
         */
        getCurrentPage() {
            const path = window.location.pathname;
            const filename = path.substring(path.lastIndexOf('/') + 1);

            // Map filename to page identifier
            const pageMap = {
                'dashboard.html': 'dashboard',
                'patterns.html': 'patterns',
                'reviews.html': 'reviews',
                'journal.html': 'reviews', // Alias for reviews
                'risk.html': 'risk',
                'sectors.html': 'sectors',
                'statistics.html': 'sectors', // Alias for sectors
                'backtest.html': 'backtest',
                'ai-assistant.html': 'ai-assistant',
                'index.html': 'index',
                '': 'index' // Default to index
            };

            return pageMap[filename] || 'index';
        },

        /**
         * Inject required CSS styles if not already present
         */
        injectStyles() {
            if (document.getElementById('glass-navigation-styles')) {
                return;
            }

            const styles = document.createElement('style');
            styles.id = 'glass-navigation-styles';
            styles.textContent = `
                /* Mobile Menu Styles */
                .mobile-menu-toggle {
                    display: none;
                    background: none;
                    border: none;
                    color: var(--text-primary);
                    font-size: 1.5rem;
                    cursor: pointer;
                    padding: var(--space-2);
                    transition: var(--transition-base);
                }

                .mobile-menu-toggle:hover {
                    color: var(--text-primary);
                    transform: scale(1.1);
                }

                .mobile-nav-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.7);
                    backdrop-filter: blur(4px);
                    z-index: 9999;
                    opacity: 0;
                    visibility: hidden;
                    transition: var(--transition-base);
                }

                .mobile-nav-overlay.active {
                    opacity: 1;
                    visibility: visible;
                }

                .mobile-nav {
                    position: fixed;
                    top: 0;
                    right: 0;
                    bottom: 0;
                    width: 280px;
                    max-width: 80vw;
                    background: var(--glass-bg);
                    backdrop-filter: var(--glass-blur);
                    -webkit-backdrop-filter: var(--glass-blur);
                    border-left: 1px solid var(--glass-border);
                    padding: var(--space-6);
                    transform: translateX(100%);
                    transition: transform var(--transition-base);
                    overflow-y: auto;
                }

                .mobile-nav-overlay.active .mobile-nav {
                    transform: translateX(0);
                }

                .mobile-nav-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: var(--space-8);
                    padding-bottom: var(--space-4);
                    border-bottom: 1px solid var(--glass-border);
                }

                .mobile-nav-close {
                    background: none;
                    border: none;
                    color: var(--text-primary);
                    font-size: 1.25rem;
                    cursor: pointer;
                    padding: var(--space-2);
                    transition: var(--transition-base);
                }

                .mobile-nav-close:hover {
                    transform: scale(1.1);
                }

                .mobile-nav-items {
                    list-style: none;
                    display: flex;
                    flex-direction: column;
                    gap: var(--space-2);
                }

                .mobile-nav-items .nav-link {
                    width: 100%;
                    justify-content: flex-start;
                    padding: var(--space-3) var(--space-4);
                }

                /* Theme Toggle Button Styles */
                .theme-toggle {
                    background: none;
                    border: none;
                    cursor: pointer;
                    position: relative;
                }

                .theme-toggle-icon {
                    transition: var(--transition-base);
                }

                .theme-toggle-icon.sun {
                    color: var(--color-warning);
                }

                .theme-toggle-icon.moon {
                    color: var(--color-info);
                }

                /* Desktop nav visibility */
                .desktop-nav {
                    display: flex;
                }

                /* Responsive breakpoint */
                @media (max-width: 1024px) {
                    .desktop-nav {
                        display: none;
                    }

                    .mobile-menu-toggle {
                        display: block;
                    }
                }

                /* Ensure navbar items align properly */
                .navbar-nav li {
                    display: flex;
                    align-items: center;
                }
            `;

            document.head.appendChild(styles);
        }
    };

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            GlassNavigation.injectStyles();
            GlassNavigation.init();
        });
    } else {
        GlassNavigation.injectStyles();
        GlassNavigation.init();
    }

    // Expose API globally
    window.GlassNavigation = GlassNavigation;
})();
