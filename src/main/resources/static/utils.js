/**
 * Trading Journal - Utility Functions
 * Centralized error handling, notifications, and common utilities
 */

// ===== Toast Notification System =====
const ToastNotification = {
    container: null,

    init() {
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.className = 'toast-container';
            this.container.setAttribute('role', 'region');
            this.container.setAttribute('aria-label', 'Notifications');
            document.body.appendChild(this.container);
        }
    },

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
                    <div style="font-size: 0.875rem; color: var(--color-text-secondary);">${message}</div>
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

    remove(toast) {
        toast.style.animation = 'slideOutRight 0.3s ease-out';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    },

    getIcon(type) {
        const icons = {
            success: '✓',
            error: '✕',
            warning: '⚠',
            info: 'ℹ'
        };
        return icons[type] || icons.info;
    },

    getTitle(type) {
        const titles = {
            success: '성공',
            error: '오류',
            warning: '경고',
            info: '알림'
        };
        return titles[type] || titles.info;
    },

    success(message, duration) {
        return this.show(message, 'success', duration);
    },

    error(message, duration) {
        return this.show(message, 'error', duration);
    },

    warning(message, duration) {
        return this.show(message, 'warning', duration);
    },

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

// ===== Loading Overlay =====
const LoadingOverlay = {
    overlay: null,

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
                    <div style="margin-top: 1rem; font-size: 1.125rem;">${message}</div>
                </div>
            `;
            document.body.appendChild(this.overlay);
            document.body.style.overflow = 'hidden';
        }
    },

    hide() {
        if (this.overlay) {
            document.body.removeChild(this.overlay);
            this.overlay = null;
            document.body.style.overflow = '';
        }
    }
};

// ===== Confirmation Dialog =====
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
                            <h5 class="modal-title" id="${modalId}Label">${title}</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="닫기"></button>
                        </div>
                        <div class="modal-body">
                            ${message}
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

// ===== AJAX Error Handler =====
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

// ===== Form Validation =====
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

function clearFormValidation(formElement) {
    const inputs = formElement.querySelectorAll('.is-invalid, .is-valid');
    inputs.forEach(input => {
        input.classList.remove('is-invalid', 'is-valid');
    });
}

// ===== Format Utilities =====
function formatCurrency(value) {
    if (value === null || value === undefined) return '₩0';
    return '₩' + Math.round(value).toLocaleString('ko-KR');
}

function formatPercent(value) {
    if (value === null || value === undefined) return '0%';
    return (value >= 0 ? '+' : '') + value.toFixed(2) + '%';
}

function formatNumber(value, decimals = 0) {
    if (value === null || value === undefined) return '0';
    return Number(value).toLocaleString('ko-KR', {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    });
}

function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

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

// ===== Empty State Helper =====
function showEmptyState(container, message = '데이터가 없습니다.', iconClass = 'bi-inbox') {
    const emptyStateHtml = `
        <div class="empty-state">
            <div class="empty-state-icon">
                <i class="bi ${iconClass}"></i>
            </div>
            <div class="empty-state-title">데이터 없음</div>
            <div class="empty-state-description">${message}</div>
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

// ===== Skeleton Loading Helper =====
function showSkeleton(container, rows = 5) {
    let skeletonHtml = '';
    for (let i = 0; i < rows; i++) {
        skeletonHtml += `
            <tr>
                <td><div class="skeleton" style="height: 20px; width: 100%;"></div></td>
                <td><div class="skeleton" style="height: 20px; width: 100%;"></div></td>
                <td><div class="skeleton" style="height: 20px; width: 100%;"></div></td>
            </tr>
        `;
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

// ===== Debounce Utility =====
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

// ===== Throttle Utility =====
function throttle(func, limit = 300) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// ===== Copy to Clipboard =====
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

// ===== Export Functions =====
if (typeof window !== 'undefined') {
    window.ToastNotification = ToastNotification;
    window.LoadingOverlay = LoadingOverlay;
    window.showConfirmDialog = showConfirmDialog;
    window.handleAjaxError = handleAjaxError;
    window.validateForm = validateForm;
    window.clearFormValidation = clearFormValidation;
    window.formatCurrency = formatCurrency;
    window.formatPercent = formatPercent;
    window.formatNumber = formatNumber;
    window.formatDate = formatDate;
    window.formatDateTime = formatDateTime;
    window.showEmptyState = showEmptyState;
    window.showSkeleton = showSkeleton;
    window.debounce = debounce;
    window.throttle = throttle;
    window.copyToClipboard = copyToClipboard;
}
