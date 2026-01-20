/**
 * Authentication Module for Trading Journal
 * Handles JWT token management, login/logout, and authenticated API requests.
 *
 * @fileoverview Provides authentication utilities including:
 * - Token storage and retrieval (access and refresh tokens)
 * - Login and logout functionality
 * - Automatic token refresh
 * - jQuery AJAX authentication setup
 * - Protected route guards
 *
 * @requires utils.js - For ToastNotification (optional, graceful fallback)
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Authentication API endpoint base URL
 * @constant {string}
 */
const AUTH_API_URL = '/api/auth';

/**
 * Authentication-related API endpoints
 * @constant {Object}
 */
const AUTH_ENDPOINTS = {
    LOGIN: `${AUTH_API_URL}/login`,
    REFRESH: `${AUTH_API_URL}/refresh`,
    ME: `${AUTH_API_URL}/me`,
    PASSWORD: `${AUTH_API_URL}/password`
};

/**
 * LocalStorage keys for token storage
 * @constant {Object}
 */
const TOKEN_KEYS = {
    ACCESS: 'jwt_access_token',
    REFRESH: 'jwt_refresh_token'
};

/**
 * Page URLs for navigation
 * @constant {Object}
 */
const AUTH_PAGES = {
    LOGIN: '/login.html',
    MAIN: '/index.html'
};

/**
 * Bearer token prefix for Authorization header
 * @constant {string}
 */
const BEARER_PREFIX = 'Bearer ';

// ============================================================================
// TOKEN MANAGEMENT
// ============================================================================

/**
 * Check if user is authenticated by verifying access token exists.
 * @returns {boolean} True if access token exists in localStorage
 */
function isAuthenticated() {
    return !!localStorage.getItem(TOKEN_KEYS.ACCESS);
}

/**
 * Get the current access token from localStorage.
 * @returns {string|null} The access token or null if not found
 */
function getAccessToken() {
    return localStorage.getItem(TOKEN_KEYS.ACCESS);
}

/**
 * Get the current refresh token from localStorage.
 * @returns {string|null} The refresh token or null if not found
 */
function getRefreshToken() {
    return localStorage.getItem(TOKEN_KEYS.REFRESH);
}

/**
 * Store authentication tokens in localStorage.
 * Only stores refresh token if provided (supports optional refresh token).
 * @param {string} accessToken - The JWT access token
 * @param {string} [refreshToken] - The JWT refresh token (optional)
 */
function storeTokens(accessToken, refreshToken) {
    localStorage.setItem(TOKEN_KEYS.ACCESS, accessToken);
    if (refreshToken) {
        localStorage.setItem(TOKEN_KEYS.REFRESH, refreshToken);
    }
}

/**
 * Clear all authentication tokens from localStorage.
 * Called on logout or when tokens are invalid.
 */
function clearTokens() {
    localStorage.removeItem(TOKEN_KEYS.ACCESS);
    localStorage.removeItem(TOKEN_KEYS.REFRESH);
}

// ============================================================================
// NAVIGATION
// ============================================================================

/**
 * Redirect user to the login page.
 * Used when authentication fails or user logs out.
 */
function redirectToLogin() {
    window.location.href = AUTH_PAGES.LOGIN;
}

/**
 * Redirect user to the main application page.
 * Used after successful login.
 */
function redirectToMain() {
    window.location.href = AUTH_PAGES.MAIN;
}

// ============================================================================
// AUTHENTICATION ACTIONS
// ============================================================================

/**
 * Handle user login by sending credentials to the server.
 * On success, stores the returned tokens in localStorage.
 *
 * @param {string} username - The user's username
 * @param {string} password - The user's password
 * @returns {Promise<Object>} jQuery promise resolving to the login response
 * @example
 * handleLogin('user@example.com', 'password123')
 *   .done(() => redirectToMain())
 *   .fail((xhr) => handleAjaxError(xhr, 'Login failed'));
 */
function handleLogin(username, password) {
    return $.ajax({
        url: AUTH_ENDPOINTS.LOGIN,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ username, password })
    }).done(function(response) {
        storeTokens(response.accessToken, response.refreshToken);
    });
}

/**
 * Refresh the access token using the stored refresh token.
 * On success, stores the new tokens. On failure, clears tokens and redirects to login.
 *
 * @returns {Promise<Object>} jQuery promise resolving to the refresh response
 */
function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
        clearTokens();
        redirectToLogin();
        return Promise.reject(new Error('No refresh token available'));
    }

    return $.ajax({
        url: AUTH_ENDPOINTS.REFRESH,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ refreshToken })
    }).done(function(response) {
        storeTokens(response.accessToken, response.refreshToken);
    }).fail(function() {
        clearTokens();
        redirectToLogin();
    });
}

/**
 * Log out the current user.
 * Clears all tokens and redirects to the login page.
 */
function logout() {
    clearTokens();
    redirectToLogin();
}

// ============================================================================
// AJAX AUTHENTICATION SETUP
// ============================================================================

/** @type {boolean} Flag to prevent multiple simultaneous redirects on 401 */
let isRedirecting = false;

/** @type {boolean} Flag to ensure AJAX auth setup only runs once */
let authSetupDone = false;

/**
 * Check if a URL is an authentication endpoint (login/refresh).
 * These endpoints should not have Authorization headers attached.
 *
 * @param {string} url - The URL to check
 * @returns {boolean} True if the URL is an auth endpoint
 * @private
 */
function isAuthEndpoint(url) {
    if (!url) return false;
    return url.includes(AUTH_ENDPOINTS.LOGIN) ||
           url.includes(AUTH_ENDPOINTS.REFRESH);
}

/**
 * Setup jQuery AJAX to automatically include Authorization header.
 * Also sets up global error handler for 401 responses.
 *
 * This function is idempotent - calling it multiple times has no additional effect.
 *
 * @example
 * // Call once at app initialization
 * setupAjaxAuth();
 * // All subsequent $.ajax calls will automatically include the auth header
 */
function setupAjaxAuth() {
    // Prevent multiple setup calls
    if (authSetupDone) {
        return;
    }
    authSetupDone = true;

    // Use ajaxPrefilter for reliable header injection
    $.ajaxPrefilter(function(options, originalOptions, jqXHR) {
        // Skip auth header for login/refresh endpoints
        if (isAuthEndpoint(options.url)) {
            return;
        }

        const token = getAccessToken();
        if (token) {
            jqXHR.setRequestHeader('Authorization', BEARER_PREFIX + token);
        }
    });

    // Global error handler for 401 responses
    $(document).off('ajaxError.auth').on('ajaxError.auth', function(event, jqXHR, settings) {
        // Skip if already redirecting or if it's an auth endpoint
        if (jqXHR.status !== 401 || isRedirecting) {
            return;
        }

        if (settings.url && settings.url.includes('/api/auth/')) {
            return;
        }

        console.log('401 error detected, redirecting to login');
        isRedirecting = true;
        clearTokens();
        redirectToLogin();
    });
}

// ============================================================================
// ROUTE PROTECTION
// ============================================================================

/**
 * Check authentication status on page load and setup AJAX auth.
 * Call this at the start of each protected page to guard against unauthorized access.
 *
 * @returns {boolean} True if authenticated, false if redirecting to login
 * @example
 * // At the top of a protected page's script
 * if (!checkAuth()) return;
 * // ... rest of the page initialization
 */
function checkAuth() {
    if (!isAuthenticated()) {
        redirectToLogin();
        return false;
    }
    setupAjaxAuth();
    return true;
}

// ============================================================================
// USER OPERATIONS
// ============================================================================

/**
 * Get the current authenticated user's information.
 *
 * @returns {Promise<Object>} jQuery promise resolving to user info
 * @example
 * getCurrentUser()
 *   .done((user) => console.log('Logged in as:', user.username))
 *   .fail((xhr) => handleAjaxError(xhr));
 */
function getCurrentUser() {
    return $.ajax({
        url: AUTH_ENDPOINTS.ME,
        method: 'GET',
        headers: {
            'Authorization': BEARER_PREFIX + getAccessToken()
        }
    });
}

/**
 * Change the current user's password.
 *
 * @param {string} currentPassword - The user's current password
 * @param {string} newPassword - The new password to set
 * @returns {Promise<Object>} jQuery promise resolving on success
 * @example
 * changePassword('oldPass123', 'newPass456')
 *   .done(() => ToastNotification.success('Password changed'))
 *   .fail((xhr) => handleAjaxError(xhr));
 */
function changePassword(currentPassword, newPassword) {
    return $.ajax({
        url: AUTH_ENDPOINTS.PASSWORD,
        method: 'POST',
        contentType: 'application/json',
        headers: {
            'Authorization': BEARER_PREFIX + getAccessToken()
        },
        data: JSON.stringify({ currentPassword, newPassword })
    });
}

// ============================================================================
// EXPORT TO GLOBAL SCOPE
// ============================================================================

if (typeof window !== 'undefined') {
    // Constants (for external use if needed)
    window.AUTH_API_URL = AUTH_API_URL;
    window.AUTH_ENDPOINTS = AUTH_ENDPOINTS;
    window.TOKEN_KEYS = TOKEN_KEYS;
    window.AUTH_PAGES = AUTH_PAGES;

    // Token Management
    window.isAuthenticated = isAuthenticated;
    window.getAccessToken = getAccessToken;
    window.getRefreshToken = getRefreshToken;
    window.storeTokens = storeTokens;
    window.clearTokens = clearTokens;

    // Navigation
    window.redirectToLogin = redirectToLogin;
    window.redirectToMain = redirectToMain;

    // Authentication Actions
    window.handleLogin = handleLogin;
    window.refreshAccessToken = refreshAccessToken;
    window.logout = logout;

    // AJAX Setup
    window.setupAjaxAuth = setupAjaxAuth;
    window.checkAuth = checkAuth;

    // User Operations
    window.getCurrentUser = getCurrentUser;
    window.changePassword = changePassword;
}
