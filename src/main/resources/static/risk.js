const API_BASE_URL = '/api';

// Charts
let rMultipleChart = null;
let sectorChart = null;

// Current account ID
let currentAccountId = null;

// Settings cache
let riskSettings = null;

$(document).ready(function() {
    // Check authentication
    if (!checkAuth()) {
        return;
    }

    // Initialize
    initializeCharts();
    loadDashboard();
    loadRMultipleAnalysis();
    loadPositionRisks();
    loadSectorExposures();
    loadSettings();

    // Setup event handlers
    setupEventHandlers();
});

function setupEventHandlers() {
    // Position sizing form
    $('#positionSizingForm').on('submit', function(e) {
        e.preventDefault();
        calculatePositionSize();
    });

    // Settings modal
    $('#settingsModal').on('show.bs.modal', function() {
        populateSettingsForm();
    });
}

// ===== Dashboard =====

function loadDashboard() {
    let url = `${API_BASE_URL}/risk/dashboard`;
    if (currentAccountId) {
        url += `?accountId=${currentAccountId}`;
    }

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateDashboardCards(data);
        },
        error: function(xhr) {
            console.error('Failed to load risk dashboard:', xhr);
        }
    });
}

function updateDashboardCards(data) {
    // Daily loss status
    updateLimitCard('daily', data.dailyLossStatus, data.todayPnl);

    // Weekly loss status
    updateLimitCard('weekly', data.weeklyLossStatus, data.weekPnl);

    // Position count status
    updatePositionCountCard(data.positionCountStatus);

    // Total open risk
    $('#totalOpenRisk').text(formatCurrency(data.totalOpenRisk || 0));
    $('#openRiskPercent').text(formatPercent(data.openRiskPercent || 0));
}

function updateLimitCard(type, status, pnl) {
    const pnlEl = $(`#${type}Pnl`);
    const statusEl = $(`#${type}LossStatus`);
    const progressEl = $(`#${type}LossProgress`);
    const remainingEl = $(`#${type}LossRemaining`);
    const cardEl = $(`#${type}LossCard`);

    if (!status) {
        pnlEl.text(formatCurrency(pnl || 0));
        statusEl.text('-').removeClass().addClass('badge bg-secondary');
        return;
    }

    pnlEl.text(formatCurrency(pnl || 0));
    pnlEl.removeClass('text-success text-danger');
    if (pnl < 0) {
        pnlEl.addClass('text-danger');
    } else if (pnl > 0) {
        pnlEl.addClass('text-success');
    }

    const percentUsed = Math.min(100, Math.abs(status.percentUsed || 0));
    progressEl.css('width', percentUsed + '%');

    // Status styling
    cardEl.removeClass('border-success border-warning border-danger');
    progressEl.removeClass('bg-success bg-warning bg-danger');
    statusEl.removeClass().addClass('badge');

    if (status.isBreached) {
        statusEl.addClass('bg-danger').text('초과');
        progressEl.addClass('bg-danger');
        cardEl.addClass('border-danger');
    } else if (percentUsed > 70) {
        statusEl.addClass('bg-warning').text('주의');
        progressEl.addClass('bg-warning');
        cardEl.addClass('border-warning');
    } else {
        statusEl.addClass('bg-success').text('정상');
        progressEl.addClass('bg-success');
        cardEl.addClass('border-success');
    }

    remainingEl.text(`한도: ${formatCurrency(status.limit || 0)}`);
}

function updatePositionCountCard(status) {
    const countEl = $('#positionCount');
    const statusEl = $('#positionCountStatus');
    const progressEl = $('#positionCountProgress');
    const remainingEl = $('#positionCountRemaining');
    const cardEl = $('#positionCountCard');

    if (!status) {
        countEl.text('0 / -');
        statusEl.text('-').removeClass().addClass('badge bg-secondary');
        return;
    }

    countEl.text(`${status.current || 0} / ${status.limit || 10}`);

    const percentUsed = status.limit > 0 ? ((status.current / status.limit) * 100) : 0;
    progressEl.css('width', percentUsed + '%');

    // Status styling
    cardEl.removeClass('border-success border-warning border-danger');
    progressEl.removeClass('bg-success bg-warning bg-danger');
    statusEl.removeClass().addClass('badge');

    if (status.isBreached) {
        statusEl.addClass('bg-danger').text('초과');
        progressEl.addClass('bg-danger');
        cardEl.addClass('border-danger');
    } else if (percentUsed > 70) {
        statusEl.addClass('bg-warning').text('주의');
        progressEl.addClass('bg-warning');
        cardEl.addClass('border-warning');
    } else {
        statusEl.addClass('bg-success').text('정상');
        progressEl.addClass('bg-success');
        cardEl.addClass('border-success');
    }

    remainingEl.text(`남은 슬롯: ${(status.limit || 0) - (status.current || 0)}`);
}

// ===== Position Sizing =====

function calculatePositionSize() {
    const entryPrice = parseFloat($('#entryPrice').val());
    const stopLossPrice = parseFloat($('#stopLossPrice').val());
    const takeProfitPrice = $('#takeProfitPrice').val() ? parseFloat($('#takeProfitPrice').val()) : null;
    const riskPercent = parseFloat($('#riskPercent').val()) || 2;

    if (!entryPrice || !stopLossPrice) {
        alert('진입가와 손절가를 입력해주세요.');
        return;
    }

    if (entryPrice === stopLossPrice) {
        alert('진입가와 손절가가 같을 수 없습니다.');
        return;
    }

    const request = {
        entryPrice: entryPrice,
        stopLossPrice: stopLossPrice,
        takeProfitPrice: takeProfitPrice,
        riskPercent: riskPercent,
        method: 'FIXED_FRACTIONAL'
    };

    if (currentAccountId) {
        request.accountId = currentAccountId;
    }

    $.ajax({
        url: `${API_BASE_URL}/risk/position-size`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(result) {
            displayPositionSizingResult(result);
        },
        error: function(xhr) {
            alert('계산 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

function displayPositionSizingResult(result) {
    $('#sizingResult').removeClass('d-none');
    $('#scenariosCard').removeClass('d-none');

    // Main results
    $('#recommendedQty').text(formatNumber(result.recommendedQuantity || 0));
    $('#positionValue').text(formatCurrency(result.recommendedPositionValue || 0));

    if (result.riskRewardRatio) {
        $('#rrRatio').text('1:' + result.riskRewardRatio.toFixed(2));
    } else {
        $('#rrRatio').text('-');
    }

    // Potential loss/profit
    $('#potentialLoss').text(formatCurrency(result.potentialLoss || 0));
    if (result.potentialProfit) {
        $('#potentialProfit').text(formatCurrency(result.potentialProfit));
    } else {
        $('#potentialProfit').text('-');
    }

    // Kelly info
    if (result.kellyPercentage && result.kellyPercentage > 0) {
        $('#kellyInfo').html(`
            <i class="bi bi-info-circle me-1"></i>
            Kelly Criterion: ${result.kellyPercentage.toFixed(2)}%
            (Full: ${formatNumber(result.fullKellyQuantity)}주,
            Half: ${formatNumber(result.halfKellyQuantity)}주,
            Quarter: ${formatNumber(result.quarterKellyQuantity)}주)
        `);
    } else {
        $('#kellyInfo').html('');
    }

    // Warning
    const warningEl = $('#sizingWarning');
    if (result.warningMessage) {
        warningEl.text(result.warningMessage).removeClass('d-none');
    } else {
        warningEl.addClass('d-none');
    }

    // Scenarios
    if (result.scenarios && result.scenarios.length > 0) {
        const tbody = $('#scenariosBody');
        tbody.empty();

        result.scenarios.forEach(scenario => {
            tbody.append(`
                <tr>
                    <td>${scenario.name}</td>
                    <td class="text-end">${formatNumber(scenario.quantity)}</td>
                    <td class="text-end">${formatCurrency(scenario.positionValue)}</td>
                    <td class="text-end text-danger">${formatCurrency(scenario.potentialLoss)}</td>
                </tr>
            `);
        });
    }
}

// ===== R-Multiple Analysis =====

function loadRMultipleAnalysis() {
    const months = parseInt($('#rMultiplePeriod').val()) || 3;
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - months);

    let url = `${API_BASE_URL}/risk/r-multiple?startDate=${formatDate(startDate)}&endDate=${formatDate(endDate)}`;
    if (currentAccountId) {
        url += `&accountId=${currentAccountId}`;
    }

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateRMultipleDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load R-multiple analysis:', xhr);
        }
    });
}

function updateRMultipleDisplay(data) {
    // Summary stats
    $('#avgRMultiple').text((data.averageRMultiple || 0).toFixed(2) + 'R');
    $('#expectancy').text((data.expectancy || 0).toFixed(2));
    $('#positiveRCount').text(data.tradesWithPositiveR || 0);
    $('#negativeRCount').text(data.tradesWithNegativeR || 0);

    // Chart
    if (data.distribution && data.distribution.length > 0) {
        updateRMultipleChart(data.distribution);
    }
}

function updateRMultipleChart(distribution) {
    const labels = distribution.map(d => d.range);
    const values = distribution.map(d => d.count);
    const colors = distribution.map(d => {
        if (d.range.includes('-')) {
            return 'rgba(239, 68, 68, 0.7)'; // Red for negative
        } else {
            return 'rgba(34, 197, 94, 0.7)'; // Green for positive
        }
    });

    if (rMultipleChart) {
        rMultipleChart.data.labels = labels;
        rMultipleChart.data.datasets[0].data = values;
        rMultipleChart.data.datasets[0].backgroundColor = colors;
        rMultipleChart.update();
    }
}

// ===== Sector Exposure =====

function loadSectorExposures() {
    let url = `${API_BASE_URL}/risk/sector-exposure`;
    if (currentAccountId) {
        url += `?accountId=${currentAccountId}`;
    }

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updateSectorDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load sector exposure:', xhr);
        }
    });
}

function updateSectorDisplay(exposures) {
    if (!exposures || exposures.length === 0) {
        $('#sectorList').html('<p class="text-muted">노출된 섹터가 없습니다.</p>');
        return;
    }

    // Update chart
    const labels = exposures.map(e => e.sectorLabel || e.sector);
    const values = exposures.map(e => e.percentage);
    const colors = generateColors(exposures.length);

    if (sectorChart) {
        sectorChart.data.labels = labels;
        sectorChart.data.datasets[0].data = values;
        sectorChart.data.datasets[0].backgroundColor = colors;
        sectorChart.update();
    }

    // Update list
    let html = '';
    exposures.forEach((e, i) => {
        const exceedClass = e.exceedsLimit ? 'text-danger fw-bold' : '';
        html += `
            <div class="d-flex justify-content-between align-items-center mb-2">
                <div>
                    <span class="badge me-2" style="background-color: ${colors[i]}; width: 12px; height: 12px; display: inline-block; border-radius: 2px;"></span>
                    ${e.sectorLabel || e.sector}
                </div>
                <span class="${exceedClass}">${(e.percentage || 0).toFixed(1)}%</span>
            </div>
        `;
    });
    $('#sectorList').html(html);
}

// ===== Position Risks =====

function loadPositionRisks() {
    let url = `${API_BASE_URL}/risk/positions`;
    if (currentAccountId) {
        url += `?accountId=${currentAccountId}`;
    }

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            updatePositionRisksTable(data);
        },
        error: function(xhr) {
            console.error('Failed to load position risks:', xhr);
            $('#positionRisksBody').html(`
                <tr><td colspan="8" class="text-center text-muted py-4">데이터를 불러올 수 없습니다.</td></tr>
            `);
        }
    });
}

function updatePositionRisksTable(positions) {
    const tbody = $('#positionRisksBody');

    if (!positions || positions.length === 0) {
        tbody.html(`
            <tr><td colspan="8" class="text-center text-muted py-4">오픈 포지션이 없습니다.</td></tr>
        `);
        return;
    }

    let html = '';
    positions.forEach(pos => {
        const pnlClass = (pos.unrealizedPnl || 0) >= 0 ? 'text-success' : 'text-danger';
        const rClass = (pos.currentR || 0) >= 0 ? 'text-success' : 'text-danger';

        html += `
            <tr>
                <td>
                    <strong>${pos.stockSymbol}</strong>
                    <small class="text-muted d-block">${pos.stockName || ''}</small>
                </td>
                <td class="text-end">${formatNumber(pos.quantity || 0)}</td>
                <td class="text-end">${formatCurrency(pos.entryPrice || 0)}</td>
                <td class="text-end">${formatCurrency(pos.currentPrice || 0)}</td>
                <td class="text-end">${pos.stopLossPrice ? formatCurrency(pos.stopLossPrice) : '-'}</td>
                <td class="text-end ${pnlClass}">
                    ${formatCurrency(pos.unrealizedPnl || 0)}
                    <small class="d-block">(${(pos.unrealizedPnlPercent || 0).toFixed(2)}%)</small>
                </td>
                <td class="text-end text-danger">${pos.riskAmount ? formatCurrency(pos.riskAmount) : '-'}</td>
                <td class="text-end ${rClass}">
                    ${pos.currentR !== null ? (pos.currentR >= 0 ? '+' : '') + pos.currentR.toFixed(2) + 'R' : '-'}
                </td>
            </tr>
        `;
    });

    tbody.html(html);
}

// ===== Settings =====

function loadSettings() {
    let url = `${API_BASE_URL}/risk/settings`;
    if (currentAccountId) {
        url = `${API_BASE_URL}/risk/settings/${currentAccountId}`;
    }

    $.ajax({
        url: url,
        method: 'GET',
        success: function(data) {
            riskSettings = data;
        },
        error: function(xhr) {
            console.error('Failed to load risk settings:', xhr);
        }
    });
}

function populateSettingsForm() {
    if (!riskSettings) {
        return;
    }

    $('#settingCapital').val(riskSettings.accountCapital || '');
    $('#settingMaxRiskPerTrade').val(riskSettings.maxRiskPerTradePercent || 2);
    $('#settingMaxDailyLoss').val(riskSettings.maxDailyLossPercent || 6);
    $('#settingMaxWeeklyLoss').val(riskSettings.maxWeeklyLossPercent || 10);
    $('#settingMaxPositions').val(riskSettings.maxOpenPositions || 10);
    $('#settingMaxPositionSize').val(riskSettings.maxPositionSizePercent || 20);
    $('#settingMaxSectorConc').val(riskSettings.maxSectorConcentrationPercent || 30);
    $('#settingKellyFraction').val(riskSettings.kellyFraction || 0.5);
}

function saveSettings() {
    const settings = {
        accountId: currentAccountId,
        accountCapital: parseFloat($('#settingCapital').val()) || null,
        maxRiskPerTradePercent: parseFloat($('#settingMaxRiskPerTrade').val()) || 2,
        maxDailyLossPercent: parseFloat($('#settingMaxDailyLoss').val()) || 6,
        maxWeeklyLossPercent: parseFloat($('#settingMaxWeeklyLoss').val()) || 10,
        maxOpenPositions: parseInt($('#settingMaxPositions').val()) || 10,
        maxPositionSizePercent: parseFloat($('#settingMaxPositionSize').val()) || 20,
        maxSectorConcentrationPercent: parseFloat($('#settingMaxSectorConc').val()) || 30,
        maxStockConcentrationPercent: parseFloat($('#settingMaxPositionSize').val()) || 20,
        kellyFraction: parseFloat($('#settingKellyFraction').val()) || 0.5
    };

    const url = riskSettings && riskSettings.id
        ? `${API_BASE_URL}/risk/settings/${riskSettings.accountId || 1}`
        : `${API_BASE_URL}/risk/settings`;

    const method = riskSettings && riskSettings.id ? 'PUT' : 'POST';

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        data: JSON.stringify(settings),
        success: function(data) {
            riskSettings = data;
            $('#settingsModal').modal('hide');
            loadDashboard();
            alert('설정이 저장되었습니다.');
        },
        error: function(xhr) {
            alert('설정 저장 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// ===== Charts =====

function initializeCharts() {
    // R-Multiple histogram chart
    const rMultipleCtx = document.getElementById('rMultipleChart');
    if (rMultipleCtx) {
        rMultipleChart = new Chart(rMultipleCtx, {
            type: 'bar',
            data: {
                labels: [],
                datasets: [{
                    label: '거래 수',
                    data: [],
                    backgroundColor: 'rgba(59, 130, 246, 0.7)',
                    borderColor: 'rgba(59, 130, 246, 1)',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    }
                }
            }
        });
    }

    // Sector pie chart
    const sectorCtx = document.getElementById('sectorChart');
    if (sectorCtx) {
        sectorChart = new Chart(sectorCtx, {
            type: 'doughnut',
            data: {
                labels: [],
                datasets: [{
                    data: [],
                    backgroundColor: [],
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                }
            }
        });
    }
}

// ===== Utilities =====

function formatCurrency(value) {
    return '₩' + new Intl.NumberFormat('ko-KR').format(Math.round(value || 0));
}

function formatNumber(value) {
    return new Intl.NumberFormat('ko-KR').format(value || 0);
}

function formatPercent(value) {
    return (value || 0).toFixed(2) + '%';
}

function formatDate(date) {
    return date.getFullYear() + '-' +
        String(date.getMonth() + 1).padStart(2, '0') + '-' +
        String(date.getDate()).padStart(2, '0');
}

function generateColors(count) {
    const baseColors = [
        'rgba(59, 130, 246, 0.8)',   // Blue
        'rgba(16, 185, 129, 0.8)',   // Green
        'rgba(245, 158, 11, 0.8)',   // Yellow
        'rgba(239, 68, 68, 0.8)',    // Red
        'rgba(139, 92, 246, 0.8)',   // Purple
        'rgba(236, 72, 153, 0.8)',   // Pink
        'rgba(20, 184, 166, 0.8)',   // Teal
        'rgba(249, 115, 22, 0.8)',   // Orange
        'rgba(99, 102, 241, 0.8)',   // Indigo
        'rgba(168, 162, 158, 0.8)'   // Gray
    ];

    const colors = [];
    for (let i = 0; i < count; i++) {
        colors.push(baseColors[i % baseColors.length]);
    }
    return colors;
}
