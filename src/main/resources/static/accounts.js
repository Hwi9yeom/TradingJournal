/**
 * Trading Journal - Accounts Management Module
 * Handles account CRUD operations and displays account portfolio information.
 *
 * @fileoverview Account management functionality including:
 * - Account list display and updates
 * - Account creation, editing, and deletion
 * - Account summary statistics
 * - Portfolio card views
 *
 * @requires utils.js - Provides formatCurrency, formatPercent, escapeHtml, applyValueClass
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * API base URL for all account-related endpoints
 * @constant {string}
 */
const API_BASE_URL = '/api';

/**
 * Account type configuration with display labels and badge styles
 * @constant {Object.<string, {label: string, badge: string}>}
 */
const ACCOUNT_TYPES = {
    ISA: { label: 'ISA', badge: 'bg-info' },
    PENSION: { label: '연금', badge: 'bg-warning text-dark' },
    GENERAL: { label: '일반', badge: 'bg-secondary' },
    CUSTOM: { label: '사용자정의', badge: 'bg-primary' }
};

/**
 * Default badge class for unknown account types
 * @constant {string}
 */
const DEFAULT_BADGE_CLASS = 'bg-secondary';

// ============================================================================
// INITIALIZATION
// ============================================================================

$(document).ready(function() {
    loadAccounts();
    loadAccountsSummary();
    setupAccountEventHandlers();
});

/**
 * Setup event handlers for account forms
 */
function setupAccountEventHandlers() {
    $('#account-form').on('submit', function(e) {
        e.preventDefault();
        createAccount();
    });
}

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load all accounts from API and update the UI
 * Updates account list table, portfolio cards, and total count
 */
function loadAccounts() {
    $.ajax({
        url: `${API_BASE_URL}/accounts`,
        method: 'GET',
        success: function(accounts) {
            updateAccountList(accounts);
            updateAccountPortfolios(accounts);
            $('#total-accounts').text(accounts.length);
        },
        error: function(xhr) {
            console.error('Failed to load accounts:', xhr);
            showAlert('danger', '계좌 목록을 불러오는데 실패했습니다.');
        }
    });
}

/**
 * Load account summary statistics from API
 * Updates total investment, current value, and profit rate displays
 */
function loadAccountsSummary() {
    $.ajax({
        url: `${API_BASE_URL}/accounts/summary`,
        method: 'GET',
        success: function(summary) {
            $('#total-investment').text(formatCurrency(summary.totalInvestment || 0));
            $('#total-value').text(formatCurrency(summary.totalCurrentValue || 0));

            const profitRate = summary.totalProfitLossPercent || 0;
            const profitRateEl = $('#total-profit-rate');
            profitRateEl.text(formatPercent(profitRate));
            applyValueClass(profitRateEl, profitRate);
        },
        error: function(xhr) {
            console.error('Failed to load accounts summary:', xhr);
        }
    });
}

// ============================================================================
// UI RENDERING
// ============================================================================

/**
 * Update the account list table with account data
 * @param {Array<Object>} accounts - Array of account objects
 */
function updateAccountList(accounts) {
    const tbody = $('#account-list');
    tbody.empty();

    if (accounts.length === 0) {
        tbody.append('<tr><td colspan="8" class="text-center text-muted">등록된 계좌가 없습니다.</td></tr>');
        return;
    }

    accounts.forEach(account => {
        const row = createAccountTableRow(account);
        tbody.append(row);
    });
}

/**
 * Create a table row element for an account
 * @param {Object} account - Account object
 * @returns {jQuery} jQuery table row element
 */
function createAccountTableRow(account) {
    const row = $('<tr>');
    const profitRate = account.profitLossPercent || 0;

    row.append(`<td><strong>${escapeHtml(account.name)}</strong></td>`);
    row.append(`<td><span class="badge ${getAccountTypeBadge(account.accountType)}">${getAccountTypeLabel(account.accountType)}</span></td>`);
    row.append(`<td>${escapeHtml(account.description) || '-'}</td>`);
    row.append(`<td>${formatCurrency(account.totalInvestment || 0)}</td>`);
    row.append(`<td>${formatCurrency(account.totalCurrentValue || 0)}</td>`);

    const profitCell = $('<td>');
    profitCell.text(formatPercent(profitRate));
    applyValueClass(profitCell, profitRate);
    row.append(profitCell);

    row.append(`<td>${account.isDefault ? '<i class="bi bi-check-circle-fill text-success"></i>' : ''}</td>`);
    row.append(createAccountActionButtons(account));

    return row;
}

/**
 * Create action buttons cell for an account row
 * @param {Object} account - Account object
 * @returns {jQuery} jQuery table cell with action buttons
 */
function createAccountActionButtons(account) {
    const actions = $('<td>');
    actions.append(`<button class="btn btn-sm btn-outline-primary me-1" onclick="editAccount(${account.id})"><i class="bi bi-pencil"></i></button>`);

    if (!account.isDefault) {
        actions.append(`<button class="btn btn-sm btn-outline-warning me-1" onclick="setDefaultAccount(${account.id})" title="기본 계좌로 설정"><i class="bi bi-star"></i></button>`);
        actions.append(`<button class="btn btn-sm btn-outline-danger" onclick="deleteAccount(${account.id})"><i class="bi bi-trash"></i></button>`);
    }

    return actions;
}

/**
 * Update portfolio cards for all accounts
 * @param {Array<Object>} accounts - Array of account objects
 */
function updateAccountPortfolios(accounts) {
    const container = $('#account-portfolios');
    container.empty();

    if (accounts.length === 0) {
        container.append('<div class="col-12 text-center text-muted">계좌가 없습니다.</div>');
        return;
    }

    accounts.forEach(account => {
        const card = createAccountPortfolioCard(account);
        container.append(card);
    });
}

/**
 * Create a portfolio card element for an account
 * @param {Object} account - Account object
 * @returns {string} HTML string for portfolio card
 */
function createAccountPortfolioCard(account) {
    const profitRate = account.profitLossPercent || 0;
    const profitColor = profitRate >= 0 ? 'success' : 'danger';

    return `
        <div class="col-md-4 mb-3">
            <div class="card h-100">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <span>
                        <strong>${escapeHtml(account.name)}</strong>
                        ${account.isDefault ? '<i class="bi bi-star-fill text-warning ms-1"></i>' : ''}
                    </span>
                    <span class="badge ${getAccountTypeBadge(account.accountType)}">${getAccountTypeLabel(account.accountType)}</span>
                </div>
                <div class="card-body">
                    <div class="row text-center">
                        <div class="col-6">
                            <small class="text-muted">투자금액</small>
                            <p class="mb-0 fw-bold">${formatCurrency(account.totalInvestment || 0)}</p>
                        </div>
                        <div class="col-6">
                            <small class="text-muted">평가금액</small>
                            <p class="mb-0 fw-bold">${formatCurrency(account.totalCurrentValue || 0)}</p>
                        </div>
                    </div>
                    <hr>
                    <div class="text-center">
                        <small class="text-muted">수익률</small>
                        <p class="mb-0 h5 text-${profitColor}">${formatPercent(profitRate)}</p>
                    </div>
                    <hr>
                    <div class="d-flex justify-content-between">
                        <small class="text-muted">보유 종목</small>
                        <small>${account.holdingsCount || 0}개</small>
                    </div>
                </div>
                <div class="card-footer">
                    <a href="index.html?accountId=${account.id}" class="btn btn-sm btn-outline-primary w-100">
                        <i class="bi bi-box-arrow-up-right"></i> 거래내역 보기
                    </a>
                </div>
            </div>
        </div>
    `;
}

// ============================================================================
// ACCOUNT CRUD OPERATIONS
// ============================================================================

/**
 * Create a new account from form data
 */
function createAccount() {
    const account = {
        name: $('#account-name').val(),
        accountType: $('#account-type').val(),
        description: $('#account-description').val() || null
    };

    $.ajax({
        url: `${API_BASE_URL}/accounts`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(account),
        success: function() {
            $('#account-form')[0].reset();
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 추가되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 추가 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

/**
 * Open edit modal for an account
 * @param {number} id - Account ID
 */
function editAccount(id) {
    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'GET',
        success: function(account) {
            $('#edit-account-id').val(account.id);
            $('#edit-account-name').val(account.name);
            $('#edit-account-type').val(account.accountType);
            $('#edit-account-description').val(account.description || '');
            new bootstrap.Modal($('#editAccountModal')).show();
        },
        error: function(xhr) {
            showAlert('danger', '계좌 정보를 불러오는데 실패했습니다.');
        }
    });
}

/**
 * Update an existing account from edit form data
 */
function updateAccount() {
    const id = $('#edit-account-id').val();
    const account = {
        name: $('#edit-account-name').val(),
        accountType: $('#edit-account-type').val(),
        description: $('#edit-account-description').val() || null
    };

    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify(account),
        success: function() {
            bootstrap.Modal.getInstance($('#editAccountModal')).hide();
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 수정되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 수정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

/**
 * Delete an account after confirmation
 * @param {number} id - Account ID
 */
function deleteAccount(id) {
    if (!confirm('정말 이 계좌를 삭제하시겠습니까?\n\n주의: 해당 계좌의 거래 내역과 포트폴리오도 모두 삭제됩니다.')) {
        return;
    }

    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'DELETE',
        success: function() {
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 삭제되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 삭제 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

/**
 * Set an account as the default account
 * @param {number} id - Account ID
 */
function setDefaultAccount(id) {
    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}/default`,
        method: 'PUT',
        success: function() {
            loadAccounts();
            showAlert('success', '기본 계좌가 변경되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '기본 계좌 설정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Get Bootstrap badge class for an account type
 * @param {string} type - Account type key (ISA, PENSION, GENERAL, CUSTOM)
 * @returns {string} Bootstrap badge class
 */
function getAccountTypeBadge(type) {
    return ACCOUNT_TYPES[type]?.badge || DEFAULT_BADGE_CLASS;
}

/**
 * Get display label for an account type
 * @param {string} type - Account type key (ISA, PENSION, GENERAL, CUSTOM)
 * @returns {string} Human-readable account type label
 */
function getAccountTypeLabel(type) {
    return ACCOUNT_TYPES[type]?.label || type;
}

/**
 * Display a toast-style alert message
 * @param {('success'|'danger'|'warning'|'info')} type - Alert type
 * @param {string} message - Message to display
 */
function showAlert(type, message) {
    const alertHtml = `
        <div class="alert alert-${type} alert-dismissible fade show position-fixed"
             style="top: 80px; right: 20px; z-index: 1050; min-width: 300px;" role="alert">
            ${escapeHtml(message)}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    `;
    $('body').append(alertHtml);
    setTimeout(() => {
        $('.alert').alert('close');
    }, 3000);
}
