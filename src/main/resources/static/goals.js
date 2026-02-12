/**
 * 목표 관리 JavaScript
 * Trading Goals Management Module
 */

// =============================================================================
// CONFIGURATION CONSTANTS
// =============================================================================

/**
 * Goal type configuration mapping.
 * Each goal type has its formatter function, unit suffix, placeholder example, and label.
 * @type {Object.<string, {format: function(number): string, unit: string, placeholder: string, label: string}>}
 */
const GOAL_TYPE_CONFIG = {
    RETURN_RATE: {
        format: (val) => val.toFixed(2) + '%',
        unit: '%',
        placeholder: '예: 20',
        label: '수익률'
    },
    WIN_RATE: {
        format: (val) => val.toFixed(2) + '%',
        unit: '%',
        placeholder: '예: 60',
        label: '승률'
    },
    MAX_DRAWDOWN_LIMIT: {
        format: (val) => val.toFixed(2) + '%',
        unit: '%',
        placeholder: '예: 10',
        label: '최대 손실'
    },
    TARGET_AMOUNT: {
        format: (val) => formatCurrency(val),
        unit: '원',
        placeholder: '예: 10000000',
        label: '목표 금액'
    },
    SAVINGS_AMOUNT: {
        format: (val) => formatCurrency(val),
        unit: '원',
        placeholder: '예: 10000000',
        label: '저축 금액'
    },
    DIVIDEND_INCOME: {
        format: (val) => formatCurrency(val),
        unit: '원',
        placeholder: '예: 10000000',
        label: '배당 수익'
    },
    TRADE_COUNT: {
        format: (val) => val.toFixed(0) + '회',
        unit: '회',
        placeholder: '예: 100',
        label: '거래 횟수'
    },
    SHARPE_RATIO: {
        format: (val) => val.toFixed(2),
        unit: '',
        placeholder: '예: 1.5',
        label: '샤프 비율'
    }
};

/** Default formatter for unknown goal types */
const DEFAULT_GOAL_FORMAT = {
    format: (val) => val.toFixed(2),
    unit: '',
    placeholder: '목표 수치 입력',
    label: '기타'
};

/**
 * Status configuration for badge rendering.
 * Maps status codes to Bootstrap color classes and Korean labels.
 * @type {Object.<string, {color: string, label: string}>}
 */
const STATUS_CONFIG = {
    ACTIVE: { color: 'info', label: '진행 중' },
    COMPLETED: { color: 'success', label: '달성' },
    FAILED: { color: 'danger', label: '미달성' },
    PAUSED: { color: 'secondary', label: '일시중지' },
    CANCELLED: { color: 'dark', label: '취소' }
};

/** Status values that indicate a goal is finalized (no further actions allowed) */
const FINALIZED_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'];

/**
 * Progress thresholds for color coding.
 * Sorted in descending order for easy evaluation.
 * @type {Array<{threshold: number, color: string}>}
 */
const PROGRESS_THRESHOLDS = [
    { threshold: 75, color: 'success' },
    { threshold: 50, color: 'info' },
    { threshold: 25, color: 'warning' },
    { threshold: 0, color: 'danger' }
];

// =============================================================================
// STATE VARIABLES
// =============================================================================

let allGoals = [];
let currentFilter = 'all';
let currentGoalId = null;

// =============================================================================
// INITIALIZATION
// =============================================================================

/**
 * Initialize the goals page on document ready.
 * Loads goals, summary, and sets default start date.
 */
$(document).ready(function() {
    loadGoals();
    loadGoalSummary();

    // 시작일 기본값 설정
    $('#startDate').val(new Date().toISOString().split('T')[0]);
});

// =============================================================================
// DATA LOADING
// =============================================================================

/**
 * Load all goals from the server.
 * Updates the allGoals array and renders filtered goals.
 */
function loadGoals() {
    $.ajax({
        url: '/api/goals',
        method: 'GET',
        success: function(goals) {
            allGoals = goals;
            renderGoals(filterGoalsByStatus(currentFilter));
        },
        error: function(xhr) {
            console.error('목표 로드 실패:', xhr);
            showToast('목표를 불러오는데 실패했습니다.', 'danger');
        }
    });
}

/**
 * Load goal summary statistics from the server.
 * Updates the summary cards in the UI.
 */
function loadGoalSummary() {
    $.ajax({
        url: '/api/goals/summary',
        method: 'GET',
        success: function(summary) {
            updateSummaryCards(summary);
        },
        error: function(xhr) {
            console.error('요약 로드 실패:', xhr);
        }
    });
}

/**
 * Update summary cards with statistics.
 * @param {Object} summary - Summary statistics object
 * @param {number} summary.totalGoals - Total number of goals
 * @param {number} summary.activeGoals - Number of active goals
 * @param {number} summary.completedGoals - Number of completed goals
 * @param {number} summary.failedGoals - Number of failed goals
 * @param {number} summary.upcomingDeadlines - Number of upcoming deadlines
 * @param {number} summary.averageProgress - Average progress percentage
 */
function updateSummaryCards(summary) {
    $('#total-goals').text(summary.totalGoals || 0);
    $('#active-goals').text(summary.activeGoals || 0);
    $('#completed-goals').text(summary.completedGoals || 0);
    $('#failed-goals').text(summary.failedGoals || 0);
    $('#upcoming-goals').text(summary.upcomingDeadlines || 0);
    $('#avg-progress').text((summary.averageProgress || 0).toFixed(1) + '%');
}

// =============================================================================
// FILTERING
// =============================================================================

/**
 * Filter goals by status.
 * @param {string} status - Status to filter by, or 'all' for no filtering
 * @returns {Array} Filtered array of goals
 */
function filterGoalsByStatus(status) {
    if (status === 'all') {
        return allGoals;
    }
    return allGoals.filter(g => g.status === status);
}

/**
 * Apply filter and update the UI.
 * Updates button states and renders filtered goals.
 * @param {string} [status] - Status to filter by
 */
function filterGoals(status) {
    if (status) {
        currentFilter = status;
    }

    // 버튼 활성화 상태 업데이트
    $('.btn-group .btn').removeClass('active');
    if (currentFilter === 'all') {
        $('.btn-group .btn-outline-secondary').addClass('active');
    } else {
        $(`.btn-group .btn[onclick*="${currentFilter}"]`).addClass('active');
    }

    // 유형 필터 적용
    const typeFilter = $('#goalTypeFilter').val();
    let filtered = filterGoalsByStatus(currentFilter);

    if (typeFilter) {
        filtered = filtered.filter(g => g.goalType === typeFilter);
    }

    renderGoals(filtered);
}

/**
 * Refresh all goals by triggering server-side progress recalculation.
 */
function refreshGoals() {
    $.ajax({
        url: '/api/goals/refresh',
        method: 'POST',
        success: function() {
            loadGoals();
            loadGoalSummary();
            showToast('목표 진행률이 갱신되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('갱신 실패:', xhr);
            showToast('갱신에 실패했습니다.', 'danger');
        }
    });
}

// =============================================================================
// RENDERING
// =============================================================================

/**
 * Render goals to the container.
 * Shows empty state if no goals exist.
 * @param {Array} goals - Array of goal objects to render
 */
function renderGoals(goals) {
    const container = $('#goals-container');
    const emptyState = $('#empty-state');

    container.empty();

    if (!goals || goals.length === 0) {
        emptyState.show();
        return;
    }

    emptyState.hide();

    goals.forEach(goal => {
        const card = createGoalCard(goal);
        container.append(card);
    });
}

/**
 * Create HTML for a goal card.
 * @param {Object} goal - Goal object
 * @returns {string} HTML string for the goal card
 */
function createGoalCard(goal) {
    const progressColor = getProgressColor(goal.progressPercent, goal.status);
    const statusBadge = getStatusBadge(goal.status);
    const deadlineText = getDeadlineText(goal);
    const priorityColor = getPriorityColor(goal);

    return `
        <div class="col-md-6 col-lg-4">
            <div class="card goal-card h-100 cursor-pointer" onclick="showGoalDetail(${goal.id})">
                <div class="d-flex">
                    <div class="priority-indicator bg-${priorityColor}"></div>
                    <div class="card-body flex-grow-1">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <h6 class="card-title mb-0">${escapeHtml(goal.name)}</h6>
                            ${statusBadge}
                        </div>

                        <p class="text-muted small mb-2">
                            <i class="bi bi-tag me-1"></i>${goal.goalTypeLabel || goal.goalType}
                        </p>

                        <div class="mb-3">
                            <div class="d-flex justify-content-between small mb-1">
                                <span>진행률</span>
                                <span class="fw-bold">${(goal.progressPercent || 0).toFixed(1)}%</span>
                            </div>
                            <div class="progress" style="height: 8px;">
                                <div class="progress-bar bg-${progressColor}"
                                     role="progressbar"
                                     style="width: ${Math.min(goal.progressPercent || 0, 100)}%">
                                </div>
                            </div>
                        </div>

                        <div class="row text-center small">
                            <div class="col-6 border-end">
                                <div class="text-muted">현재</div>
                                <div class="fw-bold">${formatValue(goal.currentValue, goal.goalType)}</div>
                            </div>
                            <div class="col-6">
                                <div class="text-muted">목표</div>
                                <div class="fw-bold">${formatValue(goal.targetValue, goal.goalType)}</div>
                            </div>
                        </div>

                        ${deadlineText ? `
                        <div class="mt-2 pt-2 border-top small text-muted">
                            <i class="bi bi-calendar3 me-1"></i>${deadlineText}
                        </div>
                        ` : ''}
                    </div>
                </div>
            </div>
        </div>
    `;
}

// =============================================================================
// FORMATTING UTILITIES
// =============================================================================

/**
 * Get progress bar color based on percentage and status.
 * Status takes precedence over progress percentage.
 * @param {number} progress - Progress percentage (0-100)
 * @param {string} status - Goal status
 * @returns {string} Bootstrap color class name
 */
function getProgressColor(progress, status) {
    // Status-based colors take precedence
    const statusColors = {
        COMPLETED: 'success',
        FAILED: 'danger',
        PAUSED: 'secondary'
    };

    if (statusColors[status]) {
        return statusColors[status];
    }

    // Find the appropriate threshold-based color
    for (const { threshold, color } of PROGRESS_THRESHOLDS) {
        if (progress >= threshold) {
            return color;
        }
    }

    return 'danger';
}

/**
 * Generate status badge HTML.
 * @param {string} status - Goal status code
 * @returns {string} HTML string for the status badge
 */
function getStatusBadge(status) {
    const config = STATUS_CONFIG[status];
    if (!config) return '';

    return `<span class="badge bg-${config.color} status-badge">${config.label}</span>`;
}

/**
 * Get deadline text with appropriate styling.
 * @param {Object} goal - Goal object with deadline info
 * @returns {string|null} HTML string for deadline display, or null if no deadline
 */
function getDeadlineText(goal) {
    if (!goal.deadline) return null;

    if (goal.isOverdue) {
        return `<span class="text-danger">마감일 초과 (${Math.abs(goal.daysRemaining)}일 전)</span>`;
    }

    if (goal.daysRemaining <= 7) {
        return `<span class="text-warning">D-${goal.daysRemaining}</span>`;
    }

    return `D-${goal.daysRemaining}`;
}

/**
 * Get priority indicator color based on goal state.
 * @param {Object} goal - Goal object
 * @returns {string} Bootstrap color class name
 */
function getPriorityColor(goal) {
    if (goal.status !== 'ACTIVE') return 'secondary';
    if (goal.isOverdue) return 'danger';
    if (goal.daysRemaining && goal.daysRemaining <= 7) return 'warning';
    if (goal.progressPercent >= 75) return 'success';
    return 'primary';
}

/**
 * Format a value based on goal type.
 * Uses GOAL_TYPE_CONFIG for type-specific formatting.
 * @param {number|null|undefined} value - The value to format
 * @param {string} goalType - The goal type code
 * @returns {string} Formatted value string
 */
function formatValue(value, goalType) {
    if (value === null || value === undefined) return '-';

    const val = parseFloat(value);
    const config = GOAL_TYPE_CONFIG[goalType] || DEFAULT_GOAL_FORMAT;

    return config.format(val);
}

/**
 * Format currency value with Korean units (억, 만).
 * @param {number} value - The currency value
 * @returns {string} Formatted currency string
 */
function formatCurrency(value) {
    if (value >= 100000000) {
        return (value / 100000000).toFixed(1) + '억';
    }
    if (value >= 10000) {
        return (value / 10000).toFixed(0) + '만';
    }
    return value.toLocaleString();
}

/**
 * Format datetime string to Korean locale format.
 * @param {string} dateTimeStr - ISO datetime string
 * @returns {string} Formatted date and time string
 */
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleDateString('ko-KR') + ' ' + date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

// =============================================================================
// MODAL HANDLING - CREATE/EDIT
// =============================================================================

/**
 * Open the create goal modal with default values.
 */
function openCreateModal() {
    currentGoalId = null;
    $('#goalModalLabel').html('<i class="bi bi-bullseye me-2"></i>새 목표 설정');
    $('#goalForm')[0].reset();
    $('#goalId').val('');
    $('#startDate').val(new Date().toISOString().split('T')[0]);
    updateTargetPlaceholder();
}

/**
 * Update target value placeholder and unit based on selected goal type.
 * Uses GOAL_TYPE_CONFIG for type-specific configuration.
 */
function updateTargetPlaceholder() {
    const goalType = $('#goalType').val();
    const config = GOAL_TYPE_CONFIG[goalType] || DEFAULT_GOAL_FORMAT;

    $('#targetUnit').text(config.unit);
    $('#startUnit').text(config.unit);
    $('#targetValue').attr('placeholder', config.placeholder);
}

/**
 * Save a new or existing goal.
 * Validates required fields and sends data to the server.
 */
function saveGoal() {
    const goalData = {
        name: $('#goalName').val(),
        goalType: $('#goalType').val(),
        targetValue: parseFloat($('#targetValue').val()),
        startValue: $('#startValue').val() ? parseFloat($('#startValue').val()) : null,
        startDate: $('#startDate').val(),
        deadline: $('#deadline').val() || null,
        description: $('#goalDescription').val(),
        milestoneInterval: parseInt($('#milestoneInterval').val()),
        notificationEnabled: $('#notificationEnabled').is(':checked'),
        notes: $('#goalNotes').val()
    };

    // 유효성 검사
    if (!goalData.name || !goalData.goalType || !goalData.targetValue) {
        showToast('필수 항목을 모두 입력해주세요.', 'warning');
        return;
    }

    const isEdit = currentGoalId !== null;
    const url = isEdit ? `/api/goals/${currentGoalId}` : '/api/goals';
    const method = isEdit ? 'PUT' : 'POST';

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        data: JSON.stringify(goalData),
        success: function(result) {
            $('#goalModal').modal('hide');
            loadGoals();
            loadGoalSummary();
            showToast(isEdit ? '목표가 수정되었습니다.' : '새 목표가 생성되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('저장 실패:', xhr);
            showToast('저장에 실패했습니다: ' + (xhr.responseJSON?.message || xhr.statusText), 'danger');
        }
    });
}

/**
 * Open the edit modal with existing goal data.
 * @param {Object} goal - Goal object to edit
 */
function openEditModal(goal) {
    currentGoalId = goal.id;
    $('#goalModalLabel').html('<i class="bi bi-pencil me-2"></i>목표 수정');

    $('#goalId').val(goal.id);
    $('#goalName').val(goal.name);
    $('#goalType').val(goal.goalType);
    updateTargetPlaceholder();
    $('#targetValue').val(goal.targetValue);
    $('#startValue').val(goal.startValue);
    $('#startDate').val(goal.startDate);
    $('#deadline').val(goal.deadline || '');
    $('#goalDescription').val(goal.description || '');
    $('#milestoneInterval').val(goal.milestoneInterval || 25);
    $('#notificationEnabled').prop('checked', goal.notificationEnabled !== false);
    $('#goalNotes').val(goal.notes || '');

    $('#goalModal').modal('show');
}

// =============================================================================
// MODAL HANDLING - DETAIL VIEW
// =============================================================================

/**
 * Show goal detail modal.
 * @param {number} id - Goal ID to display
 */
function showGoalDetail(id) {
    $.ajax({
        url: `/api/goals/${id}`,
        method: 'GET',
        success: function(goal) {
            currentGoalId = goal.id;
            renderGoalDetail(goal);
            $('#goalDetailModal').modal('show');
        },
        error: function(xhr) {
            console.error('상세 조회 실패:', xhr);
            showToast('목표 정보를 불러올 수 없습니다.', 'danger');
        }
    });
}

/**
 * Render goal detail content in the modal.
 * @param {Object} goal - Goal object to render
 */
function renderGoalDetail(goal) {
    $('#goalDetailTitle').text(goal.name);

    const progressColor = getProgressColor(goal.progressPercent, goal.status);
    const circumference = 2 * Math.PI * 45;
    const offset = circumference - (Math.min(goal.progressPercent || 0, 100) / 100) * circumference;

    const content = `
        <div class="row">
            <div class="col-md-4 text-center mb-4">
                <svg class="progress-ring" width="120" height="120">
                    <circle cx="60" cy="60" r="45" stroke="#e9ecef" stroke-width="10" fill="none"/>
                    <circle class="progress-ring-circle" cx="60" cy="60" r="45"
                            stroke="var(--bs-${progressColor})" stroke-width="10" fill="none"
                            stroke-dasharray="${circumference}"
                            stroke-dashoffset="${offset}"
                            stroke-linecap="round"/>
                </svg>
                <h3 class="mt-2 mb-0">${(goal.progressPercent || 0).toFixed(1)}%</h3>
                <small class="text-muted">진행률</small>
            </div>
            <div class="col-md-8">
                <div class="row g-3">
                    <div class="col-6">
                        <label class="text-muted small">목표 유형</label>
                        <p class="mb-0 fw-bold">${goal.goalTypeLabel}</p>
                    </div>
                    <div class="col-6">
                        <label class="text-muted small">상태</label>
                        <p class="mb-0">${getStatusBadge(goal.status)}</p>
                    </div>
                    <div class="col-6">
                        <label class="text-muted small">현재 값</label>
                        <p class="mb-0 fw-bold text-primary">${formatValue(goal.currentValue, goal.goalType)}</p>
                    </div>
                    <div class="col-6">
                        <label class="text-muted small">목표 값</label>
                        <p class="mb-0 fw-bold">${formatValue(goal.targetValue, goal.goalType)}</p>
                    </div>
                    <div class="col-6">
                        <label class="text-muted small">시작일</label>
                        <p class="mb-0">${goal.startDate}</p>
                    </div>
                    <div class="col-6">
                        <label class="text-muted small">마감일</label>
                        <p class="mb-0">${goal.deadline || '-'}</p>
                    </div>
                </div>
            </div>
        </div>

        ${goal.description ? `
        <div class="mt-3 pt-3 border-top">
            <label class="text-muted small">설명</label>
            <p class="mb-0">${escapeHtml(goal.description)}</p>
        </div>
        ` : ''}

        ${goal.notes ? `
        <div class="mt-3 pt-3 border-top">
            <label class="text-muted small">메모</label>
            <p class="mb-0">${escapeHtml(goal.notes)}</p>
        </div>
        ` : ''}

        <div class="mt-3 pt-3 border-top">
            <div class="row text-center small">
                <div class="col-4">
                    <div class="text-muted">시작 값</div>
                    <div class="fw-bold">${formatValue(goal.startValue, goal.goalType)}</div>
                </div>
                <div class="col-4">
                    <div class="text-muted">경과일</div>
                    <div class="fw-bold">${goal.daysElapsed}일</div>
                </div>
                <div class="col-4">
                    <div class="text-muted">남은 일</div>
                    <div class="fw-bold ${goal.isOverdue ? 'text-danger' : ''}">${goal.daysRemaining !== null ? goal.daysRemaining + '일' : '-'}</div>
                </div>
            </div>
        </div>

        <div class="mt-3 text-muted small">
            <i class="bi bi-clock me-1"></i>생성: ${formatDateTime(goal.createdAt)}
            ${goal.updatedAt ? ` | 수정: ${formatDateTime(goal.updatedAt)}` : ''}
        </div>
    `;

    $('#goalDetailContent').html(content);

    // 버튼 이벤트 설정
    setupDetailButtons(goal);
}

/**
 * Setup event handlers for detail modal buttons.
 * Configures delete, pause/resume, and edit buttons based on goal state.
 * @param {Object} goal - Goal object
 */
function setupDetailButtons(goal) {
    $('#btnDeleteGoal').off('click').on('click', function() {
        if (confirm('이 목표를 삭제하시겠습니까?')) {
            deleteGoal(goal.id);
        }
    });

    $('#btnPauseGoal').off('click').on('click', function() {
        const newStatus = goal.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED';
        updateGoalStatus(goal.id, newStatus);
    });

    // 일시중지 버튼 텍스트 및 가시성 업데이트
    const isPaused = goal.status === 'PAUSED';
    const isFinalized = FINALIZED_STATUSES.includes(goal.status);

    $('#btnPauseGoal')
        .html(isPaused ? '<i class="bi bi-play me-1"></i>재개' : '<i class="bi bi-pause me-1"></i>일시중지')
        .toggle(!isFinalized);

    $('#btnEditGoal').off('click').on('click', function() {
        $('#goalDetailModal').modal('hide');
        openEditModal(goal);
    });
}

// =============================================================================
// GOAL OPERATIONS (DELETE, STATUS UPDATE)
// =============================================================================

/**
 * Delete a goal by ID.
 * @param {number} id - Goal ID to delete
 */
function deleteGoal(id) {
    $.ajax({
        url: `/api/goals/${id}`,
        method: 'DELETE',
        success: function() {
            $('#goalDetailModal').modal('hide');
            loadGoals();
            loadGoalSummary();
            showToast('목표가 삭제되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('삭제 실패:', xhr);
            showToast('삭제에 실패했습니다.', 'danger');
        }
    });
}

/**
 * Update goal status.
 * @param {number} id - Goal ID
 * @param {string} status - New status value
 */
function updateGoalStatus(id, status) {
    $.ajax({
        url: `/api/goals/${id}/status?status=${status}`,
        method: 'PATCH',
        success: function() {
            $('#goalDetailModal').modal('hide');
            loadGoals();
            loadGoalSummary();
            showToast('목표 상태가 변경되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('상태 변경 실패:', xhr);
            showToast('상태 변경에 실패했습니다.', 'danger');
        }
    });
}

/**
 * Show a Bootstrap toast notification.
 * @param {string} message - Message to display
 * @param {string} type - Bootstrap color type (success, danger, warning, info)
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
