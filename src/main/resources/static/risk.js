/**
 * Trading Journal - Risk Management Module
 * Handles risk dashboard, position sizing, R-multiple analysis, and sector exposure
 */

const API_BASE_URL = '/api';

// ==================== Constants ====================

/**
 * Risk status thresholds for card styling
 */
const RISK_THRESHOLDS = {
    WARNING_PERCENT: 70,
    DEFAULT_RISK_PERCENT: 2,
    DEFAULT_DAILY_LOSS: 6,
    DEFAULT_WEEKLY_LOSS: 10,
    DEFAULT_MAX_POSITIONS: 10,
    DEFAULT_POSITION_SIZE: 20,
    DEFAULT_SECTOR_CONCENTRATION: 30,
    DEFAULT_KELLY_FRACTION: 0.5
};

/**
 * Status labels in Korean
 */
const STATUS_LABELS = {
    BREACHED: '초과',
    WARNING: '주의',
    NORMAL: '정상',
    NONE: '-'
};

/**
 * CSS class names for styling
 */
const CSS_CLASSES = {
    SUCCESS: 'text-success',
    DANGER: 'text-danger',
    WARNING: 'text-warning',
    MUTED: 'text-muted',
    BG_SUCCESS: 'bg-success',
    BG_DANGER: 'bg-danger',
    BG_WARNING: 'bg-warning',
    BG_SECONDARY: 'bg-secondary',
    BORDER_SUCCESS: 'border-success',
    BORDER_DANGER: 'border-danger',
    BORDER_WARNING: 'border-warning'
};

/**
 * Chart color palette
 */
const CHART_COLORS = {
    BLUE: 'rgba(59, 130, 246, 0.7)',
    BLUE_SOLID: 'rgba(59, 130, 246, 1)',
    GREEN: 'rgba(16, 185, 129, 0.8)',
    YELLOW: 'rgba(245, 158, 11, 0.8)',
    RED: 'rgba(239, 68, 68, 0.8)',
    PURPLE: 'rgba(139, 92, 246, 0.8)',
    PINK: 'rgba(236, 72, 153, 0.8)',
    TEAL: 'rgba(20, 184, 166, 0.8)',
    ORANGE: 'rgba(249, 115, 22, 0.8)',
    INDIGO: 'rgba(99, 102, 241, 0.8)',
    GRAY: 'rgba(168, 162, 158, 0.8)',
    POSITIVE: 'rgba(34, 197, 94, 0.7)',
    NEGATIVE: 'rgba(239, 68, 68, 0.7)'
};

/**
 * Color palette for sector chart
 */
const SECTOR_COLOR_PALETTE = [
    CHART_COLORS.BLUE,
    CHART_COLORS.GREEN,
    CHART_COLORS.YELLOW,
    CHART_COLORS.RED,
    CHART_COLORS.PURPLE,
    CHART_COLORS.PINK,
    CHART_COLORS.TEAL,
    CHART_COLORS.ORANGE,
    CHART_COLORS.INDIGO,
    CHART_COLORS.GRAY
];

// ==================== State Management ====================

/**
 * Application state object containing charts, data, and settings
 */
const state = {
    charts: {
        rMultiple: null,
        sector: null
    },
    currentAccountId: null,
    riskSettings: null
};

// ==================== Utility Functions ====================

/**
 * Applies CSS classes to element based on value comparison
 * @param {jQuery} $element - Target jQuery element
 * @param {number} value - Value to compare
 */
function applyValueClass($element, value) {
    $element.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER}`);
    if (value > 0) {
        $element.addClass(CSS_CLASSES.SUCCESS);
    } else if (value < 0) {
        $element.addClass(CSS_CLASSES.DANGER);
    }
}

/**
 * Determines risk status based on percentage and breach flag
 * @param {boolean} isBreached - Whether limit is breached
 * @param {number} percentUsed - Percentage of limit used
 * @returns {{label: string, bgClass: string, borderClass: string}}
 */
function getRiskStatus(isBreached, percentUsed) {
    if (isBreached) {
        return {
            label: STATUS_LABELS.BREACHED,
            bgClass: CSS_CLASSES.BG_DANGER,
            borderClass: CSS_CLASSES.BORDER_DANGER
        };
    } else if (percentUsed > RISK_THRESHOLDS.WARNING_PERCENT) {
        return {
            label: STATUS_LABELS.WARNING,
            bgClass: CSS_CLASSES.BG_WARNING,
            borderClass: CSS_CLASSES.BORDER_WARNING
        };
    }
    return {
        label: STATUS_LABELS.NORMAL,
        bgClass: CSS_CLASSES.BG_SUCCESS,
        borderClass: CSS_CLASSES.BORDER_SUCCESS
    };
}

/**
 * Applies risk status styling to card elements
 * @param {jQuery} $statusEl - Status badge element
 * @param {jQuery} $progressEl - Progress bar element
 * @param {jQuery} $cardEl - Card container element
 * @param {{label: string, bgClass: string, borderClass: string}} status - Status object
 */
function applyRiskStatusStyling($statusEl, $progressEl, $cardEl, status) {
    // Clear existing classes
    $cardEl.removeClass(`${CSS_CLASSES.BORDER_SUCCESS} ${CSS_CLASSES.BORDER_WARNING} ${CSS_CLASSES.BORDER_DANGER}`);
    $progressEl.removeClass(`${CSS_CLASSES.BG_SUCCESS} ${CSS_CLASSES.BG_WARNING} ${CSS_CLASSES.BG_DANGER}`);
    $statusEl.removeClass().addClass('badge');

    // Apply new classes
    $statusEl.addClass(status.bgClass).text(status.label);
    $progressEl.addClass(status.bgClass);
    $cardEl.addClass(status.borderClass);
}

/**
 * Generates an array of colors for charts
 * @param {number} count - Number of colors needed
 * @returns {string[]} Array of color strings
 */
function generateColors(count) {
    const colors = [];
    for (let i = 0; i < count; i++) {
        colors.push(SECTOR_COLOR_PALETTE[i % SECTOR_COLOR_PALETTE.length]);
    }
    return colors;
}

/**
 * Formats a Date object to YYYY-MM-DD string
 * @param {Date} date - Date to format
 * @returns {string} Formatted date string
 */
function formatDateForApi(date) {
    return date.getFullYear() + '-' +
        String(date.getMonth() + 1).padStart(2, '0') + '-' +
        String(date.getDate()).padStart(2, '0');
}

/**
 * Builds API URL with optional account ID parameter
 * @param {string} endpoint - API endpoint path
 * @param {Object} [params={}] - Additional query parameters
 * @returns {string} Complete URL with query parameters
 */
function buildApiUrl(endpoint, params = {}) {
    let url = `${API_BASE_URL}${endpoint}`;
    const queryParams = { ...params };

    if (state.currentAccountId) {
        queryParams.accountId = state.currentAccountId;
    }

    const queryString = Object.entries(queryParams)
        .filter(([, value]) => value !== undefined && value !== null)
        .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
        .join('&');

    return queryString ? `${url}?${queryString}` : url;
}

// ==================== Initialization ====================

$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    initializeCharts();
    loadDashboard();
    loadRMultipleAnalysis();
    loadPositionRisks();
    loadSectorExposures();
    loadSettings();
    setupEventHandlers();
});

/**
 * Sets up event handlers for user interactions
 */
function setupEventHandlers() {
    $('#positionSizingForm').on('submit', function(e) {
        e.preventDefault();
        calculatePositionSize();
    });

    $('#settingsModal').on('show.bs.modal', function() {
        populateSettingsForm();
    });
}

// ==================== Dashboard ====================

/**
 * Loads risk dashboard data from API
 */
function loadDashboard() {
    const url = buildApiUrl('/risk/dashboard');

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateDashboardCards(data);
        },
        error: function(xhr) {
            console.error('Failed to load risk dashboard:', xhr);
        }
    });
}

/**
 * Updates all dashboard cards with API data
 * @param {Object} data - Dashboard data from API
 */
function updateDashboardCards(data) {
    updateLimitCard('daily', data.dailyLossStatus, data.todayPnl);
    updateLimitCard('weekly', data.weeklyLossStatus, data.weekPnl);
    updatePositionCountCard(data.positionCountStatus);

    $('#totalOpenRisk').text(formatCurrency(data.totalOpenRisk || 0));
    $('#openRiskPercent').text(formatPercent(data.openRiskPercent || 0));
}

/**
 * Updates a loss limit card (daily or weekly)
 * @param {string} type - Card type ('daily' or 'weekly')
 * @param {Object} status - Loss status object
 * @param {number} pnl - Profit/loss value
 */
function updateLimitCard(type, status, pnl) {
    const $pnlEl = $(`#${type}Pnl`);
    const $statusEl = $(`#${type}LossStatus`);
    const $progressEl = $(`#${type}LossProgress`);
    const $remainingEl = $(`#${type}LossRemaining`);
    const $cardEl = $(`#${type}LossCard`);

    if (!status) {
        $pnlEl.text(formatCurrency(pnl || 0));
        $statusEl.text(STATUS_LABELS.NONE).removeClass().addClass(`badge ${CSS_CLASSES.BG_SECONDARY}`);
        return;
    }

    $pnlEl.text(formatCurrency(pnl || 0));
    applyValueClass($pnlEl, pnl);

    const percentUsed = Math.min(100, Math.abs(status.percentUsed || 0));
    $progressEl.css('width', percentUsed + '%');

    const riskStatus = getRiskStatus(status.isBreached, percentUsed);
    applyRiskStatusStyling($statusEl, $progressEl, $cardEl, riskStatus);

    $remainingEl.text(`한도: ${formatCurrency(status.limit || 0)}`);
}

/**
 * Updates the position count card
 * @param {Object} status - Position count status object
 */
function updatePositionCountCard(status) {
    const $countEl = $('#positionCount');
    const $statusEl = $('#positionCountStatus');
    const $progressEl = $('#positionCountProgress');
    const $remainingEl = $('#positionCountRemaining');
    const $cardEl = $('#positionCountCard');

    if (!status) {
        $countEl.text('0 / -');
        $statusEl.text(STATUS_LABELS.NONE).removeClass().addClass(`badge ${CSS_CLASSES.BG_SECONDARY}`);
        return;
    }

    $countEl.text(`${status.current || 0} / ${status.limit || RISK_THRESHOLDS.DEFAULT_MAX_POSITIONS}`);

    const percentUsed = status.limit > 0 ? ((status.current / status.limit) * 100) : 0;
    $progressEl.css('width', percentUsed + '%');

    const riskStatus = getRiskStatus(status.isBreached, percentUsed);
    applyRiskStatusStyling($statusEl, $progressEl, $cardEl, riskStatus);

    $remainingEl.text(`남은 슬롯: ${(status.limit || 0) - (status.current || 0)}`);
}

// ==================== Position Sizing ====================

/**
 * Calculates recommended position size based on form inputs
 */
function calculatePositionSize() {
    const entryPrice = parseFloat($('#entryPrice').val());
    const stopLossPrice = parseFloat($('#stopLossPrice').val());
    const takeProfitPrice = $('#takeProfitPrice').val() ? parseFloat($('#takeProfitPrice').val()) : null;
    const riskPercent = parseFloat($('#riskPercent').val()) || RISK_THRESHOLDS.DEFAULT_RISK_PERCENT;

    if (!entryPrice || !stopLossPrice) {
        alert('진입가와 손절가를 입력해주세요.');
        return;
    }

    if (entryPrice === stopLossPrice) {
        alert('진입가와 손절가가 같을 수 없습니다.');
        return;
    }

    const request = {
        entryPrice,
        stopLossPrice,
        takeProfitPrice,
        riskPercent,
        method: 'FIXED_FRACTIONAL'
    };

    if (state.currentAccountId) {
        request.accountId = state.currentAccountId;
    }

    $.ajax({
        url: `${API_BASE_URL}/risk/position-size`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(result) {
            displayPositionSizingResult(result);
        },
        error: function(xhr) {
            alert('계산 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

/**
 * Displays position sizing calculation results
 * @param {Object} result - Position sizing result from API
 */
function displayPositionSizingResult(result) {
    $('#sizingResult').removeClass('d-none');
    $('#scenariosCard').removeClass('d-none');

    $('#recommendedQty').text(formatNumber(result.recommendedQuantity || 0));
    $('#positionValue').text(formatCurrency(result.recommendedPositionValue || 0));
    $('#rrRatio').text(result.riskRewardRatio ? '1:' + result.riskRewardRatio.toFixed(2) : '-');
    $('#potentialLoss').text(formatCurrency(result.potentialLoss || 0));
    $('#potentialProfit').text(result.potentialProfit ? formatCurrency(result.potentialProfit) : '-');

    displayKellyInfo(result);
    displaySizingWarning(result.warningMessage);
    displayScenarios(result.scenarios);
}

/**
 * Displays Kelly Criterion information
 * @param {Object} result - Position sizing result containing Kelly data
 */
function displayKellyInfo(result) {
    if (result.kellyPercentage && result.kellyPercentage > 0) {
        $('#kellyInfo').html(`
            <i class="bi bi-info-circle me-1"></i>
            Kelly Criterion: ${result.kellyPercentage.toFixed(2)}%
            (Full: ${formatNumber(result.fullKellyQuantity)}주,
            Half: ${formatNumber(result.halfKellyQuantity)}주,
            Quarter: ${formatNumber(result.quarterKellyQuantity)}주)
        `);
    } else {
        $('#kellyInfo').html('');
    }
}

/**
 * Displays position sizing warning message
 * @param {string} message - Warning message to display
 */
function displaySizingWarning(message) {
    const $warningEl = $('#sizingWarning');
    if (message) {
        $warningEl.text(message).removeClass('d-none');
    } else {
        $warningEl.addClass('d-none');
    }
}

/**
 * Displays position sizing scenarios in table
 * @param {Array} scenarios - Array of scenario objects
 */
function displayScenarios(scenarios) {
    if (!scenarios || scenarios.length === 0) {
        return;
    }

    const $tbody = $('#scenariosBody');
    $tbody.empty();

    scenarios.forEach(scenario => {
        $tbody.append(`
            <tr>
                <td>${scenario.name}</td>
                <td class="text-end">${formatNumber(scenario.quantity)}</td>
                <td class="text-end">${formatCurrency(scenario.positionValue)}</td>
                <td class="text-end ${CSS_CLASSES.DANGER}">${formatCurrency(scenario.potentialLoss)}</td>
            </tr>
        `);
    });
}

// ==================== R-Multiple Analysis ====================

/**
 * Loads R-multiple analysis data from API
 */
function loadRMultipleAnalysis() {
    const months = parseInt($('#rMultiplePeriod').val()) || 3;
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - months);

    const url = buildApiUrl('/risk/r-multiple', {
        startDate: formatDateForApi(startDate),
        endDate: formatDateForApi(endDate)
    });

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateRMultipleDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load R-multiple analysis:', xhr);
        }
    });
}

/**
 * Updates R-multiple analysis display
 * @param {Object} data - R-multiple analysis data from API
 */
function updateRMultipleDisplay(data) {
    $('#avgRMultiple').text((data.averageRMultiple || 0).toFixed(2) + 'R');
    $('#expectancy').text((data.expectancy || 0).toFixed(2));
    $('#positiveRCount').text(data.tradesWithPositiveR || 0);
    $('#negativeRCount').text(data.tradesWithNegativeR || 0);

    if (data.distribution && data.distribution.length > 0) {
        updateRMultipleChart(data.distribution);
    }
}

/**
 * Updates R-multiple distribution chart
 * @param {Array} distribution - Distribution data array
 */
function updateRMultipleChart(distribution) {
    const labels = distribution.map(d => d.range);
    const values = distribution.map(d => d.count);
    const colors = distribution.map(d =>
        d.range.includes('-') ? CHART_COLORS.NEGATIVE : CHART_COLORS.POSITIVE
    );

    if (state.charts.rMultiple) {
        state.charts.rMultiple.data.labels = labels;
        state.charts.rMultiple.data.datasets[0].data = values;
        state.charts.rMultiple.data.datasets[0].backgroundColor = colors;
        state.charts.rMultiple.update();
    }
}

// ==================== Sector Exposure ====================

/**
 * Loads sector exposure data from API
 */
function loadSectorExposures() {
    const url = buildApiUrl('/risk/sector-exposure');

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateSectorDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load sector exposure:', xhr);
        }
    });
}

/**
 * Updates sector exposure display and chart
 * @param {Array} exposures - Array of sector exposure objects
 */
function updateSectorDisplay(exposures) {
    if (!exposures || exposures.length === 0) {
        $('#sectorList').html('<p class="text-muted">노출된 섹터가 없습니다.</p>');
        return;
    }

    const labels = exposures.map(e => e.sectorLabel || e.sector);
    const values = exposures.map(e => e.percentage);
    const colors = generateColors(exposures.length);

    if (state.charts.sector) {
        state.charts.sector.data.labels = labels;
        state.charts.sector.data.datasets[0].data = values;
        state.charts.sector.data.datasets[0].backgroundColor = colors;
        state.charts.sector.update();
    }

    updateSectorList(exposures, colors);
}

/**
 * Updates the sector list display
 * @param {Array} exposures - Array of sector exposure objects
 * @param {Array} colors - Array of color strings for each sector
 */
function updateSectorList(exposures, colors) {
    let html = '';
    exposures.forEach((e, i) => {
        const exceedClass = e.exceedsLimit ? `${CSS_CLASSES.DANGER} fw-bold` : '';
        html += `
            <div class="d-flex justify-content-between align-items-center mb-2">
                <div>
                    <span class="badge me-2" style="background-color: ${colors[i]}; width: 12px; height: 12px; display: inline-block; border-radius: 2px;"></span>
                    ${e.sectorLabel || e.sector}
                </div>
                <span class="${exceedClass}">${(e.percentage || 0).toFixed(1)}%</span>
            </div>
        `;
    });
    $('#sectorList').html(html);
}

// ==================== Position Risks ====================

/**
 * Loads position risk data from API
 */
function loadPositionRisks() {
    const url = buildApiUrl('/risk/positions');

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updatePositionRisksTable(data);
        },
        error: function(xhr) {
            console.error('Failed to load position risks:', xhr);
            $('#positionRisksBody').html(`
                <tr><td colspan="8" class="text-center text-muted py-4">데이터를 불러올 수 없습니다.</td></tr>
            `);
        }
    });
}

/**
 * Updates position risks table
 * @param {Array} positions - Array of position risk objects
 */
function updatePositionRisksTable(positions) {
    const $tbody = $('#positionRisksBody');

    if (!positions || positions.length === 0) {
        $tbody.html(`
            <tr><td colspan="8" class="text-center text-muted py-4">오픈 포지션이 없습니다.</td></tr>
        `);
        return;
    }

    const html = positions.map(pos => buildPositionRow(pos)).join('');
    $tbody.html(html);
}

/**
 * Builds a single position row HTML
 * @param {Object} pos - Position object
 * @returns {string} HTML string for table row
 */
function buildPositionRow(pos) {
    const pnlClass = (pos.unrealizedPnl || 0) >= 0 ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;
    const rClass = (pos.currentR || 0) >= 0 ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;
    const rDisplay = pos.currentR !== null
        ? (pos.currentR >= 0 ? '+' : '') + pos.currentR.toFixed(2) + 'R'
        : '-';

    return `
        <tr>
            <td>
                <strong>${pos.stockSymbol}</strong>
                <small class="text-muted d-block">${pos.stockName || ''}</small>
            </td>
            <td class="text-end">${formatNumber(pos.quantity || 0)}</td>
            <td class="text-end">${formatCurrency(pos.entryPrice || 0)}</td>
            <td class="text-end">${formatCurrency(pos.currentPrice || 0)}</td>
            <td class="text-end">${pos.stopLossPrice ? formatCurrency(pos.stopLossPrice) : '-'}</td>
            <td class="text-end ${pnlClass}">
                ${formatCurrency(pos.unrealizedPnl || 0)}
                <small class="d-block">(${(pos.unrealizedPnlPercent || 0).toFixed(2)}%)</small>
            </td>
            <td class="text-end ${CSS_CLASSES.DANGER}">${pos.riskAmount ? formatCurrency(pos.riskAmount) : '-'}</td>
            <td class="text-end ${rClass}">${rDisplay}</td>
        </tr>
    `;
}

// ==================== Settings ====================

/**
 * Loads risk settings from API
 */
function loadSettings() {
    const url = state.currentAccountId
        ? `${API_BASE_URL}/risk/settings/${state.currentAccountId}`
        : `${API_BASE_URL}/risk/settings`;

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            state.riskSettings = data;
        },
        error: function(xhr) {
            console.error('Failed to load risk settings:', xhr);
        }
    });
}

/**
 * Populates the settings form with current values
 */
function populateSettingsForm() {
    if (!state.riskSettings) {
        return;
    }

    const s = state.riskSettings;
    $('#settingCapital').val(s.accountCapital || '');
    $('#settingMaxRiskPerTrade').val(s.maxRiskPerTradePercent || RISK_THRESHOLDS.DEFAULT_RISK_PERCENT);
    $('#settingMaxDailyLoss').val(s.maxDailyLossPercent || RISK_THRESHOLDS.DEFAULT_DAILY_LOSS);
    $('#settingMaxWeeklyLoss').val(s.maxWeeklyLossPercent || RISK_THRESHOLDS.DEFAULT_WEEKLY_LOSS);
    $('#settingMaxPositions').val(s.maxOpenPositions || RISK_THRESHOLDS.DEFAULT_MAX_POSITIONS);
    $('#settingMaxPositionSize').val(s.maxPositionSizePercent || RISK_THRESHOLDS.DEFAULT_POSITION_SIZE);
    $('#settingMaxSectorConc').val(s.maxSectorConcentrationPercent || RISK_THRESHOLDS.DEFAULT_SECTOR_CONCENTRATION);
    $('#settingKellyFraction').val(s.kellyFraction || RISK_THRESHOLDS.DEFAULT_KELLY_FRACTION);
}

/**
 * Saves risk settings to API
 */
function saveSettings() {
    const settings = {
        accountId: state.currentAccountId,
        accountCapital: parseFloat($('#settingCapital').val()) || null,
        maxRiskPerTradePercent: parseFloat($('#settingMaxRiskPerTrade').val()) || RISK_THRESHOLDS.DEFAULT_RISK_PERCENT,
        maxDailyLossPercent: parseFloat($('#settingMaxDailyLoss').val()) || RISK_THRESHOLDS.DEFAULT_DAILY_LOSS,
        maxWeeklyLossPercent: parseFloat($('#settingMaxWeeklyLoss').val()) || RISK_THRESHOLDS.DEFAULT_WEEKLY_LOSS,
        maxOpenPositions: parseInt($('#settingMaxPositions').val()) || RISK_THRESHOLDS.DEFAULT_MAX_POSITIONS,
        maxPositionSizePercent: parseFloat($('#settingMaxPositionSize').val()) || RISK_THRESHOLDS.DEFAULT_POSITION_SIZE,
        maxSectorConcentrationPercent: parseFloat($('#settingMaxSectorConc').val()) || RISK_THRESHOLDS.DEFAULT_SECTOR_CONCENTRATION,
        maxStockConcentrationPercent: parseFloat($('#settingMaxPositionSize').val()) || RISK_THRESHOLDS.DEFAULT_POSITION_SIZE,
        kellyFraction: parseFloat($('#settingKellyFraction').val()) || RISK_THRESHOLDS.DEFAULT_KELLY_FRACTION
    };

    const hasExistingSettings = state.riskSettings && state.riskSettings.id;
    const url = hasExistingSettings
        ? `${API_BASE_URL}/risk/settings/${state.riskSettings.accountId || 1}`
        : `${API_BASE_URL}/risk/settings`;
    const method = hasExistingSettings ? 'PUT' : 'POST';

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        data: JSON.stringify(settings),
        success: function(data) {
            state.riskSettings = data;
            $('#settingsModal').modal('hide');
            loadDashboard();
            alert('설정이 저장되었습니다.');
        },
        error: function(xhr) {
            alert('설정 저장 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// ==================== Charts ====================

/**
 * Initializes all charts
 */
function initializeCharts() {
    initializeRMultipleChart();
    initializeSectorChart();
}

/**
 * Initializes the R-Multiple histogram chart
 */
function initializeRMultipleChart() {
    const ctx = document.getElementById('rMultipleChart');
    if (!ctx) return;

    state.charts.rMultiple = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: '거래 수',
                data: [],
                backgroundColor: CHART_COLORS.BLUE,
                borderColor: CHART_COLORS.BLUE_SOLID,
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { stepSize: 1 }
                }
            }
        }
    });
}

/**
 * Initializes the sector exposure doughnut chart
 */
function initializeSectorChart() {
    const ctx = document.getElementById('sectorChart');
    if (!ctx) return;

    state.charts.sector = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: [],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            }
        }
    });
}
