/**
 * 커스텀 대시보드 JavaScript
 * @fileoverview Dashboard widget management system with drag-and-drop support.
 * Provides customizable widgets for portfolio overview, performance charts,
 * and trading statistics.
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Default widget dimensions
 * @constant {Object}
 */
const WIDGET_DEFAULTS = {
    WIDTH: 1,
    HEIGHT: 1
};

/**
 * API endpoints for dashboard operations
 * @constant {Object}
 */
const DASHBOARD_API = {
    CONFIG: '/api/dashboard-config',
    WIDGETS_POSITIONS: '/api/dashboard-config/widgets/positions',
    AVAILABLE_WIDGETS: '/api/dashboard-config/available-widgets',
    RESET: '/api/dashboard-config/reset',
    PORTFOLIO_SUMMARY: '/api/portfolio/summary',
    EQUITY_CURVE: '/api/analysis/equity-curve',
    DRAWDOWN: '/api/analysis/drawdown',
    MONTHLY_RETURNS: '/api/analysis/monthly-returns',
    RECENT_TRANSACTIONS: '/api/transactions/recent',
    GOALS_SUMMARY: '/api/goals/summary',
    ALERTS_UNREAD: '/api/alerts/unread-count',
    STATISTICS: '/api/analysis/statistics'
};

/**
 * Chart color configurations
 * @constant {Object}
 */
const CHART_COLORS = {
    PRIMARY: '#667eea',
    PRIMARY_BG: 'rgba(102, 126, 234, 0.1)',
    DANGER: '#dc3545',
    DANGER_BG: 'rgba(220, 53, 69, 0.1)',
    SUCCESS: '#28a745',
    PIE_COLORS: [
        '#667eea', '#764ba2', '#f093fb', '#f5576c', '#4facfe',
        '#00f2fe', '#43e97b', '#38f9d7', '#fa709a', '#fee140',
        '#30cfd0', '#330867', '#667eea', '#764ba2'
    ]
};

/**
 * Common chart options
 * @constant {Object}
 */
const CHART_OPTIONS = {
    RESPONSIVE: {
        responsive: true,
        maintainAspectRatio: false
    },
    NO_LEGEND: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } }
    }
};

/**
 * Messages for UI display
 * @constant {Object}
 */
const MESSAGES = {
    LOAD_FAILED: '데이터 로드 실패',
    NO_DATA: '데이터가 없습니다',
    NO_HOLDINGS: '보유 종목이 없습니다',
    NO_TRANSACTIONS: '최근 거래가 없습니다',
    NO_GOALS: '설정된 목표가 없습니다',
    UNSUPPORTED_WIDGET: '지원되지 않는 위젯입니다',
    CONFIG_LOAD_FAILED: '대시보드 설정을 불러오는데 실패했습니다',
    LAYOUT_SAVED: '레이아웃이 저장되었습니다',
    LAYOUT_SAVE_FAILED: '레이아웃 저장 실패',
    RESET_CONFIRM: '대시보드 설정을 기본값으로 초기화하시겠습니까?',
    RESET_SUCCESS: '대시보드가 초기화되었습니다',
    RESET_FAILED: '초기화 실패',
    WIDGET_ADDED: '위젯이 추가되었습니다',
    WIDGET_ADD_FAILED: '위젯 추가 실패',
    WIDGET_REMOVE_CONFIRM: '이 위젯을 제거하시겠습니까?',
    WIDGET_REMOVED: '위젯이 제거되었습니다',
    WIDGET_REMOVE_FAILED: '위젯 제거 실패',
    SETTINGS_SAVED: '위젯 설정이 저장되었습니다',
    SETTINGS_SAVE_FAILED: '설정 저장 실패'
};

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Dashboard state object containing all mutable state
 * @type {Object}
 */
const dashboardState = {
    /** @type {Object|null} Current dashboard configuration */
    config: null,
    /** @type {boolean} Whether edit mode is active */
    editMode: false,
    /** @type {Object|null} Currently selected widget for settings */
    selectedWidget: null,
    /** @type {Object.<string, Chart>} Map of widget keys to Chart instances */
    charts: {}
};

// ============================================================================
// WIDGET REGISTRY
// ============================================================================

/**
 * Registry mapping widget types to their loader functions.
 * Each loader receives (body, widgetKey?) and renders content.
 * @type {Object.<string, Function>}
 */
const WIDGET_LOADERS = {
    'PORTFOLIO_SUMMARY': loadPortfolioSummary,
    'TODAY_PERFORMANCE': loadTodayPerformance,
    'PROFIT_LOSS_CARD': loadProfitLoss,
    'HOLDINGS_COUNT': loadHoldingsCount,
    'EQUITY_CURVE': loadEquityCurve,
    'DRAWDOWN_CHART': loadDrawdownChart,
    'ALLOCATION_PIE': loadAllocationPie,
    'MONTHLY_RETURNS': loadMonthlyReturns,
    'HOLDINGS_LIST': loadHoldingsList,
    'RECENT_TRANSACTIONS': loadRecentTransactions,
    'TOP_PERFORMERS': loadTopPerformers,
    'WORST_PERFORMERS': loadWorstPerformers,
    'GOALS_PROGRESS': loadGoalsProgress,
    'ACTIVE_ALERTS': loadActiveAlerts,
    'RISK_METRICS': loadRiskMetrics,
    'TRADING_STATS': loadTradingStats,
    'STREAK_INDICATOR': loadStreakIndicator
};

// ============================================================================
// INITIALIZATION
// ============================================================================

$(document).ready(function() {
    init();
});

/**
 * Initialize the dashboard
 */
function init() {
    loadDashboardConfig();
    bindEvents();
}

/**
 * Bind event handlers to dashboard controls
 */
function bindEvents() {
    $('#btnEditMode').click(toggleEditMode);
    $('#btnSaveLayout').click(saveLayout);
    $('#btnResetLayout').click(resetLayout);
    $('#btnAddWidget').click(showAddWidgetPanel);
    $('#btnCancelAdd').click(hideAddWidgetPanel);
    $('#btnSaveWidgetSettings').click(saveWidgetSettings);
}

// ============================================================================
// DASHBOARD CONFIGURATION
// ============================================================================

/**
 * Load dashboard configuration from server
 */
function loadDashboardConfig() {
    $.get(DASHBOARD_API.CONFIG)
        .done(function(data) {
            dashboardState.config = data;
            renderDashboard();
        })
        .fail(function(err) {
            console.error('대시보드 설정 로드 실패:', err);
            showToast(MESSAGES.CONFIG_LOAD_FAILED, 'error');
        });
}

/**
 * Render the dashboard by creating widget elements
 */
function renderDashboard() {
    const grid = $('#dashboardGrid');
    grid.empty();

    if (!dashboardState.config || !dashboardState.config.widgets) return;

    dashboardState.config.widgets.forEach(widget => {
        if (widget.visible !== false) {
            const widgetEl = createWidgetElement(widget);
            grid.append(widgetEl);
            loadWidgetContent(widget);
        }
    });
}

// ============================================================================
// WIDGET CREATION
// ============================================================================

/**
 * Create a widget DOM element
 * @param {Object} widget - Widget configuration object
 * @param {string} widget.widgetKey - Unique widget identifier
 * @param {string} widget.widgetType - Type of widget
 * @param {number} widget.width - Grid column span
 * @param {number} widget.height - Grid row span
 * @param {string} widget.iconClass - CSS class for widget icon
 * @param {string} widget.title - Widget display title
 * @returns {jQuery} jQuery element representing the widget
 */
function createWidgetElement(widget) {
    const el = $(`
        <div class="widget"
             data-widget-key="${widget.widgetKey}"
             data-widget-type="${widget.widgetType}"
             style="grid-column: span ${widget.width}; grid-row: span ${widget.height};">
            <div class="widget-header" draggable="true">
                <h6><i class="${widget.iconClass} me-2"></i>${widget.title}</h6>
                <div class="widget-actions">
                    <button class="btn-settings" title="설정"><i class="bi bi-gear"></i></button>
                    <button class="btn-remove" title="제거"><i class="bi bi-x"></i></button>
                </div>
            </div>
            <div class="widget-body" id="widget-body-${widget.widgetKey}">
                <div class="text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">로딩중...</span>
                    </div>
                </div>
            </div>
            <div class="resize-handle" style="display: none;"></div>
        </div>
    `);

    el.find('.btn-settings').click(() => openWidgetSettings(widget));
    el.find('.btn-remove').click(() => removeWidget(widget.widgetKey));
    setupDragAndDrop(el, widget);

    return el;
}

/**
 * Load content for a widget based on its type
 * @param {Object} widget - Widget configuration object
 */
function loadWidgetContent(widget) {
    const bodyId = `widget-body-${widget.widgetKey}`;
    const body = $(`#${bodyId}`);
    const loader = WIDGET_LOADERS[widget.widgetType];

    if (loader) {
        loader(body, widget.widgetKey);
    } else {
        body.html(`<div class="text-muted text-center py-4">${MESSAGES.UNSUPPORTED_WIDGET}</div>`);
    }
}

// ============================================================================
// WIDGET LOADERS - CARD WIDGETS
// ============================================================================

/**
 * Render error message in widget body
 * @param {jQuery} body - Widget body element
 */
function renderLoadError(body) {
    body.html(`<div class="text-muted">${MESSAGES.LOAD_FAILED}</div>`);
}

/**
 * Render empty state message in widget body
 * @param {jQuery} body - Widget body element
 * @param {string} message - Message to display
 */
function renderEmptyState(body, message) {
    body.html(`<div class="text-muted text-center py-4">${message}</div>`);
}

/**
 * Load portfolio summary widget
 * @param {jQuery} body - Widget body element
 */
function loadPortfolioSummary(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            const pnlClass = data.totalPnl >= 0 ? 'positive' : 'negative';
            body.html(`
                <div class="stat-value">${formatCurrency(data.totalValue)}</div>
                <div class="stat-label">총 평가금액</div>
                <hr>
                <div class="row text-center">
                    <div class="col-6">
                        <div class="small text-muted">투자원금</div>
                        <div class="fw-bold">${formatCurrency(data.totalInvested)}</div>
                    </div>
                    <div class="col-6">
                        <div class="small text-muted">총 손익</div>
                        <div class="fw-bold ${pnlClass}">${formatCurrency(data.totalPnl)}</div>
                    </div>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load today's performance widget
 * @param {jQuery} body - Widget body element
 */
function loadTodayPerformance(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            const todayPnl = data.dailyPnl || 0;
            const todayPct = data.dailyReturn || 0;
            const pnlClass = todayPnl >= 0 ? 'positive' : 'negative';
            const badgeClass = todayPct >= 0 ? 'bg-success' : 'bg-danger';
            const sign = todayPnl >= 0 ? '+' : '';

            body.html(`
                <div class="stat-value ${pnlClass}">${sign}${formatCurrency(todayPnl)}</div>
                <div class="stat-label">오늘의 손익</div>
                <div class="mt-2">
                    <span class="badge ${badgeClass} fs-6">${sign}${todayPct.toFixed(2)}%</span>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load total profit/loss widget
 * @param {jQuery} body - Widget body element
 */
function loadProfitLoss(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            const pnlClass = data.totalPnl >= 0 ? 'positive' : 'negative';
            const badgeClass = data.totalPnlPercent >= 0 ? 'bg-success' : 'bg-danger';
            const sign = data.totalPnl >= 0 ? '+' : '';
            const pct = (data.totalPnlPercent || 0).toFixed(2);

            body.html(`
                <div class="stat-value ${pnlClass}">${sign}${formatCurrency(data.totalPnl)}</div>
                <div class="stat-label">총 손익</div>
                <div class="mt-2">
                    <span class="badge ${badgeClass}">${sign}${pct}%</span>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load holdings count widget
 * @param {jQuery} body - Widget body element
 */
function loadHoldingsCount(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            const count = data.holdings ? data.holdings.length : 0;
            body.html(`
                <div class="stat-value">${count}</div>
                <div class="stat-label">보유 종목 수</div>
            `);
        })
        .fail(() => renderLoadError(body));
}

// ============================================================================
// WIDGET LOADERS - CHART WIDGETS
// ============================================================================

/**
 * Create a chart in the widget body
 * @param {jQuery} body - Widget body element
 * @param {string} key - Widget key for canvas ID
 * @param {Object} chartConfig - Chart.js configuration object
 */
function createChart(body, key, chartConfig) {
    body.html(`<canvas id="chart-${key}"></canvas>`);
    const ctx = document.getElementById(`chart-${key}`);

    if (dashboardState.charts[key]) {
        dashboardState.charts[key].destroy();
    }

    dashboardState.charts[key] = new Chart(ctx, chartConfig);
}

/**
 * Load equity curve chart widget
 * @param {jQuery} body - Widget body element
 * @param {string} key - Widget key
 */
function loadEquityCurve(body, key) {
    body.html(`<canvas id="chart-${key}"></canvas>`);

    $.get(DASHBOARD_API.EQUITY_CURVE)
        .done(data => {
            createChart(body, key, {
                type: 'line',
                data: {
                    labels: data.labels || [],
                    datasets: [{
                        label: '누적 수익률',
                        data: data.values || [],
                        borderColor: CHART_COLORS.PRIMARY,
                        backgroundColor: CHART_COLORS.PRIMARY_BG,
                        fill: true,
                        tension: 0.4
                    }]
                },
                options: CHART_OPTIONS.NO_LEGEND
            });
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load drawdown chart widget
 * @param {jQuery} body - Widget body element
 * @param {string} key - Widget key
 */
function loadDrawdownChart(body, key) {
    body.html(`<canvas id="chart-${key}"></canvas>`);

    $.get(DASHBOARD_API.DRAWDOWN)
        .done(data => {
            createChart(body, key, {
                type: 'line',
                data: {
                    labels: data.labels || [],
                    datasets: [{
                        label: 'Drawdown',
                        data: data.values || [],
                        borderColor: CHART_COLORS.DANGER,
                        backgroundColor: CHART_COLORS.DANGER_BG,
                        fill: true,
                        tension: 0.4
                    }]
                },
                options: CHART_OPTIONS.NO_LEGEND
            });
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load allocation pie chart widget
 * @param {jQuery} body - Widget body element
 * @param {string} key - Widget key
 */
function loadAllocationPie(body, key) {
    body.html(`<canvas id="chart-${key}"></canvas>`);

    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            if (!data.holdings || data.holdings.length === 0) {
                renderEmptyState(body, MESSAGES.NO_HOLDINGS);
                return;
            }

            createChart(body, key, {
                type: 'doughnut',
                data: {
                    labels: data.holdings.map(h => h.stockName || h.symbol),
                    datasets: [{
                        data: data.holdings.map(h => h.currentValue || h.marketValue),
                        backgroundColor: CHART_COLORS.PIE_COLORS.slice(0, data.holdings.length)
                    }]
                },
                options: CHART_OPTIONS.RESPONSIVE
            });
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load monthly returns bar chart widget
 * @param {jQuery} body - Widget body element
 * @param {string} key - Widget key
 */
function loadMonthlyReturns(body, key) {
    body.html(`<canvas id="chart-${key}"></canvas>`);

    $.get(DASHBOARD_API.MONTHLY_RETURNS)
        .done(data => {
            const values = data.values || [];

            createChart(body, key, {
                type: 'bar',
                data: {
                    labels: data.labels || [],
                    datasets: [{
                        label: '월별 수익률',
                        data: values,
                        backgroundColor: values.map(v => v >= 0 ? CHART_COLORS.SUCCESS : CHART_COLORS.DANGER)
                    }]
                },
                options: CHART_OPTIONS.NO_LEGEND
            });
        })
        .fail(() => renderLoadError(body));
}

// ============================================================================
// WIDGET LOADERS - LIST WIDGETS
// ============================================================================

/**
 * Load holdings list widget
 * @param {jQuery} body - Widget body element
 */
function loadHoldingsList(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            if (!data.holdings || data.holdings.length === 0) {
                renderEmptyState(body, MESSAGES.NO_HOLDINGS);
                return;
            }

            let html = '<div class="table-responsive"><table class="table table-sm mb-0">';
            html += '<thead><tr><th>종목</th><th class="text-end">평가금액</th><th class="text-end">손익</th></tr></thead><tbody>';

            data.holdings.slice(0, 5).forEach(h => {
                const pnl = h.unrealizedPnl || h.pnl || 0;
                const pnlClass = pnl >= 0 ? 'positive' : 'negative';
                html += `<tr>
                    <td>${h.stockName || h.symbol}</td>
                    <td class="text-end">${formatCurrency(h.currentValue || h.marketValue)}</td>
                    <td class="text-end ${pnlClass}">${formatCurrency(pnl)}</td>
                </tr>`;
            });

            html += '</tbody></table></div>';

            if (data.holdings.length > 5) {
                html += `<div class="text-center mt-2"><small class="text-muted">외 ${data.holdings.length - 5}종목</small></div>`;
            }

            body.html(html);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load recent transactions widget
 * @param {jQuery} body - Widget body element
 */
function loadRecentTransactions(body) {
    $.get(`${DASHBOARD_API.RECENT_TRANSACTIONS}?limit=5`)
        .done(data => {
            if (!data || data.length === 0) {
                renderEmptyState(body, MESSAGES.NO_TRANSACTIONS);
                return;
            }

            let html = '<div class="list-group list-group-flush">';

            data.forEach(tx => {
                const isBuy = tx.transactionType === 'BUY';
                const badgeClass = isBuy ? 'bg-primary' : 'bg-danger';
                const label = isBuy ? '매수' : '매도';

                html += `
                    <div class="list-group-item px-0 py-2">
                        <div class="d-flex justify-content-between">
                            <div>
                                <span class="badge ${badgeClass}">${label}</span>
                                <span class="ms-2">${tx.stockName || tx.symbol}</span>
                            </div>
                            <small class="text-muted">${formatDate(tx.transactionDate)}</small>
                        </div>
                    </div>
                `;
            });

            html += '</div>';
            body.html(html);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load top performers widget
 * @param {jQuery} body - Widget body element
 */
function loadTopPerformers(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            if (!data.holdings || data.holdings.length === 0) {
                renderEmptyState(body, MESSAGES.NO_HOLDINGS);
                return;
            }

            const sorted = [...data.holdings].sort((a, b) =>
                (b.unrealizedPnlPercent || 0) - (a.unrealizedPnlPercent || 0)
            );
            const top3 = sorted.slice(0, 3);

            let html = '<div class="list-group list-group-flush">';

            top3.forEach((h, i) => {
                const pct = h.unrealizedPnlPercent || 0;
                html += `
                    <div class="list-group-item px-0 py-2 d-flex justify-content-between align-items-center">
                        <div>
                            <span class="badge bg-warning text-dark me-2">${i + 1}</span>
                            ${h.stockName || h.symbol}
                        </div>
                        <span class="positive fw-bold">+${pct.toFixed(2)}%</span>
                    </div>
                `;
            });

            html += '</div>';
            body.html(html);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load worst performers widget
 * @param {jQuery} body - Widget body element
 */
function loadWorstPerformers(body) {
    $.get(DASHBOARD_API.PORTFOLIO_SUMMARY)
        .done(data => {
            if (!data.holdings || data.holdings.length === 0) {
                renderEmptyState(body, MESSAGES.NO_HOLDINGS);
                return;
            }

            const sorted = [...data.holdings].sort((a, b) =>
                (a.unrealizedPnlPercent || 0) - (b.unrealizedPnlPercent || 0)
            );
            const worst3 = sorted.slice(0, 3);

            let html = '<div class="list-group list-group-flush">';

            worst3.forEach((h, i) => {
                const pct = h.unrealizedPnlPercent || 0;
                const pctClass = pct >= 0 ? 'positive' : 'negative';
                const sign = pct >= 0 ? '+' : '';

                html += `
                    <div class="list-group-item px-0 py-2 d-flex justify-content-between align-items-center">
                        <div>
                            <span class="badge bg-secondary me-2">${i + 1}</span>
                            ${h.stockName || h.symbol}
                        </div>
                        <span class="${pctClass} fw-bold">${sign}${pct.toFixed(2)}%</span>
                    </div>
                `;
            });

            html += '</div>';
            body.html(html);
        })
        .fail(() => renderLoadError(body));
}

// ============================================================================
// WIDGET LOADERS - STATS WIDGETS
// ============================================================================

/**
 * Load goals progress widget
 * @param {jQuery} body - Widget body element
 */
function loadGoalsProgress(body) {
    $.get(DASHBOARD_API.GOALS_SUMMARY)
        .done(data => {
            if (!data.totalGoals || data.totalGoals === 0) {
                renderEmptyState(body, MESSAGES.NO_GOALS);
                return;
            }

            const rate = (data.achievementRate || 0).toFixed(1);

            body.html(`
                <div class="text-center mb-3">
                    <div class="stat-value">${data.achievedGoals || 0}/${data.totalGoals}</div>
                    <div class="stat-label">달성 목표</div>
                </div>
                <div class="progress" style="height: 10px;">
                    <div class="progress-bar bg-success" style="width: ${data.achievementRate || 0}%"></div>
                </div>
                <div class="text-center mt-2">
                    <small class="text-muted">${rate}% 달성</small>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load active alerts widget
 * @param {jQuery} body - Widget body element
 */
function loadActiveAlerts(body) {
    $.get(DASHBOARD_API.ALERTS_UNREAD)
        .done(data => {
            const count = data.count || data || 0;

            body.html(`
                <div class="text-center">
                    <div class="stat-value">${count}</div>
                    <div class="stat-label">읽지 않은 알림</div>
                    <a href="alerts.html" class="btn btn-sm btn-outline-primary mt-3">알림 보기</a>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load risk metrics widget
 * @param {jQuery} body - Widget body element
 */
function loadRiskMetrics(body) {
    $.get(DASHBOARD_API.STATISTICS)
        .done(data => {
            body.html(`
                <div class="row text-center">
                    <div class="col-6 mb-3">
                        <div class="fw-bold">${(data.sharpeRatio || 0).toFixed(2)}</div>
                        <small class="text-muted">샤프 비율</small>
                    </div>
                    <div class="col-6 mb-3">
                        <div class="fw-bold negative">${(data.maxDrawdown || 0).toFixed(2)}%</div>
                        <small class="text-muted">최대 낙폭</small>
                    </div>
                    <div class="col-6">
                        <div class="fw-bold">${(data.volatility || 0).toFixed(2)}%</div>
                        <small class="text-muted">변동성</small>
                    </div>
                    <div class="col-6">
                        <div class="fw-bold">${(data.winRate || 0).toFixed(1)}%</div>
                        <small class="text-muted">승률</small>
                    </div>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load trading stats widget
 * @param {jQuery} body - Widget body element
 */
function loadTradingStats(body) {
    $.get(DASHBOARD_API.STATISTICS)
        .done(data => {
            body.html(`
                <div class="row text-center">
                    <div class="col-6 mb-2">
                        <div class="fw-bold">${data.totalTrades || 0}</div>
                        <small class="text-muted">총 거래</small>
                    </div>
                    <div class="col-6 mb-2">
                        <div class="fw-bold">${data.winningTrades || 0}</div>
                        <small class="text-muted">수익 거래</small>
                    </div>
                    <div class="col-6">
                        <div class="fw-bold positive">${formatCurrency(data.avgProfit || 0)}</div>
                        <small class="text-muted">평균 수익</small>
                    </div>
                    <div class="col-6">
                        <div class="fw-bold negative">${formatCurrency(data.avgLoss || 0)}</div>
                        <small class="text-muted">평균 손실</small>
                    </div>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

/**
 * Load streak indicator widget
 * @param {jQuery} body - Widget body element
 */
function loadStreakIndicator(body) {
    $.get(DASHBOARD_API.STATISTICS)
        .done(data => {
            const streak = data.currentStreak || 0;
            const isWin = streak >= 0;
            const streakClass = isWin ? 'positive' : 'negative';
            const label = isWin ? '연승' : '연패';

            body.html(`
                <div class="text-center">
                    <div class="stat-value ${streakClass}">${Math.abs(streak)}</div>
                    <div class="stat-label">${label} 중</div>
                </div>
            `);
        })
        .fail(() => renderLoadError(body));
}

// ============================================================================
// DRAG AND DROP
// ============================================================================

/**
 * Setup drag and drop functionality for a widget
 * @param {jQuery} el - Widget jQuery element
 * @param {Object} widget - Widget configuration object
 */
function setupDragAndDrop(el, widget) {
    const header = el.find('.widget-header')[0];

    header.addEventListener('dragstart', function(e) {
        if (!dashboardState.editMode) {
            e.preventDefault();
            return;
        }
        el.addClass('dragging');
        e.dataTransfer.setData('text/plain', widget.widgetKey);
    });

    header.addEventListener('dragend', function() {
        el.removeClass('dragging');
    });

    el[0].addEventListener('dragover', function(e) {
        if (!dashboardState.editMode) return;
        e.preventDefault();
    });

    el[0].addEventListener('drop', function(e) {
        if (!dashboardState.editMode) return;
        e.preventDefault();
        const draggedKey = e.dataTransfer.getData('text/plain');
        if (draggedKey && draggedKey !== widget.widgetKey) {
            swapWidgets(draggedKey, widget.widgetKey);
        }
    });
}

/**
 * Swap the display order of two widgets
 * @param {string} key1 - First widget key
 * @param {string} key2 - Second widget key
 */
function swapWidgets(key1, key2) {
    const widgets = dashboardState.config.widgets;
    const widget1 = widgets.find(w => w.widgetKey === key1);
    const widget2 = widgets.find(w => w.widgetKey === key2);

    if (widget1 && widget2) {
        const tempOrder = widget1.displayOrder;
        widget1.displayOrder = widget2.displayOrder;
        widget2.displayOrder = tempOrder;

        widgets.sort((a, b) => a.displayOrder - b.displayOrder);
        renderDashboard();
    }
}

// ============================================================================
// EDIT MODE
// ============================================================================

/**
 * Toggle dashboard edit mode
 */
function toggleEditMode() {
    dashboardState.editMode = !dashboardState.editMode;

    const grid = $('#dashboardGrid');
    const btn = $('#btnEditMode');
    const saveBtn = $('#btnSaveLayout');

    if (dashboardState.editMode) {
        grid.addClass('edit-mode');
        btn.removeClass('btn-outline-secondary').addClass('btn-secondary');
        btn.html('<i class="bi bi-x-lg me-1"></i>편집 취소');
        saveBtn.removeClass('d-none');
        $('.resize-handle').show();
    } else {
        grid.removeClass('edit-mode');
        btn.removeClass('btn-secondary').addClass('btn-outline-secondary');
        btn.html('<i class="bi bi-pencil me-1"></i>편집 모드');
        saveBtn.addClass('d-none');
        $('.resize-handle').hide();
        loadDashboardConfig();
    }
}

/**
 * Save current layout to server
 */
function saveLayout() {
    $.ajax({
        url: DASHBOARD_API.WIDGETS_POSITIONS,
        method: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify(dashboardState.config.widgets)
    })
    .done(function(data) {
        dashboardState.config = data;
        showToast(MESSAGES.LAYOUT_SAVED, 'success');
        toggleEditMode();
    })
    .fail(function() {
        showToast(MESSAGES.LAYOUT_SAVE_FAILED, 'error');
    });
}

/**
 * Reset layout to default configuration
 */
function resetLayout() {
    if (!confirm(MESSAGES.RESET_CONFIRM)) return;

    $.post(DASHBOARD_API.RESET)
        .done(function(data) {
            dashboardState.config = data;
            renderDashboard();
            showToast(MESSAGES.RESET_SUCCESS, 'success');
        })
        .fail(function() {
            showToast(MESSAGES.RESET_FAILED, 'error');
        });
}

// ============================================================================
// ADD WIDGET PANEL
// ============================================================================

/**
 * Show the add widget panel with available widgets
 */
function showAddWidgetPanel() {
    $.get(DASHBOARD_API.AVAILABLE_WIDGETS)
        .done(function(widgets) {
            const container = $('#availableWidgets');
            container.empty();

            const existingTypes = dashboardState.config.widgets.map(w => w.widgetType);

            widgets.forEach(widget => {
                const isAdded = existingTypes.includes(widget.widgetType);
                const opacityClass = isAdded ? 'opacity-50' : '';
                const titleAttr = isAdded ? 'title="이미 추가됨"' : '';
                const addedLabel = isAdded ? '<small class="text-muted">추가됨</small>' : '';

                container.append(`
                    <div class="col-6 col-md-4 col-lg-3">
                        <div class="widget-option ${opacityClass}"
                             data-widget-type="${widget.widgetType}"
                             ${titleAttr}>
                            <i class="${widget.iconClass} d-block"></i>
                            <div class="name">${widget.widgetTypeLabel}</div>
                            ${addedLabel}
                        </div>
                    </div>
                `);
            });

            container.find('.widget-option:not(.opacity-50)').click(function() {
                const type = $(this).data('widget-type');
                addWidget(type);
            });

            $('#addWidgetPanel').removeClass('d-none');
        });
}

/**
 * Hide the add widget panel
 */
function hideAddWidgetPanel() {
    $('#addWidgetPanel').addClass('d-none');
}

/**
 * Add a new widget to the dashboard
 * @param {string} widgetType - Type of widget to add
 */
function addWidget(widgetType) {
    $.ajax({
        url: `${DASHBOARD_API.CONFIG}/widgets`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ widgetType: widgetType })
    })
    .done(function(data) {
        dashboardState.config = data;
        renderDashboard();
        hideAddWidgetPanel();
        showToast(MESSAGES.WIDGET_ADDED, 'success');
    })
    .fail(function() {
        showToast(MESSAGES.WIDGET_ADD_FAILED, 'error');
    });
}

/**
 * Remove a widget from the dashboard
 * @param {string} widgetKey - Key of widget to remove
 */
function removeWidget(widgetKey) {
    if (!confirm(MESSAGES.WIDGET_REMOVE_CONFIRM)) return;

    $.ajax({
        url: `${DASHBOARD_API.CONFIG}/widgets/${widgetKey}`,
        method: 'DELETE'
    })
    .done(function(data) {
        dashboardState.config = data;
        renderDashboard();
        showToast(MESSAGES.WIDGET_REMOVED, 'success');
    })
    .fail(function() {
        showToast(MESSAGES.WIDGET_REMOVE_FAILED, 'error');
    });
}

// ============================================================================
// WIDGET SETTINGS MODAL
// ============================================================================

/**
 * Open widget settings modal
 * @param {Object} widget - Widget configuration object
 */
function openWidgetSettings(widget) {
    dashboardState.selectedWidget = widget;
    $('#widgetTitle').val(widget.title);
    $('#widgetWidth').val(widget.width);
    $('#widgetHeight').val(widget.height);
    new bootstrap.Modal('#widgetSettingsModal').show();
}

/**
 * Save widget settings from modal
 */
function saveWidgetSettings() {
    const widget = dashboardState.selectedWidget;
    if (!widget) return;

    widget.title = $('#widgetTitle').val();
    widget.width = parseInt($('#widgetWidth').val()) || widget.width;
    widget.height = parseInt($('#widgetHeight').val()) || widget.height;

    $.ajax({
        url: DASHBOARD_API.CONFIG,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(dashboardState.config)
    })
    .done(function(data) {
        dashboardState.config = data;
        renderDashboard();
        bootstrap.Modal.getInstance('#widgetSettingsModal').hide();
        showToast(MESSAGES.SETTINGS_SAVED, 'success');
    })
    .fail(function() {
        showToast(MESSAGES.SETTINGS_SAVE_FAILED, 'error');
    });
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Show a toast notification.
 * Uses ToastNotification from utils.js if available, otherwise falls back to Bootstrap toast.
 * @param {string} message - Message to display
 * @param {string} type - Notification type ('success', 'error', 'info')
 */
function showToast(message, type) {
    if (typeof ToastNotification !== 'undefined') {
        ToastNotification.show(message, type);
        return;
    }

    const bgClass = type === 'error' ? 'bg-danger' : type === 'success' ? 'bg-success' : 'bg-info';
    const toast = $(`
        <div class="toast align-items-center text-white ${bgClass} border-0 position-fixed bottom-0 end-0 m-3" role="alert">
            <div class="d-flex">
                <div class="toast-body">${message}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `);
    $('body').append(toast);
    const bsToast = new bootstrap.Toast(toast[0]);
    bsToast.show();
    toast.on('hidden.bs.toast', () => toast.remove());
}
