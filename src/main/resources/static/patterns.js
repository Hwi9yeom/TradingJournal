const API_BASE_URL = '/api';
let dayOfWeekChart, monthlySeasonalityChart;
let currentPeriod = '1Y';

$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    initializeCharts();
    loadPatterns(currentPeriod);
});

function initializeCharts() {
    // 요일별 성과 차트
    const dayCtx = document.getElementById('dayOfWeekChart').getContext('2d');
    dayOfWeekChart = new Chart(dayCtx, {
        type: 'bar',
        data: {
            labels: ['월', '화', '수', '목', '금'],
            datasets: [{
                label: '승률 (%)',
                data: [],
                backgroundColor: 'rgba(54, 162, 235, 0.7)',
                yAxisID: 'y'
            }, {
                label: '수익금',
                data: [],
                type: 'line',
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.1)',
                yAxisID: 'y1',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'top'
                }
            },
            scales: {
                y: {
                    type: 'linear',
                    position: 'left',
                    title: {
                        display: true,
                        text: '승률 (%)'
                    },
                    min: 0,
                    max: 100
                },
                y1: {
                    type: 'linear',
                    position: 'right',
                    title: {
                        display: true,
                        text: '수익금'
                    },
                    grid: {
                        drawOnChartArea: false
                    }
                }
            }
        }
    });

    // 월별 계절성 차트
    const monthCtx = document.getElementById('monthlySeasonalityChart').getContext('2d');
    monthlySeasonalityChart = new Chart(monthCtx, {
        type: 'bar',
        data: {
            labels: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
            datasets: [{
                label: '수익금',
                data: [],
                backgroundColor: function(context) {
                    const value = context.parsed.y;
                    return value >= 0 ? 'rgba(34, 197, 94, 0.7)' : 'rgba(239, 68, 68, 0.7)';
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
                            return '수익금: ' + formatCurrency(context.parsed.y);
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

function loadPatterns(period) {
    const range = getDateRange(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/patterns`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateStreakAnalysis(data.streakAnalysis);
            updateDayOfWeekPerformance(data.dayOfWeekPerformance);
            updateMonthlySeasonality(data.monthlySeasonality);
            updateTradeSizeAnalysis(data.tradeSizeAnalysis);
            updateHoldingPeriodAnalysis(data.holdingPeriodAnalysis);
            $('#total-trades').text(data.totalTrades);
        },
        error: function(xhr) {
            console.error('Failed to load trading patterns:', xhr);
        }
    });
}

function updatePeriod(period) {
    currentPeriod = period;

    // 버튼 활성화 상태 변경
    $('.btn-group button').removeClass('active');
    event.target.classList.add('active');

    loadPatterns(period);
}

function updateStreakAnalysis(data) {
    if (!data) return;

    // 현재 스트릭 상태
    const current = data.currentStreak;
    const $current = $('#current-streak');
    if (current > 0) {
        $current.text(`${current}연승`).removeClass('text-danger').addClass('text-success');
    } else if (current < 0) {
        $current.text(`${Math.abs(current)}연패`).removeClass('text-success').addClass('text-danger');
    } else {
        $current.text('-').removeClass('text-success text-danger');
    }

    $('#max-win-streak').text(data.maxWinStreak + '연승');
    $('#max-loss-streak').text(data.maxLossStreak + '연패');
    $('#avg-win-streak').text(parseFloat(data.avgWinStreak || 0).toFixed(1));
    $('#avg-loss-streak').text(parseFloat(data.avgLossStreak || 0).toFixed(1));

    // 최근 스트릭 타임라인
    const $container = $('#recent-streaks');
    if (!data.recentStreaks || data.recentStreaks.length === 0) {
        $container.html('<div class="text-center text-muted py-3">최근 연속 기록 없음</div>');
        return;
    }

    let html = '<div class="list-group">';
    data.recentStreaks.forEach(streak => {
        const isWin = streak.winStreak || streak.isWinStreak;
        const cssClass = isWin ? 'streak-win' : 'streak-loss';
        const icon = isWin ? 'bi-trophy-fill text-success' : 'bi-x-circle-fill text-danger';
        const label = isWin ? '연승' : '연패';
        const profit = parseFloat(streak.totalProfit || 0);

        html += `
            <div class="list-group-item ${cssClass} d-flex justify-content-between align-items-center">
                <div>
                    <i class="bi ${icon} me-2"></i>
                    <strong>${streak.streakLength}${label}</strong>
                    <small class="text-muted ms-2">${streak.startDate} ~ ${streak.endDate}</small>
                </div>
                <span class="${profit >= 0 ? 'text-success' : 'text-danger'}">${formatCurrency(profit)}</span>
            </div>
        `;
    });
    html += '</div>';
    $container.html(html);
}

function updateDayOfWeekPerformance(data) {
    if (!data) return;

    // 월~금만 필터링 (주말 제외)
    const weekdays = data.filter(d => d.dayOfWeek !== 'SATURDAY' && d.dayOfWeek !== 'SUNDAY');
    const labels = weekdays.map(d => d.dayOfWeekKorean);
    const winRates = weekdays.map(d => parseFloat(d.winRate || 0));
    const profits = weekdays.map(d => parseFloat(d.totalProfit || 0));

    dayOfWeekChart.data.labels = labels;
    dayOfWeekChart.data.datasets[0].data = winRates;
    dayOfWeekChart.data.datasets[1].data = profits;
    dayOfWeekChart.update();

    // 테이블 업데이트
    let tableHtml = '';
    weekdays.forEach(d => {
        const profit = parseFloat(d.totalProfit || 0);
        tableHtml += `
            <tr>
                <td>${d.dayOfWeekKorean}</td>
                <td class="text-end">${d.tradeCount}</td>
                <td class="text-end">${parseFloat(d.winRate || 0).toFixed(1)}%</td>
                <td class="text-end ${profit >= 0 ? 'text-success' : 'text-danger'}">${formatCurrency(profit)}</td>
            </tr>
        `;
    });
    $('#day-of-week-table tbody').html(tableHtml);
}

function updateMonthlySeasonality(data) {
    if (!data) return;

    const profits = data.map(d => parseFloat(d.totalProfit || 0));

    monthlySeasonalityChart.data.datasets[0].data = profits;
    monthlySeasonalityChart.update();

    // 테이블 업데이트
    let tableHtml = '';
    data.forEach(d => {
        if (d.tradeCount > 0) {
            const profit = parseFloat(d.totalProfit || 0);
            tableHtml += `
                <tr>
                    <td>${d.monthName}</td>
                    <td class="text-end">${d.tradeCount}</td>
                    <td class="text-end">${parseFloat(d.winRate || 0).toFixed(1)}%</td>
                    <td class="text-end ${profit >= 0 ? 'text-success' : 'text-danger'}">${formatCurrency(profit)}</td>
                </tr>
            `;
        }
    });
    $('#monthly-table tbody').html(tableHtml || '<tr><td colspan="4" class="text-center text-muted">데이터 없음</td></tr>');
}

function updateTradeSizeAnalysis(data) {
    if (!data) return;

    $('#avg-trade-amount').text(formatCurrency(data.avgTradeAmount || 0));
    $('#max-trade-amount').text(formatCurrency(data.maxTradeAmount || 0));
    $('#min-trade-amount').text(formatCurrency(data.minTradeAmount || 0));
    $('#median-trade-amount').text(formatCurrency(data.medianTradeAmount || 0));
    $('#avg-win-amount').text(formatCurrency(data.avgWinTradeAmount || 0));
    $('#avg-loss-amount').text(formatCurrency(data.avgLossTradeAmount || 0));
}

function updateHoldingPeriodAnalysis(data) {
    if (!data) return;

    $('#avg-holding-days').text(parseFloat(data.avgHoldingDays || 0).toFixed(1) + '일');
    $('#avg-win-holding').text(parseFloat(data.avgWinHoldingDays || 0).toFixed(1) + '일');
    $('#avg-loss-holding').text(parseFloat(data.avgLossHoldingDays || 0).toFixed(1) + '일');

    // 보유 기간별 분포 바 차트
    const $container = $('#holding-distribution');
    if (!data.holdingPeriodDistribution) {
        $container.html('<div class="text-center text-muted">데이터 없음</div>');
        return;
    }

    const totalTrades = data.holdingPeriodDistribution.reduce((sum, b) => sum + b.tradeCount, 0);

    let html = '';
    data.holdingPeriodDistribution.forEach(bucket => {
        const percentage = totalTrades > 0 ? (bucket.tradeCount / totalTrades * 100) : 0;
        const barColor = bucket.totalProfit >= 0 ? 'bg-success' : 'bg-danger';

        html += `
            <div class="mb-2">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span>${bucket.label}</span>
                    <span class="text-muted">${bucket.tradeCount}건 (${percentage.toFixed(1)}%)</span>
                </div>
                <div class="progress" style="height: 20px;">
                    <div class="progress-bar ${barColor}" role="progressbar"
                         style="width: ${percentage}%;"
                         title="승률: ${parseFloat(bucket.winRate || 0).toFixed(1)}%">
                        ${percentage > 10 ? percentage.toFixed(0) + '%' : ''}
                    </div>
                </div>
            </div>
        `;
    });
    $container.html(html);
}

function formatCurrency(value) {
    return '₩' + Math.round(value || 0).toLocaleString();
}
