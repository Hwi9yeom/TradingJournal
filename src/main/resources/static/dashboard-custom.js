/**
 * 커스텀 대시보드 JavaScript
 */
$(document).ready(function() {
    let dashboardConfig = null;
    let editMode = false;
    let selectedWidget = null;
    let charts = {};

    // 초기화
    init();

    function init() {
        loadDashboardConfig();
        bindEvents();
    }

    function bindEvents() {
        $('#btnEditMode').click(toggleEditMode);
        $('#btnSaveLayout').click(saveLayout);
        $('#btnResetLayout').click(resetLayout);
        $('#btnAddWidget').click(showAddWidgetPanel);
        $('#btnCancelAdd').click(hideAddWidgetPanel);
        $('#btnSaveWidgetSettings').click(saveWidgetSettings);
    }

    // 대시보드 설정 로드
    function loadDashboardConfig() {
        $.get('/api/dashboard-config')
            .done(function(data) {
                dashboardConfig = data;
                renderDashboard();
            })
            .fail(function(err) {
                console.error('대시보드 설정 로드 실패:', err);
                showToast('대시보드 설정을 불러오는데 실패했습니다', 'error');
            });
    }

    // 대시보드 렌더링
    function renderDashboard() {
        const grid = $('#dashboardGrid');
        grid.empty();

        if (!dashboardConfig || !dashboardConfig.widgets) return;

        dashboardConfig.widgets.forEach(widget => {
            if (widget.visible !== false) {
                const widgetEl = createWidgetElement(widget);
                grid.append(widgetEl);
                loadWidgetContent(widget);
            }
        });
    }

    // 위젯 요소 생성
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

        // 이벤트 바인딩
        el.find('.btn-settings').click(() => openWidgetSettings(widget));
        el.find('.btn-remove').click(() => removeWidget(widget.widgetKey));

        // 드래그 앤 드롭
        setupDragAndDrop(el, widget);

        return el;
    }

    // 위젯 콘텐츠 로드
    function loadWidgetContent(widget) {
        const bodyId = `widget-body-${widget.widgetKey}`;
        const body = $(`#${bodyId}`);

        switch(widget.widgetType) {
            case 'PORTFOLIO_SUMMARY':
                loadPortfolioSummary(body);
                break;
            case 'TODAY_PERFORMANCE':
                loadTodayPerformance(body);
                break;
            case 'PROFIT_LOSS_CARD':
                loadProfitLoss(body);
                break;
            case 'HOLDINGS_COUNT':
                loadHoldingsCount(body);
                break;
            case 'EQUITY_CURVE':
                loadEquityCurve(body, widget.widgetKey);
                break;
            case 'DRAWDOWN_CHART':
                loadDrawdownChart(body, widget.widgetKey);
                break;
            case 'ALLOCATION_PIE':
                loadAllocationPie(body, widget.widgetKey);
                break;
            case 'MONTHLY_RETURNS':
                loadMonthlyReturns(body, widget.widgetKey);
                break;
            case 'HOLDINGS_LIST':
                loadHoldingsList(body);
                break;
            case 'RECENT_TRANSACTIONS':
                loadRecentTransactions(body);
                break;
            case 'TOP_PERFORMERS':
                loadTopPerformers(body);
                break;
            case 'WORST_PERFORMERS':
                loadWorstPerformers(body);
                break;
            case 'GOALS_PROGRESS':
                loadGoalsProgress(body);
                break;
            case 'ACTIVE_ALERTS':
                loadActiveAlerts(body);
                break;
            case 'RISK_METRICS':
                loadRiskMetrics(body);
                break;
            case 'TRADING_STATS':
                loadTradingStats(body);
                break;
            case 'STREAK_INDICATOR':
                loadStreakIndicator(body);
                break;
            default:
                body.html('<div class="text-muted text-center py-4">지원되지 않는 위젯입니다</div>');
        }
    }

    // 위젯 콘텐츠 로더들
    function loadPortfolioSummary(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
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
                            <div class="fw-bold ${data.totalPnl >= 0 ? 'positive' : 'negative'}">
                                ${formatCurrency(data.totalPnl)}
                            </div>
                        </div>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadTodayPerformance(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                const todayPnl = data.dailyPnl || 0;
                const todayPct = data.dailyReturn || 0;
                body.html(`
                    <div class="stat-value ${todayPnl >= 0 ? 'positive' : 'negative'}">
                        ${todayPnl >= 0 ? '+' : ''}${formatCurrency(todayPnl)}
                    </div>
                    <div class="stat-label">오늘의 손익</div>
                    <div class="mt-2">
                        <span class="badge ${todayPct >= 0 ? 'bg-success' : 'bg-danger'} fs-6">
                            ${todayPct >= 0 ? '+' : ''}${todayPct.toFixed(2)}%
                        </span>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadProfitLoss(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                body.html(`
                    <div class="stat-value ${data.totalPnl >= 0 ? 'positive' : 'negative'}">
                        ${data.totalPnl >= 0 ? '+' : ''}${formatCurrency(data.totalPnl)}
                    </div>
                    <div class="stat-label">총 손익</div>
                    <div class="mt-2">
                        <span class="badge ${data.totalPnlPercent >= 0 ? 'bg-success' : 'bg-danger'}">
                            ${data.totalPnlPercent >= 0 ? '+' : ''}${(data.totalPnlPercent || 0).toFixed(2)}%
                        </span>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadHoldingsCount(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                const count = data.holdings ? data.holdings.length : 0;
                body.html(`
                    <div class="stat-value">${count}</div>
                    <div class="stat-label">보유 종목 수</div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadEquityCurve(body, key) {
        body.html(`<canvas id="chart-${key}"></canvas>`);
        $.get('/api/analysis/equity-curve')
            .done(data => {
                const ctx = document.getElementById(`chart-${key}`);
                if (charts[key]) charts[key].destroy();
                charts[key] = new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: data.labels || [],
                        datasets: [{
                            label: '누적 수익률',
                            data: data.values || [],
                            borderColor: '#667eea',
                            backgroundColor: 'rgba(102, 126, 234, 0.1)',
                            fill: true,
                            tension: 0.4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } }
                    }
                });
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadDrawdownChart(body, key) {
        body.html(`<canvas id="chart-${key}"></canvas>`);
        $.get('/api/analysis/drawdown')
            .done(data => {
                const ctx = document.getElementById(`chart-${key}`);
                if (charts[key]) charts[key].destroy();
                charts[key] = new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: data.labels || [],
                        datasets: [{
                            label: 'Drawdown',
                            data: data.values || [],
                            borderColor: '#dc3545',
                            backgroundColor: 'rgba(220, 53, 69, 0.1)',
                            fill: true,
                            tension: 0.4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } }
                    }
                });
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadAllocationPie(body, key) {
        body.html(`<canvas id="chart-${key}"></canvas>`);
        $.get('/api/portfolio/summary')
            .done(data => {
                if (!data.holdings || data.holdings.length === 0) {
                    body.html('<div class="text-muted text-center py-4">보유 종목이 없습니다</div>');
                    return;
                }
                const ctx = document.getElementById(`chart-${key}`);
                if (charts[key]) charts[key].destroy();
                charts[key] = new Chart(ctx, {
                    type: 'doughnut',
                    data: {
                        labels: data.holdings.map(h => h.stockName || h.symbol),
                        datasets: [{
                            data: data.holdings.map(h => h.currentValue || h.marketValue),
                            backgroundColor: generateColors(data.holdings.length)
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false
                    }
                });
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadMonthlyReturns(body, key) {
        body.html(`<canvas id="chart-${key}"></canvas>`);
        $.get('/api/analysis/monthly-returns')
            .done(data => {
                const ctx = document.getElementById(`chart-${key}`);
                if (charts[key]) charts[key].destroy();
                charts[key] = new Chart(ctx, {
                    type: 'bar',
                    data: {
                        labels: data.labels || [],
                        datasets: [{
                            label: '월별 수익률',
                            data: data.values || [],
                            backgroundColor: (data.values || []).map(v => v >= 0 ? '#28a745' : '#dc3545')
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } }
                    }
                });
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadHoldingsList(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                if (!data.holdings || data.holdings.length === 0) {
                    body.html('<div class="text-muted text-center py-4">보유 종목이 없습니다</div>');
                    return;
                }
                let html = '<div class="table-responsive"><table class="table table-sm mb-0">';
                html += '<thead><tr><th>종목</th><th class="text-end">평가금액</th><th class="text-end">손익</th></tr></thead><tbody>';
                data.holdings.slice(0, 5).forEach(h => {
                    const pnl = h.unrealizedPnl || h.pnl || 0;
                    html += `<tr>
                        <td>${h.stockName || h.symbol}</td>
                        <td class="text-end">${formatCurrency(h.currentValue || h.marketValue)}</td>
                        <td class="text-end ${pnl >= 0 ? 'positive' : 'negative'}">${formatCurrency(pnl)}</td>
                    </tr>`;
                });
                html += '</tbody></table></div>';
                if (data.holdings.length > 5) {
                    html += `<div class="text-center mt-2"><small class="text-muted">외 ${data.holdings.length - 5}종목</small></div>`;
                }
                body.html(html);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadRecentTransactions(body) {
        $.get('/api/transactions/recent?limit=5')
            .done(data => {
                if (!data || data.length === 0) {
                    body.html('<div class="text-muted text-center py-4">최근 거래가 없습니다</div>');
                    return;
                }
                let html = '<div class="list-group list-group-flush">';
                data.forEach(tx => {
                    const isBuy = tx.transactionType === 'BUY';
                    html += `
                        <div class="list-group-item px-0 py-2">
                            <div class="d-flex justify-content-between">
                                <div>
                                    <span class="badge ${isBuy ? 'bg-primary' : 'bg-danger'}">${isBuy ? '매수' : '매도'}</span>
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
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadTopPerformers(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                if (!data.holdings || data.holdings.length === 0) {
                    body.html('<div class="text-muted text-center py-4">보유 종목이 없습니다</div>');
                    return;
                }
                const sorted = [...data.holdings].sort((a, b) => (b.unrealizedPnlPercent || 0) - (a.unrealizedPnlPercent || 0));
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
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadWorstPerformers(body) {
        $.get('/api/portfolio/summary')
            .done(data => {
                if (!data.holdings || data.holdings.length === 0) {
                    body.html('<div class="text-muted text-center py-4">보유 종목이 없습니다</div>');
                    return;
                }
                const sorted = [...data.holdings].sort((a, b) => (a.unrealizedPnlPercent || 0) - (b.unrealizedPnlPercent || 0));
                const worst3 = sorted.slice(0, 3);
                let html = '<div class="list-group list-group-flush">';
                worst3.forEach((h, i) => {
                    const pct = h.unrealizedPnlPercent || 0;
                    html += `
                        <div class="list-group-item px-0 py-2 d-flex justify-content-between align-items-center">
                            <div>
                                <span class="badge bg-secondary me-2">${i + 1}</span>
                                ${h.stockName || h.symbol}
                            </div>
                            <span class="${pct >= 0 ? 'positive' : 'negative'} fw-bold">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</span>
                        </div>
                    `;
                });
                html += '</div>';
                body.html(html);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadGoalsProgress(body) {
        $.get('/api/goals/summary')
            .done(data => {
                if (!data.totalGoals || data.totalGoals === 0) {
                    body.html('<div class="text-muted text-center py-4">설정된 목표가 없습니다</div>');
                    return;
                }
                body.html(`
                    <div class="text-center mb-3">
                        <div class="stat-value">${data.achievedGoals || 0}/${data.totalGoals}</div>
                        <div class="stat-label">달성 목표</div>
                    </div>
                    <div class="progress" style="height: 10px;">
                        <div class="progress-bar bg-success" style="width: ${data.achievementRate || 0}%"></div>
                    </div>
                    <div class="text-center mt-2">
                        <small class="text-muted">${(data.achievementRate || 0).toFixed(1)}% 달성</small>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadActiveAlerts(body) {
        $.get('/api/alerts/unread-count')
            .done(data => {
                body.html(`
                    <div class="text-center">
                        <div class="stat-value">${data.count || data || 0}</div>
                        <div class="stat-label">읽지 않은 알림</div>
                        <a href="alerts.html" class="btn btn-sm btn-outline-primary mt-3">알림 보기</a>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadRiskMetrics(body) {
        $.get('/api/analysis/statistics')
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
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadTradingStats(body) {
        $.get('/api/analysis/statistics')
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
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    function loadStreakIndicator(body) {
        // 스트릭 정보는 별도 API가 없으면 통계에서 유추
        $.get('/api/analysis/statistics')
            .done(data => {
                const streak = data.currentStreak || 0;
                const isWin = streak >= 0;
                body.html(`
                    <div class="text-center">
                        <div class="stat-value ${isWin ? 'positive' : 'negative'}">
                            ${Math.abs(streak)}
                        </div>
                        <div class="stat-label">${isWin ? '연승' : '연패'} 중</div>
                    </div>
                `);
            })
            .fail(() => body.html('<div class="text-muted">데이터 로드 실패</div>'));
    }

    // 드래그 앤 드롭 설정
    function setupDragAndDrop(el, widget) {
        const header = el.find('.widget-header')[0];

        header.addEventListener('dragstart', function(e) {
            if (!editMode) {
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
            if (!editMode) return;
            e.preventDefault();
        });

        el[0].addEventListener('drop', function(e) {
            if (!editMode) return;
            e.preventDefault();
            const draggedKey = e.dataTransfer.getData('text/plain');
            if (draggedKey && draggedKey !== widget.widgetKey) {
                swapWidgets(draggedKey, widget.widgetKey);
            }
        });
    }

    // 위젯 순서 교환
    function swapWidgets(key1, key2) {
        const widget1 = dashboardConfig.widgets.find(w => w.widgetKey === key1);
        const widget2 = dashboardConfig.widgets.find(w => w.widgetKey === key2);

        if (widget1 && widget2) {
            const tempOrder = widget1.displayOrder;
            widget1.displayOrder = widget2.displayOrder;
            widget2.displayOrder = tempOrder;

            dashboardConfig.widgets.sort((a, b) => a.displayOrder - b.displayOrder);
            renderDashboard();
        }
    }

    // 편집 모드 토글
    function toggleEditMode() {
        editMode = !editMode;
        const grid = $('#dashboardGrid');
        const btn = $('#btnEditMode');
        const saveBtn = $('#btnSaveLayout');

        if (editMode) {
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
            loadDashboardConfig(); // 변경 취소
        }
    }

    // 레이아웃 저장
    function saveLayout() {
        $.ajax({
            url: '/api/dashboard-config/widgets/positions',
            method: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(dashboardConfig.widgets)
        })
        .done(function(data) {
            dashboardConfig = data;
            showToast('레이아웃이 저장되었습니다', 'success');
            toggleEditMode();
        })
        .fail(function() {
            showToast('레이아웃 저장 실패', 'error');
        });
    }

    // 레이아웃 초기화
    function resetLayout() {
        if (!confirm('대시보드 설정을 기본값으로 초기화하시겠습니까?')) return;

        $.post('/api/dashboard-config/reset')
            .done(function(data) {
                dashboardConfig = data;
                renderDashboard();
                showToast('대시보드가 초기화되었습니다', 'success');
            })
            .fail(function() {
                showToast('초기화 실패', 'error');
            });
    }

    // 위젯 추가 패널 표시
    function showAddWidgetPanel() {
        $.get('/api/dashboard-config/available-widgets')
            .done(function(widgets) {
                const container = $('#availableWidgets');
                container.empty();

                // 이미 추가된 위젯 타입 목록
                const existingTypes = dashboardConfig.widgets.map(w => w.widgetType);

                widgets.forEach(widget => {
                    const isAdded = existingTypes.includes(widget.widgetType);
                    container.append(`
                        <div class="col-6 col-md-4 col-lg-3">
                            <div class="widget-option ${isAdded ? 'opacity-50' : ''}"
                                 data-widget-type="${widget.widgetType}"
                                 ${isAdded ? 'title="이미 추가됨"' : ''}>
                                <i class="${widget.iconClass} d-block"></i>
                                <div class="name">${widget.widgetTypeLabel}</div>
                                ${isAdded ? '<small class="text-muted">추가됨</small>' : ''}
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

    function hideAddWidgetPanel() {
        $('#addWidgetPanel').addClass('d-none');
    }

    // 위젯 추가
    function addWidget(widgetType) {
        $.ajax({
            url: '/api/dashboard-config/widgets',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ widgetType: widgetType })
        })
        .done(function(data) {
            dashboardConfig = data;
            renderDashboard();
            hideAddWidgetPanel();
            showToast('위젯이 추가되었습니다', 'success');
        })
        .fail(function() {
            showToast('위젯 추가 실패', 'error');
        });
    }

    // 위젯 제거
    function removeWidget(widgetKey) {
        if (!confirm('이 위젯을 제거하시겠습니까?')) return;

        $.ajax({
            url: `/api/dashboard-config/widgets/${widgetKey}`,
            method: 'DELETE'
        })
        .done(function(data) {
            dashboardConfig = data;
            renderDashboard();
            showToast('위젯이 제거되었습니다', 'success');
        })
        .fail(function() {
            showToast('위젯 제거 실패', 'error');
        });
    }

    // 위젯 설정 열기
    function openWidgetSettings(widget) {
        selectedWidget = widget;
        $('#widgetTitle').val(widget.title);
        $('#widgetWidth').val(widget.width);
        $('#widgetHeight').val(widget.height);
        new bootstrap.Modal('#widgetSettingsModal').show();
    }

    // 위젯 설정 저장
    function saveWidgetSettings() {
        if (!selectedWidget) return;

        selectedWidget.title = $('#widgetTitle').val();
        selectedWidget.width = parseInt($('#widgetWidth').val()) || selectedWidget.width;
        selectedWidget.height = parseInt($('#widgetHeight').val()) || selectedWidget.height;

        $.ajax({
            url: '/api/dashboard-config',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(dashboardConfig)
        })
        .done(function(data) {
            dashboardConfig = data;
            renderDashboard();
            bootstrap.Modal.getInstance('#widgetSettingsModal').hide();
            showToast('위젯 설정이 저장되었습니다', 'success');
        })
        .fail(function() {
            showToast('설정 저장 실패', 'error');
        });
    }

    // 유틸리티 함수들
    function formatCurrency(value) {
        if (value == null) return '₩0';
        return new Intl.NumberFormat('ko-KR', {
            style: 'currency',
            currency: 'KRW',
            maximumFractionDigits: 0
        }).format(value);
    }

    function formatDate(dateStr) {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('ko-KR');
    }

    function generateColors(count) {
        const colors = [
            '#667eea', '#764ba2', '#f093fb', '#f5576c', '#4facfe',
            '#00f2fe', '#43e97b', '#38f9d7', '#fa709a', '#fee140',
            '#30cfd0', '#330867', '#667eea', '#764ba2'
        ];
        return colors.slice(0, count);
    }

    function showToast(message, type) {
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
});
