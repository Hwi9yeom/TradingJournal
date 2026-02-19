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
            showToast('error', '계좌 목록을 불러오는데 실패했습니다.');
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

            updateProfitRateCardStyling(profitRate);
        },
        error: function(xhr) {
            console.error('Failed to load accounts summary:', xhr);
        }
    });
}

/**
 * Update profit rate card styling based on value
 * @param {number} profitRate - Profit rate percentage (optional, will read from DOM if not provided)
 */
function updateProfitRateCardStyling(profitRate) {
    const card = $('#profit-rate-card');
    const valueEl = $('#total-profit-rate');

    // Get profitRate from element if not provided
    if (profitRate === undefined) {
        const text = valueEl.text().replace('%', '');
        profitRate = parseFloat(text) || 0;
    }

    card.removeClass('positive negative');
    valueEl.removeClass('text-positive text-negative');

    if (profitRate > 0) {
        card.addClass('positive');
        valueEl.addClass('text-positive');
    } else if (profitRate < 0) {
        card.addClass('negative');
        valueEl.addClass('text-negative');
    }
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

    // Update profit rate card styling
    updateProfitRateCardStyling();
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
    row.append(`<td>${createAccountTypeBadge(account.accountType)}</td>`);
    row.append(`<td>${escapeHtml(account.description) || '-'}</td>`);
    row.append(`<td class="text-right font-mono">${formatCurrency(account.totalInvestment || 0)}</td>`);
    row.append(`<td class="text-right font-mono">${formatCurrency(account.totalCurrentValue || 0)}</td>`);

    const profitCell = $('<td class="text-right font-mono">');
    profitCell.text(formatPercent(profitRate));
    if (profitRate > 0) {
        profitCell.addClass('text-positive');
    } else if (profitRate < 0) {
        profitCell.addClass('text-negative');
    }
    row.append(profitCell);

    row.append(`<td>${account.isDefault ? '<i class="bi bi-check-circle-fill" style="color: var(--color-positive);"></i>' : ''}</td>`);
    row.append(createAccountActionButtons(account));

    return row;
}

/**
 * Create account type badge HTML
 * @param {string} type - Account type
 * @returns {string} Badge HTML
 */
function createAccountTypeBadge(type) {
    const label = getAccountTypeLabel(type);
    const colorMap = {
        'ISA': 'var(--color-info)',
        '연금': 'var(--color-warning)',
        '일반': 'var(--text-muted)',
        '사용자정의': 'var(--color-accent)'
    };
    const color = colorMap[label] || 'var(--text-muted)';
    return `<span style="padding: var(--space-1) var(--space-3); background: rgba(102, 126, 234, 0.15); color: ${color}; border-radius: var(--radius-full); font-size: var(--font-size-xs); font-weight: 600;">${label}</span>`;
}

/**
 * Create action buttons cell for an account row
 * @param {Object} account - Account object
 * @returns {jQuery} jQuery table cell with action buttons
 */
function createAccountActionButtons(account) {
    const actions = $('<td>').css('display', 'flex').css('gap', 'var(--space-2)');
    actions.append(`<button class="btn-glass" style="padding: var(--space-2) var(--space-3);" onclick="editAccount(${account.id})" title="수정"><i class="bi bi-pencil"></i></button>`);

    if (!account.isDefault) {
        actions.append(`<button class="btn-glass" style="padding: var(--space-2) var(--space-3);" onclick="setDefaultAccount(${account.id})" title="기본 계좌로 설정"><i class="bi bi-star"></i></button>`);
        actions.append(`<button class="btn-glass" style="padding: var(--space-2) var(--space-3); color: var(--color-negative);" onclick="deleteAccount(${account.id})" title="삭제"><i class="bi bi-trash"></i></button>`);
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
        container.append(`
            <div class="empty-state">
                <div class="empty-state-icon"><i class="bi bi-inbox"></i></div>
                <div class="empty-state-title">계좌 없음</div>
                <div class="empty-state-desc">등록된 계좌가 없습니다. 위에서 새 계좌를 추가하세요.</div>
            </div>
        `);
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
    const profitClass = profitRate > 0 ? 'text-positive' : profitRate < 0 ? 'text-negative' : '';

    return `
        <div class="glass-card" style="transition: var(--transition-base);">
            <div class="glass-card-header" style="border-bottom: 1px solid var(--glass-border);">
                <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="display: flex; align-items: center; gap: var(--space-2);">
                        <strong>${escapeHtml(account.name)}</strong>
                        ${account.isDefault ? '<i class="bi bi-star-fill" style="color: var(--color-warning);"></i>' : ''}
                    </span>
                    ${createAccountTypeBadge(account.accountType)}
                </div>
            </div>
            <div class="glass-card-body">
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4); margin-bottom: var(--space-4); text-align: center;">
                    <div>
                        <div style="font-size: var(--font-size-xs); color: var(--text-muted); margin-bottom: var(--space-1);">투자금액</div>
                        <div class="font-mono" style="font-weight: 600; font-size: var(--font-size-base);">${formatCurrency(account.totalInvestment || 0)}</div>
                    </div>
                    <div>
                        <div style="font-size: var(--font-size-xs); color: var(--text-muted); margin-bottom: var(--space-1);">평가금액</div>
                        <div class="font-mono" style="font-weight: 600; font-size: var(--font-size-base);">${formatCurrency(account.totalCurrentValue || 0)}</div>
                    </div>
                </div>
                <div style="border-top: 1px solid var(--glass-border); padding-top: var(--space-4); margin-bottom: var(--space-4); text-align: center;">
                    <div style="font-size: var(--font-size-xs); color: var(--text-muted); margin-bottom: var(--space-2);">수익률</div>
                    <div class="font-mono ${profitClass}" style="font-weight: 600; font-size: var(--font-size-2xl);">${formatPercent(profitRate)}</div>
                </div>
                <div style="display: flex; justify-content: space-between; font-size: var(--font-size-sm); color: var(--text-secondary); border-top: 1px solid var(--glass-border); padding-top: var(--space-4);">
                    <span>보유 종목</span>
                    <span class="font-mono" style="font-weight: 600;">${account.holdingsCount || 0}개</span>
                </div>
            </div>
            <div class="glass-card-footer">
                <a href="index.html?accountId=${account.id}" class="btn-glass primary" style="width: 100%; justify-content: center;">
                    <i class="bi bi-box-arrow-up-right"></i>
                    거래내역 보기
                </a>
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
            showToast('success', '계좌가 추가되었습니다.');
        },
        error: function(xhr) {
            showToast('error', '계좌 추가 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
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
            openEditModal();
        },
        error: function(xhr) {
            showToast('error', '계좌 정보를 불러오는데 실패했습니다.');
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
            closeEditModal();
            loadAccounts();
            loadAccountsSummary();
            showToast('success', '계좌가 수정되었습니다.');
        },
        error: function(xhr) {
            showToast('error', '계좌 수정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
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
            showToast('success', '계좌가 삭제되었습니다.');
        },
        error: function(xhr) {
            showToast('error', '계좌 삭제 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
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
            showToast('success', '기본 계좌가 변경되었습니다.');
        },
        error: function(xhr) {
            showToast('error', '기본 계좌 설정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
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
 * Display a toast-style alert message using glass design
 * @param {('success'|'error'|'warning'|'info')} type - Toast type
 * @param {string} message - Message to display
 */
function showToast(type, message) {
    // Use glass-utils.js showToast if available
    if (typeof window.showToast === 'function') {
        window.showToast(type, message);
        return;
    }

    // Fallback implementation
    const iconMap = {
        success: 'bi-check-circle-fill',
        error: 'bi-exclamation-circle-fill',
        warning: 'bi-exclamation-triangle-fill',
        info: 'bi-info-circle-fill'
    };

    const toast = $(`
        <div class="toast-glass ${type}">
            <i class="bi ${iconMap[type] || iconMap.info}"></i>
            <span>${escapeHtml(message)}</span>
        </div>
    `);

    $('#toastContainer').append(toast);

    setTimeout(() => {
        toast.css('opacity', '0');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}
