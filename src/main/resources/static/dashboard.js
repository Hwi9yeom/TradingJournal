const API_BASE_URL = '/api';

// ==================== 상수 정의 ====================
const CSS_CLASSES = {
    SUCCESS: 'text-success',
    DANGER: 'text-danger',
    WARNING: 'text-warning',
    MUTED: 'text-muted'
};

const PERIOD_MONTHS = {
    '1M': 1,
    '3M': 3,
    '6M': 6,
    '1Y': 12,
    'ALL': null  // ALL은 2020년부터
};

const ALL_PERIOD_START_YEAR = 2020;

// ==================== 유틸리티 함수 ====================

/**
 * 기간 문자열을 기반으로 시작일과 종료일을 반환합니다.
 * @param {string} period - 기간 문자열 ('1M', '3M', '6M', '1Y', 'ALL')
 * @returns {{startDate: Date, endDate: Date}} 시작일과 종료일 객체
 */
function getPeriodDates(period) {
    const endDate = new Date();
    const startDate = new Date();

    const months = PERIOD_MONTHS[period];
    if (months !== null && months !== undefined) {
        startDate.setMonth(endDate.getMonth() - months);
    } else {
        // 'ALL' 기간
        startDate.setFullYear(ALL_PERIOD_START_YEAR, 0, 1);
    }

    return { startDate, endDate };
}

/**
 * 날짜를 API 호출용 YYYY-MM-DD 형식으로 변환합니다.
 * @param {Date} date - 변환할 날짜 객체
 * @returns {string} YYYY-MM-DD 형식의 문자열
 */
function formatDateForApi(date) {
    return date.toISOString().split('T')[0];
}

/**
 * 기간에 따른 API용 날짜 범위를 반환합니다.
 * @param {string} period - 기간 문자열
 * @returns {{startDate: string, endDate: string}} API용 날짜 문자열 객체
 */
function getDateRangeForApi(period) {
    const { startDate, endDate } = getPeriodDates(period);
    return {
        startDate: formatDateForApi(startDate),
        endDate: formatDateForApi(endDate)
    };
}

/**
 * 값에 따라 jQuery 요소에 성공/실패 CSS 클래스를 적용합니다.
 * @param {jQuery} $element - 대상 jQuery 요소
 * @param {number} value - 판단 기준 값
 * @param {Object} options - 추가 옵션
 * @param {boolean} options.includeWarning - 경고 클래스 포함 여부
 * @param {number} options.warningThreshold - 경고 임계값
 */
function applyValueClass($element, value, options = {}) {
    const { includeWarning = false, warningThreshold = 0 } = options;

    $element.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER} ${CSS_CLASSES.WARNING} ${CSS_CLASSES.MUTED}`);

    if (includeWarning && value < 0 && value >= warningThreshold) {
        $element.addClass(CSS_CLASSES.WARNING);
    } else if (value >= 0) {
        $element.addClass(CSS_CLASSES.SUCCESS);
    } else {
        $element.addClass(CSS_CLASSES.DANGER);
    }
}

/**
 * 버튼 그룹에서 활성 버튼을 변경합니다.
 * @param {Event} event - 클릭 이벤트
 */
function updateButtonGroupActive(event) {
    $(event.target).closest('.btn-group').find('button').removeClass('active');
    event.target.classList.add('active');
}

/**
 * 임계값 기반으로 jQuery 요소에 CSS 클래스를 적용합니다.
 * @param {jQuery} $element - 대상 jQuery 요소
 * @param {number} value - 판단 기준 값
 * @param {number} successThreshold - 성공 클래스 임계값
 * @param {number} warningThreshold - 경고 클래스 임계값
 */
function applyThresholdClass($element, value, successThreshold, warningThreshold) {
    $element.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER} ${CSS_CLASSES.WARNING}`);

    if (value >= successThreshold) {
        $element.addClass(CSS_CLASSES.SUCCESS);
    } else if (value >= warningThreshold) {
        $element.addClass(CSS_CLASSES.WARNING);
    } else {
        $element.addClass(CSS_CLASSES.DANGER);
    }
}

// ==================== 차트 인스턴스 ====================
let assetValueChart, portfolioCompositionChart, monthlyReturnChart;
let equityCurveChart, drawdownChart;

$(document).ready(function() {
    // Check authentication first
    if (!checkAuth()) {
        return; // Will be redirected to login
    }

    loadDashboardData();
    initializeCharts();
    initializeAdvancedCharts();

    // Load advanced charts with default period
    loadEquityCurve('1Y');
    loadDrawdown('1Y');
    loadCorrelation('1Y');
    loadRiskMetrics('1Y');

    // Initialize Portfolio Treemap
    initializeTreemap();
});

function loadDashboardData() {
    // 포트폴리오 요약 데이터 로드
    $.ajax({
        url: `${API_BASE_URL}/portfolio/summary`,
        method: 'GET',
        success: function(data) {
            updateSummaryCards(data);
            updatePortfolioComposition(data.holdings);
            updateTopPerformers(data.holdings);
        },
        error: function(xhr) {
            console.error('Failed to load portfolio summary:', xhr);
        }
    });

    // 거래 통계 로드
    loadTradeStatistics();
    
    // 자산 가치 추이 로드
    loadAssetValueHistory('1M');
    
    // 월별 수익률 로드
    loadMonthlyReturns();
}

function updateSummaryCards(summary) {
    $('#total-investment').text(formatCurrency(summary.totalInvestment));
    $('#total-value').text(formatCurrency(summary.totalCurrentValue));

    const totalReturn = summary.totalProfitLossPercent;
    const totalProfit = summary.totalProfitLoss;

    $('#total-return').text(formatPercent(totalReturn));
    $('#total-profit-amount').text(formatCurrency(totalProfit));

    // 색상 업데이트
    applyValueClass($('#total-return'), totalReturn);
    applyValueClass($('#total-return-icon'), totalReturn);

    // 테두리 색상 업데이트
    const $returnCard = $('#total-return').parent().parent();
    $returnCard.removeClass('border-success border-danger');
    $returnCard.addClass(totalReturn >= 0 ? 'border-success' : 'border-danger');

    // 실현 손익 (FIFO 기반 매도 거래의 손익 합계)
    const realizedPnL = summary.totalRealizedPnl || 0;
    $('#realized-pnl').text(formatCurrency(realizedPnL));

    applyValueClass($('#realized-pnl'), realizedPnL);
    applyValueClass($('#realized-pnl-icon'), realizedPnL);
}

function updatePortfolioComposition(holdings) {
    const labels = holdings.map(h => h.stockName);
    const data = holdings.map(h => h.currentValue);
    const backgroundColor = generateColors(holdings.length);
    
    if (portfolioCompositionChart) {
        portfolioCompositionChart.data.labels = labels;
        portfolioCompositionChart.data.datasets[0].data = data;
        portfolioCompositionChart.data.datasets[0].backgroundColor = backgroundColor;
        portfolioCompositionChart.update();
    }
}

function updateTopPerformers(holdings) {
    // 수익률 기준 정렬
    const sortedByReturn = [...holdings].sort((a, b) => b.profitLossPercent - a.profitLossPercent);
    
    // TOP 5 수익
    const topGainers = sortedByReturn.filter(h => h.profitLossPercent > 0).slice(0, 5);
    const topGainersHtml = topGainers.map(h => `
        <tr>
            <td>${h.stockName}</td>
            <td class="text-end text-success">+${h.profitLossPercent.toFixed(2)}%</td>
            <td class="text-end text-success">${formatCurrency(h.profitLoss)}</td>
        </tr>
    `).join('');
    $('#top-gainers').html(topGainersHtml || '<tr><td colspan="3" class="text-center text-muted">수익 종목 없음</td></tr>');
    
    // TOP 5 손실
    const topLosers = sortedByReturn.filter(h => h.profitLossPercent < 0).slice(-5).reverse();
    const topLosersHtml = topLosers.map(h => `
        <tr>
            <td>${h.stockName}</td>
            <td class="text-end text-danger">${h.profitLossPercent.toFixed(2)}%</td>
            <td class="text-end text-danger">${formatCurrency(h.profitLoss)}</td>
        </tr>
    `).join('');
    $('#top-losers').html(topLosersHtml || '<tr><td colspan="3" class="text-center text-muted">손실 종목 없음</td></tr>');
}

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
        error: function(xhr) {
            console.error('Failed to load trade statistics:', xhr);
        }
    });
}

function loadAssetValueHistory(period) {
    const range = getDateRangeForApi(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/asset-history`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateAssetValueChart(data);
        },
        error: function(xhr) {
            console.error('Failed to load asset value history:', xhr);
        }
    });
}

function updateAssetValueChart(data) {
    if (!data || !data.labels || !data.values || data.values.length === 0) {
        console.warn('No asset history data available');
        if (assetValueChart) {
            assetValueChart.data.labels = [];
            assetValueChart.data.datasets[0].data = [];
            assetValueChart.update();
        }
        return;
    }

    // 날짜 레이블 포맷팅 (예: "1월 15일")
    const formattedLabels = data.labels.map(label => {
        const date = new Date(label);
        return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    if (assetValueChart) {
        assetValueChart.data.labels = formattedLabels;
        assetValueChart.data.datasets[0].data = data.values;
        assetValueChart.update();
    }
}

function loadMonthlyReturns() {
    $.ajax({
        url: `${API_BASE_URL}/analysis/monthly-returns`,
        method: 'GET',
        success: function(data) {
            updateMonthlyReturnChart(data);
        },
        error: function(xhr) {
            console.error('Failed to load monthly returns:', xhr);
        }
    });
}

function updateMonthlyReturnChart(data) {
    if (!data || data.length === 0) {
        console.warn('No monthly return data available');
        if (monthlyReturnChart) {
            monthlyReturnChart.data.labels = [];
            monthlyReturnChart.data.datasets[0].data = [];
            monthlyReturnChart.update();
        }
        return;
    }

    // 월별로 정렬하고 레이블 포맷팅
    const sortedData = [...data].sort((a, b) => a.month.localeCompare(b.month));
    const labels = sortedData.map(d => {
        const [year, month] = d.month.split('-');
        return `${year.slice(2)}년 ${parseInt(month)}월`;
    });
    const returns = sortedData.map(d => parseFloat(d.returnRate) || 0);

    if (monthlyReturnChart) {
        monthlyReturnChart.data.labels = labels;
        monthlyReturnChart.data.datasets[0].data = returns;
        monthlyReturnChart.update();
    }
}

function initializeCharts() {
    // 자산 가치 추이 차트
    const assetCtx = document.getElementById('assetValueChart').getContext('2d');
    assetValueChart = new Chart(assetCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: '자산 가치',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.1)',
                tension: 0.1,
                fill: true
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
                            return '자산 가치: ' + formatCurrency(context.parsed.y);
                        }
                    }
                }
            },
            scales: {
                y: {
                    ticks: {
                        callback: function(value) {
                            return formatCurrency(value);
                        }
                    }
                }
            }
        }
    });
    
    // 포트폴리오 구성 차트
    const portfolioCtx = document.getElementById('portfolioCompositionChart').getContext('2d');
    portfolioCompositionChart = new Chart(portfolioCtx, {
        type: 'doughnut',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: []
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        padding: 10,
                        font: {
                            size: 11
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const label = context.label || '';
                            const value = formatCurrency(context.parsed);
                            const percentage = ((context.parsed / context.dataset.data.reduce((a, b) => a + b, 0)) * 100).toFixed(1);
                            return `${label}: ${value} (${percentage}%)`;
                        }
                    }
                }
            }
        }
    });
    
    // 월별 수익률 차트
    const monthlyCtx = document.getElementById('monthlyReturnChart').getContext('2d');
    monthlyReturnChart = new Chart(monthlyCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: '월별 수익률',
                data: [],
                backgroundColor: function(context) {
                    const value = context.parsed.y;
                    return value >= 0 ? 'rgba(75, 192, 192, 0.8)' : 'rgba(255, 99, 132, 0.8)';
                }
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

function updateAssetChart(period) {
    updateButtonGroupActive(event);
    loadAssetValueHistory(period);
}

// 유틸리티 함수들
function formatCurrency(value) {
    return '₩' + Math.round(value).toLocaleString();
}

function formatPercent(value) {
    return (value >= 0 ? '+' : '') + value.toFixed(2) + '%';
}

function generateColors(count) {
    const colors = [
        '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF',
        '#FF9F40', '#FF6384', '#C9CBCF', '#4BC0C0', '#36A2EB'
    ];
    return colors.slice(0, count);
}

function generateDateLabels(days) {
    const labels = [];
    const today = new Date();
    
    for (let i = days - 1; i >= 0; i--) {
        const date = new Date(today);
        date.setDate(date.getDate() - i);
        labels.push(date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }));
    }
    
    return labels;
}

function generateRandomValues(count, min, max) {
    const values = [];
    let current = min + Math.random() * (max - min);

    for (let i = 0; i < count; i++) {
        current += (Math.random() - 0.5) * (max - min) * 0.1;
        current = Math.max(min, Math.min(max, current));
        values.push(current);
    }

    return values;
}

// ==================== 고급 분석 차트 ====================

function initializeAdvancedCharts() {
    // Equity Curve 차트
    const equityCtx = document.getElementById('equityCurveChart').getContext('2d');
    equityCurveChart = new Chart(equityCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: '누적 수익률',
                data: [],
                borderColor: 'rgb(34, 197, 94)',
                backgroundColor: 'rgba(34, 197, 94, 0.1)',
                tension: 0.1,
                fill: true,
                pointRadius: 0,
                pointHoverRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: 'index'
            },
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
                x: {
                    ticks: {
                        maxTicksLimit: 8
                    }
                },
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

    // Drawdown 차트
    const drawdownCtx = document.getElementById('drawdownChart').getContext('2d');
    drawdownChart = new Chart(drawdownCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Drawdown',
                data: [],
                borderColor: 'rgb(239, 68, 68)',
                backgroundColor: 'rgba(239, 68, 68, 0.3)',
                tension: 0.1,
                fill: true,
                pointRadius: 0,
                pointHoverRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: 'index'
            },
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
                x: {
                    ticks: {
                        maxTicksLimit: 8
                    }
                },
                y: {
                    reverse: false,
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
}

/**
 * @deprecated getDateRangeForApi를 사용하세요.
 * 이전 버전과의 호환성을 위해 유지됩니다.
 */
function getDateRange(period) {
    return getDateRangeForApi(period || '1Y');
}

function loadEquityCurve(period) {
    const range = getDateRange(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/equity-curve`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateEquityCurveChart(data);
        },
        error: function(xhr) {
            console.error('Failed to load equity curve:', xhr);
        }
    });
}

function updateEquityCurveChart(data) {
    if (!data || !data.labels || data.labels.length === 0) {
        console.warn('No equity curve data available');
        return;
    }

    // 날짜 레이블 포맷팅
    const formattedLabels = data.labels.map(label => {
        const date = new Date(label);
        return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    // 요약 정보 업데이트
    const totalReturn = parseFloat(data.totalReturn || 0);
    const $totalReturnEl = $('#equity-total-return');
    $totalReturnEl.text(formatPercent(totalReturn));
    applyValueClass($totalReturnEl, totalReturn);

    $('#equity-cagr').text(formatPercent(parseFloat(data.cagr || 0)));
    $('#equity-final-value').text(formatCurrency(data.finalValue || 0));

    // 차트 업데이트
    if (equityCurveChart) {
        equityCurveChart.data.labels = formattedLabels;
        equityCurveChart.data.datasets[0].data = data.cumulativeReturns;
        equityCurveChart.update();
    }
}

function updateEquityCurve(period) {
    updateButtonGroupActive(event);
    loadEquityCurve(period);
}

function loadDrawdown(period) {
    const range = getDateRange(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/drawdown`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateDrawdownChart(data);
        },
        error: function(xhr) {
            console.error('Failed to load drawdown:', xhr);
        }
    });
}

function updateDrawdownChart(data) {
    if (!data || !data.labels || data.labels.length === 0) {
        console.warn('No drawdown data available');
        return;
    }

    // 날짜 레이블 포맷팅
    const formattedLabels = data.labels.map(label => {
        const date = new Date(label);
        return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    // 요약 정보 업데이트
    const maxDrawdown = parseFloat(data.maxDrawdown || 0);
    const currentDrawdown = parseFloat(data.currentDrawdown || 0);

    $('#max-drawdown').text(maxDrawdown.toFixed(2) + '%');
    const $currentDrawdown = $('#current-drawdown');
    $currentDrawdown.text(currentDrawdown.toFixed(2) + '%');
    applyValueClass($currentDrawdown, currentDrawdown, { includeWarning: true, warningThreshold: -5 });

    if (data.recoveryDays !== null) {
        $('#recovery-days').text(data.recoveryDays + '일');
    } else {
        $('#recovery-days').text('미회복');
    }

    // 차트 업데이트
    if (drawdownChart) {
        drawdownChart.data.labels = formattedLabels;
        drawdownChart.data.datasets[0].data = data.drawdowns;

        // Y축 최소값 동적 조정
        const minDrawdown = Math.min(...data.drawdowns);
        drawdownChart.options.scales.y.min = Math.floor(minDrawdown * 1.1);

        drawdownChart.update();
    }
}

function updateDrawdown(period) {
    updateButtonGroupActive(event);
    loadDrawdown(period);
}

// ==================== 상관관계 히트맵 ====================

function loadCorrelation(period) {
    const range = getDateRange(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/correlation`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateCorrelationMatrix(data);
        },
        error: function(xhr) {
            console.error('Failed to load correlation:', xhr);
        }
    });
}

function updateCorrelationMatrix(data) {
    const $header = $('#correlation-header');
    const $body = $('#correlation-body');
    const $empty = $('#correlation-empty');
    const $matrix = $('#correlation-matrix');

    // 데이터 없음 처리
    if (!data || !data.symbols || data.symbols.length < 2) {
        $matrix.hide();
        $empty.show();
        $('#avg-correlation').text('-');
        $('#diversification-score').text('-');
        $('#correlation-stocks').text(data ? data.symbols.length : 0);
        return;
    }

    $matrix.show();
    $empty.hide();

    // 요약 정보 업데이트
    const avgCorr = parseFloat(data.averageCorrelation || 0);
    const $avgCorrelation = $('#avg-correlation').text(avgCorr.toFixed(2));
    // 상관관계는 낮을수록 좋음 (0.3 미만: 성공, 0.3-0.6: 경고, 0.6 이상: 위험)
    $avgCorrelation.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.WARNING} ${CSS_CLASSES.DANGER}`);
    $avgCorrelation.addClass(avgCorr < 0.3 ? CSS_CLASSES.SUCCESS : (avgCorr < 0.6 ? CSS_CLASSES.WARNING : CSS_CLASSES.DANGER));

    const divScore = parseFloat(data.diversificationScore || 50);
    let divText = '보통';
    let divClass = CSS_CLASSES.WARNING;
    if (divScore < 40) {
        divText = '우수';
        divClass = CSS_CLASSES.SUCCESS;
    } else if (divScore > 70) {
        divText = '미흡';
        divClass = CSS_CLASSES.DANGER;
    }
    const $diversificationScore = $('#diversification-score').text(divText);
    $diversificationScore.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.WARNING} ${CSS_CLASSES.DANGER}`);
    $diversificationScore.addClass(divClass);

    $('#correlation-stocks').text(data.symbols.length);

    // 테이블 헤더 생성
    let headerHtml = '<tr><th></th>';
    for (let i = 0; i < data.symbols.length; i++) {
        const name = data.names[i] || data.symbols[i];
        const shortName = name.length > 6 ? name.substring(0, 6) + '..' : name;
        headerHtml += `<th title="${name}">${shortName}</th>`;
    }
    headerHtml += '</tr>';
    $header.html(headerHtml);

    // 테이블 본문 생성
    let bodyHtml = '';
    for (let i = 0; i < data.matrix.length; i++) {
        const name = data.names[i] || data.symbols[i];
        const shortName = name.length > 6 ? name.substring(0, 6) + '..' : name;
        bodyHtml += `<tr><th title="${name}">${shortName}</th>`;

        for (let j = 0; j < data.matrix[i].length; j++) {
            const value = parseFloat(data.matrix[i][j]);
            const color = getCorrelationColor(value);
            const textColor = Math.abs(value) > 0.5 ? 'white' : 'black';

            bodyHtml += `<td style="background-color: ${color}; color: ${textColor};" title="${data.names[i]} ↔ ${data.names[j]}: ${value.toFixed(2)}">`;
            bodyHtml += i === j ? '-' : value.toFixed(2);
            bodyHtml += '</td>';
        }
        bodyHtml += '</tr>';
    }
    $body.html(bodyHtml);
}

function getCorrelationColor(value) {
    // -1 (녹색) ~ 0 (노란색) ~ +1 (빨간색) 그라데이션
    if (value <= -1) return '#22c55e';
    if (value >= 1) return '#ef4444';

    if (value < 0) {
        // -1 ~ 0: 녹색 -> 노란색
        const ratio = (value + 1); // 0 ~ 1
        const r = Math.round(34 + (251 - 34) * ratio);
        const g = Math.round(197 + (191 - 197) * ratio);
        const b = Math.round(94 + (36 - 94) * ratio);
        return `rgb(${r}, ${g}, ${b})`;
    } else {
        // 0 ~ 1: 노란색 -> 빨간색
        const ratio = value; // 0 ~ 1
        const r = Math.round(251 + (239 - 251) * ratio);
        const g = Math.round(191 + (68 - 191) * ratio);
        const b = Math.round(36 + (68 - 36) * ratio);
        return `rgb(${r}, ${g}, ${b})`;
    }
}

function updateCorrelation(period) {
    updateButtonGroupActive(event);
    loadCorrelation(period);
}

// ==================== 리스크 지표 ====================

function loadRiskMetrics(period) {
    const range = getDateRange(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/risk-metrics`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateRiskMetricsDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load risk metrics:', xhr);
            // 에러 시 기본값 표시
            resetRiskMetricsDisplay();
        }
    });
}

function updateRiskMetricsDisplay(data) {
    if (!data) {
        resetRiskMetricsDisplay();
        return;
    }

    // 리스크 등급 표시
    const riskLevel = data.riskLevel || 'MEDIUM';
    const $badge = $('#risk-level-badge');
    const $text = $('#risk-level-text');

    $badge.removeClass('bg-success bg-warning bg-danger');
    switch(riskLevel) {
        case 'LOW':
            $badge.addClass('bg-success');
            $text.text('낮은 리스크 - 안정적인 포트폴리오');
            break;
        case 'HIGH':
            $badge.addClass('bg-danger');
            $text.text('높은 리스크 - 공격적인 포트폴리오');
            break;
        default:
            $badge.addClass('bg-warning');
            $text.text('중간 리스크 - 적절한 리스크 수준');
    }

    // 주요 비율 지표
    const sharpe = parseFloat(data.sharpeRatio || 0);
    const $sharpeRatio = $('#sharpe-ratio').text(sharpe.toFixed(2));
    applyThresholdClass($sharpeRatio, sharpe, 1, 0.5);

    const sortino = parseFloat(data.sortinoRatio || 0);
    const $sortinoRatio = $('#sortino-ratio').text(sortino.toFixed(2));
    applyThresholdClass($sortinoRatio, sortino, 1.5, 0.5);

    const calmar = parseFloat(data.calmarRatio || 0);
    const $calmarRatio = $('#calmar-ratio').text(calmar.toFixed(2));
    applyThresholdClass($calmarRatio, calmar, 1, 0.5);

    const profitFactor = parseFloat(data.profitFactor || 0);
    const $profitFactor = $('#profit-factor').text(profitFactor.toFixed(2));
    applyThresholdClass($profitFactor, profitFactor, 1.5, 1);

    // VaR 지표
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

    // 추가 지표
    const volatility = parseFloat(data.volatility || 0);
    $('#volatility').text(volatility.toFixed(2) + '%');

    const downsideDev = parseFloat(data.downsideDeviation || 0);
    $('#downside-deviation').text(downsideDev.toFixed(2) + '%');

    const mdd = parseFloat(data.maxDrawdown || 0);
    $('#risk-mdd').text(mdd.toFixed(2) + '%');

    const cagr = parseFloat(data.cagr || 0);
    const $riskCagr = $('#risk-cagr').text(formatPercent(cagr));
    applyValueClass($riskCagr, cagr);

    const winRate = parseFloat(data.winRate || 0);
    const $riskWinrate = $('#risk-winrate').text(winRate.toFixed(1) + '%');
    applyThresholdClass($riskWinrate, winRate, 50, 50);

    $('#trading-days').text(data.tradingDays || 0);
}

function resetRiskMetricsDisplay() {
    $('#risk-level-badge').removeClass('bg-success bg-warning bg-danger').addClass('bg-secondary');
    $('#risk-level-text').text('데이터 없음');

    $('#sharpe-ratio, #sortino-ratio, #calmar-ratio, #profit-factor').text('-');
    $('#var95-daily, #var95-weekly, #var95-monthly').text('-');
    $('#var99-daily, #var99-weekly, #var99-monthly').text('-');
    $('#var-amount').text('-');
    $('#volatility, #downside-deviation, #risk-mdd, #risk-cagr, #risk-winrate, #trading-days').text('-');
}

function updateRiskMetrics(period) {
    updateButtonGroupActive(event);
    loadRiskMetrics(period);
}

// ==================== 벤치마크 비교 ====================

let benchmarkComparisonChart;
let currentBenchmarkPeriod = '1Y';

// 페이지 로드 시 벤치마크 차트 초기화 및 데이터 로드
$(document).ready(function() {
    // 벤치마크 차트 초기화 (다른 차트 초기화 후 실행)
    setTimeout(function() {
        initializeBenchmarkChart();
        loadBenchmarkComparison(currentBenchmarkPeriod);
    }, 100);

    // 벤치마크 선택 변경 이벤트
    $('#benchmark-select').on('change', function() {
        loadBenchmarkComparison(currentBenchmarkPeriod);
    });
});

function initializeBenchmarkChart() {
    const ctx = document.getElementById('benchmarkComparisonChart');
    if (!ctx) return;

    benchmarkComparisonChart = new Chart(ctx.getContext('2d'), {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: '포트폴리오',
                    data: [],
                    borderColor: 'rgb(59, 130, 246)',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    tension: 0.1,
                    fill: false,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    borderWidth: 2
                },
                {
                    label: '벤치마크',
                    data: [],
                    borderColor: 'rgb(156, 163, 175)',
                    backgroundColor: 'rgba(156, 163, 175, 0.1)',
                    tension: 0.1,
                    fill: false,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    borderWidth: 2,
                    borderDash: [5, 5]
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: 'index'
            },
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return context.dataset.label + ': ' + context.parsed.y.toFixed(2) + '%';
                        }
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        maxTicksLimit: 8
                    }
                },
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

function loadBenchmarkComparison(period) {
    currentBenchmarkPeriod = period;
    const range = getDateRange(period);
    const benchmark = $('#benchmark-select').val();

    $.ajax({
        url: `${API_BASE_URL}/analysis/benchmark/compare`,
        method: 'GET',
        data: {
            benchmark: benchmark,
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateBenchmarkDisplay(data);
            $('#benchmark-empty').hide();
        },
        error: function(xhr) {
            console.error('Failed to load benchmark comparison:', xhr);
            if (xhr.status === 404 || (xhr.responseJSON && xhr.responseJSON.portfolioReturns && xhr.responseJSON.portfolioReturns.length === 0)) {
                showBenchmarkEmpty();
            } else {
                resetBenchmarkDisplay();
            }
        }
    });
}

function updateBenchmarkDisplay(data) {
    if (!data || !data.labels || data.labels.length === 0) {
        showBenchmarkEmpty();
        return;
    }

    // 날짜 레이블 포맷팅
    const formattedLabels = data.labels.map(label => {
        const date = new Date(label);
        return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    });

    // 차트 업데이트
    if (benchmarkComparisonChart) {
        benchmarkComparisonChart.data.labels = formattedLabels;
        benchmarkComparisonChart.data.datasets[0].data = data.portfolioReturns;
        benchmarkComparisonChart.data.datasets[1].data = data.benchmarkReturns;
        benchmarkComparisonChart.data.datasets[1].label = data.benchmarkLabel || '벤치마크';
        benchmarkComparisonChart.update();
    }

    // 요약 지표 업데이트
    const portfolioReturn = parseFloat(data.portfolioTotalReturn || 0);
    const benchmarkReturn = parseFloat(data.benchmarkTotalReturn || 0);
    const excessReturn = parseFloat(data.excessReturn || 0);

    const $portfolioReturn = $('#benchmark-portfolio-return').text(formatPercent(portfolioReturn));
    applyValueClass($portfolioReturn, portfolioReturn);

    const $benchmarkIndexReturn = $('#benchmark-index-return').text(formatPercent(benchmarkReturn));
    applyValueClass($benchmarkIndexReturn, benchmarkReturn);

    const $benchmarkExcessReturn = $('#benchmark-excess-return').text(formatPercent(excessReturn));
    applyValueClass($benchmarkExcessReturn, excessReturn);

    // 초과수익 카드 배경색
    const $excessCard = $('#excess-return-card');
    $excessCard.removeClass('bg-success bg-danger bg-light');
    if (excessReturn > 0) {
        $excessCard.addClass('bg-success').find('small, h5').addClass('text-white');
    } else if (excessReturn < 0) {
        $excessCard.addClass('bg-danger').find('small, h5').addClass('text-white');
    } else {
        $excessCard.addClass('bg-light').find('small, h5').removeClass('text-white');
    }

    // 알파, 베타, 상관계수
    const alpha = parseFloat(data.alpha || 0);
    const $benchmarkAlpha = $('#benchmark-alpha').text((alpha >= 0 ? '+' : '') + alpha.toFixed(4));
    applyValueClass($benchmarkAlpha, alpha);

    const beta = parseFloat(data.beta || 1);
    const $benchmarkBeta = $('#benchmark-beta').text(beta.toFixed(4));
    // 베타는 낮을수록 좋음 (0.8 미만: 성공, 0.8-1.2: 경고, 1.2 초과: 위험)
    $benchmarkBeta.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.WARNING} ${CSS_CLASSES.DANGER}`);
    $benchmarkBeta.addClass(beta < 0.8 ? CSS_CLASSES.SUCCESS : (beta > 1.2 ? CSS_CLASSES.DANGER : CSS_CLASSES.WARNING));

    const correlation = parseFloat(data.correlation || 0);
    $('#benchmark-correlation').text(correlation.toFixed(4));

    // 추가 지표
    $('#benchmark-info-ratio').text(parseFloat(data.informationRatio || 0).toFixed(4));
    $('#benchmark-tracking-error').text(parseFloat(data.trackingError || 0).toFixed(4) + '%');
    $('#benchmark-treynor').text(parseFloat(data.treynorRatio || 0).toFixed(4));
    $('#benchmark-rsquared').text(parseFloat(data.rSquared || 0).toFixed(4));

    // MDD
    const portfolioMdd = parseFloat(data.portfolioMaxDrawdown || 0);
    const benchmarkMdd = parseFloat(data.benchmarkMaxDrawdown || 0);
    $('#benchmark-portfolio-mdd').text('-' + portfolioMdd.toFixed(2) + '%');
    $('#benchmark-index-mdd').text('-' + benchmarkMdd.toFixed(2) + '%');

    // 월별 승패
    const winMonths = data.portfolioWinMonths || 0;
    const loseMonths = data.benchmarkWinMonths || 0;
    $('#benchmark-win-months').text('승: ' + winMonths + '개월');
    $('#benchmark-lose-months').text('패: ' + loseMonths + '개월');
}

function resetBenchmarkDisplay() {
    $('#benchmark-portfolio-return, #benchmark-index-return, #benchmark-excess-return').text('-');
    $('#benchmark-alpha, #benchmark-beta, #benchmark-correlation').text('-');
    $('#benchmark-info-ratio, #benchmark-tracking-error, #benchmark-treynor, #benchmark-rsquared').text('-');
    $('#benchmark-portfolio-mdd, #benchmark-index-mdd').text('-');
    $('#benchmark-win-months').text('승: 0개월');
    $('#benchmark-lose-months').text('패: 0개월');

    if (benchmarkComparisonChart) {
        benchmarkComparisonChart.data.labels = [];
        benchmarkComparisonChart.data.datasets[0].data = [];
        benchmarkComparisonChart.data.datasets[1].data = [];
        benchmarkComparisonChart.update();
    }
}

function showBenchmarkEmpty() {
    resetBenchmarkDisplay();
    $('#benchmark-empty').show();
}

function updateBenchmark(period) {
    updateButtonGroupActive(event);
    loadBenchmarkComparison(period);
}

function generateSampleBenchmark() {
    const benchmark = $('#benchmark-select').val();
    const range = getDateRange('1Y');

    $.ajax({
        url: `${API_BASE_URL}/analysis/benchmark/generate-sample`,
        method: 'POST',
        data: {
            benchmark: benchmark,
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(response) {
            alert(response.message || '샘플 데이터가 생성되었습니다.');
            loadBenchmarkComparison(currentBenchmarkPeriod);
        },
        error: function(xhr) {
            alert('샘플 데이터 생성 실패: ' + (xhr.responseJSON?.message || xhr.statusText));
        }
    });
}

// ==================== PDF 리포트 다운로드 ====================

/**
 * PDF 리포트 다운로드
 * @param {string} type - 리포트 유형 (ytd, monthly, yearly)
 */
function downloadReport(type) {
    let url = '';
    const today = new Date();

    switch(type) {
        case 'ytd':
            url = `${API_BASE_URL}/reports/portfolio/pdf/ytd`;
            break;
        case 'monthly':
            url = `${API_BASE_URL}/reports/portfolio/pdf/monthly?year=${today.getFullYear()}&month=${today.getMonth() + 1}`;
            break;
        case 'yearly':
            url = `${API_BASE_URL}/reports/portfolio/pdf/yearly?year=${today.getFullYear()}`;
            break;
        default:
            console.error('Unknown report type:', type);
            return;
    }

    downloadPdfReport(url);
}

/**
 * 커스텀 기간 리포트 다운로드
 */
function downloadCustomReport() {
    const startDate = $('#reportStartDate').val();
    const endDate = $('#reportEndDate').val();

    if (!startDate || !endDate) {
        alert('시작일과 종료일을 모두 선택해주세요.');
        return;
    }

    if (new Date(startDate) > new Date(endDate)) {
        alert('시작일이 종료일보다 늦을 수 없습니다.');
        return;
    }

    const url = `${API_BASE_URL}/reports/portfolio/pdf?startDate=${startDate}&endDate=${endDate}`;
    downloadPdfReport(url);

    // 모달 닫기
    const modal = bootstrap.Modal.getInstance(document.getElementById('customReportModal'));
    if (modal) {
        modal.hide();
    }
}

/**
 * PDF 파일 다운로드 실행
 * @param {string} url - API 엔드포인트 URL
 */
function downloadPdfReport(url) {
    // 로딩 표시
    const $reportBtn = $('#reportDropdown');
    const originalText = $reportBtn.html();
    $reportBtn.html('<span class="spinner-border spinner-border-sm me-1"></span>생성 중...').prop('disabled', true);

    fetch(url, {
        method: 'GET',
        headers: {
            'Accept': 'application/pdf'
        }
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('리포트 생성에 실패했습니다.');
        }

        // Content-Disposition 헤더에서 파일명 추출
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'portfolio_report.pdf';
        if (contentDisposition) {
            const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
            if (filenameMatch && filenameMatch[1]) {
                filename = filenameMatch[1].replace(/['"]/g, '');
            }
        }

        return response.blob().then(blob => ({ blob, filename }));
    })
    .then(({ blob, filename }) => {
        // Blob URL 생성 및 다운로드
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
    })
    .catch(error => {
        console.error('PDF download error:', error);
        alert('PDF 리포트 생성에 실패했습니다. 다시 시도해주세요.');
    })
    .finally(() => {
        // 버튼 복원
        $reportBtn.html(originalText).prop('disabled', false);
    });
}

/**
 * 커스텀 리포트 모달 초기화 (날짜 기본값 설정)
 */
$(document).ready(function() {
    // 모달이 열릴 때 기본 날짜 설정
    $('#customReportModal').on('show.bs.modal', function() {
        const today = new Date();
        const startOfYear = new Date(today.getFullYear(), 0, 1);

        $('#reportEndDate').val(today.toISOString().split('T')[0]);
        $('#reportStartDate').val(startOfYear.toISOString().split('T')[0]);
    });
});

// ==================== 포트폴리오 트리맵 (Finviz 스타일) ====================

let currentTreemapPeriod = '1D';

const PERIOD_LABELS = {
    '1D': '1일',
    '1W': '1주',
    '1M': '1개월',
    'MTD': '이번달',
    '3M': '3개월',
    '6M': '6개월',
    '1Y': '1년'
};

function initializeTreemap() {
    // 기본 기간으로 트리맵 로드
    loadPortfolioTreemap('1D');

    // 기간 선택 버튼 이벤트 핸들러
    $('#treemap-period-selector button').on('click', function() {
        $('#treemap-period-selector button').removeClass('active');
        $(this).addClass('active');
        const period = $(this).data('period');
        loadPortfolioTreemap(period);
    });

    // 윈도우 리사이즈 시 트리맵 재렌더링
    let resizeTimeout;
    $(window).on('resize', function() {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(function() {
            if (window.treemapData) {
                renderTreemap(window.treemapData);
            }
        }, 250);
    });
}

function loadPortfolioTreemap(period) {
    currentTreemapPeriod = period;
    $('#treemap-period-label').text(PERIOD_LABELS[period] || period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/portfolio/treemap`,
        method: 'GET',
        data: { period: period },
        success: function(data) {
            window.treemapData = data;
            updateTreemapSummary(data);
            renderTreemap(data);
            $('#treemap-empty').hide();
            $('#portfolio-treemap').show();
        },
        error: function(xhr) {
            console.error('Failed to load treemap:', xhr);
            $('#portfolio-treemap').hide();
            $('#treemap-empty').show();
        }
    });
}

function updateTreemapSummary(data) {
    $('#treemap-total-investment').text(formatCurrency(data.totalInvestment || 0));

    const avgPerf = parseFloat(data.totalPerformance || 0);
    const $avgPerformance = $('#treemap-avg-performance').text(formatPercent(avgPerf));
    $avgPerformance.removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER} ${CSS_CLASSES.MUTED}`);
    $avgPerformance.addClass(avgPerf > 0 ? CSS_CLASSES.SUCCESS : (avgPerf < 0 ? CSS_CLASSES.DANGER : CSS_CLASSES.MUTED));
}

function renderTreemap(data) {
    const container = document.getElementById('portfolio-treemap');
    if (!container) return;

    const width = container.clientWidth;
    const height = 400;

    // 기존 내용 제거
    d3.select('#portfolio-treemap').selectAll('*').remove();

    if (!data.cells || data.cells.length === 0) {
        $('#portfolio-treemap').hide();
        $('#treemap-empty').show();
        return;
    }

    // SVG 생성
    const svg = d3.select('#portfolio-treemap')
        .append('svg')
        .attr('width', width)
        .attr('height', height);

    // 계층 구조 데이터 준비
    const hierarchyData = {
        name: 'Portfolio',
        children: data.cells.map(cell => ({
            name: cell.symbol,
            fullName: cell.name || cell.symbol,
            value: Math.max(parseFloat(cell.investmentAmount) || 1, 1), // 최소값 1
            performance: parseFloat(cell.performancePercent) || 0,
            currentPrice: parseFloat(cell.currentPrice) || 0,
            priceChange: parseFloat(cell.priceChange) || 0,
            sector: cell.sector || 'UNKNOWN',
            hasData: cell.hasData !== false
        }))
    };

    // 트리맵 레이아웃 생성
    const treemap = d3.treemap()
        .size([width, height])
        .padding(2)
        .round(true);

    const root = d3.hierarchy(hierarchyData)
        .sum(d => d.value)
        .sort((a, b) => b.value - a.value);

    treemap(root);

    // 셀 그룹 생성
    const cells = svg.selectAll('g')
        .data(root.leaves())
        .enter()
        .append('g')
        .attr('transform', d => `translate(${d.x0},${d.y0})`);

    // 셀 사각형 추가
    cells.append('rect')
        .attr('width', d => Math.max(d.x1 - d.x0, 0))
        .attr('height', d => Math.max(d.y1 - d.y0, 0))
        .attr('fill', d => getTreemapColor(d.data.performance, d.data.hasData))
        .attr('stroke', '#fff')
        .attr('stroke-width', 1)
        .attr('rx', 3)
        .style('cursor', 'pointer')
        .on('mouseover', function(event, d) {
            showTreemapTooltip(event, d);
            d3.select(this)
                .attr('stroke', '#000')
                .attr('stroke-width', 2);
        })
        .on('mouseout', function() {
            hideTreemapTooltip();
            d3.select(this)
                .attr('stroke', '#fff')
                .attr('stroke-width', 1);
        });

    // 심볼 레이블 추가
    cells.append('text')
        .attr('x', d => (d.x1 - d.x0) / 2)
        .attr('y', d => (d.y1 - d.y0) / 2 - 6)
        .attr('text-anchor', 'middle')
        .attr('fill', d => getTreemapTextColor(d.data.performance, d.data.hasData))
        .attr('font-size', d => {
            const cellWidth = d.x1 - d.x0;
            return Math.min(Math.max(cellWidth / 6, 9), 14) + 'px';
        })
        .attr('font-weight', 'bold')
        .text(d => {
            const cellWidth = d.x1 - d.x0;
            if (cellWidth < 40) return '';
            if (cellWidth < 60) return d.data.name.substring(0, 3);
            return d.data.name;
        });

    // 수익률 레이블 추가
    cells.append('text')
        .attr('x', d => (d.x1 - d.x0) / 2)
        .attr('y', d => (d.y1 - d.y0) / 2 + 10)
        .attr('text-anchor', 'middle')
        .attr('fill', d => getTreemapTextColor(d.data.performance, d.data.hasData))
        .attr('font-size', d => {
            const cellWidth = d.x1 - d.x0;
            return Math.min(Math.max(cellWidth / 7, 8), 12) + 'px';
        })
        .text(d => {
            const cellWidth = d.x1 - d.x0;
            const cellHeight = d.y1 - d.y0;
            if (cellWidth < 50 || cellHeight < 35) return '';
            if (!d.data.hasData) return 'N/A';
            const perf = d.data.performance;
            return (perf >= 0 ? '+' : '') + perf.toFixed(2) + '%';
        });
}

function getTreemapColor(performance, hasData) {
    if (!hasData || performance === null || performance === undefined) {
        return '#6c757d'; // Gray for no data
    }

    // -10% 이하 또는 +10% 이상은 최대 색상 강도
    const clampedPerf = Math.max(-10, Math.min(10, performance));

    if (clampedPerf < 0) {
        // 음수: Gray (#6c757d) -> Red (#dc3545)
        const intensity = Math.abs(clampedPerf) / 10;
        return d3.interpolateRgb('#6c757d', '#dc3545')(intensity);
    } else if (clampedPerf > 0) {
        // 양수: Gray (#6c757d) -> Green (#198754)
        const intensity = clampedPerf / 10;
        return d3.interpolateRgb('#6c757d', '#198754')(intensity);
    }

    return '#6c757d'; // 0%는 회색
}

function getTreemapTextColor(performance, hasData) {
    if (!hasData) return '#fff';
    if (performance === null || performance === undefined) return '#fff';
    if (Math.abs(performance) > 4) return '#fff';
    return '#212529';
}

function showTreemapTooltip(event, d) {
    // 기존 툴팁 제거
    d3.select('.treemap-tooltip').remove();

    const perfText = d.data.hasData
        ? (d.data.performance >= 0 ? '+' : '') + d.data.performance.toFixed(2) + '%'
        : '데이터 없음';

    const perfColor = d.data.performance >= 0 ? '#22c55e' : '#ef4444';
    const priceChangeText = d.data.priceChange >= 0
        ? '+' + formatCurrency(d.data.priceChange)
        : formatCurrency(d.data.priceChange);

    const tooltip = d3.select('body')
        .append('div')
        .attr('class', 'treemap-tooltip')
        .style('position', 'absolute')
        .style('background', 'rgba(0,0,0,0.9)')
        .style('color', '#fff')
        .style('padding', '12px')
        .style('border-radius', '6px')
        .style('font-size', '12px')
        .style('z-index', '10000')
        .style('pointer-events', 'none')
        .style('box-shadow', '0 4px 6px rgba(0,0,0,0.3)')
        .style('max-width', '250px');

    tooltip.html(`
        <div style="font-weight: bold; margin-bottom: 6px; font-size: 14px;">
            ${d.data.name} <span style="font-weight: normal; color: #aaa;">(${d.data.fullName})</span>
        </div>
        <div style="margin-bottom: 4px;">
            <span style="color: #aaa;">수익률:</span>
            <span style="color: ${perfColor}; font-weight: bold;">${perfText}</span>
        </div>
        <div style="margin-bottom: 4px;">
            <span style="color: #aaa;">투자금액:</span>
            <span>${formatCurrency(d.value)}</span>
        </div>
        <div style="margin-bottom: 4px;">
            <span style="color: #aaa;">현재가:</span>
            <span>${formatCurrency(d.data.currentPrice)}</span>
            <span style="color: ${d.data.priceChange >= 0 ? '#22c55e' : '#ef4444'}; font-size: 11px;">
                (${priceChangeText})
            </span>
        </div>
        <div>
            <span style="color: #aaa;">섹터:</span>
            <span>${d.data.sector}</span>
        </div>
    `);

    // 툴팁 위치 계산 (화면 밖으로 나가지 않도록)
    const tooltipNode = tooltip.node();
    const tooltipRect = tooltipNode.getBoundingClientRect();
    let left = event.pageX + 15;
    let top = event.pageY - 10;

    if (left + tooltipRect.width > window.innerWidth) {
        left = event.pageX - tooltipRect.width - 15;
    }
    if (top + tooltipRect.height > window.innerHeight + window.scrollY) {
        top = event.pageY - tooltipRect.height - 10;
    }

    tooltip
        .style('left', left + 'px')
        .style('top', top + 'px');
}

function hideTreemapTooltip() {
    d3.select('.treemap-tooltip').remove();
}