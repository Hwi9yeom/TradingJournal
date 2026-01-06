/**
 * 알림 센터 JavaScript
 */

let allAlerts = [];
let currentFilter = 'all';
let currentPage = 0;
let totalPages = 0;
let currentAlertId = null;

// 페이지 로드 시 초기화
$(document).ready(function() {
    loadAlertSummary();
    loadAlerts();

    // 30초마다 알림 갱신
    setInterval(function() {
        loadAlertSummary();
    }, 30000);
});

/**
 * 알림 요약 로드
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
 * 요약 카드 업데이트
 */
function updateSummaryCards(summary) {
    $('#unread-count').text(summary.unreadCount || 0);
    $('#critical-count').text(summary.criticalCount || 0);
    $('#high-count').text(summary.highPriorityCount || 0);
    $('#today-count').text(summary.todayCount || 0);
    $('#tab-unread-count').text(summary.unreadCount || 0);

    // 네비게이션 뱃지 업데이트
    const navBadge = $('#nav-unread-count');
    if (summary.unreadCount > 0) {
        navBadge.text(summary.unreadCount > 99 ? '99+' : summary.unreadCount).show();
    } else {
        navBadge.hide();
    }
}

/**
 * 긴급 알림 섹션 업데이트
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

/**
 * 알림 목록 로드
 */
function loadAlerts(page = 0) {
    currentPage = page;

    $.ajax({
        url: `/api/alerts?page=${page}&size=20`,
        method: 'GET',
        success: function(response) {
            allAlerts = response.content || [];
            totalPages = response.totalPages || 0;

            $('#tab-all-count').text(response.totalElements || 0);

            renderAlerts(filterAlertsByType(currentFilter));
            renderPagination();
        },
        error: function(xhr) {
            console.error('알림 로드 실패:', xhr);
            showToast('알림을 불러오는데 실패했습니다.', 'danger');
        }
    });
}

/**
 * 필터 적용
 */
function filterAlerts(filter) {
    currentFilter = filter;

    // 탭 활성화 상태 업데이트
    $('#alertTabs .nav-link').removeClass('active');
    $(`#alertTabs .nav-link[onclick*="${filter}"]`).addClass('active');

    renderAlerts(filterAlertsByType(filter));
}

/**
 * 필터링 로직
 */
function filterAlertsByType(filter) {
    switch(filter) {
        case 'unread':
            return allAlerts.filter(a => a.status === 'UNREAD');
        case 'goal':
            return allAlerts.filter(a =>
                ['GOAL_MILESTONE', 'GOAL_COMPLETED', 'GOAL_DEADLINE', 'GOAL_OVERDUE'].includes(a.alertType)
            );
        case 'trade':
            return allAlerts.filter(a =>
                ['PROFIT_TARGET', 'LOSS_LIMIT', 'DAILY_LOSS_LIMIT', 'DRAWDOWN_WARNING',
                 'TRADE_EXECUTED', 'WINNING_STREAK', 'LOSING_STREAK'].includes(a.alertType)
            );
        default:
            return allAlerts;
    }
}

/**
 * 알림 목록 렌더링
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
 * 알림 아이템 생성
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
 * 우선순위에 따른 아이콘 배경 클래스
 */
function getPriorityClass(priority) {
    const classes = {
        'CRITICAL': 'priority-critical',
        'HIGH': 'priority-high',
        'MEDIUM': 'priority-medium',
        'LOW': 'priority-low'
    };
    return classes[priority] || 'priority-medium';
}

/**
 * 우선순위에 따른 뱃지 클래스
 */
function getPriorityBadgeClass(priority) {
    const classes = {
        'CRITICAL': 'bg-danger',
        'HIGH': 'bg-warning text-dark',
        'MEDIUM': 'bg-info',
        'LOW': 'bg-secondary'
    };
    return classes[priority] || 'bg-secondary';
}

/**
 * 페이지네이션 렌더링
 */
function renderPagination() {
    const container = $('#pagination');
    const paginationContainer = $('#pagination-container');

    if (totalPages <= 1) {
        paginationContainer.hide();
        return;
    }

    paginationContainer.show();
    container.empty();

    // 이전 버튼
    container.append(`
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadAlerts(${currentPage - 1})">이전</a>
        </li>
    `);

    // 페이지 번호
    const startPage = Math.max(0, currentPage - 2);
    const endPage = Math.min(totalPages - 1, currentPage + 2);

    for (let i = startPage; i <= endPage; i++) {
        container.append(`
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="loadAlerts(${i})">${i + 1}</a>
            </li>
        `);
    }

    // 다음 버튼
    container.append(`
        <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadAlerts(${currentPage + 1})">다음</a>
        </li>
    `);
}

/**
 * 알림 상세 보기
 */
function showAlertDetail(id) {
    $.ajax({
        url: `/api/alerts/${id}`,
        method: 'GET',
        success: function(alert) {
            currentAlertId = alert.id;
            renderAlertDetail(alert);

            // 읽지 않은 경우 읽음 처리
            if (alert.status === 'UNREAD') {
                markAsRead(id, false);
            }

            $('#alertDetailModal').modal('show');
        },
        error: function(xhr) {
            console.error('상세 조회 실패:', xhr);
            showToast('알림을 불러올 수 없습니다.', 'danger');
        }
    });
}

/**
 * 상세 모달 렌더링
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

    // 버튼 설정
    setupDetailButtons(alert);
}

/**
 * 상세 모달 버튼 설정
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

/**
 * 알림 읽음 처리
 */
function markAsRead(id, reload = true) {
    $.ajax({
        url: `/api/alerts/${id}/read`,
        method: 'PATCH',
        success: function() {
            if (reload) {
                loadAlerts(currentPage);
                loadAlertSummary();
            }
        },
        error: function(xhr) {
            console.error('읽음 처리 실패:', xhr);
        }
    });
}

/**
 * 모든 알림 읽음 처리
 */
function markAllAsRead() {
    $.ajax({
        url: '/api/alerts/read-all',
        method: 'PATCH',
        success: function() {
            loadAlerts(currentPage);
            loadAlertSummary();
            showToast('모든 알림이 읽음 처리되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('전체 읽음 처리 실패:', xhr);
            showToast('처리에 실패했습니다.', 'danger');
        }
    });
}

/**
 * 알림 무시
 */
function dismissAlert(id) {
    $.ajax({
        url: `/api/alerts/${id}/dismiss`,
        method: 'PATCH',
        success: function() {
            $('#alertDetailModal').modal('hide');
            loadAlerts(currentPage);
            loadAlertSummary();
            showToast('알림이 무시되었습니다.', 'info');
        },
        error: function(xhr) {
            console.error('무시 처리 실패:', xhr);
            showToast('처리에 실패했습니다.', 'danger');
        }
    });
}

/**
 * 알림 삭제
 */
function deleteAlert(id) {
    $.ajax({
        url: `/api/alerts/${id}`,
        method: 'DELETE',
        success: function() {
            $('#alertDetailModal').modal('hide');
            loadAlerts(currentPage);
            loadAlertSummary();
            showToast('알림이 삭제되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('삭제 실패:', xhr);
            showToast('삭제에 실패했습니다.', 'danger');
        }
    });
}

/**
 * 날짜 시간 포맷
 */
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleDateString('ko-KR') + ' ' + date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

/**
 * HTML 이스케이프
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 토스트 메시지
 */
function showToast(message, type) {
    const toastHtml = `
        <div class="toast align-items-center text-white bg-${type} border-0 position-fixed bottom-0 end-0 m-3" role="alert">
            <div class="d-flex">
                <div class="toast-body">${message}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;

    const toast = $(toastHtml);
    $('body').append(toast);

    const bsToast = new bootstrap.Toast(toast[0], { delay: 3000 });
    bsToast.show();

    toast.on('hidden.bs.toast', function() {
        $(this).remove();
    });
}
