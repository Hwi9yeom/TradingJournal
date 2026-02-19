/**
 * Trading Journal - Glassmorphism UI Utilities
 * Shared UI components and utilities for the glassmorphism design system
 */

// ===================================
// TOAST NOTIFICATION SYSTEM
// ===================================

/**
 * Show a glass toast notification
 * @param {string} message - Toast message text
 * @param {string} type - Toast type: 'success', 'error', or 'warning'
 * @param {number} duration - Duration in milliseconds (default: 3000)
 */
function showGlassToast(message, type = 'success', duration = 3000) {
    // Get or create toast container
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    // Create toast element
    const toast = document.createElement('div');
    toast.className = `toast-glass ${type}`;

    // Select icon based on type
    let icon;
    switch (type) {
        case 'success':
            icon = 'bi-check-circle-fill';
            break;
        case 'error':
            icon = 'bi-x-circle-fill';
            break;
        case 'warning':
            icon = 'bi-exclamation-triangle-fill';
            break;
        default:
            icon = 'bi-info-circle-fill';
    }

    // Toast content - using DOM construction to prevent XSS
    const iconElement = document.createElement('i');
    iconElement.className = `bi ${icon}`;
    iconElement.style.fontSize = '1.25rem';

    const messageSpan = document.createElement('span');
    messageSpan.style.flex = '1';
    messageSpan.textContent = message; // Use textContent instead of innerHTML

    const closeButton = document.createElement('button');
    closeButton.onclick = function() { removeToast(this); };
    closeButton.style.cssText = 'background: none; border: none; color: var(--text-secondary); cursor: pointer; padding: 0; font-size: 1.25rem;';
    closeButton.innerHTML = '<i class="bi bi-x"></i>';

    toast.appendChild(iconElement);
    toast.appendChild(messageSpan);
    toast.appendChild(closeButton);

    // Add to container
    container.appendChild(toast);

    // Auto-remove after duration
    setTimeout(() => {
        removeToast(toast);
    }, duration);

    return toast;
}

/**
 * Remove a toast notification with fade out animation
 * @param {HTMLElement} toastElement - Toast element or button inside toast
 */
function removeToast(toastElement) {
    const toast = toastElement.classList.contains('toast-glass')
        ? toastElement
        : toastElement.closest('.toast-glass');

    if (!toast) return;

    // Fade out animation
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';

    setTimeout(() => {
        toast.remove();
    }, 300);
}

// ===================================
// MODAL MANAGEMENT
// ===================================

/**
 * Open a glass modal by ID
 * @param {string} modalId - Modal element ID
 */
function openGlassModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) {
        console.warn(`Modal with id "${modalId}" not found`);
        return;
    }

    // Add show class for animation
    modal.classList.add('show');

    // Add body class to prevent scrolling
    document.body.style.overflow = 'hidden';

    // Close modal when clicking outside content
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeGlassModal(modalId);
        }
    });

    // Close on Escape key
    const escapeHandler = function(e) {
        if (e.key === 'Escape') {
            closeGlassModal(modalId);
            document.removeEventListener('keydown', escapeHandler);
        }
    };
    document.addEventListener('keydown', escapeHandler);

    return modal;
}

/**
 * Close a glass modal by ID
 * @param {string} modalId - Modal element ID
 */
function closeGlassModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) {
        console.warn(`Modal with id "${modalId}" not found`);
        return;
    }

    // Remove show class for fade out animation
    modal.classList.remove('show');

    // Restore body scrolling
    document.body.style.overflow = '';

    return modal;
}

/**
 * Alias for backward compatibility
 */
function openModal(modalId) {
    return openGlassModal(modalId);
}

/**
 * Alias for backward compatibility
 */
function closeModal(modalId) {
    return closeGlassModal(modalId);
}

// ===================================
// DROPDOWN MANAGEMENT
// ===================================

/**
 * Toggle a glass dropdown menu by ID
 * @param {string} dropdownId - Dropdown element ID
 */
function toggleGlassDropdown(dropdownId) {
    const dropdown = document.getElementById(dropdownId);
    if (!dropdown) {
        console.warn(`Dropdown with id "${dropdownId}" not found`);
        return;
    }

    // Close all other dropdowns
    const allDropdowns = document.querySelectorAll('.dropdown-glass.show');
    allDropdowns.forEach(d => {
        if (d.id !== dropdownId) {
            d.classList.remove('show');
        }
    });

    // Toggle current dropdown
    dropdown.classList.toggle('show');

    // Close dropdown when clicking outside
    if (dropdown.classList.contains('show')) {
        const closeHandler = function(e) {
            if (!dropdown.contains(e.target)) {
                dropdown.classList.remove('show');
                document.removeEventListener('click', closeHandler);
            }
        };

        // Delay to prevent immediate closure from the toggle click
        setTimeout(() => {
            document.addEventListener('click', closeHandler);
        }, 10);
    }

    return dropdown;
}

/**
 * Alias for backward compatibility
 */
function toggleDropdown(dropdownId) {
    return toggleGlassDropdown(dropdownId);
}

/**
 * Close all glass dropdowns
 */
function closeAllGlassDropdowns() {
    const allDropdowns = document.querySelectorAll('.dropdown-glass.show');
    allDropdowns.forEach(d => d.classList.remove('show'));
}

// ===================================
// LOADING STATE UTILITIES
// ===================================

/**
 * Show a loading overlay with spinner
 * @param {string} message - Optional loading message (default: "로딩 중...")
 */
function showLoadingOverlay(message = '로딩 중...') {
    // Remove existing overlay if present
    hideLoadingOverlay();

    // Create overlay - using DOM construction to prevent XSS
    const overlay = document.createElement('div');
    overlay.id = 'glassLoadingOverlay';
    overlay.className = 'loading-overlay';

    const container = document.createElement('div');
    container.style.textAlign = 'center';

    const spinner = document.createElement('div');
    spinner.className = 'spinner';

    const messageDiv = document.createElement('div');
    messageDiv.style.cssText = 'margin-top: var(--space-4); color: var(--text-secondary); font-size: var(--font-size-sm);';
    messageDiv.textContent = message; // Use textContent instead of innerHTML

    container.appendChild(spinner);
    container.appendChild(messageDiv);
    overlay.appendChild(container);

    document.body.appendChild(overlay);
    document.body.style.overflow = 'hidden';

    return overlay;
}

/**
 * Hide the loading overlay
 */
function hideLoadingOverlay() {
    const overlay = document.getElementById('glassLoadingOverlay');
    if (overlay) {
        overlay.style.opacity = '0';
        setTimeout(() => {
            overlay.remove();
            document.body.style.overflow = '';
        }, 200);
    }
}

/**
 * Show loading state on a specific element
 * @param {HTMLElement} element - Target element
 * @param {string} message - Optional loading message
 */
function showElementLoading(element, message = '') {
    if (!element) return;

    // Store original content
    element.dataset.originalContent = element.innerHTML;
    element.dataset.originalPointerEvents = element.style.pointerEvents;

    // Add loading state
    element.style.position = 'relative';
    element.style.pointerEvents = 'none';
    element.style.opacity = '0.6';

    const spinner = document.createElement('div');
    spinner.className = 'element-loading-spinner';
    spinner.style.cssText = `
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 1000;
    `;

    const spinnerInner = document.createElement('div');
    spinnerInner.className = 'spinner';
    spinnerInner.style.cssText = 'width: 32px; height: 32px;';
    spinner.appendChild(spinnerInner);

    if (message) {
        const messageDiv = document.createElement('div');
        messageDiv.style.cssText = 'margin-top: var(--space-2); color: var(--text-secondary); font-size: var(--font-size-xs); text-align: center;';
        messageDiv.textContent = message; // Use textContent instead of innerHTML
        spinner.appendChild(messageDiv);
    }

    element.appendChild(spinner);
    return element;
}

/**
 * Hide loading state on a specific element
 * @param {HTMLElement} element - Target element
 */
function hideElementLoading(element) {
    if (!element) return;

    const spinner = element.querySelector('.element-loading-spinner');
    if (spinner) {
        spinner.remove();
    }

    element.style.opacity = '';
    element.style.pointerEvents = element.dataset.originalPointerEvents || '';

    return element;
}

// ===================================
// BUTTON LOADING STATE
// ===================================

/**
 * Set button to loading state
 * @param {HTMLElement} button - Button element
 * @param {string} loadingText - Optional loading text (default: original text)
 */
function setButtonLoading(button, loadingText = null) {
    if (!button) return;

    // Store original state
    button.dataset.originalText = button.innerHTML;
    button.dataset.originalDisabled = button.disabled;

    // Set loading state
    button.disabled = true;
    const text = loadingText || button.textContent.trim();
    button.innerHTML = `
        <span class="spinner" style="width: 16px; height: 16px; border-width: 2px; margin-right: var(--space-2);"></span>
        ${text}
    `;

    return button;
}

/**
 * Reset button from loading state
 * @param {HTMLElement} button - Button element
 */
function resetButtonLoading(button) {
    if (!button) return;

    button.innerHTML = button.dataset.originalText || button.innerHTML;
    button.disabled = button.dataset.originalDisabled === 'true';

    delete button.dataset.originalText;
    delete button.dataset.originalDisabled;

    return button;
}

// ===================================
// UTILITY FUNCTIONS
// ===================================

/**
 * Format currency value
 * @param {number} value - Numeric value
 * @param {string} currency - Currency symbol (default: '₩')
 * @returns {string} Formatted currency string
 */
function formatCurrency(value, currency = '₩') {
    if (value === null || value === undefined) return `${currency}0`;
    return `${currency}${Math.abs(value).toLocaleString('ko-KR')}`;
}

/**
 * Format percentage value
 * @param {number} value - Numeric value
 * @param {number} decimals - Decimal places (default: 2)
 * @returns {string} Formatted percentage string
 */
function formatPercentage(value, decimals = 2) {
    if (value === null || value === undefined) return '0%';
    return `${value.toFixed(decimals)}%`;
}

/**
 * Add positive/negative class to element based on value
 * @param {HTMLElement} element - Target element
 * @param {number} value - Numeric value
 */
function applyValueClass(element, value) {
    if (!element) return;

    element.classList.remove('positive', 'negative', 'text-positive', 'text-negative');

    if (value > 0) {
        element.classList.add('positive', 'text-positive');
    } else if (value < 0) {
        element.classList.add('negative', 'text-negative');
    }

    return element;
}

/**
 * Debounce function to limit function calls
 * @param {Function} func - Function to debounce
 * @param {number} wait - Wait time in milliseconds
 * @returns {Function} Debounced function
 */
function debounce(func, wait = 300) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Throttle function to limit function calls
 * @param {Function} func - Function to throttle
 * @param {number} limit - Limit time in milliseconds
 * @returns {Function} Throttled function
 */
function throttle(func, limit = 300) {
    let inThrottle;
    return function executedFunction(...args) {
        if (!inThrottle) {
            func(...args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

/**
 * Animate number change
 * @param {HTMLElement} element - Target element
 * @param {number} endValue - End value
 * @param {number} duration - Animation duration in milliseconds (default: 1000)
 * @param {Function} formatter - Optional formatter function
 */
function animateNumber(element, endValue, duration = 1000, formatter = null) {
    if (!element) return;

    const startValue = parseFloat(element.textContent.replace(/[^0-9.-]/g, '')) || 0;
    const startTime = performance.now();

    function update(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);

        // Easing function (ease-out)
        const easeProgress = 1 - Math.pow(1 - progress, 3);

        const currentValue = startValue + (endValue - startValue) * easeProgress;

        if (formatter) {
            element.textContent = formatter(currentValue);
        } else {
            element.textContent = Math.round(currentValue).toLocaleString('ko-KR');
        }

        if (progress < 1) {
            requestAnimationFrame(update);
        }
    }

    requestAnimationFrame(update);
}

/**
 * Copy text to clipboard
 * @param {string} text - Text to copy
 * @param {string} successMessage - Success toast message (default: "클립보드에 복사되었습니다")
 */
async function copyToClipboard(text, successMessage = '클립보드에 복사되었습니다') {
    try {
        await navigator.clipboard.writeText(text);
        showGlassToast(successMessage, 'success');
        return true;
    } catch (err) {
        console.error('Failed to copy:', err);
        showGlassToast('복사 실패', 'error');
        return false;
    }
}

/**
 * Confirm dialog with glass styling
 * @param {string} message - Confirmation message
 * @param {string} title - Dialog title (default: "확인")
 * @returns {Promise<boolean>} User's choice
 */
function glassConfirm(message, title = '확인') {
    return new Promise((resolve) => {
        // Create modal dynamically
        const modalId = 'glassConfirmModal';
        let modal = document.getElementById(modalId);

        // Remove existing modal if present
        if (modal) {
            modal.remove();
        }

        modal = document.createElement('div');
        modal.id = modalId;
        modal.className = 'modal-glass';

        // Create modal structure using DOM construction to prevent XSS
        const modalContent = document.createElement('div');
        modalContent.className = 'modal-glass-content';
        modalContent.style.maxWidth = '400px';

        const modalHeader = document.createElement('div');
        modalHeader.className = 'modal-header';
        const headerTitle = document.createElement('h3');
        headerTitle.innerHTML = '<i class="bi bi-question-circle"></i> ';
        const titleText = document.createTextNode(title);
        headerTitle.appendChild(titleText);
        modalHeader.appendChild(headerTitle);

        const modalBody = document.createElement('div');
        modalBody.className = 'modal-body';
        const bodyText = document.createElement('p');
        bodyText.style.cssText = 'color: var(--text-primary); font-size: var(--font-size-base);';
        bodyText.textContent = message; // Use textContent instead of innerHTML
        modalBody.appendChild(bodyText);

        const modalFooter = document.createElement('div');
        modalFooter.className = 'modal-footer';
        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'btn-glass';
        cancelBtn.id = 'confirmCancel';
        cancelBtn.textContent = '취소';
        const okBtn = document.createElement('button');
        okBtn.className = 'btn-glass primary';
        okBtn.id = 'confirmOk';
        okBtn.textContent = '확인';
        modalFooter.appendChild(cancelBtn);
        modalFooter.appendChild(okBtn);

        modalContent.appendChild(modalHeader);
        modalContent.appendChild(modalBody);
        modalContent.appendChild(modalFooter);
        modal.appendChild(modalContent);

        document.body.appendChild(modal);

        // Event handlers
        const cleanup = () => {
            closeGlassModal(modalId);
            setTimeout(() => modal.remove(), 300);
        };

        document.getElementById('confirmOk').addEventListener('click', () => {
            cleanup();
            resolve(true);
        });

        document.getElementById('confirmCancel').addEventListener('click', () => {
            cleanup();
            resolve(false);
        });

        // Open modal
        openGlassModal(modalId);
    });
}

// ===================================
// INITIALIZE ON DOM READY
// ===================================

document.addEventListener('DOMContentLoaded', function() {
    // Close dropdowns when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.dropdown-glass')) {
            closeAllGlassDropdowns();
        }
    });

    // Initialize any auto-close modals
    document.querySelectorAll('.modal-glass').forEach(modal => {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                closeGlassModal(modal.id);
            }
        });
    });
});

// ===================================
// EXPORT FOR MODULE USAGE
// ===================================

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        showGlassToast,
        removeToast,
        openGlassModal,
        closeGlassModal,
        openModal,
        closeModal,
        toggleGlassDropdown,
        toggleDropdown,
        closeAllGlassDropdowns,
        showLoadingOverlay,
        hideLoadingOverlay,
        showElementLoading,
        hideElementLoading,
        setButtonLoading,
        resetButtonLoading,
        formatCurrency,
        formatPercentage,
        applyValueClass,
        debounce,
        throttle,
        animateNumber,
        copyToClipboard,
        glassConfirm
    };
}
