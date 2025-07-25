const API_BASE_URL = '/api';
let assetValueChart, portfolioCompositionChart, monthlyReturnChart;

$(document).ready(function() {
    loadDashboardData();
    initializeCharts();
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
    
    // 실현 손익 계산 (임시로 0으로 설정, 실제로는 매도 거래에서 계산)
    const realizedPnL = 0; // TODO: 서버에서 실현 손익 계산 로직 추가 필요
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
    // 임시 데이터 생성 (실제로는 서버에서 계산된 데이터 사용)
    const labels = generateDateLabels(30);
    const values = generateRandomValues(30, 1000000, 2000000);
    
    if (assetValueChart) {
        assetValueChart.data.labels = labels;
        assetValueChart.data.datasets[0].data = values;
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
    // 임시 데이터 생성
    const months = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];
    const returns = months.map(() => (Math.random() - 0.5) * 20);
    
    if (monthlyReturnChart) {
        monthlyReturnChart.data.labels = months;
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