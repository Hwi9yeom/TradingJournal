/**
 * Trading Journal - Glassmorphism Dashboard
 * Modern, elegant financial dashboard with glass effects
 */

const API_BASE_URL = '/api';

// ==================== Theme Configuration ====================
const CHART_THEME = {
    colors: {
        primary: '#667eea',
        secondary: '#764ba2',
        positive: '#00f5a0',
        negative: '#ff6b6b',
        warning: '#ffd93d',
        text: 'rgba(255, 255, 255, 0.95)',
        textMuted: 'rgba(255, 255, 255, 0.35)',
        grid: 'rgba(255, 255, 255, 0.05)',
        gradient: ['#667eea', '#764ba2', '#f093fb']
    },
    fonts: {
        base: "'Outfit', sans-serif",
        mono: "'JetBrains Mono', monospace"
    }
};

// Chart default options
Chart.defaults.color = CHART_THEME.colors.textMuted;
Chart.defaults.font.family = CHART_THEME.fonts.base;
Chart.defaults.plugins.legend.labels.usePointStyle = true;
Chart.defaults.plugins.tooltip.backgroundColor = 'rgba(20, 20, 40, 0.95)';
Chart.defaults.plugins.tooltip.borderColor = 'rgba(255, 255, 255, 0.1)';
Chart.defaults.plugins.tooltip.borderWidth = 1;
Chart.defaults.plugins.tooltip.padding = 12;
Chart.defaults.plugins.tooltip.cornerRadius = 8;
Chart.defaults.plugins.tooltip.titleFont = { weight: '600' };

// ==================== Constants ====================
const CSS_CLASSES = {
    POSITIVE: 'text-positive',
    NEGATIVE: 'text-negative',
    WARNING: 'text-warning',
    MUTED: 'text-muted'
};

const PERIOD_MONTHS = {
    '1M': 1, '3M': 3, '6M': 6, '1Y': 12, 'ALL': null
};

const PERIOD_LABELS = {
    '1D': '1일', '1W': '1주', '1M': '1개월', 'MTD': '이번달',
    '3M': '3개월', '6M': '6개월', '1Y': '1년'
};

// ==================== Utility Functions ====================
function getPeriodDates(period) {
    const endDate = new Date();
    const startDate = new Date();
    const months = PERIOD_MONTHS[period];

    if (months !== null && months !== undefined) {
        startDate.setMonth(endDate.getMonth() - months);
    } else {
        startDate.setFullYear(2020, 0, 1);
    }
    return { startDate, endDate };
}

function formatDateForApi(date) {
    return date.toISOString().split('T')[0];
}

function getDateRangeForApi(period) {
    const { startDate, endDate } = getPeriodDates(period);
    return {
        startDate: formatDateForApi(startDate),
        endDate: formatDateForApi(endDate)
    };
}

function formatCurrency(value) {
    return '₩' + Math.round(value || 0).toLocaleString('ko-KR');
}

function formatPercent(value) {
    const v = parseFloat(value) || 0;
    return (v >= 0 ? '+' : '') + v.toFixed(2) + '%';
}

function applyValueClass($element, value) {
    $element.removeClass(`${CSS_CLASSES.POSITIVE} ${CSS_CLASSES.NEGATIVE} ${CSS_CLASSES.MUTED}`);
    if (value > 0) {
        $element.addClass(CSS_CLASSES.POSITIVE);
    } else if (value < 0) {
        $element.addClass(CSS_CLASSES.NEGATIVE);
    } else {
        $element.addClass(CSS_CLASSES.MUTED);
    }
}

function createGradient(ctx, colorStart, colorEnd) {
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, colorStart);
    gradient.addColorStop(1, colorEnd);
    return gradient;
}

// ==================== UI Helpers ====================
function toggleDropdown(id) {
    const dropdown = document.getElementById(id);
    dropdown.classList.toggle('show');

    // Close when clicking outside
    const closeHandler = (e) => {
        if (!dropdown.contains(e.target)) {
            dropdown.classList.remove('show');
            document.removeEventListener('click', closeHandler);
        }
    };
    setTimeout(() => document.addEventListener('click', closeHandler), 0);
}

function openCustomReportModal() {
    const modal = document.getElementById('customReportModal');
    modal.classList.add('show');

    // Set default dates
    const today = new Date();
    const startOfYear = new Date(today.getFullYear(), 0, 1);
    document.getElementById('reportEndDate').value = formatDateForApi(today);
    document.getElementById('reportStartDate').value = formatDateForApi(startOfYear);
}

function closeModal(id) {
    document.getElementById(id).classList.remove('show');
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast-glass ${type}`;
    toast.innerHTML = `
        <i class="bi bi-${type === 'success' ? 'check-circle' : type === 'error' ? 'x-circle' : 'exclamation-circle'}"></i>
        <span>${message}</span>
    `;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function updatePeriodSelector(selector, period) {
    const container = document.getElementById(selector);
    if (!container) return;
    container.querySelectorAll('.period-btn').forEach(btn => {
        btn.classList.remove('active');
        if (btn.textContent === period || btn.dataset.period === period) {
            btn.classList.add('active');
        }
    });
}

// ==================== Chart Instances ====================
let assetValueChart, portfolioCompositionChart, monthlyReturnChart;
let equityCurveChart, drawdownChart, benchmarkComparisonChart;

// ==================== Initialization ====================
$(document).ready(function() {
    if (!checkAuth()) return;

    initializeCharts();
    loadDashboardData();
    initializeAdvancedCharts();
    loadAdvancedData();
    initializeTreemap();
    initializeBenchmark();
});

function loadDashboardData() {
    $.ajax({
        url: `${API_BASE_URL}/portfolio/summary`,
        method: 'GET',
        success: function(data) {
            updateSummaryCards(data);
            updatePortfolioComposition(data.holdings);
            updateTopPerformers(data.holdings);
        },
        error: (xhr) => console.error('Failed to load portfolio summary:', xhr)
    });

    loadTradeStatistics();
    loadAssetValueHistory('1M');
    loadMonthlyReturns();
}

function loadAdvancedData() {
    loadEquityCurve('1Y');
    loadDrawdown('1Y');
    loadCorrelation('1Y');
    loadRiskMetrics('1Y');
}

// ==================== Summary Cards ====================
function updateSummaryCards(summary) {
    $('#total-investment').text(formatCurrency(summary.totalInvestment));
    $('#total-value').text(formatCurrency(summary.totalCurrentValue));

    const totalReturn = summary.totalProfitLossPercent || 0;
    const totalProfit = summary.totalProfitLoss || 0;

    $('#total-return').text(formatPercent(totalReturn));
    $('#total-profit-amount').html(`<i class="bi bi-${totalProfit >= 0 ? 'caret-up-fill' : 'caret-down-fill'}"></i> ${formatCurrency(totalProfit)}`);

    // Apply classes to cards
    const $returnCard = $('#return-card');
    $returnCard.removeClass('positive negative').addClass(totalReturn >= 0 ? 'positive' : 'negative');
    applyValueClass($('#total-return'), totalReturn);
    applyValueClass($('#total-profit-amount'), totalProfit);

    // Realized P&L
    const realizedPnL = summary.totalRealizedPnl || 0;
    $('#realized-pnl').text(formatCurrency(realizedPnL));
    const $realizedCard = $('#realized-card');
    $realizedCard.removeClass('positive negative').addClass(realizedPnL >= 0 ? 'positive' : 'negative');
    applyValueClass($('#realized-pnl'), realizedPnL);
}

// ==================== Portfolio Composition ====================
function updatePortfolioComposition(holdings) {
    if (!holdings || holdings.length === 0) return;

    const labels = holdings.map(h => h.stockName);
    const data = holdings.map(h => h.currentValue);
    const colors = generateGlassColors(holdings.length);

    if (portfolioCompositionChart) {
        portfolioCompositionChart.data.labels = labels;
        portfolioCompositionChart.data.datasets[0].data = data;
        portfolioCompositionChart.data.datasets[0].backgroundColor = colors;
        portfolioCompositionChart.data.datasets[0].borderColor = colors.map(c => c.replace('0.7', '1'));
        portfolioCompositionChart.update();
    }
}

function generateGlassColors(count) {
    const baseColors = [
        'rgba(102, 126, 234, 0.7)',
        'rgba(118, 75, 162, 0.7)',
        'rgba(240, 147, 251, 0.7)',
        'rgba(0, 245, 160, 0.7)',
        'rgba(0, 217, 245, 0.7)',
        'rgba(255, 217, 61, 0.7)',
        'rgba(255, 107, 107, 0.7)',
        'rgba(168, 85, 247, 0.7)',
        'rgba(107, 202, 255, 0.7)',
        'rgba(255, 159, 64, 0.7)'
    ];
    return baseColors.slice(0, count);
}

// ==================== Top Performers ====================
function updateTopPerformers(holdings) {
    if (!holdings) return;

    const sorted = [...holdings].sort((a, b) => b.profitLossPercent - a.profitLossPercent);

    const gainers = sorted.filter(h => h.profitLossPercent > 0).slice(0, 5);
    const gainersHtml = gainers.map(h => `
        <tr>
            <td>${h.stockName}</td>
            <td class="text-right text-positive font-mono">+${h.profitLossPercent.toFixed(2)}%</td>
            <td class="text-right text-positive font-mono">${formatCurrency(h.profitLoss)}</td>
        </tr>
    `).join('');
    $('#top-gainers').html(gainersHtml || '<tr><td colspan="3" class="text-center text-muted">수익 종목 없음</td></tr>');

    const losers = sorted.filter(h => h.profitLossPercent < 0).slice(-5).reverse();
    const losersHtml = losers.map(h => `
        <tr>
            <td>${h.stockName}</td>
            <td class="text-right text-negative font-mono">${h.profitLossPercent.toFixed(2)}%</td>
            <td class="text-right text-negative font-mono">${formatCurrency(h.profitLoss)}</td>
        </tr>
    `).join('');
    $('#top-losers').html(losersHtml || '<tr><td colspan="3" class="text-center text-muted">손실 종목 없음</td></tr>');
}

// ==================== Trade Statistics ====================
function loadTradeStatistics() {
    $.ajax({
        url: `${API_BASE_URL}/analysis/statistics`,
        method: 'GET',
        success: function(stats) {
            $('#total-trades').text(stats.totalTrades || 0);
            $('#unique-stocks').text(stats.uniqueStocks || 0);
            $('#avg-holding-period').text((stats.avgHoldingPeriod || 0) + '일');
            $('#win-rate').text((stats.winRate || 0).toFixed(1) + '%');
            $('#avg-return').text((stats.avgReturn || 0).toFixed(2) + '%');
            $('#max-return').text((stats.maxReturn || 0).toFixed(2) + '%');
        },
        error: (xhr) => console.error('Failed to load trade statistics:', xhr)
    });
}

// ==================== Chart Initialization ====================
function initializeCharts() {
    // Asset Value Chart
    const assetCtx = document.getElementById('assetValueChart')?.getContext('2d');
    if (assetCtx) {
        assetValueChart = new Chart(assetCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: '자산 가치',
                    data: [],
                    borderColor: CHART_THEME.colors.primary,
                    backgroundColor: createGradient(assetCtx, 'rgba(102, 126, 234, 0.3)', 'rgba(102, 126, 234, 0)'),
                    borderWidth: 2,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 6,
                    pointHoverBackgroundColor: CHART_THEME.colors.primary,
                    pointHoverBorderColor: '#fff',
                    pointHoverBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => '자산 가치: ' + formatCurrency(ctx.parsed.y)
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: CHART_THEME.colors.grid, drawBorder: false },
                        ticks: { maxTicksLimit: 8 }
                    },
                    y: {
                        grid: { color: CHART_THEME.colors.grid, drawBorder: false },
                        ticks: { callback: (value) => formatCurrency(value) }
                    }
                }
            }
        });
    }

    // Portfolio Composition Chart
    const portfolioCtx = document.getElementById('portfolioCompositionChart')?.getContext('2d');
    if (portfolioCtx) {
        portfolioCompositionChart = new Chart(portfolioCtx, {
            type: 'doughnut',
            data: {
                labels: [],
                datasets: [{
                    data: [],
                    backgroundColor: [],
                    borderWidth: 2,
                    borderColor: 'rgba(255, 255, 255, 0.1)',
                    hoverBorderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '65%',
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 15,
                            font: { size: 11 },
                            color: CHART_THEME.colors.textMuted
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => {
                                const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
                                const pct = ((ctx.parsed / total) * 100).toFixed(1);
                                return `${ctx.label}: ${formatCurrency(ctx.parsed)} (${pct}%)`;
                            }
                        }
                    }
                }
            }
        });
    }

    // Monthly Returns Chart
    const monthlyCtx = document.getElementById('monthlyReturnChart')?.getContext('2d');
    if (monthlyCtx) {
        monthlyReturnChart = new Chart(monthlyCtx, {
            type: 'bar',
            data: {
                labels: [],
                datasets: [{
                    label: '월별 수익률',
                    data: [],
                    backgroundColor: (ctx) => {
                        const value = ctx.parsed?.y || 0;
                        return value >= 0 ? 'rgba(0, 245, 160, 0.7)' : 'rgba(255, 107, 107, 0.7)';
                    },
                    borderColor: (ctx) => {
                        const value = ctx.parsed?.y || 0;
                        return value >= 0 ? CHART_THEME.colors.positive : CHART_THEME.colors.negative;
                    },
                    borderWidth: 1,
                    borderRadius: 4,
                    borderSkipped: false
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => '수익률: ' + ctx.parsed.y.toFixed(2) + '%'
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { maxTicksLimit: 12 }
                    },
                    y: {
                        grid: { color: CHART_THEME.colors.grid, drawBorder: false },
                        ticks: { callback: (value) => value + '%' }
                    }
                }
            }
        });
    }
}

// ==================== Advanced Charts ====================
function initializeAdvancedCharts() {
    // Equity Curve
    const equityCtx = document.getElementById('equityCurveChart')?.getContext('2d');
    if (equityCtx) {
        equityCurveChart = new Chart(equityCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: '누적 수익률',
                    data: [],
                    borderColor: CHART_THEME.colors.positive,
                    backgroundColor: createGradient(equityCtx, 'rgba(0, 245, 160, 0.2)', 'rgba(0, 245, 160, 0)'),
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: { label: (ctx) => '수익률: ' + ctx.parsed.y.toFixed(2) + '%' }
                    }
                },
                scales: {
                    x: { grid: { color: CHART_THEME.colors.grid, drawBorder: false }, ticks: { maxTicksLimit: 8 } },
                    y: { grid: { color: CHART_THEME.colors.grid, drawBorder: false }, ticks: { callback: (v) => v + '%' } }
                }
            }
        });
    }

    // Drawdown
    const drawdownCtx = document.getElementById('drawdownChart')?.getContext('2d');
    if (drawdownCtx) {
        drawdownChart = new Chart(drawdownCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Drawdown',
                    data: [],
                    borderColor: CHART_THEME.colors.negative,
                    backgroundColor: createGradient(drawdownCtx, 'rgba(255, 107, 107, 0.3)', 'rgba(255, 107, 107, 0)'),
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: { label: (ctx) => 'Drawdown: ' + ctx.parsed.y.toFixed(2) + '%' }
                    }
                },
                scales: {
                    x: { grid: { color: CHART_THEME.colors.grid, drawBorder: false }, ticks: { maxTicksLimit: 8 } },
                    y: {
                        max: 0,
                        grid: { color: CHART_THEME.colors.grid, drawBorder: false },
                        ticks: { callback: (v) => v + '%' }
                    }
                }
            }
        });
    }
}

// ==================== Asset Value History ====================
function loadAssetValueHistory(period) {
    const range = getDateRangeForApi(period);
    $.ajax({
        url: `${API_BASE_URL}/analysis/asset-history`,
        method: 'GET',
        data: range,
        success: (data) => updateAssetValueChart(data),
        error: (xhr) => console.error('Failed to load asset history:', xhr)
    });
}

function updateAssetValueChart(data) {
    if (!data?.labels?.length || !assetValueChart) return;

    const labels = data.labels.map(l => {
        const d = new Date(l);
        return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    assetValueChart.data.labels = labels;
    assetValueChart.data.datasets[0].data = data.values;
    assetValueChart.update();
}

function updateAssetChart(period) {
    updatePeriodSelector('asset-period', period);
    loadAssetValueHistory(period);
}

// ==================== Monthly Returns ====================
function loadMonthlyReturns() {
    $.ajax({
        url: `${API_BASE_URL}/analysis/monthly-returns`,
        method: 'GET',
        success: (data) => updateMonthlyReturnChart(data),
        error: (xhr) => console.error('Failed to load monthly returns:', xhr)
    });
}

function updateMonthlyReturnChart(data) {
    if (!data?.length || !monthlyReturnChart) return;

    const sorted = [...data].sort((a, b) => a.month.localeCompare(b.month));
    const labels = sorted.map(d => {
        const [year, month] = d.month.split('-');
        return `${year.slice(2)}/${parseInt(month)}`;
    });
    const returns = sorted.map(d => parseFloat(d.returnRate) || 0);

    monthlyReturnChart.data.labels = labels;
    monthlyReturnChart.data.datasets[0].data = returns;
    monthlyReturnChart.update();
}

// ==================== Equity Curve ====================
function loadEquityCurve(period) {
    const range = getDateRangeForApi(period);
    $.ajax({
        url: `${API_BASE_URL}/analysis/equity-curve`,
        method: 'GET',
        data: range,
        success: (data) => updateEquityCurveChart(data),
        error: (xhr) => console.error('Failed to load equity curve:', xhr)
    });
}

function updateEquityCurveChart(data) {
    if (!data?.labels?.length) return;

    const labels = data.labels.map(l => {
        const d = new Date(l);
        return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    const totalReturn = parseFloat(data.totalReturn || 0);
    const $el = $('#equity-total-return').text(formatPercent(totalReturn));
    applyValueClass($el, totalReturn);

    $('#equity-cagr').text(formatPercent(parseFloat(data.cagr || 0)));
    $('#equity-final-value').text(formatCurrency(data.finalValue || 0));

    if (equityCurveChart) {
        equityCurveChart.data.labels = labels;
        equityCurveChart.data.datasets[0].data = data.cumulativeReturns;
        equityCurveChart.update();
    }
}

function updateEquityCurve(period) {
    updatePeriodSelector('equity-period', period);
    loadEquityCurve(period);
}

// ==================== Drawdown ====================
function loadDrawdown(period) {
    const range = getDateRangeForApi(period);
    $.ajax({
        url: `${API_BASE_URL}/analysis/drawdown`,
        method: 'GET',
        data: range,
        success: (data) => updateDrawdownChart(data),
        error: (xhr) => console.error('Failed to load drawdown:', xhr)
    });
}

function updateDrawdownChart(data) {
    if (!data?.labels?.length) return;

    const labels = data.labels.map(l => {
        const d = new Date(l);
        return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    $('#max-drawdown').text((data.maxDrawdown || 0).toFixed(2) + '%');

    const currentDD = parseFloat(data.currentDrawdown || 0);
    const $currentDD = $('#current-drawdown').text(currentDD.toFixed(2) + '%');
    applyValueClass($currentDD, currentDD);

    $('#recovery-days').text(data.recoveryDays !== null ? data.recoveryDays + '일' : '미회복');

    if (drawdownChart) {
        drawdownChart.data.labels = labels;
        drawdownChart.data.datasets[0].data = data.drawdowns;
        const minDD = Math.min(...data.drawdowns);
        drawdownChart.options.scales.y.min = Math.floor(minDD * 1.1);
        drawdownChart.update();
    }
}

function updateDrawdown(period) {
    updatePeriodSelector('drawdown-period', period);
    loadDrawdown(period);
}

// ==================== Correlation ====================
function loadCorrelation(period) {
    const range = getDateRangeForApi(period);
    $.ajax({
        url: `${API_BASE_URL}/analysis/correlation`,
        method: 'GET',
        data: range,
        success: (data) => updateCorrelationMatrix(data),
        error: (xhr) => console.error('Failed to load correlation:', xhr)
    });
}

function updateCorrelationMatrix(data) {
    const $header = $('#correlation-header');
    const $body = $('#correlation-body');
    const $empty = $('#correlation-empty');
    const $matrix = $('#correlation-matrix');

    if (!data?.symbols?.length || data.symbols.length < 2) {
        $matrix.hide();
        $empty.removeClass('hidden');
        $('#avg-correlation').text('-');
        $('#diversification-score').text('-');
        $('#correlation-stocks').text(data?.symbols?.length || 0);
        return;
    }

    $matrix.show();
    $empty.addClass('hidden');

    const avgCorr = parseFloat(data.averageCorrelation || 0);
    const $avgCorr = $('#avg-correlation').text(avgCorr.toFixed(2));
    $avgCorr.removeClass('text-positive text-warning text-negative');
    $avgCorr.addClass(avgCorr < 0.3 ? 'text-positive' : avgCorr < 0.6 ? 'text-warning' : 'text-negative');

    const divScore = parseFloat(data.diversificationScore || 50);
    let divText = '보통', divClass = 'text-warning';
    if (divScore < 40) { divText = '우수'; divClass = 'text-positive'; }
    else if (divScore > 70) { divText = '미흡'; divClass = 'text-negative'; }
    const $divScore = $('#diversification-score').text(divText);
    $divScore.removeClass('text-positive text-warning text-negative').addClass(divClass);

    $('#correlation-stocks').text(data.symbols.length);

    // Build table
    let headerHtml = '<tr><th></th>';
    data.symbols.forEach((s, i) => {
        const name = data.names?.[i] || s;
        const short = name.length > 5 ? name.substring(0, 5) + '..' : name;
        headerHtml += `<th title="${name}">${short}</th>`;
    });
    headerHtml += '</tr>';
    $header.html(headerHtml);

    let bodyHtml = '';
    data.matrix.forEach((row, i) => {
        const name = data.names?.[i] || data.symbols[i];
        const short = name.length > 5 ? name.substring(0, 5) + '..' : name;
        bodyHtml += `<tr><th title="${name}">${short}</th>`;
        row.forEach((val, j) => {
            const v = parseFloat(val);
            const color = getCorrelationColor(v);
            const textColor = Math.abs(v) > 0.5 ? '#fff' : 'rgba(255,255,255,0.9)';
            bodyHtml += `<td style="background:${color};color:${textColor}" title="${data.names?.[i]} ↔ ${data.names?.[j]}: ${v.toFixed(2)}">`;
            bodyHtml += i === j ? '-' : v.toFixed(2);
            bodyHtml += '</td>';
        });
        bodyHtml += '</tr>';
    });
    $body.html(bodyHtml);
}

function getCorrelationColor(value) {
    if (value <= -1) return '#22c55e';
    if (value >= 1) return '#ef4444';
    if (value < 0) {
        const ratio = value + 1;
        return d3.interpolateRgb('#22c55e', '#fbbf24')(ratio);
    } else {
        return d3.interpolateRgb('#fbbf24', '#ef4444')(value);
    }
}

function updateCorrelation(period) {
    updatePeriodSelector('correlation-period', period);
    loadCorrelation(period);
}

// ==================== Risk Metrics ====================
function loadRiskMetrics(period) {
    const range = getDateRangeForApi(period);
    $.ajax({
        url: `${API_BASE_URL}/analysis/risk-metrics`,
        method: 'GET',
        data: range,
        success: (data) => updateRiskMetricsDisplay(data),
        error: (xhr) => { console.error('Failed to load risk metrics:', xhr); resetRiskMetricsDisplay(); }
    });
}

function updateRiskMetricsDisplay(data) {
    if (!data) { resetRiskMetricsDisplay(); return; }

    // Risk Level Badge
    const level = data.riskLevel || 'MEDIUM';
    const $badge = $('#risk-level-badge');
    const $text = $('#risk-level-text');

    $badge.removeClass('low medium high');
    switch(level) {
        case 'LOW':
            $badge.addClass('low');
            $text.text('낮은 리스크 - 안정적 포트폴리오');
            break;
        case 'HIGH':
            $badge.addClass('high');
            $text.text('높은 리스크 - 공격적 포트폴리오');
            break;
        default:
            $badge.addClass('medium');
            $text.text('중간 리스크 - 적절한 수준');
    }

    // Main metrics
    const applyMetricClass = ($el, val, good, warn) => {
        $el.text(val.toFixed(2));
        $el.removeClass('text-positive text-warning text-negative');
        $el.addClass(val >= good ? 'text-positive' : val >= warn ? 'text-warning' : 'text-negative');
    };

    applyMetricClass($('#sharpe-ratio'), parseFloat(data.sharpeRatio || 0), 1, 0.5);
    applyMetricClass($('#sortino-ratio'), parseFloat(data.sortinoRatio || 0), 1.5, 0.5);
    applyMetricClass($('#calmar-ratio'), parseFloat(data.calmarRatio || 0), 1, 0.5);
    applyMetricClass($('#profit-factor'), parseFloat(data.profitFactor || 0), 1.5, 1);

    // VaR
    if (data.var95) {
        $('#var95-daily').text(data.var95.dailyVaR + '%');
        $('#var95-weekly').text(data.var95.weeklyVaR + '%');
        $('#var95-monthly').text(data.var95.monthlyVaR + '%');
        $('#var-amount').text(formatCurrency(data.var95.dailyVaRAmount || 0));
    }
    if (data.var99) {
        $('#var99-daily').text(data.var99.dailyVaR + '%');
        $('#var99-weekly').text(data.var99.weeklyVaR + '%');
        $('#var99-monthly').text(data.var99.monthlyVaR + '%');
    }

    // Additional metrics
    $('#volatility').text((data.volatility || 0).toFixed(2) + '%');
    $('#downside-deviation').text((data.downsideDeviation || 0).toFixed(2) + '%');
    $('#risk-mdd').text((data.maxDrawdown || 0).toFixed(2) + '%');

    const cagr = parseFloat(data.cagr || 0);
    const $cagr = $('#risk-cagr').text(formatPercent(cagr));
    applyValueClass($cagr, cagr);

    const winRate = parseFloat(data.winRate || 0);
    const $winRate = $('#risk-winrate').text(winRate.toFixed(1) + '%');
    $winRate.removeClass('text-positive text-negative').addClass(winRate >= 50 ? 'text-positive' : 'text-negative');

    $('#trading-days').text(data.tradingDays || 0);
}

function resetRiskMetricsDisplay() {
    $('#risk-level-badge').removeClass('low medium high').addClass('medium');
    $('#risk-level-text').text('데이터 없음');
    $('#sharpe-ratio, #sortino-ratio, #calmar-ratio, #profit-factor').text('-');
    $('#var95-daily, #var95-weekly, #var95-monthly, #var-amount').text('-');
    $('#var99-daily, #var99-weekly, #var99-monthly').text('-');
    $('#volatility, #downside-deviation, #risk-mdd, #risk-cagr, #risk-winrate, #trading-days').text('-');
}

function updateRiskMetrics(period) {
    updatePeriodSelector('risk-period', period);
    loadRiskMetrics(period);
}

// ==================== Benchmark ====================
let currentBenchmarkPeriod = '1Y';

function initializeBenchmark() {
    const ctx = document.getElementById('benchmarkComparisonChart')?.getContext('2d');
    if (!ctx) return;

    benchmarkComparisonChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: '포트폴리오',
                    data: [],
                    borderColor: CHART_THEME.colors.primary,
                    backgroundColor: 'transparent',
                    borderWidth: 2,
                    tension: 0.3,
                    pointRadius: 0,
                    pointHoverRadius: 5
                },
                {
                    label: '벤치마크',
                    data: [],
                    borderColor: 'rgba(156, 163, 175, 0.7)',
                    backgroundColor: 'transparent',
                    borderWidth: 2,
                    tension: 0.3,
                    pointRadius: 0,
                    pointHoverRadius: 5,
                    borderDash: [5, 5]
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { intersect: false, mode: 'index' },
            plugins: {
                legend: { display: true, position: 'top' },
                tooltip: {
                    callbacks: { label: (ctx) => ctx.dataset.label + ': ' + ctx.parsed.y.toFixed(2) + '%' }
                }
            },
            scales: {
                x: { grid: { color: CHART_THEME.colors.grid, drawBorder: false }, ticks: { maxTicksLimit: 8 } },
                y: { grid: { color: CHART_THEME.colors.grid, drawBorder: false }, ticks: { callback: (v) => v + '%' } }
            }
        }
    });

    $('#benchmark-select').on('change', () => loadBenchmarkComparison(currentBenchmarkPeriod));
    loadBenchmarkComparison('1Y');
}

function loadBenchmarkComparison(period) {
    currentBenchmarkPeriod = period;
    const range = getDateRangeForApi(period);
    const benchmark = $('#benchmark-select').val();

    $.ajax({
        url: `${API_BASE_URL}/analysis/benchmark/compare`,
        method: 'GET',
        data: { benchmark, ...range },
        success: (data) => { updateBenchmarkDisplay(data); $('#benchmark-empty').addClass('hidden'); },
        error: (xhr) => { console.error('Failed to load benchmark:', xhr); showBenchmarkEmpty(); }
    });
}

function updateBenchmarkDisplay(data) {
    if (!data?.labels?.length) { showBenchmarkEmpty(); return; }

    const labels = data.labels.map(l => {
        const d = new Date(l);
        return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    if (benchmarkComparisonChart) {
        benchmarkComparisonChart.data.labels = labels;
        benchmarkComparisonChart.data.datasets[0].data = data.portfolioReturns;
        benchmarkComparisonChart.data.datasets[1].data = data.benchmarkReturns;
        benchmarkComparisonChart.data.datasets[1].label = data.benchmarkLabel || '벤치마크';
        benchmarkComparisonChart.update();
    }

    // Metrics
    const portfolioReturn = parseFloat(data.portfolioTotalReturn || 0);
    const benchmarkReturn = parseFloat(data.benchmarkTotalReturn || 0);
    const excessReturn = parseFloat(data.excessReturn || 0);

    const $pRet = $('#benchmark-portfolio-return').text(formatPercent(portfolioReturn));
    applyValueClass($pRet, portfolioReturn);

    const $bRet = $('#benchmark-index-return').text(formatPercent(benchmarkReturn));
    applyValueClass($bRet, benchmarkReturn);

    const $eRet = $('#benchmark-excess-return').text(formatPercent(excessReturn));
    applyValueClass($eRet, excessReturn);

    // Highlight card
    const $card = $('#excess-return-card');
    $card.removeClass('highlight negative');
    if (excessReturn > 0) $card.addClass('highlight');
    else if (excessReturn < 0) $card.addClass('highlight negative');

    // Alpha/Beta
    const alpha = parseFloat(data.alpha || 0);
    const $alpha = $('#benchmark-alpha').text((alpha >= 0 ? '+' : '') + alpha.toFixed(4));
    applyValueClass($alpha, alpha);

    const beta = parseFloat(data.beta || 1);
    const $beta = $('#benchmark-beta').text(beta.toFixed(4));
    $beta.removeClass('text-positive text-warning text-negative');
    $beta.addClass(beta < 0.8 ? 'text-positive' : beta > 1.2 ? 'text-negative' : 'text-warning');

    $('#benchmark-correlation').text((data.correlation || 0).toFixed(4));
    $('#benchmark-info-ratio').text((data.informationRatio || 0).toFixed(4));
    $('#benchmark-tracking-error').text((data.trackingError || 0).toFixed(4) + '%');
    $('#benchmark-treynor').text((data.treynorRatio || 0).toFixed(4));
    $('#benchmark-rsquared').text((data.rSquared || 0).toFixed(4));
    $('#benchmark-portfolio-mdd').text('-' + (data.portfolioMaxDrawdown || 0).toFixed(2) + '%');
    $('#benchmark-index-mdd').text('-' + (data.benchmarkMaxDrawdown || 0).toFixed(2) + '%');
    $('#benchmark-win-months').text('승: ' + (data.portfolioWinMonths || 0) + '개월');
    $('#benchmark-lose-months').text('패: ' + (data.benchmarkWinMonths || 0) + '개월');
}

function showBenchmarkEmpty() {
    $('#benchmark-empty').removeClass('hidden');
    if (benchmarkComparisonChart) {
        benchmarkComparisonChart.data.labels = [];
        benchmarkComparisonChart.data.datasets[0].data = [];
        benchmarkComparisonChart.data.datasets[1].data = [];
        benchmarkComparisonChart.update();
    }
}

function updateBenchmark(period) {
    updatePeriodSelector('benchmark-period', period);
    loadBenchmarkComparison(period);
}

function generateSampleBenchmark() {
    const benchmark = $('#benchmark-select').val();
    const range = getDateRangeForApi('1Y');
    $.ajax({
        url: `${API_BASE_URL}/analysis/benchmark/generate-sample`,
        method: 'POST',
        data: { benchmark, ...range },
        success: (res) => { showToast(res.message || '샘플 데이터 생성됨'); loadBenchmarkComparison(currentBenchmarkPeriod); },
        error: (xhr) => showToast('샘플 데이터 생성 실패', 'error')
    });
}

// ==================== Treemap ====================
let currentTreemapPeriod = '1D';

function initializeTreemap() {
    loadPortfolioTreemap('1D');

    $('#treemap-period-selector button').on('click', function() {
        $('#treemap-period-selector button').removeClass('active');
        $(this).addClass('active');
        loadPortfolioTreemap($(this).data('period'));
    });

    let resizeTimeout;
    $(window).on('resize', () => {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(() => window.treemapData && renderTreemap(window.treemapData), 250);
    });
}

function loadPortfolioTreemap(period) {
    currentTreemapPeriod = period;
    $('#treemap-period-label').text(PERIOD_LABELS[period] || period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/portfolio/treemap`,
        method: 'GET',
        data: { period },
        success: (data) => {
            window.treemapData = data;
            updateTreemapSummary(data);
            renderTreemap(data);
            $('#treemap-empty').addClass('hidden');
            $('#portfolio-treemap').show();
        },
        error: () => {
            $('#portfolio-treemap').hide();
            $('#treemap-empty').removeClass('hidden');
        }
    });
}

function updateTreemapSummary(data) {
    $('#treemap-total-investment').text(formatCurrency(data.totalInvestment || 0));
    const avgPerf = parseFloat(data.totalPerformance || 0);
    const $perf = $('#treemap-avg-performance').text(formatPercent(avgPerf));
    applyValueClass($perf, avgPerf);
}

function renderTreemap(data) {
    const container = document.getElementById('portfolio-treemap');
    if (!container) return;

    const width = container.clientWidth;
    const height = 400;

    d3.select('#portfolio-treemap').selectAll('*').remove();

    if (!data.cells?.length) {
        $('#portfolio-treemap').hide();
        $('#treemap-empty').removeClass('hidden');
        return;
    }

    const svg = d3.select('#portfolio-treemap')
        .append('svg')
        .attr('width', width)
        .attr('height', height);

    const hierarchyData = {
        name: 'Portfolio',
        children: data.cells.map(cell => ({
            name: cell.symbol,
            fullName: cell.name || cell.symbol,
            value: Math.max(parseFloat(cell.investmentAmount) || 1, 1),
            performance: parseFloat(cell.performancePercent) || 0,
            currentPrice: parseFloat(cell.currentPrice) || 0,
            priceChange: parseFloat(cell.priceChange) || 0,
            sector: cell.sector || 'UNKNOWN',
            hasData: cell.hasData !== false
        }))
    };

    const treemap = d3.treemap()
        .size([width, height])
        .padding(3)
        .round(true);

    const root = d3.hierarchy(hierarchyData)
        .sum(d => d.value)
        .sort((a, b) => b.value - a.value);

    treemap(root);

    const cells = svg.selectAll('g')
        .data(root.leaves())
        .enter()
        .append('g')
        .attr('transform', d => `translate(${d.x0},${d.y0})`);

    cells.append('rect')
        .attr('width', d => Math.max(d.x1 - d.x0, 0))
        .attr('height', d => Math.max(d.y1 - d.y0, 0))
        .attr('fill', d => getTreemapColor(d.data.performance, d.data.hasData))
        .attr('stroke', 'rgba(255,255,255,0.1)')
        .attr('stroke-width', 1)
        .attr('rx', 6)
        .style('cursor', 'pointer')
        .on('mouseover', function(event, d) {
            showTreemapTooltip(event, d);
            d3.select(this).attr('stroke', '#fff').attr('stroke-width', 2);
        })
        .on('mouseout', function() {
            hideTreemapTooltip();
            d3.select(this).attr('stroke', 'rgba(255,255,255,0.1)').attr('stroke-width', 1);
        });

    cells.append('text')
        .attr('x', d => (d.x1 - d.x0) / 2)
        .attr('y', d => (d.y1 - d.y0) / 2 - 6)
        .attr('text-anchor', 'middle')
        .attr('fill', d => getTreemapTextColor(d.data.performance, d.data.hasData))
        .attr('font-size', d => Math.min(Math.max((d.x1 - d.x0) / 6, 9), 14) + 'px')
        .attr('font-weight', '600')
        .attr('font-family', "'Outfit', sans-serif")
        .text(d => {
            const w = d.x1 - d.x0;
            if (w < 40) return '';
            if (w < 60) return d.data.name.substring(0, 3);
            return d.data.name;
        });

    cells.append('text')
        .attr('x', d => (d.x1 - d.x0) / 2)
        .attr('y', d => (d.y1 - d.y0) / 2 + 10)
        .attr('text-anchor', 'middle')
        .attr('fill', d => getTreemapTextColor(d.data.performance, d.data.hasData))
        .attr('font-size', d => Math.min(Math.max((d.x1 - d.x0) / 7, 8), 12) + 'px')
        .attr('font-family', "'JetBrains Mono', monospace")
        .text(d => {
            const w = d.x1 - d.x0, h = d.y1 - d.y0;
            if (w < 50 || h < 35) return '';
            if (!d.data.hasData) return 'N/A';
            return (d.data.performance >= 0 ? '+' : '') + d.data.performance.toFixed(1) + '%';
        });
}

function getTreemapColor(perf, hasData) {
    if (!hasData || perf === null) return '#4a5568';
    const clamped = Math.max(-10, Math.min(10, perf));
    if (clamped < 0) {
        return d3.interpolateRgb('#4a5568', '#dc3545')(Math.abs(clamped) / 10);
    } else if (clamped > 0) {
        return d3.interpolateRgb('#4a5568', '#00f5a0')(clamped / 10);
    }
    return '#4a5568';
}

function getTreemapTextColor(perf, hasData) {
    if (!hasData || perf === null) return 'rgba(255,255,255,0.9)';
    return Math.abs(perf) > 4 ? '#fff' : 'rgba(255,255,255,0.95)';
}

function showTreemapTooltip(event, d) {
    d3.select('.treemap-tooltip-glass').remove();

    const perfText = d.data.hasData ? formatPercent(d.data.performance) : 'N/A';
    const perfColor = d.data.performance >= 0 ? '#00f5a0' : '#ff6b6b';
    const priceChangeText = d.data.priceChange >= 0 ? '+' + formatCurrency(d.data.priceChange) : formatCurrency(d.data.priceChange);

    const tooltip = d3.select('body')
        .append('div')
        .attr('class', 'treemap-tooltip-glass')
        .style('position', 'absolute')
        .style('background', 'rgba(20, 20, 40, 0.95)')
        .style('backdrop-filter', 'blur(12px)')
        .style('color', 'rgba(255,255,255,0.95)')
        .style('padding', '16px')
        .style('border-radius', '12px')
        .style('border', '1px solid rgba(255,255,255,0.1)')
        .style('font-size', '13px')
        .style('z-index', '10000')
        .style('pointer-events', 'none')
        .style('box-shadow', '0 8px 32px rgba(0,0,0,0.4)')
        .style('max-width', '280px')
        .style('font-family', "'Outfit', sans-serif");

    tooltip.html(`
        <div style="font-weight: 600; margin-bottom: 8px; font-size: 15px;">
            ${d.data.name} <span style="font-weight: 400; color: rgba(255,255,255,0.5);">(${d.data.fullName})</span>
        </div>
        <div style="margin-bottom: 6px;">
            <span style="color: rgba(255,255,255,0.5);">수익률:</span>
            <span style="color: ${perfColor}; font-weight: 600; font-family: 'JetBrains Mono';">${perfText}</span>
        </div>
        <div style="margin-bottom: 6px;">
            <span style="color: rgba(255,255,255,0.5);">투자금액:</span>
            <span style="font-family: 'JetBrains Mono';">${formatCurrency(d.value)}</span>
        </div>
        <div style="margin-bottom: 6px;">
            <span style="color: rgba(255,255,255,0.5);">현재가:</span>
            <span style="font-family: 'JetBrains Mono';">${formatCurrency(d.data.currentPrice)}</span>
            <span style="color: ${d.data.priceChange >= 0 ? '#00f5a0' : '#ff6b6b'}; font-size: 11px;">(${priceChangeText})</span>
        </div>
        <div>
            <span style="color: rgba(255,255,255,0.5);">섹터:</span>
            <span>${d.data.sector}</span>
        </div>
    `);

    const node = tooltip.node();
    const rect = node.getBoundingClientRect();
    let left = event.pageX + 15;
    let top = event.pageY - 10;

    if (left + rect.width > window.innerWidth) left = event.pageX - rect.width - 15;
    if (top + rect.height > window.innerHeight + window.scrollY) top = event.pageY - rect.height - 10;

    tooltip.style('left', left + 'px').style('top', top + 'px');
}

function hideTreemapTooltip() {
    d3.select('.treemap-tooltip-glass').remove();
}

// ==================== PDF Reports ====================
function downloadReport(type) {
    let url = '';
    const today = new Date();

    switch(type) {
        case 'ytd': url = `${API_BASE_URL}/reports/portfolio/pdf/ytd`; break;
        case 'monthly': url = `${API_BASE_URL}/reports/portfolio/pdf/monthly?year=${today.getFullYear()}&month=${today.getMonth() + 1}`; break;
        case 'yearly': url = `${API_BASE_URL}/reports/portfolio/pdf/yearly?year=${today.getFullYear()}`; break;
        default: return;
    }
    downloadPdfReport(url);
}

function downloadCustomReport() {
    const startDate = $('#reportStartDate').val();
    const endDate = $('#reportEndDate').val();

    if (!startDate || !endDate) {
        showToast('시작일과 종료일을 선택해주세요', 'warning');
        return;
    }
    if (new Date(startDate) > new Date(endDate)) {
        showToast('시작일이 종료일보다 늦을 수 없습니다', 'warning');
        return;
    }

    downloadPdfReport(`${API_BASE_URL}/reports/portfolio/pdf?startDate=${startDate}&endDate=${endDate}`);
    closeModal('customReportModal');
}

function downloadPdfReport(url) {
    showToast('리포트 생성 중...', 'info');

    fetch(url, { method: 'GET', headers: { 'Accept': 'application/pdf' } })
        .then(response => {
            if (!response.ok) throw new Error('리포트 생성 실패');
            const disposition = response.headers.get('Content-Disposition');
            let filename = 'portfolio_report.pdf';
            if (disposition) {
                const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (match?.[1]) filename = match[1].replace(/['"]/g, '');
            }
            return response.blob().then(blob => ({ blob, filename }));
        })
        .then(({ blob, filename }) => {
            const downloadUrl = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = downloadUrl;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(downloadUrl);
            showToast('리포트 다운로드 완료', 'success');
        })
        .catch(error => {
            console.error('PDF download error:', error);
            showToast('PDF 리포트 생성 실패', 'error');
        });
}