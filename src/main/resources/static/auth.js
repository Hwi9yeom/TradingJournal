/**
 * Authentication Module for Trading Journal
 */

const AUTH_API_URL = '/api/auth';
const TOKEN_KEY = 'jwt_access_token';
const REFRESH_TOKEN_KEY = 'jwt_refresh_token';

/**
 * Check if user is authenticated
 */
function isAuthenticated() {
    return !!localStorage.getItem(TOKEN_KEY);
}

/**
 * Get access token
 */
function getAccessToken() {
    return localStorage.getItem(TOKEN_KEY);
}

/**
 * Get refresh token
 */
function getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/**
 * Store tokens in localStorage
 */
function storeTokens(accessToken, refreshToken) {
    localStorage.setItem(TOKEN_KEY, accessToken);
    if (refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
}

/**
 * Clear tokens from localStorage
 */
function clearTokens() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
}

/**
 * Redirect to login page
 */
function redirectToLogin() {
    window.location.href = '/login.html';
}

/**
 * Redirect to main page
 */
function redirectToMain() {
    window.location.href = '/index.html';
}

/**
 * Handle login
 */
function handleLogin(username, password) {
    return $.ajax({
        url: `${AUTH_API_URL}/login`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ username, password })
    }).done(function(response) {
        storeTokens(response.accessToken, response.refreshToken);
    });
}

/**
 * Refresh access token using refresh token
 */
function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
        clearTokens();
        redirectToLogin();
        return Promise.reject('No refresh token');
    }

    return $.ajax({
        url: `${AUTH_API_URL}/refresh`,
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
 * Logout - clear tokens and redirect to login
 */
function logout() {
    clearTokens();
    redirectToLogin();
}

/**
 * Setup jQuery AJAX to include Authorization header
 */
function setupAjaxAuth() {
    $.ajaxSetup({
        beforeSend: function(xhr, settings) {
            // Skip auth header for login/refresh endpoints
            if (settings.url && (
                settings.url.includes('/api/auth/login') ||
                settings.url.includes('/api/auth/refresh'))) {
                return;
            }

            const token = getAccessToken();
            if (token) {
                xhr.setRequestHeader('Authorization', 'Bearer ' + token);
            }
        }
    });

    // Global error handler for 401 responses
    $(document).ajaxError(function(event, jqXHR, settings) {
        if (jqXHR.status === 401) {
            // Try to refresh token first
            const refreshToken = getRefreshToken();
            if (refreshToken && !settings.url.includes('/api/auth/')) {
                refreshAccessToken().fail(function() {
                    redirectToLogin();
                });
            } else {
                clearTokens();
                redirectToLogin();
            }
        }
    });
}

/**
 * Check authentication on page load
 * Call this at the start of each protected page
 */
function checkAuth() {
    if (!isAuthenticated()) {
        redirectToLogin();
        return false;
    }
    setupAjaxAuth();
    return true;
}

/**
 * Get current user info
 */
function getCurrentUser() {
    return $.ajax({
        url: `${AUTH_API_URL}/me`,
        method: 'GET',
        headers: {
            'Authorization': 'Bearer ' + getAccessToken()
        }
    });
}

/**
 * Change password
 */
function changePassword(currentPassword, newPassword) {
    return $.ajax({
        url: `${AUTH_API_URL}/password`,
        method: 'POST',
        contentType: 'application/json',
        headers: {
            'Authorization': 'Bearer ' + getAccessToken()
        },
        data: JSON.stringify({ currentPassword, newPassword })
    });
}
