/**
 * Trading Pattern Analysis JavaScript
 * Provides analysis of trading patterns including streak analysis, day of week performance,
 * monthly seasonality, holding period, and trade size distribution.
 *
 * @fileoverview Pattern analysis module for trading journal
 * @requires utils.js - formatCurrency, formatDate, getDateRangeForApi
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Chart color palette for consistent styling
 * @constant {Object}
 */
const CHART_COLORS = {
    PRIMARY: 'rgba(102, 126, 234, 0.7)',
    PRIMARY_SOLID: '#667eea',
    SECONDARY: 'rgba(108, 117, 125, 0.5)',
    SUCCESS: '#28a745',
    TRANSPARENT: 'transparent',
    DOUGHNUT_PALETTE: [
        '#667eea', '#764ba2', '#f093fb',
        '#f5576c', '#4facfe', '#00f2fe'
    ]
};

/**
 * Common chart configuration options
 * @constant {Object}
 */
const CHART_OPTIONS = {
    RESPONSIVE: {
        responsive: true,
        maintainAspectRatio: false
    },
    DUAL_AXIS_WIN_RATE: {
        y: {
            type: 'linear',
            position: 'left',
            title: { display: true, text: '승률 (%)' },
            min: 0,
            max: 100
        },
        y1: {
            type: 'linear',
            position: 'right',
            title: { display: true, text: '수익률 (%)' },
            grid: { drawOnChartArea: false }
        }
    },
    DUAL_AXIS_TRADE_COUNT: {
        y: {
            type: 'linear',
            position: 'left',
            title: { display: true, text: '거래 수' }
        },
        y1: {
            type: 'linear',
            position: 'right',
            title: { display: true, text: '승률 (%)' },
            min: 0,
            max: 100,
            grid: { drawOnChartArea: false }
        }
    },
    LEGEND_BOTTOM: {
        position: 'bottom',
        labels: { boxWidth: 12, font: { size: 10 } }
    }
};

/**
 * CSS class names for streak styling
 * @constant {Object}
 */
const STREAK_CLASSES = {
    WIN: 'streak-win',
    LOSS: 'streak-loss',
    POSITIVE: 'streak-positive',
    NEGATIVE: 'streak-negative'
};

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Application state object containing chart instances and cached data
 * @type {Object}
 */
const patternState = {
    /** @type {Object.<string, Chart>} Chart instances keyed by name */
    charts: {
        dayOfWeek: null,
        monthly: null,
        holdingPeriod: null,
        tradeSize: null
    },
    /** @type {Object|null} Cached analysis data */
    cachedData: null
};

// ============================================================================
// INITIALIZATION
// ============================================================================

$(document).ready(function() {
    init();
});

/**
 * Initialize the pattern analysis page.
 * Sets default date range and binds event handlers.
 */
function init() {
    setDefaultDateRange();
    bindEvents();
    loadPatternAnalysis();
}

/**
 * Set default date range to last 1 year.
 * Uses getDateRangeForApi from utils.js for consistency.
 */
function setDefaultDateRange() {
    const { startDate, endDate } = getDateRangeForApi('1Y');
    $('#startDate').val(startDate);
    $('#endDate').val(endDate);
}

/**
 * Bind event handlers for user interactions.
 */
function bindEvents() {
    $('#btnAnalyze').click(loadPatternAnalysis);
}

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load pattern analysis data from API.
 * Fetches analysis for the selected date range and updates all components.
 */
function loadPatternAnalysis() {
    const startDate = $('#startDate').val();
    const endDate = $('#endDate').val();

    if (!startDate || !endDate) {
        ToastNotification.warning('날짜를 선택해주세요');
        return;
    }

    $.get('/api/analysis/patterns', { startDate, endDate })
        .done(handlePatternDataSuccess)
        .fail(handlePatternDataError);
}

/**
 * Handle successful pattern data response.
 * Updates all UI components with the received data.
 * @param {Object} data - The pattern analysis data from API
 */
function handlePatternDataSuccess(data) {
    patternState.cachedData = data;

    updateSummaryCards(data);
    updateStreakTimeline(data.streakAnalysis);
    renderDayOfWeekChart(data.dayOfWeekPerformance);
    renderMonthlyChart(data.monthlySeasonality);
    updateHoldingPeriodStats(data.holdingPeriodAnalysis);
    renderHoldingPeriodChart(data.holdingPeriodAnalysis);
    updateTradeSizeStats(data.tradeSizeAnalysis);
    renderTradeSizeChart(data.tradeSizeAnalysis);
    renderTradeSizePerformance(data.tradeSizeAnalysis);
}

/**
 * Handle pattern data load error.
 * Logs error and shows user notification.
 * @param {Object} err - The error object from jQuery AJAX
 */
function handlePatternDataError(err) {
    console.error('패턴 분석 로드 실패:', err);
    ToastNotification.error('패턴 분석을 불러오는데 실패했습니다');
}

// ============================================================================
// SUMMARY CARDS
// ============================================================================

/**
 * Update summary cards with analysis data.
 * @param {Object} data - Pattern analysis data containing totalTrades and streakAnalysis
 */
function updateSummaryCards(data) {
    $('#totalTrades').text(data.totalTrades || 0);

    const streak = data.streakAnalysis;
    if (streak) {
        updateCurrentStreakDisplay(streak.currentStreak || 0);
        $('#maxWinStreak').text(streak.maxWinStreak || 0);
        $('#maxLossStreak').text(streak.maxLossStreak || 0);
    }
}

/**
 * Update the current streak display with appropriate styling.
 * @param {number} currentStreak - Current streak value (positive for wins, negative for losses)
 */
function updateCurrentStreakDisplay(currentStreak) {
    let streakText = '0';

    if (currentStreak > 0) {
        streakText = '+' + currentStreak + ' 연승';
    } else if (currentStreak < 0) {
        streakText = Math.abs(currentStreak) + ' 연패';
    }

    const streakClass = currentStreak > 0
        ? STREAK_CLASSES.POSITIVE
        : currentStreak < 0 ? STREAK_CLASSES.NEGATIVE : '';

    $('#currentStreak')
        .text(streakText)
        .removeClass(`${STREAK_CLASSES.POSITIVE} ${STREAK_CLASSES.NEGATIVE}`)
        .addClass(streakClass);
}

// ============================================================================
// STREAK TIMELINE
// ============================================================================

/**
 * Update the streak timeline visualization.
 * Displays recent streaks as colored blocks.
 * @param {Object} streakAnalysis - Streak analysis data containing recentStreaks array
 */
function updateStreakTimeline(streakAnalysis) {
    const $container = $('#streakTimeline');
    $container.empty();

    if (!streakAnalysis?.recentStreaks?.length) {
        $container.html('<div class="text-muted">스트릭 데이터가 없습니다</div>');
        return;
    }

    streakAnalysis.recentStreaks.forEach(function(streak) {
        const $block = createStreakBlock(streak);
        $container.append($block);
    });
}

/**
 * Create a streak block element for the timeline.
 * @param {Object} streak - Single streak data object
 * @param {boolean} streak.isWinStreak - Whether this is a winning streak
 * @param {boolean} streak.winStreak - Alternative property for win streak
 * @param {string} streak.startDate - Streak start date
 * @param {string} streak.endDate - Streak end date
 * @param {number} streak.totalPnl - Total P&L for the streak
 * @param {number} streak.length - Number of consecutive trades
 * @returns {jQuery} The streak block jQuery element
 */
function createStreakBlock(streak) {
    const isWin = streak.isWinStreak || streak.winStreak;
    const blockClass = isWin ? STREAK_CLASSES.WIN : STREAK_CLASSES.LOSS;
    const tooltipText = `${formatDate(streak.startDate)} ~ ${formatDate(streak.endDate)}\n${formatCurrency(streak.totalPnl)}`;

    return $('<div class="streak-block"></div>')
        .addClass(blockClass)
        .attr('title', tooltipText)
        .text(streak.length);
}

// ============================================================================
// DAY OF WEEK CHART
// ============================================================================

/**
 * Render the day of week performance chart.
 * Shows win rate as bars and average return as a line.
 * @param {Array} dayOfWeekData - Array of day of week performance data
 */
function renderDayOfWeekChart(dayOfWeekData) {
    if (!dayOfWeekData?.length) return;

    destroyChart('dayOfWeek');

    const ctx = document.getElementById('dayOfWeekChart');
    const chartData = extractDayOfWeekChartData(dayOfWeekData);

    patternState.charts.dayOfWeek = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: chartData.labels,
            datasets: [
                {
                    label: '승률 (%)',
                    data: chartData.winRates,
                    backgroundColor: CHART_COLORS.PRIMARY,
                    yAxisID: 'y'
                },
                {
                    label: '평균 수익률 (%)',
                    data: chartData.avgReturns,
                    type: 'line',
                    borderColor: CHART_COLORS.SUCCESS,
                    backgroundColor: CHART_COLORS.TRANSPARENT,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
            ...CHART_OPTIONS.RESPONSIVE,
            scales: CHART_OPTIONS.DUAL_AXIS_WIN_RATE
        }
    });
}

/**
 * Extract chart data from day of week performance data.
 * @param {Array} data - Raw day of week data
 * @returns {Object} Extracted labels, winRates, and avgReturns arrays
 */
function extractDayOfWeekChartData(data) {
    return {
        labels: data.map(d => d.dayOfWeekLabel),
        winRates: data.map(d => d.winRate || 0),
        avgReturns: data.map(d => d.avgReturn || 0)
    };
}

// ============================================================================
// MONTHLY CHART
// ============================================================================

/**
 * Render the monthly seasonality chart.
 * Shows trade count as bars and win rate as a line.
 * @param {Array} monthlyData - Array of monthly seasonality data
 */
function renderMonthlyChart(monthlyData) {
    if (!monthlyData?.length) return;

    destroyChart('monthly');

    const ctx = document.getElementById('monthlyChart');
    const chartData = extractMonthlyChartData(monthlyData);

    patternState.charts.monthly = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: chartData.labels,
            datasets: [
                {
                    label: '거래 수',
                    data: chartData.tradeCounts,
                    backgroundColor: CHART_COLORS.SECONDARY,
                    yAxisID: 'y'
                },
                {
                    label: '승률 (%)',
                    data: chartData.winRates,
                    type: 'line',
                    borderColor: CHART_COLORS.PRIMARY_SOLID,
                    backgroundColor: CHART_COLORS.TRANSPARENT,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
            ...CHART_OPTIONS.RESPONSIVE,
            scales: CHART_OPTIONS.DUAL_AXIS_TRADE_COUNT
        }
    });
}

/**
 * Extract chart data from monthly seasonality data.
 * @param {Array} data - Raw monthly data
 * @returns {Object} Extracted labels, tradeCounts, and winRates arrays
 */
function extractMonthlyChartData(data) {
    return {
        labels: data.map(m => m.monthLabel),
        tradeCounts: data.map(m => m.tradeCount || 0),
        winRates: data.map(m => m.winRate || 0)
    };
}

// ============================================================================
// HOLDING PERIOD ANALYSIS
// ============================================================================

/**
 * Update holding period statistics display.
 * @param {Object} holdingData - Holding period analysis data
 */
function updateHoldingPeriodStats(holdingData) {
    if (!holdingData) return;

    $('#avgHoldingDays').text((holdingData.avgHoldingDays || 0) + ' 일');
    $('#avgWinHoldingDays').text((holdingData.avgWinHoldingDays || 0) + ' 일');
    $('#avgLossHoldingDays').text((holdingData.avgLossHoldingDays || 0) + ' 일');
}

/**
 * Render the holding period distribution doughnut chart.
 * @param {Object} holdingData - Holding period analysis data with distribution array
 */
function renderHoldingPeriodChart(holdingData) {
    if (!holdingData?.distribution) return;

    destroyChart('holdingPeriod');

    const ctx = document.getElementById('holdingPeriodChart');
    const distribution = holdingData.distribution;

    patternState.charts.holdingPeriod = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: distribution.map(d => d.label),
            datasets: [{
                data: distribution.map(d => d.count || 0),
                backgroundColor: CHART_COLORS.DOUGHNUT_PALETTE
            }]
        },
        options: {
            ...CHART_OPTIONS.RESPONSIVE,
            plugins: {
                legend: CHART_OPTIONS.LEGEND_BOTTOM
            }
        }
    });
}

// ============================================================================
// TRADE SIZE ANALYSIS
// ============================================================================

/**
 * Update trade size statistics display.
 * @param {Object} tradeSizeData - Trade size analysis data
 */
function updateTradeSizeStats(tradeSizeData) {
    if (!tradeSizeData) return;

    $('#avgTradeAmount').text(formatCurrency(tradeSizeData.avgTradeAmount));
    $('#stdDeviation').text(formatCurrency(tradeSizeData.stdDeviation));
}

/**
 * Render the trade size distribution bar chart.
 * @param {Object} tradeSizeData - Trade size analysis data with distribution array
 */
function renderTradeSizeChart(tradeSizeData) {
    if (!tradeSizeData?.distribution) return;

    destroyChart('tradeSize');

    const ctx = document.getElementById('tradeSizeChart');
    const distribution = tradeSizeData.distribution;

    patternState.charts.tradeSize = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: distribution.map(d => d.label),
            datasets: [{
                label: '비중 (%)',
                data: distribution.map(d => d.percentage || 0),
                backgroundColor: CHART_COLORS.PRIMARY
            }]
        },
        options: {
            ...CHART_OPTIONS.RESPONSIVE,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: '비중 (%)' }
                }
            }
        }
    });
}

/**
 * Render trade size performance progress bars.
 * Shows win rate for each trade size bucket.
 * @param {Object} tradeSizeData - Trade size analysis data with distribution array
 */
function renderTradeSizePerformance(tradeSizeData) {
    const $container = $('#tradeSizePerformance');
    $container.empty();

    if (!tradeSizeData?.distribution) {
        $container.html('<div class="text-muted">데이터가 없습니다</div>');
        return;
    }

    tradeSizeData.distribution.forEach(function(bucket) {
        const $progressItem = createTradeSizeProgressItem(bucket);
        $container.append($progressItem);
    });
}

/**
 * Create a progress bar item for trade size performance.
 * @param {Object} bucket - Trade size bucket data
 * @param {string} bucket.label - Bucket label
 * @param {number} bucket.count - Number of trades
 * @param {number} bucket.winRate - Win rate percentage
 * @returns {string} HTML string for the progress item
 */
function createTradeSizeProgressItem(bucket) {
    const winRate = bucket.winRate || 0;
    const barColor = winRate >= 50 ? 'bg-success' : 'bg-danger';

    return `
        <div class="mb-3">
            <div class="progress-label">
                <span>${bucket.label}</span>
                <span>${bucket.count}건 (${winRate.toFixed(1)}%)</span>
            </div>
            <div class="progress" style="height: 8px;">
                <div class="progress-bar ${barColor}" style="width: ${winRate}%"></div>
            </div>
        </div>
    `;
}

// ============================================================================
// CHART UTILITIES
// ============================================================================

/**
 * Safely destroy a chart instance if it exists.
 * @param {string} chartName - Name of the chart in patternState.charts
 */
function destroyChart(chartName) {
    if (patternState.charts[chartName]) {
        patternState.charts[chartName].destroy();
        patternState.charts[chartName] = null;
    }
}
