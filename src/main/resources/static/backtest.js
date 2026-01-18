const API_BASE_URL = '/api';
let equityChart, drawdownChart, monthlyChart;
let selectedStrategy = null;
let strategies = [];
let currentResult = null;
let templates = [];

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

    // 템플릿 목록 로드
    loadTemplates();

    // 차트 초기화
    initializeCharts();

    // 색상 선택 이벤트
    $(document).on('click', '.color-btn', function() {
        $(this).closest('.d-flex').find('.color-btn').removeClass('selected');
        $(this).addClass('selected');
    });
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

// === 최적화 기능 ===

let optimizationResult = null;

// 최적화 모달이 열릴 때 파라미터 범위 입력 UI 생성
$('#optimizeModal').on('show.bs.modal', function() {
    renderParamRangeInputs();
});

function renderParamRangeInputs() {
    const container = $('#paramRangeInputs');
    container.empty();

    if (!selectedStrategy) {
        container.html('<p class="text-muted">먼저 전략을 선택하세요.</p>');
        return;
    }

    const strategy = strategies.find(function(s) { return s.type === selectedStrategy; });
    if (!strategy || !strategy.parameters) {
        container.html('<p class="text-muted">파라미터가 없는 전략입니다.</p>');
        return;
    }

    const paramLabels = {
        'shortPeriod': { label: '단기 MA 기간', defaultMin: 5, defaultMax: 30, defaultStep: 5 },
        'longPeriod': { label: '장기 MA 기간', defaultMin: 20, defaultMax: 100, defaultStep: 10 },
        'maType': null,
        'period': { label: '기간', defaultMin: 5, defaultMax: 30, defaultStep: 5 },
        'overboughtLevel': { label: '과매수 레벨', defaultMin: 60, defaultMax: 80, defaultStep: 5 },
        'oversoldLevel': { label: '과매도 레벨', defaultMin: 20, defaultMax: 40, defaultStep: 5 },
        'stdDevMultiplier': { label: '표준편차 배수', defaultMin: 1.5, defaultMax: 3.0, defaultStep: 0.5 },
        'entryThreshold': { label: '진입 임계값 (%)', defaultMin: -5, defaultMax: 5, defaultStep: 1 },
        'exitThreshold': { label: '청산 임계값 (%)', defaultMin: -5, defaultMax: 5, defaultStep: 1 },
        'fastPeriod': { label: '단기 EMA 기간', defaultMin: 8, defaultMax: 16, defaultStep: 2 },
        'slowPeriod': { label: '장기 EMA 기간', defaultMin: 20, defaultMax: 32, defaultStep: 3 },
        'signalPeriod': { label: '시그널 기간', defaultMin: 5, defaultMax: 12, defaultStep: 1 }
    };

    container.append('<h6 class="mb-3">파라미터 범위 설정</h6>');

    for (var key in strategy.parameters) {
        if (!strategy.parameters.hasOwnProperty(key)) continue;

        var config = paramLabels[key];
        if (!config) continue;

        var defaultValue = strategy.parameters[key];
        var html = '<div class="card mb-2">' +
            '<div class="card-body py-2">' +
            '<div class="d-flex justify-content-between align-items-center mb-2">' +
            '<label class="form-label mb-0">' + config.label + '</label>' +
            '<div class="form-check">' +
            '<input class="form-check-input" type="checkbox" id="optimize_' + key + '" checked>' +
            '<label class="form-check-label" for="optimize_' + key + '">최적화</label>' +
            '</div>' +
            '</div>' +
            '<div class="row g-2">' +
            '<div class="col-4">' +
            '<input type="number" class="form-control form-control-sm" id="min_' + key + '" placeholder="최소" value="' + config.defaultMin + '">' +
            '</div>' +
            '<div class="col-4">' +
            '<input type="number" class="form-control form-control-sm" id="max_' + key + '" placeholder="최대" value="' + config.defaultMax + '">' +
            '</div>' +
            '<div class="col-4">' +
            '<input type="number" class="form-control form-control-sm" id="step_' + key + '" placeholder="단위" value="' + config.defaultStep + '">' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>';
        container.append(html);
    }

    // 예상 조합 수 표시
    container.append('<div class="alert alert-secondary mt-3" id="estimatedCombinations">' +
        '<i class="bi bi-calculator me-2"></i>예상 조합 수: <strong>계산 중...</strong>' +
        '</div>');

    updateEstimatedCombinations();

    // 범위 변경 시 조합 수 업데이트
    container.find('input').on('change', updateEstimatedCombinations);
}

function updateEstimatedCombinations() {
    var ranges = getParameterRanges();
    var total = 1;

    for (var key in ranges) {
        if (ranges.hasOwnProperty(key)) {
            var range = ranges[key];
            var count = Math.floor((range.max - range.min) / range.step) + 1;
            total *= count;
        }
    }

    $('#estimatedCombinations strong').text(total + '개');

    if (total > 1000) {
        $('#estimatedCombinations').removeClass('alert-secondary').addClass('alert-warning');
        $('#estimatedCombinations').html('<i class="bi bi-exclamation-triangle me-2"></i>예상 조합 수: <strong>' + total + '개</strong> (많은 시간이 소요될 수 있습니다)');
    } else {
        $('#estimatedCombinations').removeClass('alert-warning').addClass('alert-secondary');
    }
}

function getParameterRanges() {
    var ranges = {};

    $('#paramRangeInputs input[id^="optimize_"]').each(function() {
        if (!$(this).is(':checked')) return;

        var key = $(this).attr('id').replace('optimize_', '');
        var min = parseFloat($('#min_' + key).val());
        var max = parseFloat($('#max_' + key).val());
        var step = parseFloat($('#step_' + key).val());

        if (!isNaN(min) && !isNaN(max) && !isNaN(step) && step > 0) {
            ranges[key] = { min: min, max: max, step: step };
        }
    });

    return ranges;
}

function runOptimization() {
    if (!selectedStrategy) {
        alert('전략을 선택해주세요.');
        return;
    }

    var ranges = getParameterRanges();
    if (Object.keys(ranges).length === 0) {
        alert('최적화할 파라미터를 선택해주세요.');
        return;
    }

    var request = {
        symbol: $('#symbol').val(),
        strategyType: selectedStrategy,
        startDate: $('#startDate').val(),
        endDate: $('#endDate').val(),
        initialCapital: parseFloat($('#initialCapital').val()),
        positionSizePercent: parseFloat($('#positionSize').val()),
        commissionRate: parseFloat($('#commission').val()),
        slippage: parseFloat($('#slippage').val()),
        parameterRanges: ranges,
        target: $('#optimizationTarget').val()
    };

    // 모달 닫기
    bootstrap.Modal.getInstance(document.getElementById('optimizeModal')).hide();

    // 로딩 표시
    $('#loadingOverlay').css('display', 'flex');
    $('#loadingOverlay h5').text('파라미터 최적화 중...');

    $.ajax({
        url: API_BASE_URL + '/backtest/optimize',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(data) {
            optimizationResult = data;
            displayOptimizationResult(data);
            $('#loadingOverlay').hide();
            $('#loadingOverlay h5').text('백테스트 실행 중...');

            // 결과 모달 표시
            var resultModal = new bootstrap.Modal(document.getElementById('optimizeResultModal'));
            resultModal.show();
        },
        error: function(xhr) {
            console.error('최적화 실패:', xhr);
            alert('최적화 실행에 실패했습니다: ' + (xhr.responseJSON && xhr.responseJSON.message ? xhr.responseJSON.message : '알 수 없는 오류'));
            $('#loadingOverlay').hide();
            $('#loadingOverlay h5').text('백테스트 실행 중...');
        }
    });
}

function displayOptimizationResult(result) {
    // 최적 파라미터 표시
    var bestParamsHtml = '';
    for (var key in result.bestParameters) {
        if (result.bestParameters.hasOwnProperty(key)) {
            bestParamsHtml += '<div class="col-auto"><span class="badge bg-success fs-6">' + key + ': ' + result.bestParameters[key] + '</span></div>';
        }
    }
    $('#bestParams').html(bestParamsHtml);

    // 최적 결과 통계
    var best = result.bestResult;
    var returnClass = best.totalReturn >= 0 ? 'text-success' : 'text-danger';
    $('#bestStats').html(
        '<div class="col-md-3"><div class="text-muted">총 수익률</div><h4 class="' + returnClass + '">' + formatPercent(best.totalReturn) + '</h4></div>' +
        '<div class="col-md-3"><div class="text-muted">최대 낙폭</div><h4 class="text-danger">' + formatPercent(-Math.abs(best.maxDrawdown)) + '</h4></div>' +
        '<div class="col-md-3"><div class="text-muted">샤프 비율</div><h4>' + (best.sharpeRatio ? best.sharpeRatio.toFixed(2) : '-') + '</h4></div>' +
        '<div class="col-md-3"><div class="text-muted">승률</div><h4>' + formatPercent(best.winRate) + '</h4></div>'
    );

    // 조합 수
    $('#combinationCount').text(result.totalCombinations + '개 조합');

    // 실행 시간
    $('#optimizationTime').text('실행 시간: ' + (result.executionTimeMs / 1000).toFixed(1) + '초');

    // 모든 결과 정렬 (수익률 순)
    var sortedResults = result.allResults.slice().sort(function(a, b) {
        return (b.totalReturn || 0) - (a.totalReturn || 0);
    });

    // 모든 결과 테이블
    var listHtml = '';
    sortedResults.forEach(function(r, idx) {
        var paramStr = '';
        for (var key in r.parameters) {
            if (r.parameters.hasOwnProperty(key)) {
                paramStr += key + '=' + r.parameters[key] + ' ';
            }
        }
        var rowClass = (r.totalReturn || 0) >= 0 ? 'table-success' : 'table-danger';
        if (idx === 0) rowClass = 'table-warning';

        listHtml += '<tr class="' + rowClass + '">' +
            '<td>' + (idx + 1) + '</td>' +
            '<td><small>' + paramStr + '</small></td>' +
            '<td class="text-end">' + formatPercent(r.totalReturn) + '</td>' +
            '<td class="text-end">' + formatPercent(-Math.abs(r.maxDrawdown || 0)) + '</td>' +
            '<td class="text-end">' + (r.sharpeRatio ? r.sharpeRatio.toFixed(2) : '-') + '</td>' +
            '<td class="text-end">' + formatPercent(r.winRate) + '</td>' +
            '<td class="text-end">' + (r.totalTrades || 0) + '</td>' +
            '</tr>';
    });
    $('#allResultsList').html(listHtml);
}

function applyBestParams() {
    if (!optimizationResult || !optimizationResult.bestParameters) {
        return;
    }

    // 최적 파라미터를 입력 폼에 적용
    var params = optimizationResult.bestParameters;
    for (var key in params) {
        if (params.hasOwnProperty(key)) {
            var input = $('#paramInputs [data-param="' + key + '"]');
            if (input.length) {
                input.val(params[key]);
            }
        }
    }

    // 결과 모달 닫기
    bootstrap.Modal.getInstance(document.getElementById('optimizeResultModal')).hide();

    // 최적 결과로 메인 결과 패널 업데이트
    if (optimizationResult.bestResult) {
        currentResult = optimizationResult.bestResult;
        displayResult(optimizationResult.bestResult);
    }

    alert('최적 파라미터가 적용되었습니다.');
}

// === 템플릿 기능 ===

function loadTemplates() {
    $.ajax({
        url: `${API_BASE_URL}/templates/list`,
        method: 'GET',
        success: function(data) {
            templates = data;
            renderTemplateList(data);
        },
        error: function(xhr) {
            console.error('템플릿 목록 로드 실패:', xhr);
        }
    });
}

function renderTemplateList(templates) {
    const $container = $('#templateList');
    $container.empty();

    if (!templates || templates.length === 0) {
        $container.html('<div class="text-muted">템플릿이 없습니다.</div>');
        return;
    }

    templates.forEach(function(t) {
        const colorClass = t.color ? 'btn-' + t.color : 'btn-primary';
        const defaultBadge = t.isDefault ? '<i class="bi bi-star-fill text-warning ms-1"></i>' : '';
        const html = `
            <button class="btn btn-sm ${colorClass} template-btn"
                    data-id="${t.id}"
                    onclick="applyTemplate(${t.id})"
                    title="${t.strategyTypeLabel}">
                ${t.name}${defaultBadge}
            </button>
        `;
        $container.append(html);
    });
}

function applyTemplate(templateId) {
    $.ajax({
        url: `${API_BASE_URL}/templates/${templateId}/apply`,
        method: 'POST',
        success: function(config) {
            // 전략 선택
            if (config.strategyType) {
                selectStrategy(config.strategyType);
            }

            // 파라미터 적용
            if (config.parameters) {
                setTimeout(function() {
                    for (var key in config.parameters) {
                        if (config.parameters.hasOwnProperty(key)) {
                            var input = $('#paramInputs [data-param="' + key + '"]');
                            if (input.length) {
                                input.val(config.parameters[key]);
                            }
                        }
                    }
                }, 100);
            }

            // 리스크 설정 적용
            if (config.positionSizePercent) {
                $('#positionSize').val(config.positionSizePercent);
                $('#positionSizeValue').text(config.positionSizePercent);
            }
            if (config.stopLossPercent) {
                $('#stopLoss').val(config.stopLossPercent);
            }
            if (config.takeProfitPercent) {
                $('#takeProfit').val(config.takeProfitPercent);
            }
            if (config.commissionRate) {
                $('#commission').val(config.commissionRate);
            }

            // 템플릿 목록 새로고침 (사용 횟수 업데이트)
            loadTemplates();
        },
        error: function(xhr) {
            console.error('템플릿 적용 실패:', xhr);
            alert('템플릿 적용에 실패했습니다.');
        }
    });
}

function saveCurrentAsTemplate() {
    if (!selectedStrategy) {
        alert('먼저 전략을 선택해주세요.');
        return;
    }

    // 저장 모달 초기화
    $('#templateName').val('');
    $('#templateDescription').val('');
    $('#templateDefault').prop('checked', false);
    $('#colorPicker .color-btn').removeClass('selected');
    $('#colorPicker .color-btn[data-color="primary"]').addClass('selected');

    // 모달 표시
    var modal = new bootstrap.Modal(document.getElementById('saveTemplateModal'));
    modal.show();
}

function confirmSaveTemplate() {
    const name = $('#templateName').val().trim();
    if (!name) {
        alert('템플릿 이름을 입력해주세요.');
        return;
    }

    const selectedColor = $('#colorPicker .color-btn.selected').data('color') || 'primary';

    const request = {
        name: name,
        description: $('#templateDescription').val(),
        strategyType: selectedStrategy,
        parameters: getStrategyParams(),
        positionSizePercent: parseFloat($('#positionSize').val()),
        stopLossPercent: $('#stopLoss').val() ? parseFloat($('#stopLoss').val()) : null,
        takeProfitPercent: $('#takeProfit').val() ? parseFloat($('#takeProfit').val()) : null,
        commissionRate: parseFloat($('#commission').val()),
        isDefault: $('#templateDefault').is(':checked'),
        color: selectedColor
    };

    $.ajax({
        url: `${API_BASE_URL}/templates`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(data) {
            bootstrap.Modal.getInstance(document.getElementById('saveTemplateModal')).hide();
            loadTemplates();
            alert('템플릿이 저장되었습니다.');
        },
        error: function(xhr) {
            console.error('템플릿 저장 실패:', xhr);
            alert('템플릿 저장에 실패했습니다: ' + (xhr.responseJSON && xhr.responseJSON.message ? xhr.responseJSON.message : '알 수 없는 오류'));
        }
    });
}

// 템플릿 관리 모달이 열릴 때
$('#templateModal').on('show.bs.modal', function() {
    loadTemplateTable();
});

function loadTemplateTable() {
    $.ajax({
        url: `${API_BASE_URL}/templates`,
        method: 'GET',
        success: function(data) {
            renderTemplateTable(data);
        },
        error: function(xhr) {
            console.error('템플릿 목록 로드 실패:', xhr);
        }
    });
}

function renderTemplateTable(templates) {
    const $tbody = $('#templateTableBody');
    $tbody.empty();

    if (!templates || templates.length === 0) {
        $tbody.html('<tr><td colspan="5" class="text-center text-muted">템플릿이 없습니다.</td></tr>');
        return;
    }

    templates.forEach(function(t) {
        const defaultBadge = t.isDefault ? '<i class="bi bi-star-fill text-warning"></i>' : '';
        const colorBadge = t.color ? `<span class="badge bg-${t.color}">&nbsp;</span>` : '';

        $tbody.append(`
            <tr>
                <td>${colorBadge} ${t.name}</td>
                <td>${t.strategyTypeLabel || t.strategyType}</td>
                <td class="text-center">${defaultBadge}</td>
                <td class="text-center">${t.usageCount || 0}회</td>
                <td class="text-end">
                    <div class="btn-group btn-group-sm">
                        <button class="btn btn-outline-primary" onclick="editTemplate(${t.id})" title="수정">
                            <i class="bi bi-pencil"></i>
                        </button>
                        <button class="btn btn-outline-secondary" onclick="duplicateTemplate(${t.id})" title="복사">
                            <i class="bi bi-copy"></i>
                        </button>
                        <button class="btn btn-outline-danger" onclick="deleteTemplate(${t.id})" title="삭제">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `);
    });
}

function editTemplate(id) {
    $.ajax({
        url: `${API_BASE_URL}/templates/${id}`,
        method: 'GET',
        success: function(t) {
            $('#editTemplateId').val(t.id);
            $('#editTemplateName').val(t.name);
            $('#editTemplateDescription').val(t.description || '');
            $('#editTemplateDefault').prop('checked', t.isDefault);

            $('#editColorPicker .color-btn').removeClass('selected');
            var colorBtn = $('#editColorPicker .color-btn[data-color="' + (t.color || 'primary') + '"]');
            if (colorBtn.length) {
                colorBtn.addClass('selected');
            } else {
                $('#editColorPicker .color-btn[data-color="primary"]').addClass('selected');
            }

            // 템플릿 관리 모달 닫기
            bootstrap.Modal.getInstance(document.getElementById('templateModal')).hide();

            // 수정 모달 표시
            var modal = new bootstrap.Modal(document.getElementById('editTemplateModal'));
            modal.show();
        },
        error: function(xhr) {
            console.error('템플릿 로드 실패:', xhr);
            alert('템플릿 정보를 불러올 수 없습니다.');
        }
    });
}

function confirmEditTemplate() {
    const id = $('#editTemplateId').val();
    const name = $('#editTemplateName').val().trim();

    if (!name) {
        alert('템플릿 이름을 입력해주세요.');
        return;
    }

    const selectedColor = $('#editColorPicker .color-btn.selected').data('color') || 'primary';

    // 기존 템플릿 정보 가져오기
    $.ajax({
        url: `${API_BASE_URL}/templates/${id}`,
        method: 'GET',
        success: function(original) {
            const request = {
                name: name,
                description: $('#editTemplateDescription').val(),
                strategyType: original.strategyType,
                parameters: original.parameters,
                positionSizePercent: original.positionSizePercent,
                stopLossPercent: original.stopLossPercent,
                takeProfitPercent: original.takeProfitPercent,
                commissionRate: original.commissionRate,
                isDefault: $('#editTemplateDefault').is(':checked'),
                color: selectedColor
            };

            $.ajax({
                url: `${API_BASE_URL}/templates/${id}`,
                method: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify(request),
                success: function(data) {
                    bootstrap.Modal.getInstance(document.getElementById('editTemplateModal')).hide();
                    loadTemplates();

                    // 관리 모달 다시 열기
                    var modal = new bootstrap.Modal(document.getElementById('templateModal'));
                    modal.show();
                    loadTemplateTable();
                },
                error: function(xhr) {
                    console.error('템플릿 수정 실패:', xhr);
                    alert('템플릿 수정에 실패했습니다.');
                }
            });
        }
    });
}

function duplicateTemplate(id) {
    if (!confirm('이 템플릿을 복사하시겠습니까?')) return;

    $.ajax({
        url: `${API_BASE_URL}/templates/${id}/duplicate`,
        method: 'POST',
        success: function(data) {
            loadTemplates();
            loadTemplateTable();
        },
        error: function(xhr) {
            console.error('템플릿 복사 실패:', xhr);
            alert('템플릿 복사에 실패했습니다.');
        }
    });
}

function deleteTemplate(id) {
    if (!confirm('정말 이 템플릿을 삭제하시겠습니까?')) return;

    $.ajax({
        url: `${API_BASE_URL}/templates/${id}`,
        method: 'DELETE',
        success: function() {
            loadTemplates();
            loadTemplateTable();
        },
        error: function(xhr) {
            console.error('템플릿 삭제 실패:', xhr);
            alert('템플릿 삭제에 실패했습니다.');
        }
    });
}
