const API_BASE_URL = '/api';
let monthlyDividendChart;
let dividends = [];

$(document).ready(function() {
    loadStockOptions();
    loadDividendSummary();
    loadDividends();
    setupEventHandlers();
    initializeChart();
});

function setupEventHandlers() {
    $('#dividend-form').on('submit', function(e) {
        e.preventDefault();
        addDividend();
    });
    
    $('#dividend-filter').on('change', function() {
        filterDividends($(this).val());
    });
}

function loadStockOptions() {
    $.ajax({
        url: `${API_BASE_URL}/stocks`,
        method: 'GET',
        success: function(stocks) {
            const select = $('#stock-select');
            stocks.forEach(stock => {
                select.append(`<option value="${stock.id}">${stock.name} (${stock.symbol})</option>`);
            });
        },
        error: function(xhr) {
            console.error('Failed to load stocks:', xhr);
        }
    });
}

function loadDividendSummary() {
    $.ajax({
        url: `${API_BASE_URL}/dividends/summary`,
        method: 'GET',
        success: function(summary) {
            updateSummaryCards(summary);
            updateTopDividendStocks(summary.topDividendStocks);
            updateMonthlyChart(summary.monthlyDividends);
        },
        error: function(xhr) {
            console.error('Failed to load dividend summary:', xhr);
        }
    });
}

function updateSummaryCards(summary) {
    $('#total-dividends').text(formatCurrency(summary.totalDividends || 0));
    $('#yearly-dividends').text(formatCurrency(summary.yearlyDividends || 0));
    $('#monthly-average').text(formatCurrency(summary.monthlyAverage || 0));
    $('#dividend-yield').text((summary.dividendYield || 0).toFixed(2) + '%');
}

function updateTopDividendStocks(topStocks) {
    const tbody = $('#top-dividend-stocks');
    tbody.empty();
    
    if (!topStocks || topStocks.length === 0) {
        tbody.append('<tr><td colspan="3" class="text-center text-muted">배당금 지급 종목 없음</td></tr>');
        return;
    }
    
    topStocks.forEach(stock => {
        const row = $('<tr>');
        row.append(`<td>${stock.stockName}</td>`);
        row.append(`<td class="text-end">${formatCurrency(stock.totalDividend)}</td>`);
        row.append(`<td class="text-end">${stock.paymentCount}회</td>`);
        tbody.append(row);
    });
}

function updateMonthlyChart(monthlyData) {
    if (!monthlyData || monthlyData.length === 0) return;
    
    // 최근 12개월 데이터 준비
    const labels = [];
    const data = [];
    
    monthlyData.slice(0, 12).reverse().forEach(item => {
        labels.push(`${item.year}.${String(item.month).padStart(2, '0')}`);
        data.push(item.amount);
    });
    
    if (monthlyDividendChart) {
        monthlyDividendChart.data.labels = labels;
        monthlyDividendChart.data.datasets[0].data = data;
        monthlyDividendChart.update();
    }
}

function loadDividends() {
    $.ajax({
        url: `${API_BASE_URL}/dividends`,
        method: 'GET',
        success: function(data) {
            dividends = data;
            displayDividends(data);
        },
        error: function(xhr) {
            console.error('Failed to load dividends:', xhr);
        }
    });
}

function displayDividends(dividendList) {
    const tbody = $('#dividend-list');
    tbody.empty();
    
    if (!dividendList || dividendList.length === 0) {
        tbody.append('<tr><td colspan="10" class="text-center text-muted">배당금 내역이 없습니다</td></tr>');
        return;
    }
    
    dividendList.forEach(dividend => {
        const row = $('<tr>');
        row.append(`<td>${formatDate(dividend.paymentDate)}</td>`);
        row.append(`<td>${dividend.stockName} (${dividend.stockSymbol})</td>`);
        row.append(`<td>${formatDate(dividend.exDividendDate)}</td>`);
        row.append(`<td class="text-end">${formatCurrency(dividend.dividendPerShare)}</td>`);
        row.append(`<td class="text-end">${formatNumber(dividend.quantity)}</td>`);
        row.append(`<td class="text-end">${formatCurrency(dividend.totalAmount)}</td>`);
        row.append(`<td class="text-end text-danger">${formatCurrency(dividend.taxAmount)}</td>`);
        row.append(`<td class="text-end text-success">${formatCurrency(dividend.netAmount)}</td>`);
        row.append(`<td><small>${dividend.memo || ''}</small></td>`);
        row.append(`
            <td>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteDividend(${dividend.id})">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `);
        tbody.append(row);
    });
}

function addDividend() {
    const stockId = $('#stock-select').val();
    const stockOption = $('#stock-select option:selected');
    const stockSymbol = stockOption.text().match(/\(([^)]+)\)/)[1];
    const stockName = stockOption.text().replace(/\s*\([^)]*\)/, '');
    
    const dividendData = {
        stockId: parseInt(stockId),
        stockSymbol: stockSymbol,
        stockName: stockName,
        exDividendDate: $('#ex-dividend-date').val(),
        paymentDate: $('#payment-date').val(),
        dividendPerShare: parseFloat($('#dividend-per-share').val()),
        quantity: parseFloat($('#quantity').val()),
        taxRate: parseFloat($('#tax-rate').val()),
        memo: $('#memo').val()
    };
    
    $.ajax({
        url: `${API_BASE_URL}/dividends`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(dividendData),
        success: function(dividend) {
            $('#dividend-form')[0].reset();
            $('#tax-rate').val('15.4'); // 기본 세율 복원
            loadDividends();
            loadDividendSummary();
            showAlert('배당금이 추가되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('Failed to add dividend:', xhr);
            showAlert('배당금 추가에 실패했습니다.', 'danger');
        }
    });
}

function deleteDividend(id) {
    if (!confirm('정말로 이 배당금 기록을 삭제하시겠습니까?')) {
        return;
    }
    
    $.ajax({
        url: `${API_BASE_URL}/dividends/${id}`,
        method: 'DELETE',
        success: function() {
            loadDividends();
            loadDividendSummary();
            showAlert('배당금 기록이 삭제되었습니다.', 'success');
        },
        error: function(xhr) {
            console.error('Failed to delete dividend:', xhr);
            showAlert('배당금 삭제에 실패했습니다.', 'danger');
        }
    });
}

function filterDividends(filter) {
    const now = new Date();
    let filteredDividends = [...dividends];
    
    switch(filter) {
        case 'year':
            filteredDividends = dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate.getFullYear() === now.getFullYear();
            });
            break;
        case 'quarter':
            const quarterStart = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
            filteredDividends = dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate >= quarterStart;
            });
            break;
        case 'month':
            filteredDividends = dividends.filter(d => {
                const paymentDate = new Date(d.paymentDate);
                return paymentDate.getFullYear() === now.getFullYear() && 
                       paymentDate.getMonth() === now.getMonth();
            });
            break;
    }
    
    displayDividends(filteredDividends);
}

function initializeChart() {
    const ctx = document.getElementById('monthlyDividendChart').getContext('2d');
    monthlyDividendChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: '월별 배당금',
                data: [],
                backgroundColor: 'rgba(75, 192, 192, 0.8)',
                borderColor: 'rgba(75, 192, 192, 1)',
                borderWidth: 1
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
                            return '배당금: ' + formatCurrency(context.parsed.y);
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
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

// 유틸리티 함수들
function formatCurrency(value) {
    return '₩' + Math.round(value).toLocaleString();
}

function formatNumber(value) {
    return value.toLocaleString();
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR');
}

function showAlert(message, type) {
    const alertDiv = $(`
        <div class="alert alert-${type} alert-dismissible fade show position-fixed top-0 start-50 translate-middle-x mt-3" style="z-index: 1050;">
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    `);
    $('body').append(alertDiv);
    setTimeout(() => alertDiv.alert('close'), 3000);
}