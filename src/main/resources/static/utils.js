/**
 * Trading Journal - Utility Functions
 * Centralized error handling, notifications, and common utilities
 *
 * @fileoverview Provides common utilities used across all pages including:
 * - Toast notifications
 * - Loading overlays
 * - Form validation
 * - Formatting functions (currency, percent, date)
 * - DOM utilities
 * - Date utilities
 * - Debounce/throttle functions
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Common CSS class names for styling elements based on value
 * @constant {Object}
 */
const CSS_CLASSES = {
    SUCCESS: 'text-success',
    DANGER: 'text-danger',
    WARNING: 'text-warning',
    MUTED: 'text-muted',
    INFO: 'text-info',
    PRIMARY: 'text-primary'
};

/**
 * Period to months mapping for date range calculations
 * @constant {Object}
 */
const PERIOD_MONTHS = {
    '1M': 1,
    '3M': 3,
    '6M': 6,
    '1Y': 12,
    'ALL': null  // ALL uses ALL_PERIOD_START_YEAR
};

/**
 * Start year for 'ALL' period calculations
 * @constant {number}
 */
const ALL_PERIOD_START_YEAR = 2020;

/**
 * Default debounce wait time in milliseconds
 * @constant {number}
 */
const DEFAULT_DEBOUNCE_WAIT = 300;

/**
 * Default throttle limit time in milliseconds
 * @constant {number}
 */
const DEFAULT_THROTTLE_LIMIT = 300;

// ============================================================================
// TOAST NOTIFICATION SYSTEM
// ============================================================================

/**
 * Toast notification system for displaying user feedback messages.
 * Supports success, error, warning, and info notification types.
 * @namespace
 */
const ToastNotification = {
    container: null,

    /**
     * Initialize the toast container if not already present
     */
    init() {
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.className = 'toast-container';
            this.container.setAttribute('role', 'region');
            this.container.setAttribute('aria-label', 'Notifications');
            document.body.appendChild(this.container);
        }
    },

    /**
     * Display a toast notification
     * @param {string} message - The message to display
     * @param {('info'|'success'|'warning'|'error')} [type='info'] - The notification type
     * @param {number} [duration=5000] - Duration in ms before auto-dismiss (0 for no auto-dismiss)
     * @returns {HTMLElement} The created toast element
     */
    show(message, type = 'info', duration = 5000) {
        this.init();

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'polite');
        toast.setAttribute('aria-atomic', 'true');

        const icon = this.getIcon(type);
        const closeBtn = document.createElement('button');
        closeBtn.innerHTML = '&times;';
        closeBtn.className = 'btn-close';
        closeBtn.setAttribute('aria-label', '닫기');
        closeBtn.style.cssText = 'border: none; background: none; font-size: 1.5rem; cursor: pointer; padding: 0; margin-left: auto;';
        closeBtn.onclick = () => this.remove(toast);

        toast.innerHTML = `
            <div style="display: flex; align-items: flex-start; gap: 0.75rem; width: 100%;">
                <span style="font-size: 1.25rem; flex-shrink: 0;">${icon}</span>
                <div style="flex: 1;">
                    <div style="font-weight: 600; margin-bottom: 0.25rem;">${this.getTitle(type)}</div>
                    <div style="font-size: 0.875rem; color: var(--color-text-secondary);">${escapeHtml(message)}</div>
                </div>
            </div>
        `;
        toast.appendChild(closeBtn);

        this.container.appendChild(toast);

        // Auto remove after duration
        if (duration > 0) {
            setTimeout(() => this.remove(toast), duration);
        }

        return toast;
    },

    /**
     * Remove a toast notification with animation
     * @param {HTMLElement} toast - The toast element to remove
     */
    remove(toast) {
        toast.style.animation = 'slideOutRight 0.3s ease-out';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    },

    /**
     * Get the icon for a notification type
     * @param {string} type - The notification type
     * @returns {string} The icon character
     */
    getIcon(type) {
        const icons = {
            success: '✓',
            error: '✕',
            warning: '⚠',
            info: 'ℹ'
        };
        return icons[type] || icons.info;
    },

    /**
     * Get the title for a notification type
     * @param {string} type - The notification type
     * @returns {string} The title text
     */
    getTitle(type) {
        const titles = {
            success: '성공',
            error: '오류',
            warning: '경고',
            info: '알림'
        };
        return titles[type] || titles.info;
    },

    /**
     * Display a success notification
     * @param {string} message - The message to display
     * @param {number} [duration] - Duration in ms before auto-dismiss
     * @returns {HTMLElement} The created toast element
     */
    success(message, duration) {
        return this.show(message, 'success', duration);
    },

    /**
     * Display an error notification
     * @param {string} message - The message to display
     * @param {number} [duration] - Duration in ms before auto-dismiss
     * @returns {HTMLElement} The created toast element
     */
    error(message, duration) {
        return this.show(message, 'error', duration);
    },

    /**
     * Display a warning notification
     * @param {string} message - The message to display
     * @param {number} [duration] - Duration in ms before auto-dismiss
     * @returns {HTMLElement} The created toast element
     */
    warning(message, duration) {
        return this.show(message, 'warning', duration);
    },

    /**
     * Display an info notification
     * @param {string} message - The message to display
     * @param {number} [duration] - Duration in ms before auto-dismiss
     * @returns {HTMLElement} The created toast element
     */
    info(message, duration) {
        return this.show(message, 'info', duration);
    }
};

// Add CSS animation for slideOutRight
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOutRight {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
    .btn-close:hover {
        opacity: 0.7;
    }
`;
document.head.appendChild(style);

// ============================================================================
// LOADING OVERLAY
// ============================================================================

/**
 * Loading overlay for indicating background operations.
 * Shows a spinner with customizable message.
 * @namespace
 */
const LoadingOverlay = {
    overlay: null,

    /**
     * Show the loading overlay
     * @param {string} [message='로딩 중...'] - The message to display
     */
    show(message = '로딩 중...') {
        if (!this.overlay) {
            this.overlay = document.createElement('div');
            this.overlay.className = 'loading-overlay';
            this.overlay.setAttribute('role', 'alert');
            this.overlay.setAttribute('aria-busy', 'true');
            this.overlay.setAttribute('aria-label', message);
            this.overlay.innerHTML = `
                <div style="text-align: center; color: white;">
                    <div class="loading-spinner"></div>
                    <div style="margin-top: 1rem; font-size: 1.125rem;">${escapeHtml(message)}</div>
                </div>
            `;
            document.body.appendChild(this.overlay);
            document.body.style.overflow = 'hidden';
        }
    },

    /**
     * Hide the loading overlay
     */
    hide() {
        if (this.overlay) {
            document.body.removeChild(this.overlay);
            this.overlay = null;
            document.body.style.overflow = '';
        }
    }
};

// ============================================================================
// DIALOG UTILITIES
// ============================================================================

/**
 * Show a confirmation dialog using Bootstrap Modal or native confirm.
 * @param {string} title - The dialog title
 * @param {string} message - The dialog message
 * @param {Function} [onConfirm] - Callback when user confirms
 * @param {Function} [onCancel] - Callback when user cancels
 */
function showConfirmDialog(title, message, onConfirm, onCancel) {
    // Check if Bootstrap modal is available
    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        // Create modal element
        const modalId = 'confirmModal-' + Date.now();
        const modalHtml = `
            <div class="modal fade" id="${modalId}" tabindex="-1" aria-labelledby="${modalId}Label" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="${modalId}Label">${escapeHtml(title)}</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="닫기"></button>
                        </div>
                        <div class="modal-body">
                            ${escapeHtml(message)}
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">취소</button>
                            <button type="button" class="btn btn-primary" id="${modalId}Confirm">확인</button>
                        </div>
                    </div>
                </div>
            </div>
        `;

        const modalContainer = document.createElement('div');
        modalContainer.innerHTML = modalHtml;
        document.body.appendChild(modalContainer);

        const modalEl = document.getElementById(modalId);
        const modal = new bootstrap.Modal(modalEl);

        document.getElementById(`${modalId}Confirm`).addEventListener('click', () => {
            modal.hide();
            if (onConfirm) onConfirm();
        });

        modalEl.addEventListener('hidden.bs.modal', () => {
            document.body.removeChild(modalContainer);
            if (onCancel) onCancel();
        });

        modal.show();
    } else {
        // Fallback to native confirm
        if (confirm(`${title}\n\n${message}`)) {
            if (onConfirm) onConfirm();
        } else {
            if (onCancel) onCancel();
        }
    }
}

// ============================================================================
// AJAX ERROR HANDLING
// ============================================================================

/**
 * Handle AJAX errors and display appropriate user feedback.
 * Extracts error message from response and shows toast notification.
 * @param {XMLHttpRequest} xhr - The XMLHttpRequest object
 * @param {string} [defaultMessage='요청 처리 중 오류가 발생했습니다.'] - Default error message
 */
function handleAjaxError(xhr, defaultMessage = '요청 처리 중 오류가 발생했습니다.') {
    let errorMessage = defaultMessage;

    if (xhr.responseJSON && xhr.responseJSON.message) {
        errorMessage = xhr.responseJSON.message;
    } else if (xhr.responseText) {
        try {
            const response = JSON.parse(xhr.responseText);
            errorMessage = response.message || response.error || defaultMessage;
        } catch (e) {
            errorMessage = xhr.statusText || defaultMessage;
        }
    }

    // Log to console for debugging
    console.error('AJAX Error:', {
        status: xhr.status,
        statusText: xhr.statusText,
        response: xhr.responseJSON || xhr.responseText
    });

    // Show user-friendly error (skip for 401 which is handled globally by auth.js)
    if (xhr.status !== 401) {
        ToastNotification.error(errorMessage);
    }
}

// ============================================================================
// FORM VALIDATION
// ============================================================================

/**
 * Validate a form element by checking all required fields.
 * Adds Bootstrap validation classes and feedback messages.
 * @param {HTMLFormElement} formElement - The form element to validate
 * @returns {boolean} True if form is valid, false otherwise
 */
function validateForm(formElement) {
    let isValid = true;
    const inputs = formElement.querySelectorAll('input[required], select[required], textarea[required]');

    inputs.forEach(input => {
        const feedback = input.parentElement.querySelector('.invalid-feedback');

        if (!input.value.trim()) {
            input.classList.add('is-invalid');
            input.classList.remove('is-valid');
            if (feedback) {
                feedback.textContent = '필수 입력 항목입니다.';
            }
            isValid = false;
        } else {
            input.classList.remove('is-invalid');
            input.classList.add('is-valid');
        }
    });

    return isValid;
}

/**
 * Clear all validation states from a form.
 * Removes is-invalid and is-valid classes from all inputs.
 * @param {HTMLFormElement} formElement - The form element to clear
 */
function clearFormValidation(formElement) {
    const inputs = formElement.querySelectorAll('.is-invalid, .is-valid');
    inputs.forEach(input => {
        input.classList.remove('is-invalid', 'is-valid');
    });
}

// ============================================================================
// HTML/STRING UTILITIES
// ============================================================================

/**
 * Escape HTML special characters to prevent XSS attacks.
 * Converts characters like <, >, &, ", ' to their HTML entity equivalents.
 * @param {string} text - The text to escape
 * @returns {string} The escaped text
 */
function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    if (typeof text !== 'string') text = String(text);
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Truncate a string to a specified length with ellipsis.
 * @param {string} str - The string to truncate
 * @param {number} len - Maximum length before truncation
 * @returns {string} The truncated string
 */
function truncateText(str, len) {
    if (!str) return '';
    return str.length > len ? str.substring(0, len) + '...' : str;
}

// ============================================================================
// FORMAT UTILITIES
// ============================================================================

/**
 * Format a number as Korean Won currency.
 * @param {number|null|undefined} value - The value to format
 * @returns {string} Formatted currency string (e.g., '₩1,234,567')
 */
function formatCurrency(value) {
    if (value === null || value === undefined) return '₩0';
    return '₩' + Math.round(value).toLocaleString('ko-KR');
}

/**
 * Format a number as a percentage with sign.
 * @param {number|null|undefined} value - The value to format
 * @returns {string} Formatted percentage string (e.g., '+12.34%' or '-5.67%')
 */
function formatPercent(value) {
    if (value === null || value === undefined) return '0%';
    return (value >= 0 ? '+' : '') + value.toFixed(2) + '%';
}

/**
 * Format a number with locale-specific formatting.
 * @param {number|null|undefined} value - The value to format
 * @param {number} [decimals=0] - Number of decimal places
 * @returns {string} Formatted number string
 */
function formatNumber(value, decimals = 0) {
    if (value === null || value === undefined) return '0';
    return Number(value).toLocaleString('ko-KR', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    });
}

/**
 * Format a date string for display.
 * @param {string|Date|null|undefined} dateString - The date to format
 * @returns {string} Formatted date string (e.g., '2024.01.15')
 */
function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

/**
 * Format a date/time string for display.
 * @param {string|Date|null|undefined} dateString - The date/time to format
 * @returns {string} Formatted date/time string (e.g., '2024.01.15 14:30')
 */
function formatDateTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

/**
 * Format a Date object to YYYY-MM-DD string for API calls.
 * @param {Date} date - The date to format
 * @returns {string} Date string in YYYY-MM-DD format
 */
function formatDateForApi(date) {
    if (!(date instanceof Date) || isNaN(date.getTime())) {
        return '';
    }
    return date.toISOString().split('T')[0];
}

// ============================================================================
// DATE UTILITIES
// ============================================================================

/**
 * Get start and end dates based on a period string.
 * @param {('1M'|'3M'|'6M'|'1Y'|'ALL')} period - The period identifier
 * @returns {{startDate: Date, endDate: Date}} Object containing start and end dates
 */
function getPeriodDates(period) {
    const endDate = new Date();
    const startDate = new Date();

    const months = PERIOD_MONTHS[period];
    if (months !== null && months !== undefined) {
        startDate.setMonth(endDate.getMonth() - months);
    } else {
        // 'ALL' period
        startDate.setFullYear(ALL_PERIOD_START_YEAR, 0, 1);
    }

    return { startDate, endDate };
}

/**
 * Get date range for API calls based on a period string.
 * @param {('1M'|'3M'|'6M'|'1Y'|'ALL')} period - The period identifier
 * @returns {{startDate: string, endDate: string}} Object containing formatted date strings
 */
function getDateRangeForApi(period) {
    const { startDate, endDate } = getPeriodDates(period);
    return {
        startDate: formatDateForApi(startDate),
        endDate: formatDateForApi(endDate)
    };
}

// ============================================================================
// DOM UTILITIES
// ============================================================================

/**
 * Apply CSS class to an element based on whether a value is positive or negative.
 * @param {jQuery|HTMLElement} $element - The element (jQuery or native) to apply class to
 * @param {number} value - The value to check
 * @param {string} [positiveClass=CSS_CLASSES.SUCCESS] - Class for positive values
 * @param {string} [negativeClass=CSS_CLASSES.DANGER] - Class for negative/zero values
 */
function applyValueClass($element, value, positiveClass = CSS_CLASSES.SUCCESS, negativeClass = CSS_CLASSES.DANGER) {
    // Handle both jQuery and native elements
    const removeClass = (el, cls) => {
        if (el.removeClass) el.removeClass(cls);
        else el.classList.remove(...cls.split(' '));
    };
    const addClass = (el, cls) => {
        if (el.addClass) el.addClass(cls);
        else el.classList.add(cls);
    };

    const allClasses = `${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER} ${CSS_CLASSES.WARNING} ${CSS_CLASSES.MUTED}`;
    removeClass($element, allClasses);

    if (value >= 0) {
        addClass($element, positiveClass);
    } else {
        addClass($element, negativeClass);
    }
}

/**
 * Apply CSS class based on value thresholds.
 * @param {jQuery|HTMLElement} $element - The element to apply class to
 * @param {number} value - The value to check
 * @param {number} successThreshold - Value >= this gets success class
 * @param {number} warningThreshold - Value >= this (but < successThreshold) gets warning class
 */
function applyThresholdClass($element, value, successThreshold, warningThreshold) {
    // Handle both jQuery and native elements
    const removeClass = (el, cls) => {
        if (el.removeClass) el.removeClass(cls);
        else el.classList.remove(...cls.split(' '));
    };
    const addClass = (el, cls) => {
        if (el.addClass) el.addClass(cls);
        else el.classList.add(cls);
    };

    const allClasses = `${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER} ${CSS_CLASSES.WARNING}`;
    removeClass($element, allClasses);

    if (value >= successThreshold) {
        addClass($element, CSS_CLASSES.SUCCESS);
    } else if (value >= warningThreshold) {
        addClass($element, CSS_CLASSES.WARNING);
    } else {
        addClass($element, CSS_CLASSES.DANGER);
    }
}

/**
 * Update button group active state.
 * Removes 'active' class from siblings and adds to clicked button.
 * @param {Event} event - The click event
 */
function updateButtonGroupActive(event) {
    if (typeof $ !== 'undefined') {
        $(event.target).closest('.btn-group').find('button').removeClass('active');
    } else {
        const btnGroup = event.target.closest('.btn-group');
        if (btnGroup) {
            btnGroup.querySelectorAll('button').forEach(btn => btn.classList.remove('active'));
        }
    }
    event.target.classList.add('active');
}

// ============================================================================
// EMPTY STATE & SKELETON HELPERS
// ============================================================================

/**
 * Display an empty state message in a container.
 * @param {string|HTMLElement|jQuery} container - The container element or selector
 * @param {string} [message='데이터가 없습니다.'] - The message to display
 * @param {string} [iconClass='bi-inbox'] - Bootstrap icon class
 */
function showEmptyState(container, message = '데이터가 없습니다.', iconClass = 'bi-inbox') {
    const emptyStateHtml = `
        <div class="empty-state">
            <div class="empty-state-icon">
                <i class="bi ${iconClass}"></i>
            </div>
            <div class="empty-state-title">데이터 없음</div>
            <div class="empty-state-description">${escapeHtml(message)}</div>
        </div>
    `;

    if (typeof container === 'string') {
        const element = document.querySelector(container);
        if (element) element.innerHTML = emptyStateHtml;
    } else if (container instanceof HTMLElement) {
        container.innerHTML = emptyStateHtml;
    } else if (typeof $ !== 'undefined' && container instanceof $) {
        container.html(emptyStateHtml);
    }
}

/**
 * Display skeleton loading placeholders in a table container.
 * @param {string|HTMLElement|jQuery} container - The container element or selector
 * @param {number} [rows=5] - Number of skeleton rows to display
 * @param {number} [cols=3] - Number of columns per row
 */
function showSkeleton(container, rows = 5, cols = 3) {
    let skeletonHtml = '';
    for (let i = 0; i < rows; i++) {
        skeletonHtml += '<tr>';
        for (let j = 0; j < cols; j++) {
            skeletonHtml += '<td><div class="skeleton" style="height: 20px; width: 100%;"></div></td>';
        }
        skeletonHtml += '</tr>';
    }

    if (typeof container === 'string') {
        const element = document.querySelector(container);
        if (element) element.innerHTML = skeletonHtml;
    } else if (container instanceof HTMLElement) {
        container.innerHTML = skeletonHtml;
    } else if (typeof $ !== 'undefined' && container instanceof $) {
        container.html(skeletonHtml);
    }
}

// ============================================================================
// DEBOUNCE & THROTTLE
// ============================================================================

/**
 * Create a debounced version of a function.
 * The function will only be called after the specified wait time has passed
 * since the last invocation.
 * @param {Function} func - The function to debounce
 * @param {number} [wait=300] - The debounce delay in milliseconds
 * @returns {Function} The debounced function
 * @example
 * const debouncedSearch = debounce((query) => {
 *   fetchSearchResults(query);
 * }, 300);
 * searchInput.addEventListener('input', (e) => debouncedSearch(e.target.value));
 */
function debounce(func, wait = DEFAULT_DEBOUNCE_WAIT) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func.apply(this, args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Create a throttled version of a function.
 * The function will be called at most once per specified time period.
 * @param {Function} func - The function to throttle
 * @param {number} [limit=300] - The throttle limit in milliseconds
 * @returns {Function} The throttled function
 * @example
 * const throttledScroll = throttle(() => {
 *   updateScrollPosition();
 * }, 100);
 * window.addEventListener('scroll', throttledScroll);
 */
function throttle(func, limit = DEFAULT_THROTTLE_LIMIT) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// ============================================================================
// CLIPBOARD
// ============================================================================

/**
 * Copy text to clipboard asynchronously.
 * Shows success/error notification to user.
 * @param {string} text - The text to copy
 * @returns {Promise<boolean>} True if copy succeeded, false otherwise
 */
async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        ToastNotification.success('클립보드에 복사되었습니다.');
        return true;
    } catch (err) {
        console.error('Failed to copy:', err);
        ToastNotification.error('복사에 실패했습니다.');
        return false;
    }
}

// ============================================================================
// COLOR GENERATION
// ============================================================================

/**
 * Generate an array of colors for charts.
 * @param {number} count - Number of colors needed
 * @returns {string[]} Array of color hex codes
 */
function generateColors(count) {
    const colors = [
        '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF',
        '#FF9F40', '#FF6384', '#C9CBCF', '#4BC0C0', '#36A2EB',
        '#E7E9ED', '#71B37C', '#3E7CB1', '#F67E4B', '#A569BD'
    ];

    // If we need more colors than predefined, cycle through
    const result = [];
    for (let i = 0; i < count; i++) {
        result.push(colors[i % colors.length]);
    }
    return result;
}

// ============================================================================
// LOCAL STORAGE UTILITIES
// ============================================================================

/**
 * Safely get a value from localStorage with JSON parsing.
 * @param {string} key - The localStorage key
 * @param {*} [defaultValue=null] - Default value if key not found or parse fails
 * @returns {*} The parsed value or default
 */
function getFromStorage(key, defaultValue = null) {
    try {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) : defaultValue;
    } catch (e) {
        console.warn(`Error reading from localStorage key "${key}":`, e);
        return defaultValue;
    }
}

/**
 * Safely set a value in localStorage with JSON stringification.
 * @param {string} key - The localStorage key
 * @param {*} value - The value to store
 * @returns {boolean} True if successful, false otherwise
 */
function setToStorage(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
        return true;
    } catch (e) {
        console.warn(`Error writing to localStorage key "${key}":`, e);
        return false;
    }
}

// ============================================================================
// EXPORT FUNCTIONS TO GLOBAL SCOPE
// ============================================================================

if (typeof window !== 'undefined') {
    // Constants
    window.CSS_CLASSES = CSS_CLASSES;
    window.PERIOD_MONTHS = PERIOD_MONTHS;
    window.ALL_PERIOD_START_YEAR = ALL_PERIOD_START_YEAR;

    // Toast and Loading
    window.ToastNotification = ToastNotification;
    window.LoadingOverlay = LoadingOverlay;

    // Dialogs and Error Handling
    window.showConfirmDialog = showConfirmDialog;
    window.handleAjaxError = handleAjaxError;

    // Form Validation
    window.validateForm = validateForm;
    window.clearFormValidation = clearFormValidation;

    // HTML/String Utilities
    window.escapeHtml = escapeHtml;
    window.truncateText = truncateText;

    // Format Utilities
    window.formatCurrency = formatCurrency;
    window.formatPercent = formatPercent;
    window.formatNumber = formatNumber;
    window.formatDate = formatDate;
    window.formatDateTime = formatDateTime;
    window.formatDateForApi = formatDateForApi;

    // Date Utilities
    window.getPeriodDates = getPeriodDates;
    window.getDateRangeForApi = getDateRangeForApi;

    // DOM Utilities
    window.applyValueClass = applyValueClass;
    window.applyThresholdClass = applyThresholdClass;
    window.updateButtonGroupActive = updateButtonGroupActive;
    window.showEmptyState = showEmptyState;
    window.showSkeleton = showSkeleton;

    // Debounce/Throttle
    window.debounce = debounce;
    window.throttle = throttle;

    // Clipboard
    window.copyToClipboard = copyToClipboard;

    // Color Generation
    window.generateColors = generateColors;

    // Local Storage
    window.getFromStorage = getFromStorage;
    window.setToStorage = setToStorage;
}
