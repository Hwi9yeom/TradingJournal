const API_BASE_URL = '/api';

$(document).ready(function() {
    loadPortfolioSummary();
    loadTransactions();
    setupEventHandlers();
    setDefaultDateTime();
    // initializeCharts(); // TODO: charts.js 파일 생성 후 활성화
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

// Analysis functions
function analyzePeriod() {
    const startDate = $('#analysis-start-date').val();
    const endDate = $('#analysis-end-date').val();
    
    if (!startDate || !endDate) {
        alert('기간을 선택해주세요.');
        return;
    }
    
    $.ajax({
        url: `${API_BASE_URL}/analysis/period?startDate=${startDate}&endDate=${endDate}`,
        method: 'GET',
        success: function(data) {
            displayPeriodAnalysis(data);
        },
        error: function(xhr) {
            alert('분석 실패: ' + (xhr.responseJSON?.error || '알 수 없는 오류'));
        }
    });
}

function displayPeriodAnalysis(analysis) {
    let html = '<div class="row">';
    html += '<div class="col-md-6">';
    html += '<h6>기간 요약</h6>';
    html += '<table class="table table-sm">';
    html += `<tr><td>총 매수금액</td><td>${formatCurrency(analysis.totalBuyAmount)}</td></tr>`;
    html += `<tr><td>총 매도금액</td><td>${formatCurrency(analysis.totalSellAmount)}</td></tr>`;
    html += `<tr><td>실현손익</td><td class="${analysis.realizedProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(analysis.realizedProfit)} (${formatPercent(analysis.realizedProfitRate)})</td></tr>`;
    html += `<tr><td>미실현손익</td><td class="${analysis.unrealizedProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(analysis.unrealizedProfit)} (${formatPercent(analysis.unrealizedProfitRate)})</td></tr>`;
    html += `<tr><td>총손익</td><td class="${analysis.totalProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(analysis.totalProfit)} (${formatPercent(analysis.totalProfitRate)})</td></tr>`;
    html += `<tr><td>총거래횟수</td><td>${analysis.totalTransactions}회 (매수: ${analysis.buyTransactions}, 매도: ${analysis.sellTransactions})</td></tr>`;
    html += '</table>';
    html += '</div>';
    
    if (analysis.monthlyAnalysis && analysis.monthlyAnalysis.length > 0) {
        html += '<div class="col-md-6">';
        html += '<h6>월별 분석</h6>';
        html += '<table class="table table-sm">';
        html += '<thead><tr><th>월</th><th>매수</th><th>매도</th><th>손익</th></tr></thead>';
        html += '<tbody>';
        analysis.monthlyAnalysis.forEach(month => {
            html += '<tr>';
            html += `<td>${month.yearMonth}</td>`;
            html += `<td>${formatCurrency(month.buyAmount)}</td>`;
            html += `<td>${formatCurrency(month.sellAmount)}</td>`;
            html += `<td class="${month.profit >= 0 ? 'positive' : 'negative'}">${formatCurrency(month.profit)}</td>`;
            html += '</tr>';
        });
        html += '</tbody></table>';
        html += '</div>';
    }
    
    html += '</div>';
    $('#period-analysis-result').html(html);
}

function analyzeStock() {
    const symbol = $('#analysis-stock-symbol').val();
    
    if (!symbol) {
        alert('종목 코드를 입력해주세요.');
        return;
    }
    
    $.ajax({
        url: `${API_BASE_URL}/analysis/stock/${symbol}`,
        method: 'GET',
        success: function(data) {
            displayStockAnalysis(data);
        },
        error: function(xhr) {
            alert('분석 실패: ' + (xhr.responseJSON?.error || '알 수 없는 오류'));
        }
    });
}

function displayStockAnalysis(analysis) {
    let html = '<div class="row">';
    html += '<div class="col-md-6">';
    html += `<h6>${analysis.stockName} (${analysis.stockSymbol})</h6>`;
    html += '<table class="table table-sm">';
    html += `<tr><td>총 매수</td><td>${analysis.totalBuyCount}회 / ${formatNumber(analysis.totalBuyQuantity)}주</td></tr>`;
    html += `<tr><td>총 매도</td><td>${analysis.totalSellCount}회 / ${formatNumber(analysis.totalSellQuantity)}주</td></tr>`;
    html += `<tr><td>평균 매수가</td><td>${formatCurrency(analysis.averageBuyPrice)}</td></tr>`;
    html += `<tr><td>평균 매도가</td><td>${formatCurrency(analysis.averageSellPrice)}</td></tr>`;
    html += `<tr><td>실현손익</td><td class="${analysis.realizedProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(analysis.realizedProfit)} (${formatPercent(analysis.realizedProfitRate)})</td></tr>`;
    html += `<tr><td>현재보유</td><td>${formatNumber(analysis.currentHolding)}주</td></tr>`;
    html += `<tr><td>미실현손익</td><td class="${analysis.unrealizedProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(analysis.unrealizedProfit)} (${formatPercent(analysis.unrealizedProfitRate)})</td></tr>`;
    html += `<tr><td>보유기간</td><td>${analysis.holdingDays}일</td></tr>`;
    html += '</table>';
    html += '</div>';
    
    if (analysis.tradingPatterns && analysis.tradingPatterns.length > 0) {
        html += '<div class="col-md-6">';
        html += '<h6>매매 패턴</h6>';
        html += '<table class="table table-sm">';
        analysis.tradingPatterns.forEach(pattern => {
            html += '<tr>';
            html += `<td>${pattern.pattern}</td>`;
            html += `<td>${pattern.value}</td>`;
            html += '</tr>';
        });
        html += '</table>';
        html += '</div>';
    }
    
    html += '</div>';
    $('#stock-analysis-result').html(html);
}

function calculateTax() {
    const year = $('#tax-year').val();
    
    $.ajax({
        url: `${API_BASE_URL}/analysis/tax/${year}`,
        method: 'GET',
        success: function(data) {
            displayTaxCalculation(data);
        },
        error: function(xhr) {
            alert('세금 계산 실패: ' + (xhr.responseJSON?.error || '알 수 없는 오류'));
        }
    });
}

function displayTaxCalculation(tax) {
    let html = `<h6>${tax.taxYear}년 양도소득세 계산</h6>`;
    html += '<div class="alert alert-info">';
    html += '<table class="table table-sm mb-0">';
    html += `<tr><td>총 매도금액</td><td>${formatCurrency(tax.totalSellAmount)}</td></tr>`;
    html += `<tr><td>총 매수금액</td><td>${formatCurrency(tax.totalBuyAmount)}</td></tr>`;
    html += `<tr><td>총 이익</td><td class="positive">${formatCurrency(tax.totalProfit)}</td></tr>`;
    html += `<tr><td>총 손실</td><td class="negative">${formatCurrency(tax.totalLoss)}</td></tr>`;
    html += `<tr><td>순손익</td><td class="${tax.netProfit >= 0 ? 'positive' : 'negative'}">${formatCurrency(tax.netProfit)}</td></tr>`;
    html += `<tr><td>기본공제</td><td>₩2,500,000</td></tr>`;
    html += `<tr><td>과세표준</td><td>${formatCurrency(tax.taxableAmount)}</td></tr>`;
    html += `<tr><td>세율</td><td>${formatPercent(tax.taxRate)}</td></tr>`;
    html += `<tr><td><strong>예상 세금</strong></td><td><strong>${formatCurrency(tax.estimatedTax)}</strong></td></tr>`;
    html += '</table>';
    html += '</div>';
    
    if (tax.taxDetails && tax.taxDetails.length > 0) {
        html += '<h6>거래 상세</h6>';
        html += '<table class="table table-sm">';
        html += '<thead><tr><th>종목</th><th>매수일</th><th>매도일</th><th>매수금액</th><th>매도금액</th><th>손익</th></tr></thead>';
        html += '<tbody>';
        tax.taxDetails.forEach(detail => {
            html += '<tr>';
            html += `<td>${detail.stockSymbol}</td>`;
            html += `<td>${detail.buyDate}</td>`;
            html += `<td>${detail.sellDate}</td>`;
            html += `<td>${formatCurrency(detail.buyAmount)}</td>`;
            html += `<td>${formatCurrency(detail.sellAmount)}</td>`;
            html += `<td class="${detail.profit > 0 ? 'positive' : 'negative'}">${formatCurrency(detail.profit || -detail.loss)}</td>`;
            html += '</tr>';
        });
        html += '</tbody></table>';
    }
    
    $('#tax-calculation-result').html(html);
}

// Disclosure functions
function loadDisclosureSummary() {
    $.ajax({
        url: `${API_BASE_URL}/disclosures/summary`,
        method: 'GET',
        success: function(data) {
            updateDisclosureSummary(data);
        },
        error: function(xhr) {
            console.error('Failed to load disclosure summary:', xhr);
        }
    });
}

function updateDisclosureSummary(summary) {
    $('#total-disclosures').text(summary.totalCount || 0);
    $('#unread-disclosures').text(summary.unreadCount || 0);
    $('#important-disclosures').text(summary.importantCount || 0);
    
    // Update recent disclosures
    if (summary.recentDisclosures) {
        updateDisclosureTable('recent-disclosures-body', summary.recentDisclosures);
    }
    
    // Update unread disclosures
    if (summary.unreadDisclosures) {
        updateDisclosureTable('unread-disclosures-body', summary.unreadDisclosures);
    }
    
    // Update important disclosures
    if (summary.importantDisclosures) {
        updateDisclosureTable('important-disclosures-body', summary.importantDisclosures);
    }
}

function updateDisclosureTable(tableId, disclosures) {
    const tbody = $(`#${tableId}`);
    tbody.empty();
    
    if (disclosures.length === 0) {
        tbody.append('<tr><td colspan="6" class="text-center">공시 정보가 없습니다.</td></tr>');
        return;
    }
    
    disclosures.forEach(disclosure => {
        const row = $('<tr>');
        if (!disclosure.isRead) {
            row.addClass('table-warning');
        }
        
        row.append(`<td>${formatDateTime(disclosure.receivedDate)}</td>`);
        row.append(`<td>${disclosure.stockName} (${disclosure.stockSymbol})</td>`);
        row.append(`<td>${disclosure.reportName}</td>`);
        row.append(`<td>${disclosure.submitter}</td>`);
        row.append(`<td><span class="badge ${getReportTypeBadge(disclosure.reportType)}">${disclosure.reportType}</span></td>`);
        
        const actions = $('<td>');
        if (!disclosure.isRead) {
            actions.append(`<button class="btn btn-sm btn-outline-primary me-1" onclick="markAsRead(${disclosure.id})">읽음</button>`);
        }
        actions.append(`<button class="btn btn-sm ${disclosure.isImportant ? 'btn-warning' : 'btn-outline-warning'} me-1" onclick="toggleImportant(${disclosure.id})">
            <i class="bi ${disclosure.isImportant ? 'bi-star-fill' : 'bi-star'}"></i>
        </button>`);
        if (disclosure.viewUrl) {
            actions.append(`<a href="${disclosure.viewUrl}" target="_blank" class="btn btn-sm btn-outline-info">보기</a>`);
        }
        row.append(actions);
        
        tbody.append(row);
    });
}

function getReportTypeBadge(reportType) {
    switch(reportType) {
        case '정기공시':
            return 'bg-primary';
        case '주요사항보고':
            return 'bg-danger';
        case '자본시장법':
            return 'bg-warning';
        default:
            return 'bg-secondary';
    }
}

function markAsRead(disclosureId) {
    $.ajax({
        url: `${API_BASE_URL}/disclosures/${disclosureId}/read`,
        method: 'PUT',
        success: function() {
            loadDisclosureSummary();
        },
        error: function(xhr) {
            alert('공시 읽음 처리 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function toggleImportant(disclosureId) {
    $.ajax({
        url: `${API_BASE_URL}/disclosures/${disclosureId}/important`,
        method: 'PUT',
        success: function() {
            loadDisclosureSummary();
        },
        error: function(xhr) {
            alert('중요 표시 변경 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function syncDisclosures() {
    const btn = event.target;
    const originalHtml = btn.innerHTML;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> 동기화 중...';
    btn.disabled = true;
    
    $.ajax({
        url: `${API_BASE_URL}/disclosures/sync`,
        method: 'POST',
        success: function() {
            setTimeout(() => {
                loadDisclosureSummary();
                btn.innerHTML = originalHtml;
                btn.disabled = false;
                alert('공시 정보가 동기화되었습니다.');
            }, 1000);
        },
        error: function(xhr) {
            btn.innerHTML = originalHtml;
            btn.disabled = false;
            alert('공시 동기화 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// Set default dates and load disclosure summary
$(document).ready(function() {
    const today = new Date();
    const lastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    
    $('#analysis-start-date').val(lastMonth.toISOString().split('T')[0]);
    $('#analysis-end-date').val(today.toISOString().split('T')[0]);
    
    // Load disclosure summary
    loadDisclosureSummary();
});