const API_BASE_URL = '/api';
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
    
    $('#total-return').text((totalReturn >= 0 ? '+' : '') + totalReturn.toFixed(2) + '%');
    $('#total-profit-amount').text(formatCurrency(totalProfit));
    
    // 색상 업데이트
    if (totalReturn >= 0) {
        $('#total-return').removeClass('text-danger').addClass('text-success');
        $('#total-return-icon').removeClass('text-danger').addClass('text-success');
        $('#total-return').parent().parent().removeClass('border-danger').addClass('border-success');
    } else {
        $('#total-return').removeClass('text-success').addClass('text-danger');
        $('#total-return-icon').removeClass('text-success').addClass('text-danger');
        $('#total-return').parent().parent().removeClass('border-success').addClass('border-danger');
    }
    
    // 실현 손익 (FIFO 기반 매도 거래의 손익 합계)
    const realizedPnL = summary.totalRealizedPnl || 0;
    $('#realized-pnl').text(formatCurrency(realizedPnL));
    
    if (realizedPnL >= 0) {
        $('#realized-pnl').removeClass('text-danger').addClass('text-success');
        $('#realized-pnl-icon').removeClass('text-danger').addClass('text-success');
    } else {
        $('#realized-pnl').removeClass('text-success').addClass('text-danger');
        $('#realized-pnl-icon').removeClass('text-success').addClass('text-danger');
    }
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
    // 기간에 따른 시작일 계산
    const endDate = new Date();
    const startDate = new Date();
    
    switch(period) {
        case '1M': startDate.setMonth(endDate.getMonth() - 1); break;
        case '3M': startDate.setMonth(endDate.getMonth() - 3); break;
        case '6M': startDate.setMonth(endDate.getMonth() - 6); break;
        case '1Y': startDate.setFullYear(endDate.getFullYear() - 1); break;
        case 'ALL': startDate.setFullYear(2020); break;
    }
    
    $.ajax({
        url: `${API_BASE_URL}/analysis/asset-history`,
        method: 'GET',
        data: {
            startDate: startDate.toISOString().split('T')[0],
            endDate: endDate.toISOString().split('T')[0]
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
    // 버튼 활성화 상태 변경
    $('.btn-group button').removeClass('active');
    event.target.classList.add('active');
    
    // 차트 데이터 업데이트
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

function getDateRange(period) {
    const endDate = new Date();
    const startDate = new Date();

    switch(period) {
        case '3M': startDate.setMonth(endDate.getMonth() - 3); break;
        case '6M': startDate.setMonth(endDate.getMonth() - 6); break;
        case '1Y': startDate.setFullYear(endDate.getFullYear() - 1); break;
        case 'ALL': startDate.setFullYear(2020); break;
        default: startDate.setFullYear(endDate.getFullYear() - 1);
    }

    return {
        startDate: startDate.toISOString().split('T')[0],
        endDate: endDate.toISOString().split('T')[0]
    };
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
    const totalReturn = data.totalReturn || 0;
    $('#equity-total-return')
        .text((totalReturn >= 0 ? '+' : '') + parseFloat(totalReturn).toFixed(2) + '%')
        .removeClass('text-success text-danger')
        .addClass(totalReturn >= 0 ? 'text-success' : 'text-danger');

    $('#equity-cagr').text((data.cagr >= 0 ? '+' : '') + parseFloat(data.cagr || 0).toFixed(2) + '%');
    $('#equity-final-value').text(formatCurrency(data.finalValue || 0));

    // 차트 업데이트
    if (equityCurveChart) {
        equityCurveChart.data.labels = formattedLabels;
        equityCurveChart.data.datasets[0].data = data.cumulativeReturns;
        equityCurveChart.update();
    }
}

function updateEquityCurve(period) {
    // 버튼 활성화 상태 변경
    $(event.target).closest('.btn-group').find('button').removeClass('active');
    event.target.classList.add('active');

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
    $('#current-drawdown')
        .text(currentDrawdown.toFixed(2) + '%')
        .removeClass('text-success text-danger text-warning')
        .addClass(currentDrawdown < -5 ? 'text-danger' : (currentDrawdown < 0 ? 'text-warning' : 'text-success'));

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
    // 버튼 활성화 상태 변경
    $(event.target).closest('.btn-group').find('button').removeClass('active');
    event.target.classList.add('active');

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
    $('#avg-correlation')
        .text(avgCorr.toFixed(2))
        .removeClass('text-success text-warning text-danger')
        .addClass(avgCorr < 0.3 ? 'text-success' : (avgCorr < 0.6 ? 'text-warning' : 'text-danger'));

    const divScore = parseFloat(data.diversificationScore || 50);
    let divText = '보통';
    let divClass = 'text-warning';
    if (divScore < 40) {
        divText = '우수';
        divClass = 'text-success';
    } else if (divScore > 70) {
        divText = '미흡';
        divClass = 'text-danger';
    }
    $('#diversification-score')
        .text(divText)
        .removeClass('text-success text-warning text-danger')
        .addClass(divClass);

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
    // 버튼 활성화 상태 변경
    $(event.target).closest('.btn-group').find('button').removeClass('active');
    event.target.classList.add('active');

    loadCorrelation(period);
}