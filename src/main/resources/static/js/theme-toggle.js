/**
 * Theme Toggle Module
 * Handles dark/light theme switching with localStorage persistence
 */
(function() {
    'use strict';

    const THEME_KEY = 'trading-journal-theme';
    const DARK_THEME = 'dark';
    const LIGHT_THEME = 'light';

    /**
     * Get the current theme from localStorage or system preference
     */
    function getStoredTheme() {
        const stored = localStorage.getItem(THEME_KEY);
        if (stored) return stored;

        // Check system preference
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
            return LIGHT_THEME;
        }
        return DARK_THEME; // Default to dark
    }

    /**
     * Apply theme to document
     */
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        updateToggleIcons(theme);

        // Dispatch custom event for other components
        window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
    }

    /**
     * Update toggle button icons visibility
     */
    function updateToggleIcons(theme) {
        const sunIcons = document.querySelectorAll('.theme-toggle-icon.sun');
        const moonIcons = document.querySelectorAll('.theme-toggle-icon.moon');

        sunIcons.forEach(icon => {
            icon.style.display = theme === DARK_THEME ? 'inline' : 'none';
        });
        moonIcons.forEach(icon => {
            icon.style.display = theme === LIGHT_THEME ? 'inline' : 'none';
        });
    }

    /**
     * Toggle between dark and light themes
     */
    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || DARK_THEME;
        const newTheme = currentTheme === DARK_THEME ? LIGHT_THEME : DARK_THEME;

        localStorage.setItem(THEME_KEY, newTheme);
        applyTheme(newTheme);
    }

    /**
     * Initialize theme on page load
     */
    function initTheme() {
        const theme = getStoredTheme();
        applyTheme(theme);
    }

    /**
     * Setup event listeners for theme toggle buttons
     */
    function setupToggleButtons() {
        document.querySelectorAll('.theme-toggle, [data-theme-toggle]').forEach(button => {
            button.addEventListener('click', toggleTheme);
        });
    }

    /**
     * Listen for system theme changes
     */
    function listenForSystemChanges() {
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
                // Only update if user hasn't manually set a preference
                if (!localStorage.getItem(THEME_KEY)) {
                    applyTheme(e.matches ? DARK_THEME : LIGHT_THEME);
                }
            });
        }
    }

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initTheme();
            setupToggleButtons();
            listenForSystemChanges();
        });
    } else {
        initTheme();
        setupToggleButtons();
        listenForSystemChanges();
    }

    // Expose API globally
    window.ThemeToggle = {
        toggle: toggleTheme,
        setTheme: (theme) => {
            if (theme === DARK_THEME || theme === LIGHT_THEME) {
                localStorage.setItem(THEME_KEY, theme);
                applyTheme(theme);
            }
        },
        getTheme: () => document.documentElement.getAttribute('data-theme') || DARK_THEME,
        isDark: () => (document.documentElement.getAttribute('data-theme') || DARK_THEME) === DARK_THEME
    };
})();
