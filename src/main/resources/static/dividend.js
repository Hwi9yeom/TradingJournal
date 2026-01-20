/**
 * Trading Journal - Dividend Management Module
 * Handles dividend tracking, display, and chart visualization
 *
 * @fileoverview Provides dividend-related functionality including:
 * - Dividend CRUD operations
 * - Summary statistics display
 * - Monthly dividend chart visualization
 * - Filtering by time period
 *
 * @requires utils.js - formatCurrency, formatNumber, formatDate, ToastNotification
 */

// ============================================================================
// CONSTANTS
// ============================================================================

const API_BASE_URL = '/api';

/**
 * Chart color configuration for monthly dividend chart
 * @constant {Object}
 */
const CHART_COLORS = {
    BACKGROUND: 'rgba(75, 192, 192, 0.8)',
    BORDER: 'rgba(75, 192, 192, 1)',
    BORDER_WIDTH: 1
};

/**
 * Display settings
 * @constant {Object}
 */
const DISPLAY_SETTINGS = {
    MONTHS_TO_SHOW: 12,
    DEFAULT_TAX_RATE: '15.4',
    TABLE_COLUMNS: {
        DIVIDEND_LIST: 10,
        TOP_STOCKS: 3
    }
};

/**
 * Korean text labels
 * @constant {Object}
 */
const LABELS = {
    CHART_TITLE: '월별 배당금',
    CHART_TOOLTIP_PREFIX: '배당금: ',
    EMPTY_DIVIDEND_LIST: '배당금 내역이 없습니다',
    EMPTY_TOP_STOCKS: '배당금 지급 종목 없음',
    PAYMENT_COUNT_SUFFIX: '회',
    SUCCESS_ADD: '배당금이 추가되었습니다.',
    SUCCESS_DELETE: '배당금 기록이 삭제되었습니다.',
    ERROR_ADD: '배당금 추가에 실패했습니다.',
    ERROR_DELETE: '배당금 삭제에 실패했습니다.',
    CONFIRM_DELETE: '정말로 이 배당금 기록을 삭제하시겠습니까?'
};

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Application state object containing chart instances and data
 * @type {Object}
 */
const state = {
    /** @type {Chart|null} Chart.js instance for monthly dividend chart */
    monthlyDividendChart: null,
    /** @type {Array} Array of dividend records */
    dividends: []
};

// ============================================================================
// INITIALIZATION
// ============================================================================

$(document).ready(function() {
    // Check authentication first
    if (!checkAuth()) {
        return; // Will be redirected to login
    }

    loadStockOptions();
    loadDividendSummary();
    loadDividends();
    setupEventHandlers();
    initializeChart();
});

/**
 * Set up event handlers for form submission and filtering
 */
function setupEventHandlers() {
    $('#dividend-form').on('submit', function(e) {
        e.preventDefault();
        addDividend();
    });

    $('#dividend-filter').on('change', function() {
        filterDividends($(this).val());
    });
}

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load available stocks and populate the stock select dropdown
 */
function loadStockOptions() {
    $.ajax({
        url: `${API_BASE_URL}/stocks`,
        method: 'GET',
        success: function(stocks) {
            const select = $('#stock-select');
            stocks.forEach(stock => {
                select.append(`<option value="${stock.id}">${stock.name} (${stock.symbol})</option>`);
            });
        },
        error: function(xhr) {
            console.error('Failed to load stocks:', xhr);
        }
    });
}

/**
 * Load dividend summary data and update UI components
 */
function loadDividendSummary() {
    $.ajax({
        url: `${API_BASE_URL}/dividends/summary`,
        method: 'GET',
        success: function(summary) {
            updateSummaryCards(summary);
            updateTopDividendStocks(summary.topDividendStocks);
            updateMonthlyChart(summary.monthlyDividends);
        },
        error: function(xhr) {
            console.error('Failed to load dividend summary:', xhr);
        }
    });
}

/**
 * Load all dividend records
 */
function loadDividends() {
    $.ajax({
        url: `${API_BASE_URL}/dividends`,
        method: 'GET',
        success: function(data) {
            state.dividends = data;
            displayDividends(data);
        },
        error: function(xhr) {
            console.error('Failed to load dividends:', xhr);
        }
    });
}

// ============================================================================
// UI UPDATE FUNCTIONS
// ============================================================================

/**
 * Update summary statistics cards with dividend data
 * @param {Object} summary - Summary object from API
 * @param {number} summary.totalDividends - Total dividends received
 * @param {number} summary.yearlyDividends - Dividends received this year
 * @param {number} summary.monthlyAverage - Average monthly dividend
 * @param {number} summary.dividendYield - Dividend yield percentage
 */
function updateSummaryCards(summary) {
    $('#total-dividends').text(formatCurrency(summary.totalDividends || 0));
    $('#yearly-dividends').text(formatCurrency(summary.yearlyDividends || 0));
    $('#monthly-average').text(formatCurrency(summary.monthlyAverage || 0));
    $('#dividend-yield').text((summary.dividendYield || 0).toFixed(2) + '%');
}

/**
 * Update the top dividend stocks table
 * @param {Array} topStocks - Array of top dividend-paying stocks
 * @param {string} topStocks[].stockName - Stock name
 * @param {number} topStocks[].totalDividend - Total dividend amount
 * @param {number} topStocks[].paymentCount - Number of dividend payments
 */
function updateTopDividendStocks(topStocks) {
    const tbody = $('#top-dividend-stocks');
    tbody.empty();

    if (!topStocks || topStocks.length === 0) {
        tbody.append(`<tr><td colspan="${DISPLAY_SETTINGS.TABLE_COLUMNS.TOP_STOCKS}" class="text-center text-muted">${LABELS.EMPTY_TOP_STOCKS}</td></tr>`);
        return;
    }

    topStocks.forEach(stock => {
        const row = $('<tr>');
        row.append(`<td>${stock.stockName}</td>`);
        row.append(`<td class="text-end">${formatCurrency(stock.totalDividend)}</td>`);
        row.append(`<td class="text-end">${stock.paymentCount}${LABELS.PAYMENT_COUNT_SUFFIX}</td>`);
        tbody.append(row);
    });
}

/**
 * Update the monthly dividend chart with new data
 * @param {Array} monthlyData - Array of monthly dividend data
 * @param {number} monthlyData[].year - Year
 * @param {number} monthlyData[].month - Month (1-12)
 * @param {number} monthlyData[].amount - Dividend amount
 */
function updateMonthlyChart(monthlyData) {
    if (!monthlyData || monthlyData.length === 0) return;

    // Prepare data for the last 12 months
    const labels = [];
    const data = [];

    monthlyData.slice(0, DISPLAY_SETTINGS.MONTHS_TO_SHOW).reverse().forEach(item => {
        labels.push(`${item.year}.${String(item.month).padStart(2, '0')}`);
        data.push(item.amount);
    });

    if (state.monthlyDividendChart) {
        state.monthlyDividendChart.data.labels = labels;
        state.monthlyDividendChart.data.datasets[0].data = data;
        state.monthlyDividendChart.update();
    }
}

/**
 * Display dividend records in the table
 * @param {Array} dividendList - Array of dividend records to display
 */
function displayDividends(dividendList) {
    const tbody = $('#dividend-list');
    tbody.empty();

    if (!dividendList || dividendList.length === 0) {
        tbody.append(`<tr><td colspan="${DISPLAY_SETTINGS.TABLE_COLUMNS.DIVIDEND_LIST}" class="text-center text-muted">${LABELS.EMPTY_DIVIDEND_LIST}</td></tr>`);
        return;
    }

    dividendList.forEach(dividend => {
        const row = $('<tr>');
        row.append(`<td>${formatDate(dividend.paymentDate)}</td>`);
        row.append(`<td>${dividend.stockName} (${dividend.stockSymbol})</td>`);
        row.append(`<td>${formatDate(dividend.exDividendDate)}</td>`);
        row.append(`<td class="text-end">${formatCurrency(dividend.dividendPerShare)}</td>`);
        row.append(`<td class="text-end">${formatNumber(dividend.quantity)}</td>`);
        row.append(`<td class="text-end">${formatCurrency(dividend.totalAmount)}</td>`);
        row.append(`<td class="text-end text-danger">${formatCurrency(dividend.taxAmount)}</td>`);
        row.append(`<td class="text-end text-success">${formatCurrency(dividend.netAmount)}</td>`);
        row.append(`<td><small>${dividend.memo || ''}</small></td>`);
        row.append(`
            <td>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteDividend(${dividend.id})">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `);
        tbody.append(row);
    });
}

// ============================================================================
// CRUD OPERATIONS
// ============================================================================

/**
 * Add a new dividend record from form data
 */
function addDividend() {
    const stockId = $('#stock-select').val();
    const stockOption = $('#stock-select option:selected');
    const stockText = stockOption.text();
    const symbolMatch = stockText.match(/\(([^)]+)\)/);
    const stockSymbol = symbolMatch ? symbolMatch[1] : '';
    const stockName = stockText.replace(/\s*\([^)]*\)/, '');

    const dividendData = {
        stockId: parseInt(stockId),
        stockSymbol: stockSymbol,
        stockName: stockName,
        exDividendDate: $('#ex-dividend-date').val(),
        paymentDate: $('#payment-date').val(),
        dividendPerShare: parseFloat($('#dividend-per-share').val()),
        quantity: parseFloat($('#quantity').val()),
        taxRate: parseFloat($('#tax-rate').val()),
        memo: $('#memo').val()
    };

    $.ajax({
        url: `${API_BASE_URL}/dividends`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(dividendData),
        success: function() {
            $('#dividend-form')[0].reset();
            $('#tax-rate').val(DISPLAY_SETTINGS.DEFAULT_TAX_RATE);
            loadDividends();
            loadDividendSummary();
            ToastNotification.success(LABELS.SUCCESS_ADD);
        },
        error: function(xhr) {
            console.error('Failed to add dividend:', xhr);
            ToastNotification.error(LABELS.ERROR_ADD);
        }
    });
}

/**
 * Delete a dividend record by ID
 * @param {number} id - The dividend record ID to delete
 */
function deleteDividend(id) {
    if (!confirm(LABELS.CONFIRM_DELETE)) {
        return;
    }

    $.ajax({
        url: `${API_BASE_URL}/dividends/${id}`,
        method: 'DELETE',
        success: function() {
            loadDividends();
            loadDividendSummary();
            ToastNotification.success(LABELS.SUCCESS_DELETE);
        },
        error: function(xhr) {
            console.error('Failed to delete dividend:', xhr);
            ToastNotification.error(LABELS.ERROR_DELETE);
        }
    });
}

// ============================================================================
// FILTERING
// ============================================================================

/**
 * Filter dividends by time period
 * @param {string} filter - Filter type: 'all', 'year', 'quarter', or 'month'
 */
function filterDividends(filter) {
    const now = new Date();
    let filteredDividends = [...state.dividends];

    switch (filter) {
        case 'year':
            filteredDividends = state.dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate.getFullYear() === now.getFullYear();
            });
            break;
        case 'quarter':
            const quarterStart = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
            filteredDividends = state.dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate >= quarterStart;
            });
            break;
        case 'month':
            filteredDividends = state.dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate.getFullYear() === now.getFullYear() &&
                       paymentDate.getMonth() === now.getMonth();
            });
            break;
        // 'all' case: use the full list (already set)
    }

    displayDividends(filteredDividends);
}

// ============================================================================
// CHART INITIALIZATION
// ============================================================================

/**
 * Initialize the monthly dividend bar chart
 */
function initializeChart() {
    const ctx = document.getElementById('monthlyDividendChart').getContext('2d');
    state.monthlyDividendChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: LABELS.CHART_TITLE,
                data: [],
                backgroundColor: CHART_COLORS.BACKGROUND,
                borderColor: CHART_COLORS.BORDER,
                borderWidth: CHART_COLORS.BORDER_WIDTH
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return LABELS.CHART_TOOLTIP_PREFIX + formatCurrency(context.parsed.y);
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: function(value) {
                            return formatCurrency(value);
                        }
                    }
                }
            }
        }
    });
}
