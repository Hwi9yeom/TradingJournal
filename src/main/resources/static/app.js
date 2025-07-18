const API_BASE_URL = '/api';

$(document).ready(function() {
    loadPortfolioSummary();
    loadTransactions();
    setupEventHandlers();
    setDefaultDateTime();
});

function setupEventHandlers() {
    $('#transaction-form').on('submit', function(e) {
        e.preventDefault();
        addTransaction();
    });
}

function setDefaultDateTime() {
    const now = new Date();
    const dateTimeLocal = now.getFullYear() + '-' + 
        String(now.getMonth() + 1).padStart(2, '0') + '-' +
        String(now.getDate()).padStart(2, '0') + 'T' +
        String(now.getHours()).padStart(2, '0') + ':' +
        String(now.getMinutes()).padStart(2, '0');
    $('#transaction-date').val(dateTimeLocal);
}

function loadPortfolioSummary() {
    $.ajax({
        url: `${API_BASE_URL}/portfolio/summary`,
        method: 'GET',
        success: function(data) {
            updatePortfolioSummary(data);
            updatePortfolioHoldings(data.holdings);
        },
        error: function(xhr) {
            console.error('Failed to load portfolio summary:', xhr);
        }
    });
}

function updatePortfolioSummary(summary) {
    $('#total-investment').text(formatCurrency(summary.totalInvestment));
    $('#total-value').text(formatCurrency(summary.totalCurrentValue));
    
    const profitLossText = formatCurrency(summary.totalProfitLoss) + ' (' + formatPercent(summary.totalProfitLossPercent) + ')';
    $('#total-profit').text(profitLossText).removeClass('positive negative').addClass(summary.totalProfitLoss >= 0 ? 'positive' : 'negative');
    
    const dayChangeText = formatCurrency(summary.totalDayChange) + ' (' + formatPercent(summary.totalDayChangePercent) + ')';
    $('#day-change').text(dayChangeText).removeClass('positive negative').addClass(summary.totalDayChange >= 0 ? 'positive' : 'negative');
}

function updatePortfolioHoldings(holdings) {
    const tbody = $('#portfolio-holdings');
    tbody.empty();
    
    holdings.forEach(holding => {
        const row = $('<tr>');
        row.append(`<td>${holding.stockName} (${holding.stockSymbol})</td>`);
        row.append(`<td>${formatNumber(holding.quantity)}</td>`);
        row.append(`<td>${formatCurrency(holding.averagePrice)}</td>`);
        row.append(`<td>${formatCurrency(holding.currentPrice)}</td>`);
        row.append(`<td>${formatCurrency(holding.currentValue)}</td>`);
        
        const profitLossCell = $('<td>').text(formatCurrency(holding.profitLoss) + ' (' + formatPercent(holding.profitLossPercent) + ')');
        profitLossCell.addClass(holding.profitLoss >= 0 ? 'positive' : 'negative');
        row.append(profitLossCell);
        
        const dayChangeCell = $('<td>').text(formatCurrency(holding.dayChange) + ' (' + formatPercent(holding.dayChangePercent) + ')');
        dayChangeCell.addClass(holding.dayChange >= 0 ? 'positive' : 'negative');
        row.append(dayChangeCell);
        
        tbody.append(row);
    });
}

function loadTransactions() {
    $.ajax({
        url: `${API_BASE_URL}/transactions`,
        method: 'GET',
        success: function(data) {
            updateTransactionList(data);
        },
        error: function(xhr) {
            console.error('Failed to load transactions:', xhr);
        }
    });
}

function updateTransactionList(transactions) {
    const tbody = $('#transaction-list');
    tbody.empty();
    
    transactions.forEach(transaction => {
        const row = $('<tr>');
        row.append(`<td>${formatDateTime(transaction.transactionDate)}</td>`);
        row.append(`<td>${transaction.stockName} (${transaction.stockSymbol})</td>`);
        row.append(`<td><span class="badge ${transaction.type === 'BUY' ? 'bg-success' : 'bg-danger'}">${transaction.type === 'BUY' ? '매수' : '매도'}</span></td>`);
        row.append(`<td>${formatNumber(transaction.quantity)}</td>`);
        row.append(`<td>${formatCurrency(transaction.price)}</td>`);
        row.append(`<td>${formatCurrency(transaction.totalAmount)}</td>`);
        row.append(`<td><button class="btn btn-sm btn-danger btn-delete" onclick="deleteTransaction(${transaction.id})">삭제</button></td>`);
        tbody.append(row);
    });
}

function addTransaction() {
    const transaction = {
        stockSymbol: $('#stock-symbol').val().toUpperCase(),
        type: $('#transaction-type').val(),
        quantity: parseFloat($('#quantity').val()),
        price: parseFloat($('#price').val()),
        commission: parseFloat($('#commission').val()) || 0,
        transactionDate: $('#transaction-date').val()
    };
    
    $.ajax({
        url: `${API_BASE_URL}/transactions`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(transaction),
        success: function() {
            $('#transaction-form')[0].reset();
            setDefaultDateTime();
            loadPortfolioSummary();
            loadTransactions();
            alert('거래가 추가되었습니다.');
        },
        error: function(xhr) {
            alert('거래 추가 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function deleteTransaction(id) {
    if (!confirm('정말 삭제하시겠습니까?')) {
        return;
    }
    
    $.ajax({
        url: `${API_BASE_URL}/transactions/${id}`,
        method: 'DELETE',
        success: function() {
            loadPortfolioSummary();
            loadTransactions();
            alert('거래가 삭제되었습니다.');
        },
        error: function(xhr) {
            alert('거래 삭제 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function formatCurrency(value) {
    return '₩' + new Intl.NumberFormat('ko-KR').format(Math.round(value || 0));
}

function formatNumber(value) {
    return new Intl.NumberFormat('ko-KR').format(value || 0);
}

function formatPercent(value) {
    return (value || 0).toFixed(2) + '%';
}

function formatDateTime(dateTimeStr) {
    const date = new Date(dateTimeStr);
    return date.getFullYear() + '-' +
        String(date.getMonth() + 1).padStart(2, '0') + '-' +
        String(date.getDate()).padStart(2, '0') + ' ' +
        String(date.getHours()).padStart(2, '0') + ':' +
        String(date.getMinutes()).padStart(2, '0');
}

function exportData(format) {
    const url = `${API_BASE_URL}/data/export/${format}`;
    window.location.href = url;
}

function downloadTemplate() {
    const url = `${API_BASE_URL}/data/template/csv`;
    window.location.href = url;
}

function importData() {
    const fileInput = $('#import-file')[0];
    const fileType = $('#import-file-type').val();
    
    if (!fileInput.files.length) {
        alert('파일을 선택해주세요.');
        return;
    }
    
    const file = fileInput.files[0];
    const formData = new FormData();
    formData.append('file', file);
    
    $.ajax({
        url: `${API_BASE_URL}/data/import/${fileType}`,
        method: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        success: function(result) {
            displayImportResult(result);
            if (result.successCount > 0) {
                loadPortfolioSummary();
                loadTransactions();
            }
        },
        error: function(xhr) {
            alert('파일 가져오기 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function displayImportResult(result) {
    let html = '<div class="alert ' + (result.failureCount > 0 ? 'alert-warning' : 'alert-success') + '">';
    html += `<strong>가져오기 완료</strong><br>`;
    html += `전체: ${result.totalRows}건, 성공: ${result.successCount}건, 실패: ${result.failureCount}건`;
    
    if (result.errors && result.errors.length > 0) {
        html += '<hr><strong>오류 내역:</strong><ul>';
        result.errors.forEach(error => {
            html += `<li>행 ${error.rowNumber}: ${error.message}</li>`;
        });
        html += '</ul>';
    }
    
    html += '</div>';
    
    $('#import-result').html(html).show();
}