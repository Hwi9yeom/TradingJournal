/**
 * 트레이드 플랜 관리 JavaScript
 */

let allPlans = [];
let currentFilter = 'all';
let currentPlanId = null;
let planTypes = [];
let strategies = [];

// 페이지 로드 시 초기화
$(document).ready(function() {
    loadMetadata();
    loadPlans();
    loadStatistics();
});

/**
 * 메타데이터 로드 (유형, 전략 등)
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
 * 플랜 유형 옵션 채우기
 */
function populatePlanTypes() {
    const select = $('#planType');
    select.empty().append('<option value="">선택</option>');

    planTypes.forEach(type => {
        select.append(`<option value="${type}">${getPlanTypeLabel(type)}</option>`);
    });
}

/**
 * 전략 옵션 채우기
 */
function populateStrategies() {
    const select = $('#strategy');
    select.empty().append('<option value="">선택</option>');

    strategies.forEach(strategy => {
        select.append(`<option value="${strategy}">${strategy}</option>`);
    });
}

/**
 * 전체 플랜 로드
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
 * 통계 로드
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
 * 통계 업데이트
 */
function updateStatistics(stats) {
    $('#stat-total').text(stats.totalPlans || 0);
    $('#stat-pending').text(stats.pendingPlans || 0);
    $('#stat-executed').text(stats.executedPlans || 0);
    $('#stat-execution-rate').text((stats.executionRate || 0).toFixed(1) + '%');
    $('#stat-compliance-rate').text((stats.complianceRate || 0).toFixed(1) + '%');
    $('#stat-avg-r').text((stats.averageRMultiple || 0).toFixed(2));
}

/**
 * 상태별 필터링
 */
function filterPlansByStatus(status) {
    if (status === 'all') {
        return allPlans;
    }
    return allPlans.filter(p => p.status === status);
}

/**
 * 필터 적용
 */
function filterPlans(status) {
    currentFilter = status;

    // 버튼 활성화 상태 업데이트
    $('.filter-btn-group .btn').removeClass('active');
    if (status === 'all') {
        $('.filter-btn-group .btn:first').addClass('active');
    } else {
        $(`.filter-btn-group .btn[onclick*="${status}"]`).addClass('active');
    }

    renderPlans(filterPlansByStatus(status));
}

/**
 * 플랜 새로고침
 */
function refreshPlans() {
    loadPlans();
    loadStatistics();
    ToastNotification.success('플랜 목록이 갱신되었습니다.');
}

/**
 * 플랜 렌더링
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
 * 플랜 카드 생성
 */
function createPlanCard(plan) {
    const statusInfo = getStatusInfo(plan.status);
    const rrRatio = calculateRRRatio(plan);
    const riskReward = `1:${rrRatio.toFixed(2)}`;

    const statusColor = getStatusColor(plan.status);

    return `
        <div class="col-md-6 col-xl-4">
            <div class="card plan-card h-100" style="--status-color: ${statusColor};">
                <div class="card-body p-4">
                    <div class="d-flex justify-content-between align-items-start mb-3">
                        <div class="plan-symbol">${escapeHtml(plan.symbol)}</div>
                        <span class="status-badge badge bg-${statusInfo.class}">${statusInfo.label}</span>
                    </div>

                    <div class="plan-price-box mb-3">
                        <div class="row g-2 small">
                            <div class="col-4 text-center">
                                <div class="text-muted mb-1">진입</div>
                                <div class="fw-bold">${formatPrice(plan.entryPrice)}</div>
                            </div>
                            <div class="col-4 text-center">
                                <div class="text-muted mb-1">손절</div>
                                <div class="fw-bold text-danger">${formatPrice(plan.stopLoss)}</div>
                            </div>
                            <div class="col-4 text-center">
                                <div class="text-muted mb-1">목표</div>
                                <div class="fw-bold text-success">${formatPrice(plan.targetPrice)}</div>
                            </div>
                        </div>
                    </div>

                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <div>
                            <small class="text-muted d-block">R:R</small>
                            <span class="rr-ratio">
                                <i class="bi bi-bullseye"></i>
                                ${riskReward}
                            </span>
                        </div>
                        <div class="text-end">
                            <small class="text-muted d-block">수량</small>
                            <div class="fw-bold">${formatNumber(plan.plannedQuantity || 0)}</div>
                        </div>
                    </div>

                    <div class="mb-3">
                        <small class="text-muted d-block mb-1">
                            <i class="bi bi-diagram-3 me-1"></i>전략
                        </small>
                        <div class="small">${escapeHtml(plan.strategy || '-')}</div>
                    </div>

                    ${plan.validUntil ? `
                    <div class="mb-3">
                        <small class="text-muted">
                            <i class="bi bi-calendar-event me-1"></i>유효기한: ${formatDate(plan.validUntil)}
                        </small>
                    </div>
                    ` : ''}

                    ${plan.entryConditions ? `
                    <div class="mb-3">
                        <small class="text-muted d-block mb-1">진입 조건</small>
                        <div class="small text-truncate" title="${escapeHtml(plan.entryConditions)}">
                            ${escapeHtml(plan.entryConditions)}
                        </div>
                    </div>
                    ` : ''}

                    <div class="d-flex gap-2 mt-3 pt-3 border-top">
                        ${plan.status === 'PENDING' ? `
                            <button class="btn btn-sm btn-success action-btn flex-fill" onclick="openExecuteModal(${plan.id})">
                                <i class="bi bi-play-fill me-1"></i>실행
                            </button>
                            <button class="btn btn-sm btn-outline-primary action-btn" onclick="openEditModal(${plan.id})">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button class="btn btn-sm btn-outline-danger action-btn" onclick="cancelPlan(${plan.id})">
                                <i class="bi bi-x-lg"></i>
                            </button>
                        ` : ''}
                        ${plan.status === 'EXECUTED' ? `
                            <button class="btn btn-sm btn-outline-info action-btn flex-fill" onclick="viewPlanDetail(${plan.id})">
                                <i class="bi bi-eye me-1"></i>상세보기
                            </button>
                        ` : ''}
                        ${plan.status === 'CANCELLED' || plan.status === 'EXPIRED' ? `
                            <button class="btn btn-sm btn-outline-secondary action-btn flex-fill" onclick="viewPlanDetail(${plan.id})">
                                <i class="bi bi-eye me-1"></i>상세보기
                            </button>
                            <button class="btn btn-sm btn-outline-danger action-btn" onclick="deletePlan(${plan.id})">
                                <i class="bi bi-trash"></i>
                            </button>
                        ` : ''}
                    </div>
                </div>

                <div class="card-footer bg-light small text-muted">
                    <i class="bi bi-clock me-1"></i>생성: ${formatDateTime(plan.createdAt)}
                </div>
            </div>
        </div>
    `;
}

/**
 * R:R 비율 계산
 */
function calculateRRRatio(plan) {
    const risk = Math.abs(plan.entryPrice - plan.stopLoss);
    const reward = Math.abs(plan.targetPrice - plan.entryPrice);

    if (risk === 0) return 0;
    return reward / risk;
}

/**
 * 상태 정보
 */
function getStatusInfo(status) {
    const statusMap = {
        'PENDING': { label: '대기 중', class: 'pending' },
        'EXECUTED': { label: '실행됨', class: 'executed' },
        'CANCELLED': { label: '취소됨', class: 'cancelled' },
        'EXPIRED': { label: '만료됨', class: 'expired' }
    };
    return statusMap[status] || { label: status, class: 'secondary' };
}

/**
 * 상태 색상
 */
function getStatusColor(status) {
    const colorMap = {
        'PENDING': '#8b5cf6',
        'EXECUTED': '#059669',
        'CANCELLED': '#6b7280',
        'EXPIRED': '#dc2626'
    };
    return colorMap[status] || '#667eea';
}

/**
 * 플랜 유형 라벨
 */
function getPlanTypeLabel(type) {
    const labels = {
        'LONG': '롱 포지션',
        'SHORT': '숏 포지션',
        'SWING': '스윙 트레이딩',
        'DAY': '데이 트레이딩',
        'SCALP': '스캘핑'
    };
    return labels[type] || type;
}

/**
 * 생성 모달 열기
 */
function openCreateModal() {
    currentPlanId = null;
    $('#planModalLabel').html('<i class="bi bi-journal-plus me-2"></i>새 트레이드 플랜 작성');
    $('#planForm')[0].reset();
    $('#planId').val('');
    $('#position-calc-result').hide();
    $('#riskPercent').val('1');
}

/**
 * 수정 모달 열기
 */
function openEditModal(id) {
    $.ajax({
        url: `/api/plans/${id}`,
        method: 'GET',
        success: function(plan) {
            currentPlanId = plan.id;
            $('#planModalLabel').html('<i class="bi bi-pencil me-2"></i>트레이드 플랜 수정');

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

            $('#planModal').modal('show');
        },
        error: function(xhr) {
            console.error('플랜 조회 실패:', xhr);
            ToastNotification.error('플랜 정보를 불러올 수 없습니다.');
        }
    });
}

/**
 * 포지션 사이징 계산
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
 * 포지션 계산 결과 표시
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
 * 계산된 수량 적용
 */
function applyCalculatedQuantity(quantity) {
    $('#plannedQuantity').val(quantity);
    ToastNotification.success('수량이 적용되었습니다.');
}

/**
 * 플랜 저장
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
            $('#planModal').modal('hide');
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
 * 실행 모달 열기
 */
function openExecuteModal(id) {
    currentPlanId = id;

    // 플랜 정보 가져와서 기본값 설정
    $.ajax({
        url: `/api/plans/${id}`,
        method: 'GET',
        success: function(plan) {
            $('#actualEntryPrice').val(plan.entryPrice);
            $('#actualQuantity').val(plan.plannedQuantity || '');
            $('#executionNotes').val('');
            $('#executeModal').modal('show');
        },
        error: function(xhr) {
            console.error('플랜 조회 실패:', xhr);
            ToastNotification.error('플랜 정보를 불러올 수 없습니다.');
        }
    });
}

/**
 * 플랜 실행 확인
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
            $('#executeModal').modal('hide');
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
 * 플랜 취소
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
 * 플랜 삭제
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

/**
 * 플랜 상세 보기
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
 * 플랜 상세 모달 표시
 */
function showPlanDetailModal(plan) {
    const statusInfo = getStatusInfo(plan.status);
    const rrRatio = calculateRRRatio(plan);

    const modalHtml = `
        <div class="modal fade" id="planDetailModal" tabindex="-1">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">
                            <i class="bi bi-journal-text me-2"></i>${escapeHtml(plan.symbol)} 플랜 상세
                        </h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="row mb-4">
                            <div class="col-md-6">
                                <label class="text-muted small">상태</label>
                                <div><span class="status-badge badge bg-${statusInfo.class}">${statusInfo.label}</span></div>
                            </div>
                            <div class="col-md-6">
                                <label class="text-muted small">전략</label>
                                <div class="fw-bold">${escapeHtml(plan.strategy || '-')}</div>
                            </div>
                        </div>

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

                        <div class="row mb-4">
                            <div class="col-md-6">
                                <label class="text-muted small">계획 수량</label>
                                <div class="fw-bold">${formatNumber(plan.plannedQuantity || 0)}</div>
                            </div>
                            <div class="col-md-6">
                                <label class="text-muted small">R:R 비율</label>
                                <div class="fw-bold">1:${rrRatio.toFixed(2)}</div>
                            </div>
                        </div>

                        ${plan.entryConditions ? `
                        <div class="mb-3">
                            <label class="text-muted small">진입 조건</label>
                            <div class="p-3 bg-light rounded">${escapeHtml(plan.entryConditions)}</div>
                        </div>
                        ` : ''}

                        ${plan.exitConditions ? `
                        <div class="mb-3">
                            <label class="text-muted small">청산 조건</label>
                            <div class="p-3 bg-light rounded">${escapeHtml(plan.exitConditions)}</div>
                        </div>
                        ` : ''}

                        ${plan.marketAnalysis ? `
                        <div class="mb-3">
                            <label class="text-muted small">시장 분석</label>
                            <div class="p-3 bg-light rounded">${escapeHtml(plan.marketAnalysis)}</div>
                        </div>
                        ` : ''}

                        ${plan.notes ? `
                        <div class="mb-3">
                            <label class="text-muted small">메모</label>
                            <div class="p-3 bg-light rounded">${escapeHtml(plan.notes)}</div>
                        </div>
                        ` : ''}

                        <div class="mt-4 pt-3 border-top">
                            <small class="text-muted">
                                <i class="bi bi-clock me-1"></i>생성: ${formatDateTime(plan.createdAt)}
                                ${plan.updatedAt ? ` | 수정: ${formatDateTime(plan.updatedAt)}` : ''}
                            </small>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">닫기</button>
                    </div>
                </div>
            </div>
        </div>
    `;

    // 기존 모달 제거
    $('#planDetailModal').remove();

    // 새 모달 추가 및 표시
    $('body').append(modalHtml);
    $('#planDetailModal').modal('show');

    // 모달 닫힐 때 DOM에서 제거
    $('#planDetailModal').on('hidden.bs.modal', function() {
        $(this).remove();
    });
}

/**
 * 가격 포맷
 */
function formatPrice(price) {
    if (price === null || price === undefined) return '-';
    return '₩' + parseFloat(price).toLocaleString('ko-KR', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    });
}

/**
 * 숫자 포맷
 */
function formatNumber(num) {
    if (num === null || num === undefined) return '0';
    return parseInt(num).toLocaleString('ko-KR');
}

/**
 * 날짜 포맷
 */
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('ko-KR');
}

/**
 * 날짜시간 포맷
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

/**
 * HTML 이스케이프
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
