/**
 * 목표 관리 JavaScript
 */

let allGoals = [];
let currentFilter = 'all';
let currentGoalId = null;

// 페이지 로드 시 초기화
$(document).ready(function() {
    loadGoals();
    loadGoalSummary();

    // 시작일 기본값 설정
    $('#startDate').val(new Date().toISOString().split('T')[0]);
});

/**
 * 전체 목표 로드
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
 * 목표 요약 로드
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
 * 요약 카드 업데이트
 */
function updateSummaryCards(summary) {
    $('#total-goals').text(summary.totalGoals || 0);
    $('#active-goals').text(summary.activeGoals || 0);
    $('#completed-goals').text(summary.completedGoals || 0);
    $('#failed-goals').text(summary.failedGoals || 0);
    $('#upcoming-goals').text(summary.upcomingDeadlines || 0);
    $('#avg-progress').text((summary.averageProgress || 0).toFixed(1) + '%');
}

/**
 * 상태별 필터링
 */
function filterGoalsByStatus(status) {
    if (status === 'all') {
        return allGoals;
    }
    return allGoals.filter(g => g.status === status);
}

/**
 * 필터 적용
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
 * 목표 새로고침
 */
function refreshGoals() {
    // 진행률 갱신 API 호출
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

/**
 * 목표 카드 렌더링
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
 * 목표 카드 생성
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

/**
 * 진행률에 따른 색상
 */
function getProgressColor(progress, status) {
    if (status === 'COMPLETED') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'PAUSED') return 'secondary';

    if (progress >= 75) return 'success';
    if (progress >= 50) return 'info';
    if (progress >= 25) return 'warning';
    return 'danger';
}

/**
 * 상태 배지
 */
function getStatusBadge(status) {
    const badges = {
        'ACTIVE': '<span class="badge bg-info status-badge">진행 중</span>',
        'COMPLETED': '<span class="badge bg-success status-badge">달성</span>',
        'FAILED': '<span class="badge bg-danger status-badge">미달성</span>',
        'PAUSED': '<span class="badge bg-secondary status-badge">일시중지</span>',
        'CANCELLED': '<span class="badge bg-dark status-badge">취소</span>'
    };
    return badges[status] || '';
}

/**
 * 마감일 텍스트
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
 * 우선순위 색상
 */
function getPriorityColor(goal) {
    if (goal.status !== 'ACTIVE') return 'secondary';
    if (goal.isOverdue) return 'danger';
    if (goal.daysRemaining && goal.daysRemaining <= 7) return 'warning';
    if (goal.progressPercent >= 75) return 'success';
    return 'primary';
}

/**
 * 값 포맷팅
 */
function formatValue(value, goalType) {
    if (value === null || value === undefined) return '-';

    const val = parseFloat(value);

    switch(goalType) {
        case 'RETURN_RATE':
        case 'WIN_RATE':
        case 'MAX_DRAWDOWN_LIMIT':
            return val.toFixed(2) + '%';
        case 'TARGET_AMOUNT':
        case 'SAVINGS_AMOUNT':
        case 'DIVIDEND_INCOME':
            return formatCurrency(val);
        case 'TRADE_COUNT':
            return val.toFixed(0) + '회';
        case 'SHARPE_RATIO':
            return val.toFixed(2);
        default:
            return val.toFixed(2);
    }
}

/**
 * 통화 포맷
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
 * 생성 모달 열기
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
 * 목표 유형에 따른 단위 업데이트
 */
function updateTargetPlaceholder() {
    const goalType = $('#goalType').val();
    let unit = '';
    let placeholder = '목표 수치 입력';

    switch(goalType) {
        case 'RETURN_RATE':
        case 'WIN_RATE':
        case 'MAX_DRAWDOWN_LIMIT':
            unit = '%';
            placeholder = goalType === 'RETURN_RATE' ? '예: 20' :
                         goalType === 'WIN_RATE' ? '예: 60' : '예: 10';
            break;
        case 'TARGET_AMOUNT':
        case 'SAVINGS_AMOUNT':
        case 'DIVIDEND_INCOME':
            unit = '원';
            placeholder = '예: 10000000';
            break;
        case 'TRADE_COUNT':
            unit = '회';
            placeholder = '예: 100';
            break;
        case 'SHARPE_RATIO':
            unit = '';
            placeholder = '예: 1.5';
            break;
        default:
            unit = '';
    }

    $('#targetUnit').text(unit);
    $('#startUnit').text(unit);
    $('#targetValue').attr('placeholder', placeholder);
}

/**
 * 목표 저장
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
 * 목표 상세 보기
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
 * 상세 모달 렌더링
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
 * 상세 모달 버튼 설정
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

    // 일시중지 버튼 텍스트 업데이트
    if (goal.status === 'PAUSED') {
        $('#btnPauseGoal').html('<i class="bi bi-play me-1"></i>재개');
    } else {
        $('#btnPauseGoal').html('<i class="bi bi-pause me-1"></i>일시중지');
    }

    // 완료/실패 상태면 일시중지 버튼 숨김
    if (goal.status === 'COMPLETED' || goal.status === 'FAILED' || goal.status === 'CANCELLED') {
        $('#btnPauseGoal').hide();
    } else {
        $('#btnPauseGoal').show();
    }

    $('#btnEditGoal').off('click').on('click', function() {
        $('#goalDetailModal').modal('hide');
        openEditModal(goal);
    });
}

/**
 * 수정 모달 열기
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

/**
 * 목표 삭제
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
 * 목표 상태 변경
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
    // Bootstrap 토스트 또는 간단한 알림
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
