/**
 * 거래 통계 분석 JavaScript
 */

const API_BASE_URL = '/api/statistics';
let weekdayChart = null;
let timeChart = null;
let currentPeriod = '3M';

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    initPeriodButtons();
    loadStatistics();
});

/**
 * 기간 버튼 초기화
 */
function initPeriodButtons() {
    document.querySelectorAll('.period-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            currentPeriod = this.dataset.period;
            loadStatistics();
        });
    });
}

/**
 * 기간에 따른 날짜 계산
 */
function getDateRange(period) {
    const end = new Date();
    let start = new Date();

    switch(period) {
        case '1M':
            start.setMonth(start.getMonth() - 1);
            break;
        case '3M':
            start.setMonth(start.getMonth() - 3);
            break;
        case '6M':
            start.setMonth(start.getMonth() - 6);
            break;
        case '1Y':
            start.setFullYear(start.getFullYear() - 1);
            break;
        case 'ALL':
            start = new Date('2020-01-01');
            break;
    }

    return {
        startDate: formatDate(start),
        endDate: formatDate(end)
    };
}

/**
 * 날짜 포맷팅
 */
function formatDate(date) {
    return date.toISOString().split('T')[0];
}

/**
 * 통계 데이터 로드
 */
async function loadStatistics() {
    const { startDate, endDate } = getDateRange(currentPeriod);

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
            console.error('통계 데이터 로드 실패');
            showEmptyState();
        }
    } catch (error) {
        console.error('API 호출 오류:', error);
        showEmptyState();
    }
}

/**
 * 요약 카드 렌더링
 */
function renderSummary(summary) {
    if (!summary) {
        showEmptyState();
        return;
    }

    document.getElementById('total-trades').textContent = summary.totalTrades || 0;

    const winRate = summary.overallWinRate || 0;
    const winRateEl = document.getElementById('win-rate');
    winRateEl.textContent = winRate.toFixed(1) + '%';
    winRateEl.className = 'stat-value ' + (winRate >= 50 ? 'stat-positive' : 'stat-negative');

    const profit = summary.totalProfit || 0;
    const profitEl = document.getElementById('total-profit');
    profitEl.textContent = formatCurrency(profit);
    profitEl.className = 'stat-value ' + (profit >= 0 ? 'stat-positive' : 'stat-negative');

    document.getElementById('best-day').textContent = summary.bestDay || '-';
    document.getElementById('best-time').textContent = summary.bestTimeSlot || '-';

    const consistency = summary.consistencyScore || 0;
    const consistencyEl = document.getElementById('consistency-score');
    consistencyEl.textContent = consistency.toFixed(0) + '점';
    consistencyEl.className = 'stat-value ' + (consistency >= 70 ? 'stat-positive' : consistency >= 50 ? 'stat-neutral' : 'stat-negative');
}

/**
 * 요일별 차트 렌더링
 */
function renderWeekdayChart(weekdayStats) {
    const ctx = document.getElementById('weekday-chart').getContext('2d');

    if (weekdayChart) {
        weekdayChart.destroy();
    }

    const labels = weekdayStats.map(s => s.dayName);
    const winRates = weekdayStats.map(s => s.winRate || 0);
    const profits = weekdayStats.map(s => s.totalProfit || 0);

    weekdayChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: '승률 (%)',
                    data: winRates,
                    backgroundColor: winRates.map(r => r >= 50 ? 'rgba(40, 167, 69, 0.7)' : 'rgba(220, 53, 69, 0.7)'),
                    borderRadius: 4,
                    yAxisID: 'y'
                },
                {
                    label: '손익',
                    data: profits,
                    type: 'line',
                    borderColor: '#667eea',
                    backgroundColor: 'rgba(102, 126, 234, 0.1)',
                    fill: true,
                    tension: 0.4,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
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
                            return `손익: ${formatCurrency(context.raw)}`;
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
                        text: '손익'
                    },
                    ticks: {
                        callback: function(value) {
                            return formatCurrencyShort(value);
                        }
                    }
                }
            }
        }
    });
}

/**
 * 시간대별 차트 렌더링
 */
function renderTimeChart(timeStats) {
    const ctx = document.getElementById('time-chart').getContext('2d');

    if (timeChart) {
        timeChart.destroy();
    }

    const labels = timeStats.map(s => s.timePeriod);
    const winRates = timeStats.map(s => s.winRate || 0);
    const trades = timeStats.map(s => s.totalTrades || 0);

    timeChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: '승률 (%)',
                    data: winRates,
                    backgroundColor: winRates.map(r => r >= 50 ? 'rgba(17, 153, 142, 0.7)' : 'rgba(245, 87, 108, 0.7)'),
                    borderRadius: 4,
                    yAxisID: 'y'
                },
                {
                    label: '거래 수',
                    data: trades,
                    type: 'line',
                    borderColor: '#f5576c',
                    pointBackgroundColor: '#f5576c',
                    tension: 0.4,
                    yAxisID: 'y1'
                }
            ]
        },
        options: {
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
        }
    });
}

/**
 * 종목별 순위 렌더링
 */
function renderSymbolRanking(symbolStats) {
    const container = document.getElementById('symbol-ranking');

    if (!symbolStats || symbolStats.length === 0) {
        container.innerHTML = '<div class="text-center text-muted py-4">거래 데이터가 없습니다.</div>';
        return;
    }

    let html = '';
    symbolStats.slice(0, 10).forEach((stat, index) => {
        const rank = index + 1;
        const rankClass = rank === 1 ? 'rank-1' : rank === 2 ? 'rank-2' : rank === 3 ? 'rank-3' : 'rank-other';
        const profitClass = stat.totalProfit >= 0 ? 'stat-positive' : 'stat-negative';

        html += `
            <div class="symbol-rank">
                <div class="rank-badge ${rankClass}">${rank}</div>
                <div class="flex-grow-1">
                    <div class="fw-bold">${stat.symbol}</div>
                    <small class="text-muted">${stat.stockName || stat.symbol}</small>
                </div>
                <div class="text-end">
                    <div class="${profitClass} fw-bold">${formatCurrency(stat.totalProfit)}</div>
                    <small class="text-muted">승률 ${stat.winRate.toFixed(1)}% | ${stat.totalTrades}건</small>
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

/**
 * 실수 패턴 렌더링
 */
function renderMistakePatterns(patterns) {
    const container = document.getElementById('mistake-patterns');

    if (!patterns || patterns.length === 0) {
        container.innerHTML = '<div class="text-center text-muted py-4"><i class="bi bi-check-circle text-success fs-1"></i><p class="mt-2">분석된 실수 패턴이 없습니다!</p></div>';
        return;
    }

    let html = '';
    patterns.forEach(pattern => {
        const severityClass = pattern.severity === 'HIGH' ? '' : pattern.severity === 'MEDIUM' ? 'medium' : 'low';
        const severityBadge = pattern.severity === 'HIGH' ? 'bg-danger' : pattern.severity === 'MEDIUM' ? 'bg-warning' : 'bg-info';

        html += `
            <div class="mistake-item ${severityClass}">
                <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                        <strong>${pattern.description}</strong>
                        <span class="badge ${severityBadge} ms-2">${pattern.severity}</span>
                    </div>
                    <span class="text-danger fw-bold">${formatCurrency(pattern.totalLoss)}</span>
                </div>
                <div class="small text-muted mb-2">${pattern.count}회 발생 | 평균 손실: ${formatCurrency(pattern.avgLoss)}</div>
                ${pattern.examples && pattern.examples.length > 0 ? `
                    <div class="small">
                        <strong>예시:</strong>
                        ${pattern.examples.slice(0, 2).map(e => `${e.symbol} (${e.date})`).join(', ')}
                    </div>
                ` : ''}
            </div>
        `;
    });

    container.innerHTML = html;
}

/**
 * 개선 제안 렌더링
 */
function renderSuggestions(suggestions) {
    const container = document.getElementById('suggestions');

    if (!suggestions || suggestions.length === 0) {
        container.innerHTML = '<div class="text-center text-muted py-4">현재 특별한 개선 제안이 없습니다.</div>';
        return;
    }

    let html = '<div class="row">';
    suggestions.forEach((suggestion, index) => {
        const priorityClass = suggestion.priority === 'HIGH' ? 'high' : suggestion.priority === 'MEDIUM' ? 'medium' : '';
        const priorityBadge = suggestion.priority === 'HIGH' ? 'bg-danger' : suggestion.priority === 'MEDIUM' ? 'bg-warning text-dark' : 'bg-success';
        const categoryIcon = getCategoryIcon(suggestion.category);

        html += `
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
    });
    html += '</div>';

    container.innerHTML = html;
}

/**
 * 카테고리 아이콘 반환
 */
function getCategoryIcon(category) {
    switch(category) {
        case 'TIME': return 'bi-clock';
        case 'SYMBOL': return 'bi-graph-up';
        case 'RISK': return 'bi-shield-exclamation';
        case 'BEHAVIOR': return 'bi-person-exclamation';
        default: return 'bi-lightbulb';
    }
}

/**
 * 빈 상태 표시
 */
function showEmptyState() {
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

/**
 * 통화 포맷팅
 */
function formatCurrency(amount) {
    if (amount === null || amount === undefined) return '0원';
    const prefix = amount >= 0 ? '+' : '';
    return prefix + new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW',
        maximumFractionDigits: 0
    }).format(amount);
}

/**
 * 짧은 통화 포맷팅
 */
function formatCurrencyShort(amount) {
    if (Math.abs(amount) >= 1000000) {
        return (amount / 1000000).toFixed(1) + 'M';
    } else if (Math.abs(amount) >= 1000) {
        return (amount / 1000).toFixed(0) + 'K';
    }
    return amount.toString();
}
