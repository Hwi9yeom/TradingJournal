/**
 * Trading Journal - Main Application
 *
 * This module handles the main portfolio dashboard, transactions,
 * analysis, and disclosure management functionality.
 *
 * @requires utils.js - Provides formatting utilities and UI helpers
 * @requires auth.js - Provides authentication functionality
 */

// ===== Constants =====
const API_BASE_URL = '/api';

/** Pagination configuration */
const PAGINATION = {
    PAGE_SIZE: 20,
    SCROLL_THRESHOLD: 300
};

/** API endpoints */
const ENDPOINTS = {
    ACCOUNTS: `${API_BASE_URL}/accounts`,
    TRANSACTIONS: `${API_BASE_URL}/transactions`,
    PORTFOLIO_SUMMARY: `${API_BASE_URL}/portfolio/summary`,
    ANALYSIS: {
        PERIOD: `${API_BASE_URL}/analysis/period`,
        STOCK: `${API_BASE_URL}/analysis/stock`,
        TAX: `${API_BASE_URL}/analysis/tax`
    },
    DISCLOSURES: {
        BASE: `${API_BASE_URL}/disclosures`,
        SUMMARY: `${API_BASE_URL}/disclosures/summary`,
        SYNC: `${API_BASE_URL}/disclosures/sync`
    },
    DATA: {
        EXPORT: `${API_BASE_URL}/data/export`,
        IMPORT: `${API_BASE_URL}/data/import`,
        TEMPLATE: `${API_BASE_URL}/data/template/csv`
    }
};

/** Badge styles for report types */
const REPORT_TYPE_BADGES = {
    '정기공시': 'bg-primary',
    '주요사항보고': 'bg-danger',
    '자본시장법': 'bg-warning',
    DEFAULT: 'bg-secondary'
};

// ===== Application State =====
const AppState = {
    /** Currently selected account ID (null = all accounts) */
    currentAccountId: null,

    /** Pagination state for transactions */
    pagination: {
        currentPage: 0,
        isLoading: false,
        hasMoreData: true
    },

    /**
     * Resets pagination state
     */
    resetPagination() {
        this.pagination.currentPage = 0;
        this.pagination.isLoading = false;
        this.pagination.hasMoreData = true;
    }
};

// ===== AJAX Helper =====
/**
 * Performs an AJAX request with standardized error handling
 * @param {Object} options - jQuery AJAX options
 * @param {string} options.url - Request URL
 * @param {string} [options.method='GET'] - HTTP method
 * @param {Object} [options.data] - Request data
 * @param {string} [options.errorMessage] - Custom error message
 * @param {boolean} [options.showLoading=false] - Show loading overlay
 * @param {string} [options.loadingMessage] - Loading overlay message
 * @returns {Promise} jQuery AJAX promise
 */
function apiRequest(options) {
    const {
        url,
        method = 'GET',
        data,
        errorMessage = '요청 처리 중 오류가 발생했습니다.',
        showLoading = false,
        loadingMessage = '처리 중...',
        contentType,
        processData,
        beforeSend,
        success,
        error
    } = options;

    const ajaxOptions = {
        url,
        method,
        beforeSend: function(xhr) {
            if (showLoading) {
                LoadingOverlay.show(loadingMessage);
            }
            if (beforeSend) {
                beforeSend(xhr);
            }
        },
        success: function(response) {
            if (showLoading) {
                LoadingOverlay.hide();
            }
            if (success) {
                success(response);
            }
        },
        error: function(xhr) {
            if (showLoading) {
                LoadingOverlay.hide();
            }
            handleAjaxError(xhr, errorMessage);
            if (error) {
                error(xhr);
            }
        }
    };

    if (data) {
        ajaxOptions.data = data;
    }
    if (contentType !== undefined) {
        ajaxOptions.contentType = contentType;
    }
    if (processData !== undefined) {
        ajaxOptions.processData = processData;
    }

    return $.ajax(ajaxOptions);
}

// ===== Initialization =====
$(document).ready(function() {
    // Check authentication first
    if (!checkAuth()) {
        return; // Will be redirected to login
    }

    // Parse accountId from URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    AppState.currentAccountId = urlParams.get('accountId') || null;

    // Initialize application
    initializeApp();
    initializeAnalysisDefaults();
});

/**
 * Initializes the main application components
 */
function initializeApp() {
    loadAccounts();
    loadPortfolioSummary();
    loadTransactions();
    loadDisclosureSummary();
    setupEventHandlers();
    setDefaultDateTime();
    setupInfiniteScroll();
}

/**
 * Sets default values for analysis date inputs
 */
function initializeAnalysisDefaults() {
    const today = new Date();
    const lastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);

    $('#analysis-start-date').val(lastMonth.toISOString().split('T')[0]);
    $('#analysis-end-date').val(today.toISOString().split('T')[0]);
}

// ===== Account Management =====
/**
 * Loads the list of accounts from the server
 */
function loadAccounts() {
    apiRequest({
        url: ENDPOINTS.ACCOUNTS,
        errorMessage: '계좌 목록을 불러오는데 실패했습니다.',
        success: populateAccountSelectors
    });
}

/**
 * Populates account selector dropdowns with account data
 * @param {Array} accounts - List of account objects
 */
function populateAccountSelectors(accounts) {
    const accountSelector = $('#account-selector');
    const transactionAccount = $('#transaction-account');

    // Account filter dropdown
    if (accountSelector.length) {
        accountSelector.find('option:not(:first)').remove();
        accounts.forEach(account => {
            const selected = AppState.currentAccountId == account.id ? 'selected' : '';
            accountSelector.append(
                `<option value="${account.id}" ${selected}>${account.name}</option>`
            );
        });

        // Account selection change handler
        accountSelector.off('change').on('change', function() {
            AppState.currentAccountId = $(this).val() || null;
            loadPortfolioSummary();
            loadTransactions(true);
        });
    }

    // Transaction form account dropdown
    if (transactionAccount.length) {
        transactionAccount.empty();
        accounts.forEach(account => {
            const selected = account.isDefault ? 'selected' : '';
            transactionAccount.append(
                `<option value="${account.id}" ${selected}>${account.name}</option>`
            );
        });
    }
}

// ===== Event Handlers =====
/**
 * Sets up form and UI event handlers
 */
function setupEventHandlers() {
    $('#transaction-form').on('submit', function(e) {
        e.preventDefault();
        addTransaction();
    });
}

/**
 * Sets the transaction date input to the current date/time
 */
function setDefaultDateTime() {
    const now = new Date();
    const dateTimeLocal = formatDateTimeLocal(now);
    $('#transaction-date').val(dateTimeLocal);
}

/**
 * Formats a Date object for datetime-local input
 * @param {Date} date - Date to format
 * @returns {string} Formatted datetime string (YYYY-MM-DDTHH:MM)
 */
function formatDateTimeLocal(date) {
    return date.getFullYear() + '-' +
        String(date.getMonth() + 1).padStart(2, '0') + '-' +
        String(date.getDate()).padStart(2, '0') + 'T' +
        String(date.getHours()).padStart(2, '0') + ':' +
        String(date.getMinutes()).padStart(2, '0');
}

// ===== Portfolio Management =====
/**
 * Loads and displays the portfolio summary
 */
function loadPortfolioSummary() {
    let url = ENDPOINTS.PORTFOLIO_SUMMARY;
    if (AppState.currentAccountId) {
        url += `?accountId=${AppState.currentAccountId}`;
    }

    apiRequest({
        url: url,
        errorMessage: '포트폴리오 정보를 불러오는데 실패했습니다.',
        beforeSend: function() {
            showSkeleton('#portfolio-holdings', 5);
        },
        success: function(data) {
            updatePortfolioSummary(data);
            updatePortfolioHoldings(data.holdings || []);
        },
        error: function() {
            showEmptyState('#portfolio-holdings', '포트폴리오 정보를 불러올 수 없습니다.');
        }
    });
}

/**
 * Updates portfolio summary display
 * @param {Object} summary - Portfolio summary data
 */
function updatePortfolioSummary(summary) {
    $('#total-investment').text(formatCurrency(summary.totalInvestment));
    $('#total-value').text(formatCurrency(summary.totalCurrentValue));

    updateProfitLossDisplay(
        '#total-profit',
        summary.totalProfitLoss,
        summary.totalProfitLossPercent
    );

    updateProfitLossDisplay(
        '#day-change',
        summary.totalDayChange,
        summary.totalDayChangePercent
    );
}

/**
 * Updates a profit/loss display element with value and styling
 * @param {string} selector - jQuery selector for the element
 * @param {number} value - Profit/loss value
 * @param {number} percent - Profit/loss percentage
 */
function updateProfitLossDisplay(selector, value, percent) {
    const text = formatCurrency(value) + ' (' + formatPercent(percent) + ')';
    $(selector)
        .text(text)
        .removeClass('positive negative')
        .addClass(value >= 0 ? 'positive' : 'negative');
}

/**
 * Updates the portfolio holdings table
 * @param {Array} holdings - List of holding objects
 */
function updatePortfolioHoldings(holdings) {
    const tbody = $('#portfolio-holdings');
    tbody.empty();

    if (!holdings || holdings.length === 0) {
        showEmptyState(tbody, '보유 종목이 없습니다. 거래를 추가해주세요.', 'bi-inbox');
        return;
    }

    holdings.forEach(holding => {
        const row = createHoldingRow(holding);
        tbody.append(row);
    });
}

/**
 * Creates a table row for a holding
 * @param {Object} holding - Holding data
 * @returns {jQuery} Table row element
 */
function createHoldingRow(holding) {
    const row = $('<tr>');

    row.append(`<td>${holding.stockName} (${holding.stockSymbol})</td>`);
    row.append(`<td>${formatNumber(holding.quantity)}</td>`);
    row.append(`<td>${formatCurrency(holding.averagePrice)}</td>`);
    row.append(`<td>${formatCurrency(holding.currentPrice)}</td>`);
    row.append(`<td>${formatCurrency(holding.currentValue)}</td>`);
    row.append(createProfitLossCell(holding.profitLoss, holding.profitLossPercent));
    row.append(createProfitLossCell(holding.dayChange, holding.dayChangePercent));

    return row;
}

/**
 * Creates a profit/loss table cell with appropriate styling
 * @param {number} value - Profit/loss value
 * @param {number} percent - Profit/loss percentage
 * @returns {jQuery} Table cell element
 */
function createProfitLossCell(value, percent) {
    const cell = $('<td>')
        .text(formatCurrency(value) + ' (' + formatPercent(percent) + ')')
        .addClass(value >= 0 ? 'positive' : 'negative');
    return cell;
}

// ===== Transaction Management =====
/**
 * Loads transactions with pagination support
 * @param {boolean} [reset=false] - Reset pagination and clear existing transactions
 */
function loadTransactions(reset = false) {
    const { pagination } = AppState;

    if (pagination.isLoading) return;

    if (reset) {
        AppState.resetPagination();
        $('#transaction-list').empty();
    }

    if (!pagination.hasMoreData) return;

    pagination.isLoading = true;
    showLoadingIndicator();

    let url = `${ENDPOINTS.TRANSACTIONS}?page=${pagination.currentPage}&size=${PAGINATION.PAGE_SIZE}`;
    if (AppState.currentAccountId) {
        url += `&accountId=${AppState.currentAccountId}`;
    }

    apiRequest({
        url: url,
        errorMessage: '거래 내역을 불러오는데 실패했습니다.',
        success: function(data) {
            updateTransactionList(data.content, reset);
            pagination.hasMoreData = !data.last;
            pagination.currentPage++;
            hideLoadingIndicator();
            pagination.isLoading = false;
        },
        error: function() {
            hideLoadingIndicator();
            pagination.isLoading = false;
            if (reset) {
                showEmptyState('#transaction-list', '거래 내역을 불러올 수 없습니다.');
            }
        }
    });
}

/**
 * Shows the loading indicator for transaction list
 */
function showLoadingIndicator() {
    if ($('#loading-indicator').length === 0) {
        $('#transaction-list').after(`
            <tr id="loading-indicator">
                <td colspan="7" class="text-center p-3">
                    <div class="spinner-border spinner-border-sm" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    데이터를 불러오는 중...
                </td>
            </tr>
        `);
    }
}

/**
 * Hides the loading indicator
 */
function hideLoadingIndicator() {
    $('#loading-indicator').remove();
}

/**
 * Updates the transaction list table
 * @param {Array} transactions - List of transaction objects
 * @param {boolean} [reset=false] - Whether to clear existing rows
 */
function updateTransactionList(transactions, reset = false) {
    const tbody = $('#transaction-list');

    if (reset) {
        tbody.empty();
    }

    if (!transactions || transactions.length === 0) {
        if (reset) {
            showEmptyState(tbody, '거래 내역이 없습니다. 첫 거래를 추가해보세요!', 'bi-journal-plus');
        }
        return;
    }

    transactions.forEach(transaction => {
        const row = createTransactionRow(transaction);
        tbody.append(row);
    });
}

/**
 * Creates a table row for a transaction
 * @param {Object} transaction - Transaction data
 * @returns {jQuery} Table row element
 */
function createTransactionRow(transaction) {
    const row = $('<tr>');
    const isBuy = transaction.type === 'BUY';

    row.append(`<td>${formatDateTimeDisplay(transaction.transactionDate)}</td>`);
    row.append(`<td>${transaction.stockName} (${transaction.stockSymbol})</td>`);
    row.append(`<td><span class="badge ${isBuy ? 'bg-success' : 'bg-danger'}">${isBuy ? '매수' : '매도'}</span></td>`);
    row.append(`<td>${formatNumber(transaction.quantity)}</td>`);
    row.append(`<td>${formatCurrency(transaction.price)}</td>`);
    row.append(`<td>${formatCurrency(transaction.totalAmount)}</td>`);
    row.append(`<td><button class="btn btn-sm btn-danger btn-delete" onclick="deleteTransaction(${transaction.id})" aria-label="거래 삭제">삭제</button></td>`);

    return row;
}

/**
 * Formats a date string for display
 * @param {string} dateTimeStr - ISO date string
 * @returns {string} Formatted date string
 */
function formatDateTimeDisplay(dateTimeStr) {
    const date = new Date(dateTimeStr);
    return date.getFullYear() + '-' +
        String(date.getMonth() + 1).padStart(2, '0') + '-' +
        String(date.getDate()).padStart(2, '0') + ' ' +
        String(date.getHours()).padStart(2, '0') + ':' +
        String(date.getMinutes()).padStart(2, '0');
}

/**
 * Adds a new transaction from the form
 */
function addTransaction() {
    const form = document.getElementById('transaction-form');

    if (!validateForm(form)) {
        ToastNotification.warning('필수 항목을 모두 입력해주세요.');
        return;
    }

    const accountId = $('#transaction-account').val();
    const transaction = {
        accountId: accountId ? parseInt(accountId) : null,
        stockSymbol: $('#stock-symbol').val().toUpperCase(),
        type: $('#transaction-type').val(),
        quantity: parseFloat($('#quantity').val()),
        price: parseFloat($('#price').val()),
        commission: parseFloat($('#commission').val()) || 0,
        transactionDate: $('#transaction-date').val()
    };

    apiRequest({
        url: ENDPOINTS.TRANSACTIONS,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(transaction),
        showLoading: true,
        loadingMessage: '거래를 추가하는 중...',
        errorMessage: '거래 추가에 실패했습니다.',
        success: function() {
            $('#transaction-form')[0].reset();
            clearFormValidation(form);
            setDefaultDateTime();
            loadAccounts();
            loadPortfolioSummary();
            loadTransactions(true);
            ToastNotification.success('거래가 성공적으로 추가되었습니다.');
        }
    });
}

/**
 * Deletes a transaction after confirmation
 * @param {number} id - Transaction ID to delete
 */
function deleteTransaction(id) {
    showConfirmDialog(
        '거래 삭제',
        '이 거래를 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.',
        function() {
            apiRequest({
                url: `${ENDPOINTS.TRANSACTIONS}/${id}`,
                method: 'DELETE',
                showLoading: true,
                loadingMessage: '거래를 삭제하는 중...',
                errorMessage: '거래 삭제에 실패했습니다.',
                success: function() {
                    loadPortfolioSummary();
                    loadTransactions(true);
                    ToastNotification.success('거래가 성공적으로 삭제되었습니다.');
                }
            });
        }
    );
}

// ===== Data Import/Export =====
/**
 * Exports data in the specified format
 * @param {string} format - Export format (csv, excel, etc.)
 */
function exportData(format) {
    window.location.href = `${ENDPOINTS.DATA.EXPORT}/${format}`;
}

/**
 * Downloads the import template
 */
function downloadTemplate() {
    window.location.href = ENDPOINTS.DATA.TEMPLATE;
}

/**
 * Imports transaction data from a file
 */
function importData() {
    const fileInput = $('#import-file')[0];
    const fileType = $('#import-file-type').val();

    if (!fileInput.files.length) {
        ToastNotification.warning('파일을 선택해주세요.');
        return;
    }

    const file = fileInput.files[0];
    const formData = new FormData();
    formData.append('file', file);

    apiRequest({
        url: `${ENDPOINTS.DATA.IMPORT}/${fileType}`,
        method: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        errorMessage: '파일 가져오기에 실패했습니다.',
        success: function(result) {
            displayImportResult(result);
            if (result.successCount > 0) {
                loadPortfolioSummary();
                loadTransactions();
            }
        }
    });
}

/**
 * Displays the import result summary
 * @param {Object} result - Import result data
 */
function displayImportResult(result) {
    const alertClass = result.failureCount > 0 ? 'alert-warning' : 'alert-success';

    let html = `<div class="alert ${alertClass}">`;
    html += `<strong>가져오기 완료</strong><br>`;
    html += `전체: ${result.totalRows}건, 성공: ${result.successCount}건, 실패: ${result.failureCount}건`;

    if (result.errors && result.errors.length > 0) {
        html += '<hr><strong>오류 내역:</strong><ul>';
        result.errors.forEach(error => {
            html += `<li>행 ${error.rowNumber}: ${error.message}</li>`;
        });
        html += '</ul>';
    }

    html += '</div>';

    $('#import-result').html(html).show();
}

// ===== Analysis Functions =====
/**
 * Analyzes transactions for a given period
 */
function analyzePeriod() {
    const startDate = $('#analysis-start-date').val();
    const endDate = $('#analysis-end-date').val();

    if (!startDate || !endDate) {
        ToastNotification.warning('기간을 선택해주세요.');
        return;
    }

    apiRequest({
        url: `${ENDPOINTS.ANALYSIS.PERIOD}?startDate=${startDate}&endDate=${endDate}`,
        errorMessage: '기간 분석에 실패했습니다.',
        success: displayPeriodAnalysis
    });
}

/**
 * Displays period analysis results
 * @param {Object} analysis - Period analysis data
 */
function displayPeriodAnalysis(analysis) {
    let html = '<div class="row">';
    html += '<div class="col-md-6">';
    html += '<h6>기간 요약</h6>';
    html += '<table class="table table-sm">';
    html += createAnalysisRow('총 매수금액', formatCurrency(analysis.totalBuyAmount));
    html += createAnalysisRow('총 매도금액', formatCurrency(analysis.totalSellAmount));
    html += createAnalysisRowWithClass('실현손익',
        `${formatCurrency(analysis.realizedProfit)} (${formatPercent(analysis.realizedProfitRate)})`,
        analysis.realizedProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRowWithClass('미실현손익',
        `${formatCurrency(analysis.unrealizedProfit)} (${formatPercent(analysis.unrealizedProfitRate)})`,
        analysis.unrealizedProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRowWithClass('총손익',
        `${formatCurrency(analysis.totalProfit)} (${formatPercent(analysis.totalProfitRate)})`,
        analysis.totalProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRow('총거래횟수',
        `${analysis.totalTransactions}회 (매수: ${analysis.buyTransactions}, 매도: ${analysis.sellTransactions})`);
    html += '</table>';
    html += '</div>';

    if (analysis.monthlyAnalysis && analysis.monthlyAnalysis.length > 0) {
        html += '<div class="col-md-6">';
        html += '<h6>월별 분석</h6>';
        html += '<table class="table table-sm">';
        html += '<thead><tr><th>월</th><th>매수</th><th>매도</th><th>손익</th></tr></thead>';
        html += '<tbody>';
        analysis.monthlyAnalysis.forEach(month => {
            const profitClass = month.profit >= 0 ? 'positive' : 'negative';
            html += `<tr>
                <td>${month.yearMonth}</td>
                <td>${formatCurrency(month.buyAmount)}</td>
                <td>${formatCurrency(month.sellAmount)}</td>
                <td class="${profitClass}">${formatCurrency(month.profit)}</td>
            </tr>`;
        });
        html += '</tbody></table>';
        html += '</div>';
    }

    html += '</div>';
    $('#period-analysis-result').html(html);
}

/**
 * Creates a simple analysis table row
 * @param {string} label - Row label
 * @param {string} value - Row value
 * @returns {string} HTML table row
 */
function createAnalysisRow(label, value) {
    return `<tr><td>${label}</td><td>${value}</td></tr>`;
}

/**
 * Creates an analysis table row with a CSS class
 * @param {string} label - Row label
 * @param {string} value - Row value
 * @param {string} className - CSS class for the value cell
 * @returns {string} HTML table row
 */
function createAnalysisRowWithClass(label, value, className) {
    return `<tr><td>${label}</td><td class="${className}">${value}</td></tr>`;
}

/**
 * Analyzes a specific stock
 */
function analyzeStock() {
    const symbol = $('#analysis-stock-symbol').val();

    if (!symbol) {
        ToastNotification.warning('종목 코드를 입력해주세요.');
        return;
    }

    apiRequest({
        url: `${ENDPOINTS.ANALYSIS.STOCK}/${symbol}`,
        errorMessage: '종목 분석에 실패했습니다.',
        success: displayStockAnalysis
    });
}

/**
 * Displays stock analysis results
 * @param {Object} analysis - Stock analysis data
 */
function displayStockAnalysis(analysis) {
    let html = '<div class="row">';
    html += '<div class="col-md-6">';
    html += `<h6>${analysis.stockName} (${analysis.stockSymbol})</h6>`;
    html += '<table class="table table-sm">';
    html += createAnalysisRow('총 매수', `${analysis.totalBuyCount}회 / ${formatNumber(analysis.totalBuyQuantity)}주`);
    html += createAnalysisRow('총 매도', `${analysis.totalSellCount}회 / ${formatNumber(analysis.totalSellQuantity)}주`);
    html += createAnalysisRow('평균 매수가', formatCurrency(analysis.averageBuyPrice));
    html += createAnalysisRow('평균 매도가', formatCurrency(analysis.averageSellPrice));
    html += createAnalysisRowWithClass('실현손익',
        `${formatCurrency(analysis.realizedProfit)} (${formatPercent(analysis.realizedProfitRate)})`,
        analysis.realizedProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRow('현재보유', `${formatNumber(analysis.currentHolding)}주`);
    html += createAnalysisRowWithClass('미실현손익',
        `${formatCurrency(analysis.unrealizedProfit)} (${formatPercent(analysis.unrealizedProfitRate)})`,
        analysis.unrealizedProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRow('보유기간', `${analysis.holdingDays}일`);
    html += '</table>';
    html += '</div>';

    if (analysis.tradingPatterns && analysis.tradingPatterns.length > 0) {
        html += '<div class="col-md-6">';
        html += '<h6>매매 패턴</h6>';
        html += '<table class="table table-sm">';
        analysis.tradingPatterns.forEach(pattern => {
            html += createAnalysisRow(pattern.pattern, pattern.value);
        });
        html += '</table>';
        html += '</div>';
    }

    html += '</div>';
    $('#stock-analysis-result').html(html);
}

/**
 * Calculates tax information for a given year
 */
function calculateTax() {
    const year = $('#tax-year').val();

    apiRequest({
        url: `${ENDPOINTS.ANALYSIS.TAX}/${year}`,
        errorMessage: '세금 계산에 실패했습니다.',
        success: displayTaxCalculation
    });
}

/**
 * Displays tax calculation results
 * @param {Object} tax - Tax calculation data
 */
function displayTaxCalculation(tax) {
    let html = `<h6>${tax.taxYear}년 양도소득세 계산</h6>`;
    html += '<div class="alert alert-info">';
    html += '<table class="table table-sm mb-0">';
    html += createAnalysisRow('총 매도금액', formatCurrency(tax.totalSellAmount));
    html += createAnalysisRow('총 매수금액', formatCurrency(tax.totalBuyAmount));
    html += createAnalysisRowWithClass('총 이익', formatCurrency(tax.totalProfit), 'positive');
    html += createAnalysisRowWithClass('총 손실', formatCurrency(tax.totalLoss), 'negative');
    html += createAnalysisRowWithClass('순손익', formatCurrency(tax.netProfit),
        tax.netProfit >= 0 ? 'positive' : 'negative');
    html += createAnalysisRow('기본공제', '₩2,500,000');
    html += createAnalysisRow('과세표준', formatCurrency(tax.taxableAmount));
    html += createAnalysisRow('세율', formatPercent(tax.taxRate));
    html += `<tr><td><strong>예상 세금</strong></td><td><strong>${formatCurrency(tax.estimatedTax)}</strong></td></tr>`;
    html += '</table>';
    html += '</div>';

    if (tax.taxDetails && tax.taxDetails.length > 0) {
        html += '<h6>거래 상세</h6>';
        html += '<table class="table table-sm">';
        html += '<thead><tr><th>종목</th><th>매수일</th><th>매도일</th><th>매수금액</th><th>매도금액</th><th>손익</th></tr></thead>';
        html += '<tbody>';
        tax.taxDetails.forEach(detail => {
            const profitValue = detail.profit || -detail.loss;
            const profitClass = profitValue > 0 ? 'positive' : 'negative';
            html += `<tr>
                <td>${detail.stockSymbol}</td>
                <td>${detail.buyDate}</td>
                <td>${detail.sellDate}</td>
                <td>${formatCurrency(detail.buyAmount)}</td>
                <td>${formatCurrency(detail.sellAmount)}</td>
                <td class="${profitClass}">${formatCurrency(profitValue)}</td>
            </tr>`;
        });
        html += '</tbody></table>';
    }

    $('#tax-calculation-result').html(html);
}

// ===== Disclosure Management =====
/**
 * Loads the disclosure summary
 */
function loadDisclosureSummary() {
    apiRequest({
        url: ENDPOINTS.DISCLOSURES.SUMMARY,
        success: updateDisclosureSummary,
        error: function(xhr) {
            console.error('Failed to load disclosure summary:', xhr);
        }
    });
}

/**
 * Updates the disclosure summary display
 * @param {Object} summary - Disclosure summary data
 */
function updateDisclosureSummary(summary) {
    $('#total-disclosures').text(summary.totalCount || 0);
    $('#unread-disclosures').text(summary.unreadCount || 0);
    $('#important-disclosures').text(summary.importantCount || 0);

    if (summary.recentDisclosures) {
        updateDisclosureTable('recent-disclosures-body', summary.recentDisclosures);
    }
    if (summary.unreadDisclosures) {
        updateDisclosureTable('unread-disclosures-body', summary.unreadDisclosures);
    }
    if (summary.importantDisclosures) {
        updateDisclosureTable('important-disclosures-body', summary.importantDisclosures);
    }
}

/**
 * Updates a disclosure table with data
 * @param {string} tableId - Table body element ID
 * @param {Array} disclosures - List of disclosure objects
 */
function updateDisclosureTable(tableId, disclosures) {
    const tbody = $(`#${tableId}`);
    tbody.empty();

    if (disclosures.length === 0) {
        tbody.append('<tr><td colspan="6" class="text-center">공시 정보가 없습니다.</td></tr>');
        return;
    }

    disclosures.forEach(disclosure => {
        const row = createDisclosureRow(disclosure);
        tbody.append(row);
    });
}

/**
 * Creates a table row for a disclosure
 * @param {Object} disclosure - Disclosure data
 * @returns {jQuery} Table row element
 */
function createDisclosureRow(disclosure) {
    const row = $('<tr>');
    if (!disclosure.isRead) {
        row.addClass('table-warning');
    }

    row.append(`<td>${formatDateTimeDisplay(disclosure.receivedDate)}</td>`);
    row.append(`<td>${disclosure.stockName} (${disclosure.stockSymbol})</td>`);
    row.append(`<td>${disclosure.reportName}</td>`);
    row.append(`<td>${disclosure.submitter}</td>`);

    const badgeClass = REPORT_TYPE_BADGES[disclosure.reportType] || REPORT_TYPE_BADGES.DEFAULT;
    row.append(`<td><span class="badge ${badgeClass}">${disclosure.reportType}</span></td>`);

    row.append(createDisclosureActions(disclosure));

    return row;
}

/**
 * Creates action buttons for a disclosure row
 * @param {Object} disclosure - Disclosure data
 * @returns {jQuery} Table cell with action buttons
 */
function createDisclosureActions(disclosure) {
    const actions = $('<td>');

    if (!disclosure.isRead) {
        actions.append(
            `<button class="btn btn-sm btn-outline-primary me-1" onclick="markAsRead(${disclosure.id})">읽음</button>`
        );
    }

    const starIcon = disclosure.isImportant ? 'bi-star-fill' : 'bi-star';
    const starBtnClass = disclosure.isImportant ? 'btn-warning' : 'btn-outline-warning';
    actions.append(
        `<button class="btn btn-sm ${starBtnClass} me-1" onclick="toggleImportant(${disclosure.id})">
            <i class="bi ${starIcon}"></i>
        </button>`
    );

    if (disclosure.viewUrl) {
        actions.append(
            `<a href="${disclosure.viewUrl}" target="_blank" class="btn btn-sm btn-outline-info">보기</a>`
        );
    }

    return actions;
}

/**
 * Marks a disclosure as read
 * @param {number} disclosureId - Disclosure ID
 */
function markAsRead(disclosureId) {
    apiRequest({
        url: `${ENDPOINTS.DISCLOSURES.BASE}/${disclosureId}/read`,
        method: 'PUT',
        errorMessage: '공시 읽음 처리에 실패했습니다.',
        success: loadDisclosureSummary
    });
}

/**
 * Toggles the important flag for a disclosure
 * @param {number} disclosureId - Disclosure ID
 */
function toggleImportant(disclosureId) {
    apiRequest({
        url: `${ENDPOINTS.DISCLOSURES.BASE}/${disclosureId}/important`,
        method: 'PUT',
        errorMessage: '중요 표시 변경에 실패했습니다.',
        success: loadDisclosureSummary
    });
}

/**
 * Synchronizes disclosures from external source
 */
function syncDisclosures() {
    const btn = event.target;
    const originalHtml = btn.innerHTML;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> 동기화 중...';
    btn.disabled = true;

    apiRequest({
        url: ENDPOINTS.DISCLOSURES.SYNC,
        method: 'POST',
        errorMessage: '공시 동기화에 실패했습니다.',
        success: function() {
            setTimeout(() => {
                loadDisclosureSummary();
                btn.innerHTML = originalHtml;
                btn.disabled = false;
                ToastNotification.success('공시 정보가 동기화되었습니다.');
            }, 1000);
        },
        error: function() {
            btn.innerHTML = originalHtml;
            btn.disabled = false;
        }
    });
}

// ===== Infinite Scroll =====
/**
 * Sets up infinite scroll for transaction list
 */
function setupInfiniteScroll() {
    $(window).scroll(function() {
        const scrollPosition = $(window).scrollTop() + $(window).height();
        const threshold = $(document).height() - PAGINATION.SCROLL_THRESHOLD;

        if (scrollPosition > threshold) {
            const { pagination } = AppState;
            if (pagination.hasMoreData && !pagination.isLoading) {
                loadTransactions();
            }
        }
    });
}
