/**
 * 트레이드 플랜 관리 JavaScript
 *
 * @fileoverview Trade plan management with status-based card rendering
 * @requires jQuery, Bootstrap, utils.js (ToastNotification, LoadingOverlay, handleAjaxError, showConfirmDialog)
 */

// ============================================================================
// Constants & Configuration
// ============================================================================

/**
 * Status configuration mapping
 * Maps status codes to display properties (color, badge class, label, icon)
 * @type {Object.<string, {color: string, badgeClass: string, label: string, icon: string}>}
 */
const STATUS_CONFIG = {
    PENDING: {
        color: '#8b5cf6',
        badgeClass: 'pending',
        label: '대기 중',
        icon: 'bi-clock'
    },
    EXECUTED: {
        color: '#059669',
        badgeClass: 'executed',
        label: '실행됨',
        icon: 'bi-check-circle'
    },
    CANCELLED: {
        color: '#6b7280',
        badgeClass: 'cancelled',
        label: '취소됨',
        icon: 'bi-x-circle'
    },
    EXPIRED: {
        color: '#dc2626',
        badgeClass: 'expired',
        label: '만료됨',
        icon: 'bi-calendar-x'
    }
};

/** Default status config for unknown statuses */
const DEFAULT_STATUS_CONFIG = {
    color: '#667eea',
    badgeClass: 'secondary',
    label: '알 수 없음',
    icon: 'bi-question-circle'
};

/**
 * Plan type labels mapping
 * @type {Object.<string, string>}
 */
const PLAN_TYPE_LABELS = {
    LONG: '롱 포지션',
    SHORT: '숏 포지션',
    SWING: '스윙 트레이딩',
    DAY: '데이 트레이딩',
    SCALP: '스캘핑'
};

// ============================================================================
// State Variables
// ============================================================================

let allPlans = [];
let currentFilter = 'all';
let currentPlanId = null;
let planTypes = [];
let strategies = [];

// ============================================================================
// Initialization
// ============================================================================

/**
 * Initialize the plans page on document ready
 */
$(document).ready(function() {
    loadMetadata();
    loadPlans();
    loadStatistics();
});

// ============================================================================
// Data Loading Functions
// ============================================================================

/**
 * Load metadata (plan types, strategies)
 */
function loadMetadata() {
    // 플랜 유형 로드
    $.ajax({
        url: '/api/plans/types',
        method: 'GET',
        success: function(types) {
            planTypes = types;
            populatePlanTypes();
        },
        error: function(xhr) {
            console.error('플랜 유형 로드 실패:', xhr);
        }
    });

    // 전략 목록 로드
    $.ajax({
        url: '/api/plans/strategies',
        method: 'GET',
        success: function(data) {
            strategies = data;
            populateStrategies();
        },
        error: function(xhr) {
            console.error('전략 목록 로드 실패:', xhr);
        }
    });
}

/**
 * Populate plan type select options
 */
function populatePlanTypes() {
    const select = $('#planType');
    select.empty().append('<option value="">선택</option>');

    planTypes.forEach(type => {
        select.append(`<option value="${type}">${getPlanTypeLabel(type)}</option>`);
    });
}

/**
 * Populate strategy select options
 */
function populateStrategies() {
    const select = $('#strategy');
    select.empty().append('<option value="">선택</option>');

    strategies.forEach(strategy => {
        select.append(`<option value="${strategy}">${strategy}</option>`);
    });
}

/**
 * Load all plans from server
 */
function loadPlans() {
    $.ajax({
        url: '/api/plans',
        method: 'GET',
        data: { page: 0, size: 100 },
        success: function(response) {
            allPlans = response.content || response;
            renderPlans(filterPlansByStatus(currentFilter));
        },
        error: function(xhr) {
            console.error('플랜 로드 실패:', xhr);
            ToastNotification.error('플랜을 불러오는데 실패했습니다.');
        }
    });
}

/**
 * Load statistics from server
 */
function loadStatistics() {
    $.ajax({
        url: '/api/plans/statistics',
        method: 'GET',
        success: function(stats) {
            updateStatistics(stats);
        },
        error: function(xhr) {
            console.error('통계 로드 실패:', xhr);
        }
    });
}

/**
 * Update statistics display
 * @param {Object} stats - Statistics object from server
 */
function updateStatistics(stats) {
    $('#stat-total').text(stats.totalPlans || 0);
    $('#stat-pending').text(stats.pendingPlans || 0);
    $('#stat-executed').text(stats.executedPlans || 0);
    $('#stat-execution-rate').text((stats.executionRate || 0).toFixed(1) + '%');
    $('#stat-compliance-rate').text((stats.complianceRate || 0).toFixed(1) + '%');
    $('#stat-avg-r').text((stats.averageRMultiple || 0).toFixed(2));
}

// ============================================================================
// Filtering Functions
// ============================================================================

/**
 * Filter plans by status
 * @param {string} status - Status to filter by ('all' for no filter)
 * @returns {Array} Filtered plans array
 */
function filterPlansByStatus(status) {
    if (status === 'all') {
        return allPlans;
    }
    return allPlans.filter(p => p.status === status);
}

/**
 * Apply filter and update UI
 * @param {string} status - Status to filter by
 */
function filterPlans(status) {
    currentFilter = status;

    // Update button active state
    $('.filter-btn').removeClass('active');
    if (status === 'all') {
        $('.filter-btn:first').addClass('active');
    } else {
        $(`.filter-btn[onclick*="${status}"]`).addClass('active');
    }

    renderPlans(filterPlansByStatus(status));
}

/**
 * Refresh plans list
 */
function refreshPlans() {
    loadPlans();
    loadStatistics();
    ToastNotification.success('플랜 목록이 갱신되었습니다.');
}

// ============================================================================
// Rendering Functions
// ============================================================================

/**
 * Render plans to the container
 * @param {Array} plans - Array of plan objects to render
 */
function renderPlans(plans) {
    const container = $('#plans-container');
    const emptyState = $('#empty-state');

    container.empty();

    if (!plans || plans.length === 0) {
        emptyState.show();
        return;
    }

    emptyState.hide();

    plans.forEach(plan => {
        const card = createPlanCard(plan);
        container.append(card);
    });
}

/**
 * Create a plan card HTML
 * @param {Object} plan - Plan object
 * @returns {string} HTML string for the plan card
 */
function createPlanCard(plan) {
    const statusConfig = getStatusConfig(plan.status);
    const rrRatio = calculateRRRatio(plan);
    const riskReward = `1:${rrRatio.toFixed(2)}`;

    return `
        <div class="plan-card-glass" style="--status-color: ${statusConfig.color};">
            ${renderCardHeader(plan, statusConfig)}
            ${renderPriceBox(plan)}
            ${renderRiskRewardSection(riskReward, plan.plannedQuantity)}
            ${renderStrategySection(plan.strategy)}
            ${renderValidUntilSection(plan.validUntil)}
            ${renderEntryConditionsSection(plan.entryConditions)}
            ${renderActionButtons(plan)}
            ${renderCardFooter(plan.createdAt)}
        </div>
    `;
}

/**
 * Render card header with symbol and status badge
 * @param {Object} plan - Plan object
 * @param {Object} statusConfig - Status configuration
 * @returns {string} HTML string
 */
function renderCardHeader(plan, statusConfig) {
    return `
        <div class="plan-card-header">
            <div class="plan-symbol">${escapeHtml(plan.symbol)}</div>
            <span class="plan-status-badge ${statusConfig.badgeClass}">${statusConfig.label}</span>
        </div>
    `;
}

/**
 * Render price box with entry, stop loss, and target prices
 * @param {Object} plan - Plan object
 * @returns {string} HTML string
 */
function renderPriceBox(plan) {
    return `
        <div class="plan-prices">
            <div class="plan-price-item">
                <div class="plan-price-label">진입</div>
                <div class="plan-price-value">${formatPrice(plan.entryPrice)}</div>
            </div>
            <div class="plan-price-item">
                <div class="plan-price-label">손절</div>
                <div class="plan-price-value text-negative">${formatPrice(plan.stopLoss)}</div>
            </div>
            <div class="plan-price-item">
                <div class="plan-price-value text-positive">${formatPrice(plan.targetPrice)}</div>
            </div>
        </div>
    `;
}

/**
 * Render risk/reward ratio and quantity section
 * @param {string} riskReward - Formatted R:R ratio string
 * @param {number} plannedQuantity - Planned quantity
 * @returns {string} HTML string
 */
function renderRiskRewardSection(riskReward, plannedQuantity) {
    return `
        <div style="display: flex; justify-content: space-between; align-items: center; margin: var(--space-4) 0;">
            <div class="plan-rr-ratio">
                <i class="bi bi-bullseye"></i>
                ${riskReward}
            </div>
            <div style="text-align: right;">
                <div class="plan-price-label">수량</div>
                <div class="plan-price-value">${formatNumber(plannedQuantity || 0)}</div>
            </div>
        </div>
    `;
}

/**
 * Render strategy section
 * @param {string} strategy - Strategy name
 * @returns {string} HTML string
 */
function renderStrategySection(strategy) {
    return `
        <div style="margin-bottom: var(--space-3);">
            <div class="plan-price-label" style="display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-1);">
                <i class="bi bi-diagram-3"></i>
                전략
            </div>
            <div style="font-size: var(--font-size-sm);">${escapeHtml(strategy || '-')}</div>
        </div>
    `;
}

/**
 * Render valid until section (conditional)
 * @param {string} validUntil - Valid until date string
 * @returns {string} HTML string (empty if no date)
 */
function renderValidUntilSection(validUntil) {
    if (!validUntil) return '';

    return `
        <div class="mb-3">
            <small class="text-muted">
                <i class="bi bi-calendar-event me-1"></i>유효기한: ${formatDate(validUntil)}
            </small>
        </div>
    `;
}

/**
 * Render entry conditions section (conditional)
 * @param {string} entryConditions - Entry conditions text
 * @returns {string} HTML string (empty if no conditions)
 */
function renderEntryConditionsSection(entryConditions) {
    if (!entryConditions) return '';

    return `
        <div class="mb-3">
            <small class="text-muted d-block mb-1">진입 조건</small>
            <div class="small text-truncate" title="${escapeHtml(entryConditions)}">
                ${escapeHtml(entryConditions)}
            </div>
        </div>
    `;
}

/**
 * Render action buttons based on plan status
 * @param {Object} plan - Plan object
 * @returns {string} HTML string
 */
function renderActionButtons(plan) {
    const buttons = getActionButtonsForStatus(plan.status, plan.id);

    return `
        <div class="plan-actions">
            ${buttons}
        </div>
    `;
}

/**
 * Get action buttons HTML based on status
 * @param {string} status - Plan status
 * @param {number} id - Plan ID
 * @returns {string} HTML string for buttons
 */
function getActionButtonsForStatus(status, id) {
    switch (status) {
        case 'PENDING':
            return `
                <button class="plan-action-btn primary" onclick="openExecuteModal(${id})">
                    <i class="bi bi-play-fill"></i>
                    실행
                </button>
                <button class="plan-action-btn" onclick="openEditModal(${id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="plan-action-btn" onclick="cancelPlan(${id})">
                    <i class="bi bi-x-lg"></i>
                </button>
            `;

        case 'EXECUTED':
            return `
                <button class="plan-action-btn" onclick="viewPlanDetail(${id})">
                    <i class="bi bi-eye"></i>
                    상세보기
                </button>
            `;

        case 'CANCELLED':
        case 'EXPIRED':
            return `
                <button class="plan-action-btn" onclick="viewPlanDetail(${id})">
                    <i class="bi bi-eye"></i>
                    상세보기
                </button>
                <button class="plan-action-btn" onclick="deletePlan(${id})">
                    <i class="bi bi-trash"></i>
                </button>
            `;

        default:
            return '';
    }
}

/**
 * Render card footer with creation timestamp
 * @param {string} createdAt - Creation timestamp
 * @returns {string} HTML string
 */
function renderCardFooter(createdAt) {
    return `
        <div style="margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--glass-border); font-size: var(--font-size-xs); color: var(--text-muted);">
            <i class="bi bi-clock"></i> 생성: ${formatDateTime(createdAt)}
        </div>
    `;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Calculate Risk/Reward ratio
 * @param {Object} plan - Plan object with entryPrice, stopLoss, targetPrice
 * @returns {number} R:R ratio
 */
function calculateRRRatio(plan) {
    const risk = Math.abs(plan.entryPrice - plan.stopLoss);
    const reward = Math.abs(plan.targetPrice - plan.entryPrice);

    if (risk === 0) return 0;
    return reward / risk;
}

/**
 * Get status configuration object
 * @param {string} status - Status code
 * @returns {Object} Status configuration with color, badgeClass, label, icon
 */
function getStatusConfig(status) {
    return STATUS_CONFIG[status] || DEFAULT_STATUS_CONFIG;
}

/**
 * Get status info (legacy compatibility)
 * @param {string} status - Status code
 * @returns {Object} Status info with label and class
 * @deprecated Use getStatusConfig instead
 */
function getStatusInfo(status) {
    const config = getStatusConfig(status);
    return { label: config.label, class: config.badgeClass };
}

/**
 * Get status color (legacy compatibility)
 * @param {string} status - Status code
 * @returns {string} Color hex code
 * @deprecated Use getStatusConfig instead
 */
function getStatusColor(status) {
    return getStatusConfig(status).color;
}

/**
 * Get plan type label
 * @param {string} type - Plan type code
 * @returns {string} Localized label
 */
function getPlanTypeLabel(type) {
    return PLAN_TYPE_LABELS[type] || type;
}

// ============================================================================
// Modal Functions
// ============================================================================

/**
 * Open create plan modal
 */
function openCreateModal() {
    currentPlanId = null;
    $('#planModalLabel').html('<i class="bi bi-journal-plus"></i> 새 트레이드 플랜 작성');
    $('#planForm')[0].reset();
    $('#planId').val('');
    $('#position-calc-result').hide();
    $('#riskPercent').val('1');
    openModal('planModal');
}

/**
 * Open edit plan modal
 * @param {number} id - Plan ID to edit
 */
function openEditModal(id) {
    $.ajax({
        url: `/api/plans/${id}`,
        method: 'GET',
        success: function(plan) {
            currentPlanId = plan.id;
            $('#planModalLabel').html('<i class="bi bi-pencil"></i> 트레이드 플랜 수정');

            $('#planId').val(plan.id);
            $('#symbol').val(plan.symbol);
            $('#planType').val(plan.planType);
            $('#strategy').val(plan.strategy);
            $('#entryPrice').val(plan.entryPrice);
            $('#stopLoss').val(plan.stopLoss);
            $('#targetPrice').val(plan.targetPrice);
            $('#riskPercent').val(plan.riskPercent || 1);
            $('#plannedQuantity').val(plan.plannedQuantity);
            $('#validUntil').val(plan.validUntil || '');
            $('#entryConditions').val(plan.entryConditions || '');
            $('#exitConditions').val(plan.exitConditions || '');
            $('#marketAnalysis').val(plan.marketAnalysis || '');
            $('#notes').val(plan.notes || '');

            openModal('planModal');
        },
        error: function(xhr) {
            console.error('플랜 조회 실패:', xhr);
            showGlassToast('플랜 정보를 불러올 수 없습니다.', 'error');
        }
    });
}

/**
 * Open execute plan modal
 * @param {number} id - Plan ID to execute
 */
function openExecuteModal(id) {
    currentPlanId = id;

    // Load plan info and set default values
    $.ajax({
        url: `/api/plans/${id}`,
        method: 'GET',
        success: function(plan) {
            $('#actualEntryPrice').val(plan.entryPrice);
            $('#actualQuantity').val(plan.plannedQuantity || '');
            $('#executionNotes').val('');
            openModal('executeModal');
        },
        error: function(xhr) {
            console.error('플랜 조회 실패:', xhr);
            showGlassToast('플랜 정보를 불러올 수 없습니다.', 'error');
        }
    });
}

// ============================================================================
// Position Sizing Functions
// ============================================================================

/**
 * Calculate position size based on risk parameters
 */
function calculatePosition() {
    const entryPrice = parseFloat($('#entryPrice').val());
    const stopLoss = parseFloat($('#stopLoss').val());
    const riskPercent = parseFloat($('#riskPercent').val());

    if (!entryPrice || !stopLoss || !riskPercent) {
        ToastNotification.warning('진입가, 손절가, 리스크 비율을 입력해주세요.');
        return;
    }

    $.ajax({
        url: '/api/plans/calculate-position',
        method: 'GET',
        data: {
            entryPrice: entryPrice,
            stopLoss: stopLoss,
            riskPercent: riskPercent
        },
        success: function(result) {
            displayPositionResult(result);
        },
        error: function(xhr) {
            console.error('포지션 계산 실패:', xhr);
            ToastNotification.error('포지션 사이징 계산에 실패했습니다.');
        }
    });
}

/**
 * Display position calculation result
 * @param {Object} result - Position calculation result
 */
function displayPositionResult(result) {
    const resultHtml = `
        <div class="position-calc-result">
            <h6 class="mb-3">
                <i class="bi bi-calculator me-2"></i>포지션 사이징 결과
            </h6>
            <div class="row g-3">
                <div class="col-md-3">
                    <div class="text-muted small">권장 수량</div>
                    <div class="h5 mb-0 text-success">${formatNumber(result.recommendedQuantity)}</div>
                </div>
                <div class="col-md-3">
                    <div class="text-muted small">리스크 금액</div>
                    <div class="h6 mb-0">${formatCurrency(result.riskAmount)}</div>
                </div>
                <div class="col-md-3">
                    <div class="text-muted small">포지션 크기</div>
                    <div class="h6 mb-0">${formatCurrency(result.positionSize)}</div>
                </div>
                <div class="col-md-3">
                    <div class="text-muted small">R:R 비율</div>
                    <div class="h6 mb-0">1:${result.rrRatio.toFixed(2)}</div>
                </div>
            </div>
            <div class="mt-3 pt-3 border-top">
                <button type="button" class="btn btn-sm btn-success" onclick="applyCalculatedQuantity(${result.recommendedQuantity})">
                    <i class="bi bi-check-lg me-1"></i>이 수량 적용
                </button>
            </div>
        </div>
    `;

    $('#position-calc-result').html(resultHtml).slideDown();
}

/**
 * Apply calculated quantity to the form
 * @param {number} quantity - Quantity to apply
 */
function applyCalculatedQuantity(quantity) {
    $('#plannedQuantity').val(quantity);
    ToastNotification.success('수량이 적용되었습니다.');
}

// ============================================================================
// CRUD Operations
// ============================================================================

/**
 * Save plan (create or update)
 */
function savePlan() {
    const planData = {
        symbol: $('#symbol').val().trim().toUpperCase(),
        planType: $('#planType').val(),
        strategy: $('#strategy').val(),
        entryPrice: parseFloat($('#entryPrice').val()),
        stopLoss: parseFloat($('#stopLoss').val()),
        targetPrice: parseFloat($('#targetPrice').val()),
        riskPercent: parseFloat($('#riskPercent').val()),
        plannedQuantity: $('#plannedQuantity').val() ? parseInt($('#plannedQuantity').val()) : null,
        validUntil: $('#validUntil').val() || null,
        entryConditions: $('#entryConditions').val().trim() || null,
        exitConditions: $('#exitConditions').val().trim() || null,
        marketAnalysis: $('#marketAnalysis').val().trim() || null,
        notes: $('#notes').val().trim() || null
    };

    // 유효성 검사
    if (!planData.symbol || !planData.planType || !planData.strategy) {
        ToastNotification.warning('필수 항목을 모두 입력해주세요.');
        return;
    }

    if (!planData.entryPrice || !planData.stopLoss || !planData.targetPrice) {
        ToastNotification.warning('가격 정보를 모두 입력해주세요.');
        return;
    }

    // R:R 비율 검증
    const risk = Math.abs(planData.entryPrice - planData.stopLoss);
    const reward = Math.abs(planData.targetPrice - planData.entryPrice);
    if (reward / risk < 1) {
        if (!confirm('R:R 비율이 1:1 미만입니다. 계속하시겠습니까?')) {
            return;
        }
    }

    const isEdit = currentPlanId !== null;
    const url = isEdit ? `/api/plans/${currentPlanId}` : '/api/plans';
    const method = isEdit ? 'PUT' : 'POST';

    LoadingOverlay.show('저장 중...');

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        data: JSON.stringify(planData),
        success: function(result) {
            LoadingOverlay.hide();
            closeModal('planModal');
            loadPlans();
            loadStatistics();
            ToastNotification.success(isEdit ? '플랜이 수정되었습니다.' : '새 플랜이 생성되었습니다.');
        },
        error: function(xhr) {
            LoadingOverlay.hide();
            console.error('저장 실패:', xhr);
            handleAjaxError(xhr, '플랜 저장에 실패했습니다.');
        }
    });
}

/**
 * Execute plan and create trade
 */
function confirmExecutePlan() {
    const actualEntryPrice = parseFloat($('#actualEntryPrice').val());
    const actualQuantity = parseInt($('#actualQuantity').val());
    const executionNotes = $('#executionNotes').val().trim();

    if (!actualEntryPrice || !actualQuantity) {
        ToastNotification.warning('실제 진입가와 수량을 입력해주세요.');
        return;
    }

    const executeData = {
        actualEntryPrice: actualEntryPrice,
        actualQuantity: actualQuantity,
        executionNotes: executionNotes || null
    };

    LoadingOverlay.show('실행 중...');

    $.ajax({
        url: `/api/plans/${currentPlanId}/execute`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(executeData),
        success: function(result) {
            LoadingOverlay.hide();
            closeModal('executeModal');
            loadPlans();
            loadStatistics();
            ToastNotification.success('플랜이 실행되었습니다. 거래가 생성되었습니다.');
        },
        error: function(xhr) {
            LoadingOverlay.hide();
            console.error('실행 실패:', xhr);
            handleAjaxError(xhr, '플랜 실행에 실패했습니다.');
        }
    });
}

/**
 * Cancel a pending plan
 * @param {number} id - Plan ID to cancel
 */
function cancelPlan(id) {
    showConfirmDialog(
        '플랜 취소',
        '이 플랜을 취소하시겠습니까?',
        function() {
            $.ajax({
                url: `/api/plans/${id}/cancel`,
                method: 'POST',
                success: function() {
                    loadPlans();
                    loadStatistics();
                    ToastNotification.success('플랜이 취소되었습니다.');
                },
                error: function(xhr) {
                    console.error('취소 실패:', xhr);
                    handleAjaxError(xhr, '플랜 취소에 실패했습니다.');
                }
            });
        }
    );
}

/**
 * Delete a plan permanently
 * @param {number} id - Plan ID to delete
 */
function deletePlan(id) {
    showConfirmDialog(
        '플랜 삭제',
        '이 플랜을 완전히 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.',
        function() {
            $.ajax({
                url: `/api/plans/${id}`,
                method: 'DELETE',
                success: function() {
                    loadPlans();
                    loadStatistics();
                    ToastNotification.success('플랜이 삭제되었습니다.');
                },
                error: function(xhr) {
                    console.error('삭제 실패:', xhr);
                    handleAjaxError(xhr, '플랜 삭제에 실패했습니다.');
                }
            });
        }
    );
}

// ============================================================================
// Plan Detail View
// ============================================================================

/**
 * View plan details
 * @param {number} id - Plan ID to view
 */
function viewPlanDetail(id) {
    $.ajax({
        url: `/api/plans/${id}`,
        method: 'GET',
        success: function(plan) {
            showPlanDetailModal(plan);
        },
        error: function(xhr) {
            console.error('플랜 조회 실패:', xhr);
            ToastNotification.error('플랜 정보를 불러올 수 없습니다.');
        }
    });
}

/**
 * Show plan detail modal
 * @param {Object} plan - Plan object to display
 */
function showPlanDetailModal(plan) {
    const statusConfig = getStatusConfig(plan.status);
    const rrRatio = calculateRRRatio(plan);

    const modalHtml = `
        <div class="modal-glass" id="planDetailModal">
            <div class="modal-glass-content" style="max-width: 800px;">
                <div class="modal-header">
                    <h3>
                        <i class="bi bi-journal-text"></i> ${escapeHtml(plan.symbol)} 플랜 상세
                    </h3>
                    <button type="button" class="modal-close" onclick="closePlanDetailModal()">
                        <i class="bi bi-x-lg"></i>
                    </button>
                </div>
                <div class="modal-body">
                        ${renderDetailStatusRow(statusConfig, plan.strategy)}
                        ${renderDetailPriceRow(plan)}
                        ${renderDetailQuantityRow(plan.plannedQuantity, rrRatio)}
                        ${renderDetailTextSection('진입 조건', plan.entryConditions)}
                        ${renderDetailTextSection('청산 조건', plan.exitConditions)}
                        ${renderDetailTextSection('시장 분석', plan.marketAnalysis)}
                        ${renderDetailTextSection('메모', plan.notes)}
                        ${renderDetailTimestamps(plan.createdAt, plan.updatedAt)}
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-glass" onclick="closePlanDetailModal()">닫기</button>
                </div>
            </div>
        </div>
    `;

    $('#planDetailModal').remove();
    $('body').append(modalHtml);

    const modalElement = document.getElementById('planDetailModal');
    modalElement.addEventListener('click', function(e) {
        if (e.target === modalElement) {
            closePlanDetailModal();
        }
    });

    openModal('planDetailModal');
}

/**
 * Close dynamically generated plan detail modal
 */
function closePlanDetailModal() {
    const modal = document.getElementById('planDetailModal');
    if (!modal) {
        return;
    }

    closeModal('planDetailModal');
    setTimeout(() => {
        if (modal.parentNode) {
            modal.remove();
        }
    }, 300);
}

/**
 * Render detail modal status row
 * @param {Object} statusConfig - Status configuration
 * @param {string} strategy - Strategy name
 * @returns {string} HTML string
 */
function renderDetailStatusRow(statusConfig, strategy) {
    return `
        <div class="row mb-4">
            <div class="col-md-6">
                <label class="text-muted small">상태</label>
                <div><span class="status-badge badge bg-${statusConfig.badgeClass}">${statusConfig.label}</span></div>
            </div>
            <div class="col-md-6">
                <label class="text-muted small">전략</label>
                <div class="fw-bold">${escapeHtml(strategy || '-')}</div>
            </div>
        </div>
    `;
}

/**
 * Render detail modal price row
 * @param {Object} plan - Plan object
 * @returns {string} HTML string
 */
function renderDetailPriceRow(plan) {
    return `
        <div class="row mb-4">
            <div class="col-md-4">
                <label class="text-muted small">진입가</label>
                <div class="h5">${formatPrice(plan.entryPrice)}</div>
            </div>
            <div class="col-md-4">
                <label class="text-muted small">손절가</label>
                <div class="h5 text-danger">${formatPrice(plan.stopLoss)}</div>
            </div>
            <div class="col-md-4">
                <label class="text-muted small">목표가</label>
                <div class="h5 text-success">${formatPrice(plan.targetPrice)}</div>
            </div>
        </div>
    `;
}

/**
 * Render detail modal quantity and R:R row
 * @param {number} plannedQuantity - Planned quantity
 * @param {number} rrRatio - Risk/Reward ratio
 * @returns {string} HTML string
 */
function renderDetailQuantityRow(plannedQuantity, rrRatio) {
    return `
        <div class="row mb-4">
            <div class="col-md-6">
                <label class="text-muted small">계획 수량</label>
                <div class="fw-bold">${formatNumber(plannedQuantity || 0)}</div>
            </div>
            <div class="col-md-6">
                <label class="text-muted small">R:R 비율</label>
                <div class="fw-bold">1:${rrRatio.toFixed(2)}</div>
            </div>
        </div>
    `;
}

/**
 * Render detail modal text section (conditional)
 * @param {string} label - Section label
 * @param {string} content - Section content
 * @returns {string} HTML string (empty if no content)
 */
function renderDetailTextSection(label, content) {
    if (!content) return '';

    return `
        <div class="mb-3">
            <label class="text-muted small">${label}</label>
            <div class="p-3 bg-light rounded">${escapeHtml(content)}</div>
        </div>
    `;
}

/**
 * Render detail modal timestamps
 * @param {string} createdAt - Creation timestamp
 * @param {string} updatedAt - Update timestamp
 * @returns {string} HTML string
 */
function renderDetailTimestamps(createdAt, updatedAt) {
    return `
        <div class="mt-4 pt-3 border-top">
            <small class="text-muted">
                <i class="bi bi-clock me-1"></i>생성: ${formatDateTime(createdAt)}
                ${updatedAt ? ` | 수정: ${formatDateTime(updatedAt)}` : ''}
            </small>
        </div>
    `;
}

// ============================================================================
// Formatting Functions (local overrides, can use utils.js versions if available)
// ============================================================================

/**
 * Format price with currency symbol
 * @param {number} price - Price value
 * @returns {string} Formatted price string
 */
function formatPrice(price) {
    if (price === null || price === undefined) return '-';
    return '₩' + parseFloat(price).toLocaleString('ko-KR', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    });
}

/**
 * Format number with locale formatting
 * @param {number} num - Number to format
 * @returns {string} Formatted number string
 */
function formatNumber(num) {
    if (num === null || num === undefined) return '0';
    return parseInt(num).toLocaleString('ko-KR');
}

/**
 * Format date string
 * @param {string} dateStr - Date string
 * @returns {string} Formatted date string
 */
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('ko-KR');
}

/**
 * Format datetime string
 * @param {string} dateTimeStr - DateTime string
 * @returns {string} Formatted datetime string
 */
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}
