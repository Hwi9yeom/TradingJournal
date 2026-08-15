/**
 * 가계·저축 점검 JavaScript
 * Monthly Budget Management Module
 */

// =============================================================================
// STATE
// =============================================================================

/** @type {Array<Object>} 서버에서 받은 월별 기록 (최신순) */
let budgetRecords = [];

/** @type {Chart|null} 추이 차트 인스턴스 */
let budgetTrendChart = null;

/** @type {Array<Object>} 서버에서 받은 저축 기록 (최신순) */
let savingsRecords = [];

/** @type {number|null} 수정 중인 저축 기록 ID */
let editingSavingsId = null;

/** 요약 집계 대상 개월 수 */
const SUMMARY_MONTHS = 12;

// =============================================================================
// INITIALIZATION
// =============================================================================

$(document).ready(function() {
    $('#budget-month').val(currentMonthValue());
    bindFormEvents();
    loadBudgets();
    loadSummary();
    loadSavingsRecords();
});

/** 현재 월을 <input type="month"> 값 형식(YYYY-MM)으로 반환 */
function currentMonthValue() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** 입력 폼 이벤트 바인딩 */
function bindFormEvents() {
    $('#budget-form').on('submit', function(e) {
        e.preventDefault();
        saveBudget();
    });

    $('#fixed-income, #variable-income, #fixed-expense, #variable-expense')
        .on('input', updateCapacityPreview);

    $('#savings-form').on('submit', function(e) {
        e.preventDefault();
        saveSavingsRecord();
    });

    $('#savings-date').val(formatDateForApi(new Date()));
}

/** 입력 중인 값으로 저축 여력 미리보기 갱신 */
function updateCapacityPreview() {
    const income = numberValue('#fixed-income') + numberValue('#variable-income');
    const expense = numberValue('#fixed-expense') + numberValue('#variable-expense');
    const capacity = income - expense;

    if (income === 0 && expense === 0) {
        $('#capacity-preview').text('');
        return;
    }

    const rate = income === 0 ? 0 : (capacity / income) * 100;
    const tone = capacity < 0 ? 'var(--color-negative)' : 'var(--color-positive)';
    $('#capacity-preview').html(
        `이번 달 저축 가능 금액: <strong style="color: ${tone};">${formatCurrency(capacity)}</strong>` +
        ` <span style="color: var(--text-muted);">(저축률 ${rate.toFixed(1)}%)</span>`
    );
}

/**
 * 입력 필드의 숫자 값을 읽는다. 비어 있거나 숫자가 아니면 0.
 * @param {string} selector
 * @returns {number}
 */
function numberValue(selector) {
    const raw = parseFloat($(selector).val());
    return Number.isFinite(raw) ? raw : 0;
}

/**
 * 입력 필드의 숫자 값을 읽되, 비어 있으면 null (서버에 미입력으로 전달).
 * @param {string} selector
 * @returns {number|null}
 */
function nullableNumberValue(selector) {
    const val = $(selector).val();
    if (val === '' || val === null || val === undefined) {
        return null;
    }
    const parsed = parseFloat(val);
    return Number.isFinite(parsed) ? parsed : null;
}

// =============================================================================
// DATA LOADING
// =============================================================================

/** 월별 기록 전체 조회 */
function loadBudgets() {
    $.ajax({
        url: '/api/budgets',
        method: 'GET',
        success: function(data) {
            budgetRecords = data || [];
            renderBudgetTable(budgetRecords);
        },
        error: function(xhr) {
            console.error('가계 기록 조회 실패:', xhr);
            showToast('가계 기록을 불러오지 못했습니다.', 'danger');
        }
    });
}

/** 요약 조회 및 카드/차트 갱신 */
function loadSummary() {
    $.ajax({
        url: '/api/budgets/summary',
        method: 'GET',
        data: { months: SUMMARY_MONTHS },
        success: function(summary) {
            updateSummaryCards(summary);
            renderTrendChart(summary.trend || []);
        },
        error: function(xhr) {
            console.error('요약 조회 실패:', xhr);
        }
    });
}

/**
 * 요약 카드 갱신.
 * @param {Object} summary - BudgetSummaryDto
 */
function updateSummaryCards(summary) {
    $('#avg-income').text(formatCurrency(summary.averageIncome || 0));
    $('#avg-expense').text(formatCurrency(summary.averageExpense || 0));
    $('#avg-capacity').text(formatCurrency(summary.averageSavingsCapacity || 0));
    $('#avg-rate').text(`${Number(summary.averageSavingsRatePercent || 0).toFixed(1)}%`);

    if (summary.netWorthChange === null || summary.netWorthChange === undefined) {
        $('#net-worth-change').text('-');
    } else {
        const change = Number(summary.netWorthChange);
        const sign = change >= 0 ? '+' : '';
        $('#net-worth-change')
            .text(`${sign}${formatCurrency(change)}`)
            .css('color', change >= 0 ? 'var(--color-positive)' : 'var(--color-negative)');
    }
}

// =============================================================================
// RENDERING
// =============================================================================

/**
 * 월별 기록 테이블 렌더링.
 * @param {Array<Object>} records - MonthlyBudgetDto 배열 (최신순)
 */
function renderBudgetTable(records) {
    const $tbody = $('#budget-list');

    if (!records.length) {
        $tbody.html('<tr><td colspan="11" class="text-center" style="color: var(--text-muted); padding: var(--space-6);">아직 기록이 없습니다. 위에서 이번 달부터 입력해보세요.</td></tr>');
        return;
    }

    $tbody.html(records.map(rowHtml).join(''));
}

/**
 * 기록 한 줄의 HTML.
 * @param {Object} r - MonthlyBudgetDto
 * @returns {string}
 */
function rowHtml(r) {
    const capacity = Number(r.savingsCapacity || 0);
    const capacityColor = capacity < 0 ? 'var(--color-negative)' : 'var(--color-positive)';
    const achievement = r.savingsAchievementPercent === null || r.savingsAchievementPercent === undefined
        ? '-'
        : `${Number(r.savingsAchievementPercent).toFixed(1)}%`;

    return `
        <tr>
            <td><strong>${escapeHtml(r.monthLabel || '')}</strong></td>
            <td class="text-right">${formatCurrency(r.totalIncome || 0)}</td>
            <td class="text-right">${formatCurrency(r.totalExpense || 0)}</td>
            <td class="text-right" style="color: ${capacityColor};">${formatCurrency(capacity)}</td>
            <td class="text-right">${Number(r.savingsRatePercent || 0).toFixed(1)}%</td>
            <td class="text-right">${r.plannedSavings ? formatCurrency(r.plannedSavings) : '-'}</td>
            <td class="text-right">${r.effectiveActualSavings ? formatCurrency(r.effectiveActualSavings) : '-'}</td>
            <td class="text-right">${achievement}</td>
            <td class="text-right">${r.netWorth ? formatCurrency(r.netWorth) : '-'}</td>
            <td>${escapeHtml(r.notes || '')}</td>
            <td>
                <button class="btn-glass" style="padding: var(--space-1) var(--space-2);" onclick="editBudget(${r.id})" title="수정">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-glass" style="padding: var(--space-1) var(--space-2); color: var(--color-negative);" onclick="deleteBudget(${r.id})" title="삭제">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `;
}

/**
 * 추이 차트 렌더링.
 * @param {Array<Object>} trend - 오래된 달 → 최신 달 순의 MonthlyBudgetDto 배열
 */
function renderTrendChart(trend) {
    const canvas = document.getElementById('budget-trend-chart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }

    if (budgetTrendChart) {
        budgetTrendChart.destroy();
        budgetTrendChart = null;
    }

    if (!trend.length) {
        return;
    }

    budgetTrendChart = new Chart(canvas, {
        type: 'bar',
        data: {
            labels: trend.map(t => t.monthLabel),
            datasets: [
                {
                    label: '총 소득',
                    data: trend.map(t => Number(t.totalIncome || 0)),
                    backgroundColor: 'rgba(56, 189, 248, 0.5)'
                },
                {
                    label: '총 지출',
                    data: trend.map(t => Number(t.totalExpense || 0)),
                    backgroundColor: 'rgba(248, 113, 113, 0.5)'
                },
                {
                    label: '저축 가능 금액',
                    type: 'line',
                    data: trend.map(t => Number(t.savingsCapacity || 0)),
                    borderColor: 'rgba(52, 211, 153, 1)',
                    backgroundColor: 'rgba(52, 211, 153, 0.2)',
                    tension: 0.3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom' }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (value) => formatCurrency(value)
                    }
                }
            }
        }
    });
}

// =============================================================================
// MUTATIONS
// =============================================================================

/** 폼 내용을 저장 (같은 월이면 서버가 덮어씀) */
function saveBudget() {
    const month = $('#budget-month').val();
    if (!month) {
        showToast('대상 월을 선택해주세요.', 'warning');
        return;
    }

    const payload = {
        budgetMonth: `${month}-01`,
        fixedIncome: nullableNumberValue('#fixed-income'),
        variableIncome: nullableNumberValue('#variable-income'),
        fixedExpense: nullableNumberValue('#fixed-expense'),
        variableExpense: nullableNumberValue('#variable-expense'),
        plannedSavings: nullableNumberValue('#planned-savings'),
        actualSavings: nullableNumberValue('#actual-savings'),
        netWorth: nullableNumberValue('#net-worth'),
        notes: $('#budget-notes').val()
    };

    $.ajax({
        url: '/api/budgets',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(payload),
        success: function() {
            showToast('월 기록이 저장되었습니다.', 'success');
            resetForm();
            loadBudgets();
            loadSummary();
        },
        error: function(xhr) {
            console.error('저장 실패:', xhr);
            showToast('저장에 실패했습니다: ' + (xhr.responseJSON?.message || xhr.statusText), 'danger');
        }
    });
}

/**
 * 기존 기록을 폼에 불러온다.
 * @param {number} id - 기록 ID
 */
function editBudget(id) {
    const record = budgetRecords.find(r => r.id === id);
    if (!record) {
        return;
    }

    $('#budget-id').val(record.id);
    $('#budget-month').val(record.monthLabel);
    $('#fixed-income').val(record.fixedIncome ?? '');
    $('#variable-income').val(record.variableIncome ?? '');
    $('#fixed-expense').val(record.fixedExpense ?? '');
    $('#variable-expense').val(record.variableExpense ?? '');
    $('#planned-savings').val(record.plannedSavings ?? '');
    $('#actual-savings').val(record.actualSavings ?? '');
    $('#net-worth').val(record.netWorth ?? '');
    $('#budget-notes').val(record.notes ?? '');
    updateCapacityPreview();

    window.scrollTo({ top: 0, behavior: 'smooth' });
}

/**
 * 기록 삭제.
 * @param {number} id - 기록 ID
 */
function deleteBudget(id) {
    if (!confirm('이 월 기록을 삭제하시겠습니까?')) {
        return;
    }

    $.ajax({
        url: `/api/budgets/${id}`,
        method: 'DELETE',
        success: function() {
            showToast('월 기록이 삭제되었습니다.', 'success');
            loadBudgets();
            loadSummary();
        },
        error: function(xhr) {
            console.error('삭제 실패:', xhr);
            showToast('삭제에 실패했습니다.', 'danger');
        }
    });
}

/** 폼 초기화 (대상 월은 이번 달로 되돌린다) */
function resetForm() {
    $('#budget-form')[0].reset();
    $('#budget-id').val('');
    $('#budget-month').val(currentMonthValue());
    $('#capacity-preview').text('');
}

// =============================================================================
// SAVINGS JOURNAL
// =============================================================================

/** 저축 기록 전체 조회 */
function loadSavingsRecords() {
    $.ajax({
        url: '/api/savings',
        method: 'GET',
        success: function(data) {
            savingsRecords = data || [];
            renderSavingsTable(savingsRecords);
        },
        error: function(xhr) {
            console.error('저축 기록 조회 실패:', xhr);
            showToast('저축 기록을 불러오지 못했습니다.', 'danger');
        }
    });
}

/**
 * 저축 기록 테이블 렌더링.
 * @param {Array<Object>} records - SavingsRecordDto 배열
 */
function renderSavingsTable(records) {
    const $tbody = $('#savings-list');

    if (!records.length) {
        $tbody.html('<tr><td colspan="6" class="text-center" style="color: var(--text-muted); padding: var(--space-6);">저축 기록이 없습니다. 입금할 때마다 한 줄씩 남겨보세요.</td></tr>');
        return;
    }

    $tbody.html(records.map(savingsRowHtml).join(''));
}

/**
 * 저축 기록 한 줄의 HTML.
 * @param {Object} r - SavingsRecordDto
 * @returns {string}
 */
function savingsRowHtml(r) {
    return `
        <tr>
            <td>${escapeHtml(r.savedDate || '')}</td>
            <td class="text-right" style="color: var(--color-positive);">${formatCurrency(r.amount || 0)}</td>
            <td>${escapeHtml(r.categoryLabel || r.category || '')}</td>
            <td>${escapeHtml(r.institution || '')}</td>
            <td>${escapeHtml(r.memo || '')}</td>
            <td>
                <button class="btn-glass" style="padding: var(--space-1) var(--space-2);" onclick="editSavingsRecord(${r.id})" title="수정">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-glass" style="padding: var(--space-1) var(--space-2); color: var(--color-negative);" onclick="deleteSavingsRecord(${r.id})" title="삭제">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `;
}

/** 저축 기록 저장 (신규 또는 수정) */
function saveSavingsRecord() {
    const amount = nullableNumberValue('#savings-amount');
    if (!$('#savings-date').val() || amount === null || amount <= 0) {
        showToast('저축일과 0보다 큰 금액을 입력해주세요.', 'warning');
        return;
    }

    const payload = {
        savedDate: $('#savings-date').val(),
        amount: amount,
        category: $('#savings-category').val(),
        institution: $('#savings-institution').val(),
        memo: $('#savings-memo').val()
    };

    const isEdit = editingSavingsId !== null;

    $.ajax({
        url: isEdit ? `/api/savings/${editingSavingsId}` : '/api/savings',
        method: isEdit ? 'PUT' : 'POST',
        contentType: 'application/json',
        data: JSON.stringify(payload),
        success: function() {
            showToast(isEdit ? '저축 기록이 수정되었습니다.' : '저축 기록이 추가되었습니다.', 'success');
            resetSavingsForm();
            loadSavingsRecords();
            // 월 실제 저축액이 재집계되도록 가계 데이터도 갱신한다.
            loadBudgets();
            loadSummary();
        },
        error: function(xhr) {
            console.error('저축 기록 저장 실패:', xhr);
            showToast('저장에 실패했습니다: ' + (xhr.responseJSON?.message || xhr.statusText), 'danger');
        }
    });
}

/**
 * 저축 기록을 폼에 불러온다.
 * @param {number} id - 기록 ID
 */
function editSavingsRecord(id) {
    const record = savingsRecords.find(r => r.id === id);
    if (!record) {
        return;
    }

    editingSavingsId = id;
    $('#savings-id').val(id);
    $('#savings-date').val(record.savedDate);
    $('#savings-amount').val(record.amount ?? '');
    $('#savings-category').val(record.category || 'REGULAR_SAVINGS');
    $('#savings-institution').val(record.institution ?? '');
    $('#savings-memo').val(record.memo ?? '');
    $('#savings-submit-btn').html('<i class="bi bi-check-lg"></i>수정');
    $('#savings-cancel-btn').show();
}

/**
 * 저축 기록 삭제.
 * @param {number} id - 기록 ID
 */
function deleteSavingsRecord(id) {
    if (!confirm('이 저축 기록을 삭제하시겠습니까?')) {
        return;
    }

    $.ajax({
        url: `/api/savings/${id}`,
        method: 'DELETE',
        success: function() {
            showToast('저축 기록이 삭제되었습니다.', 'success');
            resetSavingsForm();
            loadSavingsRecords();
            loadBudgets();
            loadSummary();
        },
        error: function(xhr) {
            console.error('저축 기록 삭제 실패:', xhr);
            showToast('삭제에 실패했습니다.', 'danger');
        }
    });
}

/** 저축 기록 폼 초기화 */
function resetSavingsForm() {
    editingSavingsId = null;
    $('#savings-form')[0].reset();
    $('#savings-id').val('');
    $('#savings-date').val(formatDateForApi(new Date()));
    $('#savings-submit-btn').html('<i class="bi bi-plus-lg"></i>추가');
    $('#savings-cancel-btn').hide();
}

// =============================================================================
// UTILITIES
// =============================================================================

/**
 * Show a toast notification, falling back through the available toast systems.
 * @param {string} message - Message to display
 * @param {string} type - Toast type (success, danger, warning, info)
 */
function showToast(message, type) {
    const mappedType = type === 'danger' ? 'error' : type;

    if (typeof ToastNotification !== 'undefined') {
        ToastNotification.show(message, mappedType);
        return;
    }

    if (typeof showGlassToast === 'function') {
        showGlassToast(message, mappedType);
        return;
    }

    alert(message);
}
