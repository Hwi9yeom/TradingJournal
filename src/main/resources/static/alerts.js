/**
 * 알림 센터 JavaScript
 * @fileoverview Alert center functionality including loading, filtering, and managing alerts.
 * @requires utils.js - escapeHtml, formatDateTime, ToastNotification
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Alert priority CSS classes for icon backgrounds
 * @constant {Object<string, string>}
 */
const PRIORITY_CLASSES = {
    CRITICAL: 'priority-critical',
    HIGH: 'priority-high',
    MEDIUM: 'priority-medium',
    LOW: 'priority-low'
};

/**
 * Alert priority badge CSS classes
 * @constant {Object<string, string>}
 */
const PRIORITY_BADGE_CLASSES = {
    CRITICAL: 'bg-danger',
    HIGH: 'bg-warning text-dark',
    MEDIUM: 'bg-info',
    LOW: 'bg-secondary'
};

/**
 * Alert types grouped by category for filtering
 * @constant {Object<string, string[]>}
 */
const ALERT_TYPE_FILTERS = {
    goal: ['GOAL_MILESTONE', 'GOAL_COMPLETED', 'GOAL_DEADLINE', 'GOAL_OVERDUE'],
    trade: [
        'PROFIT_TARGET', 'LOSS_LIMIT', 'DAILY_LOSS_LIMIT', 'DRAWDOWN_WARNING',
        'TRADE_EXECUTED', 'WINNING_STREAK', 'LOSING_STREAK'
    ]
};

/**
 * Default page size for alert pagination
 * @constant {number}
 */
const DEFAULT_PAGE_SIZE = 20;

/**
 * Alert refresh interval in milliseconds (30 seconds)
 * @constant {number}
 */
const ALERT_REFRESH_INTERVAL = 30000;

// ============================================================================
// STATE
// ============================================================================

/**
 * Alert center state object
 * @type {Object}
 * @property {Array} alerts - All loaded alerts
 * @property {string} filter - Current filter type ('all', 'unread', 'goal', 'trade')
 * @property {number} page - Current page number (0-indexed)
 * @property {number} totalPages - Total number of pages
 * @property {number|null} currentAlertId - Currently selected alert ID for detail modal
 */
const alertState = {
    alerts: [],
    filter: 'all',
    page: 0,
    totalPages: 0,
    currentAlertId: null
};

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Initialize the alert center on page load
 */
$(document).ready(function() {
    loadAlertSummary();
    loadAlerts();

    // Auto-refresh alert summary
    setInterval(loadAlertSummary, ALERT_REFRESH_INTERVAL);
});

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load alert summary statistics
 * @returns {void}
 */
function loadAlertSummary() {
    $.ajax({
        url: '/api/alerts/summary',
        method: 'GET',
        success: function(summary) {
            updateSummaryCards(summary);
            updateCriticalSection(summary.criticalAlerts);
        },
        error: function(xhr) {
            console.error('요약 로드 실패:', xhr);
        }
    });
}

/**
 * Load paginated alert list
 * @param {number} [page=0] - Page number to load (0-indexed)
 * @returns {void}
 */
function loadAlerts(page = 0) {
    alertState.page = page;

    $.ajax({
        url: `/api/alerts?page=${page}&size=${DEFAULT_PAGE_SIZE}`,
        method: 'GET',
        success: function(response) {
            alertState.alerts = response.content || [];
            alertState.totalPages = response.totalPages || 0;

            $('#tab-all-count').text(response.totalElements || 0);

            renderAlerts(filterAlertsByType(alertState.filter));
            renderPagination();
        },
        error: function(xhr) {
            console.error('알림 로드 실패:', xhr);
            ToastNotification.error('알림을 불러오는데 실패했습니다.');
        }
    });
}

// ============================================================================
// UI UPDATE FUNCTIONS
// ============================================================================

/**
 * Update summary statistic cards
 * @param {Object} summary - Summary data from API
 * @param {number} summary.unreadCount - Number of unread alerts
 * @param {number} summary.criticalCount - Number of critical alerts
 * @param {number} summary.highPriorityCount - Number of high priority alerts
 * @param {number} summary.todayCount - Number of alerts created today
 * @returns {void}
 */
function updateSummaryCards(summary) {
    $('#unread-count').text(summary.unreadCount || 0);
    $('#critical-count').text(summary.criticalCount || 0);
    $('#high-count').text(summary.highPriorityCount || 0);
    $('#today-count').text(summary.todayCount || 0);
    $('#tab-unread-count').text(summary.unreadCount || 0);

    // Update navigation badge
    const navBadge = $('#nav-unread-count');
    const unreadCount = summary.unreadCount || 0;

    if (unreadCount > 0) {
        navBadge.text(unreadCount > 99 ? '99+' : unreadCount).show();
    } else {
        navBadge.hide();
    }
}

/**
 * Update critical alerts section
 * @param {Array} criticalAlerts - Array of critical alert objects
 * @returns {void}
 */
function updateCriticalSection(criticalAlerts) {
    const section = $('#critical-section');
    const container = $('#critical-alerts');

    if (!criticalAlerts || criticalAlerts.length === 0) {
        section.hide();
        return;
    }

    section.show();
    container.empty();

    criticalAlerts.forEach(alert => {
        container.append(createAlertItem(alert));
    });
}

// ============================================================================
// FILTERING
// ============================================================================

/**
 * Apply filter and re-render alerts
 * @param {string} filter - Filter type ('all', 'unread', 'goal', 'trade')
 * @returns {void}
 */
function filterAlerts(filter) {
    alertState.filter = filter;

    // Update tab active state
    $('#alertTabs .nav-link').removeClass('active');
    $(`#alertTabs .nav-link[onclick*="${filter}"]`).addClass('active');

    renderAlerts(filterAlertsByType(filter));
}

/**
 * Filter alerts by type
 * @param {string} filter - Filter type ('all', 'unread', 'goal', 'trade')
 * @returns {Array} Filtered alerts array
 */
function filterAlertsByType(filter) {
    const { alerts } = alertState;

    switch (filter) {
        case 'unread':
            return alerts.filter(a => a.status === 'UNREAD');
        case 'goal':
            return alerts.filter(a => ALERT_TYPE_FILTERS.goal.includes(a.alertType));
        case 'trade':
            return alerts.filter(a => ALERT_TYPE_FILTERS.trade.includes(a.alertType));
        default:
            return alerts;
    }
}

// ============================================================================
// RENDERING
// ============================================================================

/**
 * Render alert list
 * @param {Array} alerts - Array of alert objects to render
 * @returns {void}
 */
function renderAlerts(alerts) {
    const container = $('#alerts-container');
    const emptyState = $('#empty-state');

    container.empty();

    if (!alerts || alerts.length === 0) {
        emptyState.show();
        return;
    }

    emptyState.hide();

    alerts.forEach(alert => {
        container.append(createAlertItem(alert));
    });
}

/**
 * Create HTML for a single alert item
 * @param {Object} alert - Alert object
 * @param {number} alert.id - Alert ID
 * @param {string} alert.status - Alert status ('UNREAD', 'READ', etc.)
 * @param {string} alert.priority - Alert priority level
 * @param {string} alert.title - Alert title
 * @param {string} [alert.message] - Alert message
 * @param {string} [alert.iconClass] - Bootstrap icon class
 * @param {string} [alert.timeAgo] - Human-readable time ago string
 * @param {string} alert.alertType - Alert type code
 * @param {string} [alert.alertTypeLabel] - Human-readable alert type
 * @param {string} [alert.priorityLabel] - Human-readable priority
 * @returns {string} HTML string for the alert item
 */
function createAlertItem(alert) {
    const isUnread = alert.status === 'UNREAD';
    const priorityClass = getPriorityClass(alert.priority);

    return `
        <div class="list-group-item alert-item ${isUnread ? 'unread' : ''}" onclick="showAlertDetail(${alert.id})">
            <div class="d-flex align-items-start">
                <div class="alert-icon ${priorityClass} me-3">
                    <i class="bi ${alert.iconClass || 'bi-bell'}"></i>
                </div>
                <div class="flex-grow-1">
                    <div class="d-flex justify-content-between align-items-start">
                        <h6 class="mb-1 ${isUnread ? 'fw-bold' : ''}">${escapeHtml(alert.title)}</h6>
                        <small class="text-muted">${alert.timeAgo || ''}</small>
                    </div>
                    <p class="mb-1 small text-muted">${escapeHtml(alert.message || '')}</p>
                    <div class="d-flex gap-2">
                        <span class="badge bg-secondary">${alert.alertTypeLabel || alert.alertType}</span>
                        <span class="badge ${getPriorityBadgeClass(alert.priority)}">${alert.priorityLabel || alert.priority}</span>
                    </div>
                </div>
                ${isUnread ? '<span class="badge bg-primary ms-2">NEW</span>' : ''}
            </div>
        </div>
    `;
}

/**
 * Get CSS class for priority icon background
 * @param {string} priority - Priority level ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')
 * @returns {string} CSS class name
 */
function getPriorityClass(priority) {
    return PRIORITY_CLASSES[priority] || PRIORITY_CLASSES.MEDIUM;
}

/**
 * Get CSS class for priority badge
 * @param {string} priority - Priority level ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')
 * @returns {string} CSS class name
 */
function getPriorityBadgeClass(priority) {
    return PRIORITY_BADGE_CLASSES[priority] || PRIORITY_BADGE_CLASSES.LOW;
}

/**
 * Render pagination controls
 * @returns {void}
 */
function renderPagination() {
    const container = $('#pagination');
    const paginationContainer = $('#pagination-container');
    const { page, totalPages } = alertState;

    if (totalPages <= 1) {
        paginationContainer.hide();
        return;
    }

    paginationContainer.show();
    container.empty();

    // Previous button
    container.append(`
        <li class="page-item ${page === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadAlerts(${page - 1})">이전</a>
        </li>
    `);

    // Page numbers
    const startPage = Math.max(0, page - 2);
    const endPage = Math.min(totalPages - 1, page + 2);

    for (let i = startPage; i <= endPage; i++) {
        container.append(`
            <li class="page-item ${i === page ? 'active' : ''}">
                <a class="page-link" href="#" onclick="loadAlerts(${i})">${i + 1}</a>
            </li>
        `);
    }

    // Next button
    container.append(`
        <li class="page-item ${page >= totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadAlerts(${page + 1})">다음</a>
        </li>
    `);
}

// ============================================================================
// ALERT DETAIL MODAL
// ============================================================================

/**
 * Show alert detail modal
 * @param {number} id - Alert ID to display
 * @returns {void}
 */
function showAlertDetail(id) {
    $.ajax({
        url: `/api/alerts/${id}`,
        method: 'GET',
        success: function(alert) {
            alertState.currentAlertId = alert.id;
            renderAlertDetail(alert);

            // Mark as read if unread
            if (alert.status === 'UNREAD') {
                markAsRead(id, false);
            }

            $('#alertDetailModal').modal('show');
        },
        error: function(xhr) {
            console.error('상세 조회 실패:', xhr);
            ToastNotification.error('알림을 불러올 수 없습니다.');
        }
    });
}

/**
 * Render alert detail content in modal
 * @param {Object} alert - Alert object with full details
 * @returns {void}
 */
function renderAlertDetail(alert) {
    $('#alertDetailTitle').html(`
        <i class="bi ${alert.iconClass || 'bi-bell'} me-2 ${alert.colorClass || ''}"></i>
        ${escapeHtml(alert.title)}
    `);

    const content = `
        <div class="mb-3">
            <p class="mb-2">${escapeHtml(alert.message || '')}</p>
        </div>

        <div class="row g-3">
            <div class="col-6">
                <label class="text-muted small">유형</label>
                <p class="mb-0">${alert.alertTypeLabel}</p>
            </div>
            <div class="col-6">
                <label class="text-muted small">우선순위</label>
                <p class="mb-0">
                    <span class="badge ${getPriorityBadgeClass(alert.priority)}">${alert.priorityLabel}</span>
                </p>
            </div>
            ${alert.relatedValue ? `
            <div class="col-6">
                <label class="text-muted small">관련 값</label>
                <p class="mb-0 fw-bold">${alert.relatedValue}</p>
            </div>
            ` : ''}
            ${alert.thresholdValue ? `
            <div class="col-6">
                <label class="text-muted small">임계값</label>
                <p class="mb-0">${alert.thresholdValue}</p>
            </div>
            ` : ''}
        </div>

        <div class="mt-3 pt-3 border-top text-muted small">
            <i class="bi bi-clock me-1"></i>${formatDateTime(alert.createdAt)}
            ${alert.readAt ? ` | 읽음: ${formatDateTime(alert.readAt)}` : ''}
        </div>
    `;

    $('#alertDetailContent').html(content);

    setupDetailButtons(alert);
}

/**
 * Setup detail modal action buttons
 * @param {Object} alert - Alert object
 * @returns {void}
 */
function setupDetailButtons(alert) {
    $('#btnDismissAlert').off('click').on('click', function() {
        dismissAlert(alert.id);
    });

    $('#btnDeleteAlert').off('click').on('click', function() {
        if (confirm('이 알림을 삭제하시겠습니까?')) {
            deleteAlert(alert.id);
        }
    });

    const btnGoTo = $('#btnGoToAction');
    if (alert.actionUrl) {
        btnGoTo.show().off('click').on('click', function() {
            window.location.href = alert.actionUrl;
        });
    } else {
        btnGoTo.hide();
    }
}

// ============================================================================
// ALERT ACTIONS
// ============================================================================

/**
 * Mark a single alert as read
 * @param {number} id - Alert ID
 * @param {boolean} [reload=true] - Whether to reload alerts after marking
 * @returns {void}
 */
function markAsRead(id, reload = true) {
    $.ajax({
        url: `/api/alerts/${id}/read`,
        method: 'PATCH',
        success: function() {
            if (reload) {
                loadAlerts(alertState.page);
                loadAlertSummary();
            }
        },
        error: function(xhr) {
            console.error('읽음 처리 실패:', xhr);
        }
    });
}

/**
 * Mark all alerts as read
 * @returns {void}
 */
function markAllAsRead() {
    $.ajax({
        url: '/api/alerts/read-all',
        method: 'PATCH',
        success: function() {
            loadAlerts(alertState.page);
            loadAlertSummary();
            ToastNotification.success('모든 알림이 읽음 처리되었습니다.');
        },
        error: function(xhr) {
            console.error('전체 읽음 처리 실패:', xhr);
            ToastNotification.error('처리에 실패했습니다.');
        }
    });
}

/**
 * Dismiss an alert (mark as dismissed/ignored)
 * @param {number} id - Alert ID
 * @returns {void}
 */
function dismissAlert(id) {
    $.ajax({
        url: `/api/alerts/${id}/dismiss`,
        method: 'PATCH',
        success: function() {
            $('#alertDetailModal').modal('hide');
            loadAlerts(alertState.page);
            loadAlertSummary();
            ToastNotification.info('알림이 무시되었습니다.');
        },
        error: function(xhr) {
            console.error('무시 처리 실패:', xhr);
            ToastNotification.error('처리에 실패했습니다.');
        }
    });
}

/**
 * Delete an alert permanently
 * @param {number} id - Alert ID
 * @returns {void}
 */
function deleteAlert(id) {
    $.ajax({
        url: `/api/alerts/${id}`,
        method: 'DELETE',
        success: function() {
            $('#alertDetailModal').modal('hide');
            loadAlerts(alertState.page);
            loadAlertSummary();
            ToastNotification.success('알림이 삭제되었습니다.');
        },
        error: function(xhr) {
            console.error('삭제 실패:', xhr);
            ToastNotification.error('삭제에 실패했습니다.');
        }
    });
}
