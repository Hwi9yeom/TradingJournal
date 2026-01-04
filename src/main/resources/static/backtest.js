const API_BASE_URL = '/api';
let equityChart, drawdownChart, monthlyChart;
let selectedStrategy = null;
let strategies = [];
let currentResult = null;

$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    // 기본 날짜 설정 (1년 전 ~ 오늘)
    const today = new Date();
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(today.getFullYear() - 1);

    $('#startDate').val(oneYearAgo.toISOString().split('T')[0]);
    $('#endDate').val(today.toISOString().split('T')[0]);

    // 포지션 크기 슬라이더
    $('#positionSize').on('input', function() {
        $('#positionSizeValue').text($(this).val());
    });

    // 전략 목록 로드
    loadStrategies();

    // 차트 초기화
    initializeCharts();
});

function loadStrategies() {
    $.ajax({
        url: `${API_BASE_URL}/backtest/strategies`,
        method: 'GET',
        success: function(data) {
            strategies = data;
            renderStrategies(data);
        },
        error: function(xhr) {
            console.error('전략 목록 로드 실패:', xhr);
        }
    });
}

function renderStrategies(strategies) {
    const $container = $('#strategyList');
    $container.empty();

    const icons = {
        'MOVING_AVERAGE': 'bi-graph-up',
        'RSI': 'bi-speedometer2',
        'BOLLINGER_BAND': 'bi-bar-chart',
        'MOMENTUM': 'bi-lightning',
        'MACD': 'bi-activity',
        'CUSTOM': 'bi-gear'
    };

    strategies.forEach((strategy, index) => {
        const icon = icons[strategy.type] || 'bi-diagram-3';
        const html = `
            <div class="card strategy-card mb-2" data-strategy="${strategy.type}" onclick="selectStrategy('${strategy.type}')">
                <div class="card-body py-2">
                    <div class="d-flex align-items-center">
                        <i class="bi ${icon} fs-4 text-primary me-3"></i>
                        <div>
                            <h6 class="mb-0">${strategy.label}</h6>
                            <small class="text-muted">${strategy.description}</small>
                        </div>
                    </div>
                </div>
            </div>
        `;
        $container.append(html);
    });
}

function selectStrategy(type) {
    selectedStrategy = type;

    // 카드 선택 상태 업데이트
    $('.strategy-card').removeClass('selected');
    $(`.strategy-card[data-strategy="${type}"]`).addClass('selected');

    // 파라미터 표시
    const strategy = strategies.find(s => s.type === type);
    if (strategy && strategy.parameters) {
        renderParameters(strategy.parameters);
        $('#strategyParams').show();
    } else {
        $('#strategyParams').hide();
    }
}

function renderParameters(params) {
    const $container = $('#paramInputs');
    $container.empty();

    const labels = {
        'shortPeriod': '단기 MA 기간',
        'longPeriod': '장기 MA 기간',
        'maType': 'MA 유형',
        'period': '기간',
        'overboughtLevel': '과매수 레벨',
        'oversoldLevel': '과매도 레벨',
        'stdDevMultiplier': '표준편차 배수',
        'entryThreshold': '진입 임계값 (%)',
        'exitThreshold': '청산 임계값 (%)'
    };

    for (const [key, value] of Object.entries(params)) {
        const label = labels[key] || key;

        if (key === 'maType') {
            $container.append(`
                <div class="mb-2 d-flex justify-content-between align-items-center">
                    <label class="form-label mb-0">${label}</label>
                    <select class="form-select form-select-sm param-input" data-param="${key}">
                        <option value="SMA" ${value === 'SMA' ? 'selected' : ''}>SMA</option>
                        <option value="EMA" ${value === 'EMA' ? 'selected' : ''}>EMA</option>
                    </select>
                </div>
            `);
        } else {
            $container.append(`
                <div class="mb-2 d-flex justify-content-between align-items-center">
                    <label class="form-label mb-0">${label}</label>
                    <input type="number" class="form-control form-control-sm param-input"
                           data-param="${key}" value="${value}" step="any">
                </div>
            `);
        }
    }
}

function getStrategyParams() {
    const params = {};
    $('#paramInputs [data-param]').each(function() {
        const key = $(this).data('param');
        let value = $(this).val();

        // 숫자로 변환 시도
        if (!isNaN(value) && value !== '') {
            value = parseFloat(value);
        }
        params[key] = value;
    });
    return params;
}

function runBacktest() {
    if (!selectedStrategy) {
        alert('전략을 선택해주세요.');
        return;
    }

    const request = {
        symbol: $('#symbol').val(),
        strategyType: selectedStrategy,
        strategyParams: getStrategyParams(),
        startDate: $('#startDate').val(),
        endDate: $('#endDate').val(),
        initialCapital: parseFloat($('#initialCapital').val()),
        positionSizePercent: parseFloat($('#positionSize').val()),
        commissionRate: parseFloat($('#commission').val()),
        slippage: parseFloat($('#slippage').val()),
        stopLossPercent: $('#stopLoss').val() ? parseFloat($('#stopLoss').val()) : null,
        takeProfitPercent: $('#takeProfit').val() ? parseFloat($('#takeProfit').val()) : null
    };

    $('#loadingOverlay').css('display', 'flex');

    $.ajax({
        url: `${API_BASE_URL}/backtest/run`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(data) {
            currentResult = data;
            displayResult(data);
            $('#loadingOverlay').hide();
        },
        error: function(xhr) {
            console.error('백테스트 실행 실패:', xhr);
            alert('백테스트 실행에 실패했습니다: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
            $('#loadingOverlay').hide();
        }
    });
}

function displayResult(result) {
    $('#noResultPanel').hide();
    $('#resultPanel').show();

    // 성과 요약 업데이트
    const returnClass = result.totalReturn >= 0 ? 'text-success' : 'text-danger';
    $('#totalReturn').text(formatPercent(result.totalReturn)).removeClass('text-success text-danger').addClass(returnClass);
    $('#cagr').text(formatPercent(result.cagr)).removeClass('text-success text-danger').addClass(returnClass);
    $('#maxDrawdown').text(formatPercent(-Math.abs(result.maxDrawdown)));
    $('#sharpeRatio').text(result.sharpeRatio ? result.sharpeRatio.toFixed(2) : '-');

    // 수익성 지표 테이블
    $('#profitabilityStats').html(`
        <tr><td>초기 자본금</td><td class="text-end">${formatCurrency(result.initialCapital)}</td></tr>
        <tr><td>최종 자본금</td><td class="text-end">${formatCurrency(result.finalCapital)}</td></tr>
        <tr><td>총 손익</td><td class="text-end ${result.totalProfit >= 0 ? 'text-success' : 'text-danger'}">${formatCurrency(result.totalProfit)}</td></tr>
        <tr><td>총 수익률</td><td class="text-end">${formatPercent(result.totalReturn)}</td></tr>
        <tr><td>CAGR</td><td class="text-end">${formatPercent(result.cagr)}</td></tr>
        <tr><td>최대 낙폭</td><td class="text-end text-danger">${formatPercent(-Math.abs(result.maxDrawdown))}</td></tr>
        <tr><td>샤프 비율</td><td class="text-end">${result.sharpeRatio?.toFixed(2) || '-'}</td></tr>
        <tr><td>소르티노 비율</td><td class="text-end">${result.sortinoRatio?.toFixed(2) || '-'}</td></tr>
        <tr><td>칼마 비율</td><td class="text-end">${result.calmarRatio?.toFixed(2) || '-'}</td></tr>
    `);

    // 거래 통계 테이블
    $('#tradeStats').html(`
        <tr><td>총 거래 횟수</td><td class="text-end">${result.totalTrades}회</td></tr>
        <tr><td>승리 거래</td><td class="text-end text-success">${result.winningTrades}회</td></tr>
        <tr><td>패배 거래</td><td class="text-end text-danger">${result.losingTrades}회</td></tr>
        <tr><td>승률</td><td class="text-end">${formatPercent(result.winRate)}</td></tr>
        <tr><td>평균 수익 거래</td><td class="text-end text-success">${formatCurrency(result.avgWin)}</td></tr>
        <tr><td>평균 손실 거래</td><td class="text-end text-danger">${formatCurrency(result.avgLoss)}</td></tr>
        <tr><td>손익비</td><td class="text-end">${result.profitFactor?.toFixed(2) || '-'}</td></tr>
        <tr><td>최대 연승</td><td class="text-end">${result.maxWinStreak}연승</td></tr>
        <tr><td>최대 연패</td><td class="text-end">${result.maxLossStreak}연패</td></tr>
        <tr><td>평균 보유 기간</td><td class="text-end">${result.avgHoldingDays?.toFixed(1) || '-'}일</td></tr>
    `);

    // 거래 목록
    $('#tradeCount').text(result.totalTrades + '건');
    const $tradeList = $('#tradeList');
    $tradeList.empty();

    if (result.trades && result.trades.length > 0) {
        result.trades.forEach(trade => {
            const rowClass = trade.profit >= 0 ? 'trade-win' : 'trade-loss';
            const profitClass = trade.profit >= 0 ? 'text-success' : 'text-danger';

            $tradeList.append(`
                <tr class="${rowClass}">
                    <td>${trade.tradeNumber}</td>
                    <td>${trade.entryDate}</td>
                    <td>${trade.exitDate}</td>
                    <td class="text-end">${formatCurrency(trade.entryPrice)}</td>
                    <td class="text-end">${formatCurrency(trade.exitPrice)}</td>
                    <td class="text-end ${profitClass}">${formatCurrency(trade.profit)}</td>
                    <td class="text-end ${profitClass}">${formatPercent(trade.profitPercent)}</td>
                    <td class="text-end">${trade.holdingDays}일</td>
                </tr>
            `);
        });
    } else {
        $tradeList.append('<tr><td colspan="8" class="text-center text-muted">거래 내역이 없습니다.</td></tr>');
    }

    // 차트 업데이트
    updateEquityChart(result);
    updateDrawdownChart(result);
    updateMonthlyChart(result);
}

function initializeCharts() {
    // 자산 곡선 차트
    const equityCtx = document.getElementById('equityChart').getContext('2d');
    equityChart = new Chart(equityCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: '전략',
                    data: [],
                    borderColor: '#0d6efd',
                    backgroundColor: 'rgba(13, 110, 253, 0.1)',
                    fill: true,
                    tension: 0.1
                },
                {
                    label: 'Buy & Hold',
                    data: [],
                    borderColor: '#6c757d',
                    borderDash: [5, 5],
                    fill: false,
                    tension: 0.1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top'
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return context.dataset.label + ': ' + formatCurrency(context.parsed.y);
                        }
                    }
                }
            },
            scales: {
                y: {
                    ticks: {
                        callback: function(value) {
                            return formatCurrencyShort(value);
                        }
                    }
                }
            }
        }
    });

    // 낙폭 차트
    const drawdownCtx = document.getElementById('drawdownChart').getContext('2d');
    drawdownChart = new Chart(drawdownCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Drawdown',
                data: [],
                borderColor: '#dc3545',
                backgroundColor: 'rgba(220, 53, 69, 0.3)',
                fill: true,
                tension: 0.1
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
                            return 'Drawdown: ' + context.parsed.y.toFixed(2) + '%';
                        }
                    }
                }
            },
            scales: {
                y: {
                    max: 0,
                    ticks: {
                        callback: function(value) {
                            return value + '%';
                        }
                    }
                }
            }
        }
    });

    // 월별 성과 차트
    const monthlyCtx = document.getElementById('monthlyChart').getContext('2d');
    monthlyChart = new Chart(monthlyCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: '월간 수익률',
                data: [],
                backgroundColor: []
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
                            return '수익률: ' + context.parsed.y.toFixed(2) + '%';
                        }
                    }
                }
            },
            scales: {
                y: {
                    ticks: {
                        callback: function(value) {
                            return value + '%';
                        }
                    }
                }
            }
        }
    });
}

function updateEquityChart(result) {
    equityChart.data.labels = result.equityLabels || [];
    equityChart.data.datasets[0].data = result.equityCurve || [];
    equityChart.data.datasets[1].data = result.benchmarkCurve || [];
    equityChart.update();
}

function updateDrawdownChart(result) {
    drawdownChart.data.labels = result.equityLabels || [];
    drawdownChart.data.datasets[0].data = result.drawdownCurve || [];
    drawdownChart.update();
}

function updateMonthlyChart(result) {
    if (!result.monthlyPerformance || result.monthlyPerformance.length === 0) {
        monthlyChart.data.labels = [];
        monthlyChart.data.datasets[0].data = [];
        monthlyChart.update();
        return;
    }

    monthlyChart.data.labels = result.monthlyPerformance.map(m => m.month);
    monthlyChart.data.datasets[0].data = result.monthlyPerformance.map(m => m.returnPct);
    monthlyChart.data.datasets[0].backgroundColor = result.monthlyPerformance.map(m =>
        m.returnPct >= 0 ? 'rgba(34, 197, 94, 0.8)' : 'rgba(239, 68, 68, 0.8)');
    monthlyChart.update();
}

function loadHistory() {
    $.ajax({
        url: `${API_BASE_URL}/backtest/history`,
        method: 'GET',
        success: function(data) {
            renderHistory(data);
        }
    });
}

function renderHistory(history) {
    const $list = $('#historyList');
    $list.empty();

    if (history.length === 0) {
        $list.append('<tr><td colspan="7" class="text-center text-muted">히스토리가 없습니다.</td></tr>');
        return;
    }

    history.forEach(item => {
        const returnClass = item.totalReturn >= 0 ? 'text-success' : 'text-danger';
        $list.append(`
            <tr>
                <td>${item.strategyName}</td>
                <td>${item.symbol}</td>
                <td>${item.startDate} ~ ${item.endDate}</td>
                <td class="text-end ${returnClass}">${formatPercent(item.totalReturn)}</td>
                <td class="text-end text-danger">${formatPercent(-Math.abs(item.maxDrawdown))}</td>
                <td>${formatDateTime(item.executedAt)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-primary" onclick="loadResult(${item.id})">
                        <i class="bi bi-eye"></i>
                    </button>
                </td>
            </tr>
        `);
    });
}

function loadResult(id) {
    $.ajax({
        url: `${API_BASE_URL}/backtest/${id}`,
        method: 'GET',
        success: function(data) {
            currentResult = data;
            displayResult(data);
            bootstrap.Modal.getInstance(document.getElementById('historyModal')).hide();
        }
    });
}

// 히스토리 모달 열릴 때 로드
$('#historyModal').on('show.bs.modal', function() {
    loadHistory();
});

// === Helper Functions ===

function formatCurrency(value) {
    if (value === null || value === undefined) return '-';
    const num = parseFloat(value);
    return '₩' + Math.round(num).toLocaleString();
}

function formatCurrencyShort(value) {
    const num = parseFloat(value);
    if (Math.abs(num) >= 100000000) {
        return (num / 100000000).toFixed(1) + '억';
    } else if (Math.abs(num) >= 10000) {
        return (num / 10000).toFixed(0) + '만';
    }
    return num.toLocaleString();
}

function formatPercent(value) {
    if (value === null || value === undefined) return '-';
    const num = parseFloat(value);
    return (num >= 0 ? '+' : '') + num.toFixed(2) + '%';
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleDateString('ko-KR') + ' ' + date.toLocaleTimeString('ko-KR', {hour: '2-digit', minute: '2-digit'});
}
