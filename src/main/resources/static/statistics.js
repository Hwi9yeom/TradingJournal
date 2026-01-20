/**
 * Trading Statistics Analysis Module
 * Displays trading performance statistics including weekday analysis,
 * time-of-day patterns, symbol rankings, and improvement suggestions.
 *
 * @fileoverview Handles loading and rendering of trading statistics data
 * @requires utils.js - For getDateRangeForApi, formatCurrency, formatPercent, applyThresholdClass
 * @requires auth.js - For fetchWithAuth
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/** @constant {string} Base URL for statistics API endpoints */
const API_BASE_URL = '/api/statistics';

/**
 * Chart color palette for consistent styling across charts
 * @constant {Object}
 */
const CHART_COLORS = {
    // Win rate colors
    WIN_RATE_POSITIVE: 'rgba(40, 167, 69, 0.7)',
    WIN_RATE_NEGATIVE: 'rgba(220, 53, 69, 0.7)',
    // Time chart colors
    TIME_WIN_POSITIVE: 'rgba(17, 153, 142, 0.7)',
    TIME_WIN_NEGATIVE: 'rgba(245, 87, 108, 0.7)',
    // Line colors
    PROFIT_LINE: '#667eea',
    PROFIT_FILL: 'rgba(102, 126, 234, 0.1)',
    TRADE_COUNT_LINE: '#f5576c'
};

/**
 * Threshold values for styling statistics
 * @constant {Object}
 */
const THRESHOLDS = {
    WIN_RATE_POSITIVE: 50,
    CONSISTENCY_HIGH: 70,
    CONSISTENCY_MEDIUM: 50
};

/**
 * CSS class names for statistics styling
 * @constant {Object}
 */
const STAT_CLASSES = {
    POSITIVE: 'stat-positive',
    NEGATIVE: 'stat-negative',
    NEUTRAL: 'stat-neutral'
};

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Application state for managing chart instances and current period
 * @type {Object}
 */
const state = {
    /** @type {Chart|null} Chart.js instance for weekday analysis */
    weekdayChart: null,
    /** @type {Chart|null} Chart.js instance for time-of-day analysis */
    timeChart: null,
    /** @type {string} Currently selected period filter */
    currentPeriod: '3M'
};

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', function() {
    initPeriodButtons();
    loadStatistics();
});

/**
 * Initialize period filter buttons with click handlers.
 * Updates active state and reloads statistics when period changes.
 */
function initPeriodButtons() {
    document.querySelectorAll('.period-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            state.currentPeriod = this.dataset.period;
            loadStatistics();
        });
    });
}

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load statistics data from API for the current period.
 * Renders all statistics sections on success, shows empty state on failure.
 * @async
 */
async function loadStatistics() {
    const { startDate, endDate } = getDateRangeForApi(state.currentPeriod);

    try {
        const response = await fetchWithAuth(`${API_BASE_URL}?startDate=${startDate}&endDate=${endDate}`);

        if (response.ok) {
            const data = await response.json();
            renderSummary(data.overallSummary);
            renderWeekdayChart(data.weekdayStats);
            renderTimeChart(data.timeOfDayStats);
            renderSymbolRanking(data.symbolStats);
            renderMistakePatterns(data.mistakePatterns);
            renderSuggestions(data.suggestions);
        } else {
            console.error('Failed to load statistics data');
            showEmptyStatisticsState();
        }
    } catch (error) {
        console.error('API call error:', error);
        showEmptyStatisticsState();
    }
}

// ============================================================================
// RENDERING FUNCTIONS
// ============================================================================

/**
 * Render summary statistics cards.
 * Displays total trades, win rate, profit, best day/time, and consistency score.
 * @param {Object|null} summary - Summary statistics data
 * @param {number} [summary.totalTrades] - Total number of trades
 * @param {number} [summary.overallWinRate] - Overall win rate percentage
 * @param {number} [summary.totalProfit] - Total profit/loss amount
 * @param {string} [summary.bestDay] - Best performing day of week
 * @param {string} [summary.bestTimeSlot] - Best performing time slot
 * @param {number} [summary.consistencyScore] - Consistency score (0-100)
 */
function renderSummary(summary) {
    if (!summary) {
        showEmptyStatisticsState();
        return;
    }

    document.getElementById('total-trades').textContent = summary.totalTrades || 0;

    // Win rate with conditional styling
    const winRate = summary.overallWinRate || 0;
    const winRateEl = document.getElementById('win-rate');
    winRateEl.textContent = winRate.toFixed(1) + '%';
    winRateEl.className = 'stat-value ' + getValueClass(winRate, THRESHOLDS.WIN_RATE_POSITIVE);

    // Total profit with conditional styling
    const profit = summary.totalProfit || 0;
    const profitEl = document.getElementById('total-profit');
    profitEl.textContent = formatStatCurrency(profit);
    profitEl.className = 'stat-value ' + getValueClass(profit, 0);

    document.getElementById('best-day').textContent = summary.bestDay || '-';
    document.getElementById('best-time').textContent = summary.bestTimeSlot || '-';

    // Consistency score with three-tier styling
    const consistency = summary.consistencyScore || 0;
    const consistencyEl = document.getElementById('consistency-score');
    consistencyEl.textContent = consistency.toFixed(0) + '점';
    consistencyEl.className = 'stat-value ' + getConsistencyClass(consistency);
}

/**
 * Render weekday performance chart.
 * Bar chart showing win rate by day with profit line overlay.
 * @param {Array<Object>} weekdayStats - Array of weekday statistics
 * @param {string} weekdayStats[].dayName - Name of the day
 * @param {number} weekdayStats[].winRate - Win rate for the day
 * @param {number} weekdayStats[].totalProfit - Total profit for the day
 */
function renderWeekdayChart(weekdayStats) {
    const ctx = document.getElementById('weekday-chart').getContext('2d');

    if (state.weekdayChart) {
        state.weekdayChart.destroy();
    }

    const labels = weekdayStats.map(s => s.dayName);
    const winRates = weekdayStats.map(s => s.winRate || 0);
    const profits = weekdayStats.map(s => s.totalProfit || 0);

    state.weekdayChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: '승률 (%)',
                    data: winRates,
                    backgroundColor: winRates.map(r =>
                        r >= THRESHOLDS.WIN_RATE_POSITIVE
                            ? CHART_COLORS.WIN_RATE_POSITIVE
                            : CHART_COLORS.WIN_RATE_NEGATIVE
                    ),
                    borderRadius: 4,
                    yAxisID: 'y'
                },
                {
                    label: '손익',
                    data: profits,
                    type: 'line',
                    borderColor: CHART_COLORS.PROFIT_LINE,
                    backgroundColor: CHART_COLORS.PROFIT_FILL,
                    fill: true,
                    tension: 0.4,
                    yAxisID: 'y1'
                }
            ]
        },
        options: createDualAxisChartOptions('승률 (%)', '손익', formatStatCurrencyShort)
    });
}

/**
 * Render time-of-day performance chart.
 * Bar chart showing win rate by time period with trade count line overlay.
 * @param {Array<Object>} timeStats - Array of time period statistics
 * @param {string} timeStats[].timePeriod - Time period label
 * @param {number} timeStats[].winRate - Win rate for the period
 * @param {number} timeStats[].totalTrades - Number of trades in the period
 */
function renderTimeChart(timeStats) {
    const ctx = document.getElementById('time-chart').getContext('2d');

    if (state.timeChart) {
        state.timeChart.destroy();
    }

    const labels = timeStats.map(s => s.timePeriod);
    const winRates = timeStats.map(s => s.winRate || 0);
    const trades = timeStats.map(s => s.totalTrades || 0);

    state.timeChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: '승률 (%)',
                    data: winRates,
                    backgroundColor: winRates.map(r =>
                        r >= THRESHOLDS.WIN_RATE_POSITIVE
                            ? CHART_COLORS.TIME_WIN_POSITIVE
                            : CHART_COLORS.TIME_WIN_NEGATIVE
                    ),
                    borderRadius: 4,
                    yAxisID: 'y'
                },
                {
                    label: '거래 수',
                    data: trades,
                    type: 'line',
                    borderColor: CHART_COLORS.TRADE_COUNT_LINE,
                    pointBackgroundColor: CHART_COLORS.TRADE_COUNT_LINE,
                    tension: 0.4,
                    yAxisID: 'y1'
                }
            ]
        },
        options: createTimeChartOptions()
    });
}

/**
 * Render symbol performance ranking list.
 * Displays top 10 symbols sorted by profitability.
 * @param {Array<Object>} symbolStats - Array of symbol statistics
 * @param {string} symbolStats[].symbol - Symbol/ticker
 * @param {string} [symbolStats[].stockName] - Full stock name
 * @param {number} symbolStats[].totalProfit - Total profit for symbol
 * @param {number} symbolStats[].winRate - Win rate for symbol
 * @param {number} symbolStats[].totalTrades - Number of trades
 */
function renderSymbolRanking(symbolStats) {
    const container = document.getElementById('symbol-ranking');

    if (!symbolStats || symbolStats.length === 0) {
        container.innerHTML = '<div class="text-center text-muted py-4">거래 데이터가 없습니다.</div>';
        return;
    }

    const html = symbolStats.slice(0, 10).map((stat, index) => {
        const rank = index + 1;
        const rankClass = getRankClass(rank);
        const profitClass = stat.totalProfit >= 0 ? STAT_CLASSES.POSITIVE : STAT_CLASSES.NEGATIVE;

        return `
            <div class="symbol-rank">
                <div class="rank-badge ${rankClass}">${rank}</div>
                <div class="flex-grow-1">
                    <div class="fw-bold">${stat.symbol}</div>
                    <small class="text-muted">${stat.stockName || stat.symbol}</small>
                </div>
                <div class="text-end">
                    <div class="${profitClass} fw-bold">${formatStatCurrency(stat.totalProfit)}</div>
                    <small class="text-muted">승률 ${stat.winRate.toFixed(1)}% | ${stat.totalTrades}건</small>
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML = html;
}

/**
 * Render mistake patterns section.
 * Displays identified trading mistakes with severity and impact.
 * @param {Array<Object>} patterns - Array of mistake patterns
 * @param {string} patterns[].description - Pattern description
 * @param {string} patterns[].severity - Severity level (HIGH/MEDIUM/LOW)
 * @param {number} patterns[].totalLoss - Total loss from pattern
 * @param {number} patterns[].count - Number of occurrences
 * @param {number} patterns[].avgLoss - Average loss per occurrence
 * @param {Array<Object>} [patterns[].examples] - Example trades
 */
function renderMistakePatterns(patterns) {
    const container = document.getElementById('mistake-patterns');

    if (!patterns || patterns.length === 0) {
        container.innerHTML = `
            <div class="text-center text-muted py-4">
                <i class="bi bi-check-circle text-success fs-1"></i>
                <p class="mt-2">분석된 실수 패턴이 없습니다!</p>
            </div>
        `;
        return;
    }

    const html = patterns.map(pattern => {
        const { severityClass, severityBadge } = getSeverityStyles(pattern.severity);

        return `
            <div class="mistake-item ${severityClass}">
                <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                        <strong>${pattern.description}</strong>
                        <span class="badge ${severityBadge} ms-2">${pattern.severity}</span>
                    </div>
                    <span class="text-danger fw-bold">${formatStatCurrency(pattern.totalLoss)}</span>
                </div>
                <div class="small text-muted mb-2">${pattern.count}회 발생 | 평균 손실: ${formatStatCurrency(pattern.avgLoss)}</div>
                ${renderPatternExamples(pattern.examples)}
            </div>
        `;
    }).join('');

    container.innerHTML = html;
}

/**
 * Render improvement suggestions section.
 * Displays actionable trading improvement recommendations.
 * @param {Array<Object>} suggestions - Array of suggestions
 * @param {string} suggestions[].title - Suggestion title
 * @param {string} suggestions[].message - Detailed message
 * @param {string} suggestions[].priority - Priority level (HIGH/MEDIUM/LOW)
 * @param {string} suggestions[].category - Category (TIME/SYMBOL/RISK/BEHAVIOR)
 * @param {string} suggestions[].actionItem - Specific action to take
 * @param {number} [suggestions[].potentialImpact] - Expected improvement percentage
 */
function renderSuggestions(suggestions) {
    const container = document.getElementById('suggestions');

    if (!suggestions || suggestions.length === 0) {
        container.innerHTML = '<div class="text-center text-muted py-4">현재 특별한 개선 제안이 없습니다.</div>';
        return;
    }

    const html = suggestions.map(suggestion => {
        const { priorityClass, priorityBadge } = getPriorityStyles(suggestion.priority);
        const categoryIcon = getCategoryIcon(suggestion.category);

        return `
            <div class="col-md-6 mb-3">
                <div class="suggestion-item ${priorityClass}">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div>
                            <i class="bi ${categoryIcon} me-2"></i>
                            <strong>${suggestion.title}</strong>
                        </div>
                        <span class="badge ${priorityBadge}">${suggestion.priority}</span>
                    </div>
                    <div class="mb-2">${suggestion.message}</div>
                    <div class="d-flex justify-content-between align-items-center">
                        <small class="text-muted"><i class="bi bi-check2-square me-1"></i>${suggestion.actionItem}</small>
                        ${suggestion.potentialImpact ? `<span class="badge bg-light text-dark">예상 효과: +${suggestion.potentialImpact}%</span>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML = '<div class="row">' + html + '</div>';
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format currency with sign prefix for statistics display.
 * @param {number|null|undefined} amount - The amount to format
 * @returns {string} Formatted currency string with +/- prefix
 */
function formatStatCurrency(amount) {
    if (amount === null || amount === undefined) return '0원';
    const prefix = amount >= 0 ? '+' : '';
    return prefix + new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
        maximumFractionDigits: 0
    }).format(amount);
}

/**
 * Format currency in short notation (K/M suffixes).
 * @param {number} amount - The amount to format
 * @returns {string} Shortened currency string
 */
function formatStatCurrencyShort(amount) {
    if (Math.abs(amount) >= 1000000) {
        return (amount / 1000000).toFixed(1) + 'M';
    } else if (Math.abs(amount) >= 1000) {
        return (amount / 1000).toFixed(0) + 'K';
    }
    return amount.toString();
}

/**
 * Get CSS class based on value comparison to threshold.
 * @param {number} value - Value to evaluate
 * @param {number} threshold - Threshold for positive class
 * @returns {string} CSS class name
 */
function getValueClass(value, threshold) {
    return value >= threshold ? STAT_CLASSES.POSITIVE : STAT_CLASSES.NEGATIVE;
}

/**
 * Get CSS class for consistency score with three-tier styling.
 * @param {number} consistency - Consistency score (0-100)
 * @returns {string} CSS class name
 */
function getConsistencyClass(consistency) {
    if (consistency >= THRESHOLDS.CONSISTENCY_HIGH) {
        return STAT_CLASSES.POSITIVE;
    } else if (consistency >= THRESHOLDS.CONSISTENCY_MEDIUM) {
        return STAT_CLASSES.NEUTRAL;
    }
    return STAT_CLASSES.NEGATIVE;
}

/**
 * Get CSS class for ranking badge.
 * @param {number} rank - Rank position (1-based)
 * @returns {string} CSS class name
 */
function getRankClass(rank) {
    if (rank === 1) return 'rank-1';
    if (rank === 2) return 'rank-2';
    if (rank === 3) return 'rank-3';
    return 'rank-other';
}

/**
 * Get severity styling classes.
 * @param {string} severity - Severity level (HIGH/MEDIUM/LOW)
 * @returns {{severityClass: string, severityBadge: string}} Style classes
 */
function getSeverityStyles(severity) {
    const styles = {
        HIGH: { severityClass: '', severityBadge: 'bg-danger' },
        MEDIUM: { severityClass: 'medium', severityBadge: 'bg-warning' },
        LOW: { severityClass: 'low', severityBadge: 'bg-info' }
    };
    return styles[severity] || styles.LOW;
}

/**
 * Get priority styling classes.
 * @param {string} priority - Priority level (HIGH/MEDIUM/LOW)
 * @returns {{priorityClass: string, priorityBadge: string}} Style classes
 */
function getPriorityStyles(priority) {
    const styles = {
        HIGH: { priorityClass: 'high', priorityBadge: 'bg-danger' },
        MEDIUM: { priorityClass: 'medium', priorityBadge: 'bg-warning text-dark' },
        LOW: { priorityClass: '', priorityBadge: 'bg-success' }
    };
    return styles[priority] || styles.LOW;
}

/**
 * Get Bootstrap icon class for suggestion category.
 * @param {string} category - Category identifier
 * @returns {string} Bootstrap icon class
 */
function getCategoryIcon(category) {
    const icons = {
        TIME: 'bi-clock',
        SYMBOL: 'bi-graph-up',
        RISK: 'bi-shield-exclamation',
        BEHAVIOR: 'bi-person-exclamation'
    };
    return icons[category] || 'bi-lightbulb';
}

/**
 * Render pattern examples HTML.
 * @param {Array<Object>} [examples] - Array of example trades
 * @returns {string} HTML string for examples
 */
function renderPatternExamples(examples) {
    if (!examples || examples.length === 0) return '';

    const exampleText = examples.slice(0, 2)
        .map(e => `${e.symbol} (${e.date})`)
        .join(', ');

    return `<div class="small"><strong>예시:</strong> ${exampleText}</div>`;
}

/**
 * Display empty state for all statistics sections.
 */
function showEmptyStatisticsState() {
    document.getElementById('total-trades').textContent = '0';
    document.getElementById('win-rate').textContent = '-';
    document.getElementById('total-profit').textContent = '-';
    document.getElementById('best-day').textContent = '-';
    document.getElementById('best-time').textContent = '-';
    document.getElementById('consistency-score').textContent = '-';
    document.getElementById('symbol-ranking').innerHTML = '<div class="text-center text-muted py-4">거래 데이터가 없습니다.</div>';
    document.getElementById('mistake-patterns').innerHTML = '<div class="text-center text-muted py-4">분석할 데이터가 없습니다.</div>';
    document.getElementById('suggestions').innerHTML = '<div class="text-center text-muted py-4">분석할 데이터가 없습니다.</div>';
}

// ============================================================================
// CHART CONFIGURATION HELPERS
// ============================================================================

/**
 * Create chart options for dual-axis charts (weekday chart).
 * @param {string} leftAxisTitle - Title for left Y-axis
 * @param {string} rightAxisTitle - Title for right Y-axis
 * @param {Function} rightAxisFormatter - Formatter function for right axis ticks
 * @returns {Object} Chart.js options object
 */
function createDualAxisChartOptions(leftAxisTitle, rightAxisTitle, rightAxisFormatter) {
    return {
        responsive: true,
        interaction: {
            mode: 'index',
            intersect: false
        },
        plugins: {
            tooltip: {
                callbacks: {
                    label: function(context) {
                        if (context.dataset.label === '승률 (%)') {
                            return `승률: ${context.raw.toFixed(1)}%`;
                        }
                        return `손익: ${formatStatCurrency(context.raw)}`;
                    }
                }
            }
        },
        scales: {
            y: {
                type: 'linear',
                position: 'left',
                min: 0,
                max: 100,
                title: {
                    display: true,
                    text: leftAxisTitle
                }
            },
            y1: {
                type: 'linear',
                position: 'right',
                grid: {
                    drawOnChartArea: false
                },
                title: {
                    display: true,
                    text: rightAxisTitle
                },
                ticks: {
                    callback: function(value) {
                        return rightAxisFormatter(value);
                    }
                }
            }
        }
    };
}

/**
 * Create chart options for time-of-day chart.
 * @returns {Object} Chart.js options object
 */
function createTimeChartOptions() {
    return {
        responsive: true,
        interaction: {
            mode: 'index',
            intersect: false
        },
        plugins: {
            tooltip: {
                callbacks: {
                    label: function(context) {
                        if (context.dataset.label === '승률 (%)') {
                            return `승률: ${context.raw.toFixed(1)}%`;
                        }
                        return `거래 수: ${context.raw}건`;
                    }
                }
            }
        },
        scales: {
            y: {
                type: 'linear',
                position: 'left',
                min: 0,
                max: 100,
                title: {
                    display: true,
                    text: '승률 (%)'
                }
            },
            y1: {
                type: 'linear',
                position: 'right',
                grid: {
                    drawOnChartArea: false
                },
                title: {
                    display: true,
                    text: '거래 수'
                }
            }
        }
    };
}
