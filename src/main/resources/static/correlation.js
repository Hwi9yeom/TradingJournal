/**
 * 상관관계 분석 페이지 JavaScript
 */

// 전역 변수
let correlationData = null;
let sectorData = null;
let rollingChart = null;
let comparisonChart = null;
let selectedPair = { symbol1: null, symbol2: null };

// 페이지 로드 시 초기화
$(document).ready(function() {
    initCharts();
    loadAllData();

    // 기간 선택 변경 이벤트
    $('#period-selector').on('change', function() {
        loadAllData();
    });
});

/**
 * 모든 데이터 로드
 */
function loadAllData() {
    const period = $('#period-selector').val();
    const { startDate, endDate } = getPeriodDates(period);

    loadCorrelationMatrix(startDate, endDate);
    loadSectorSummary(startDate, endDate);
}

/**
 * 기간에 따른 날짜 계산
 */
function getPeriodDates(period) {
    const endDate = new Date();
    let startDate = new Date();

    switch(period) {
        case '3M':
            startDate.setMonth(startDate.getMonth() - 3);
            break;
        case '6M':
            startDate.setMonth(startDate.getMonth() - 6);
            break;
        case '1Y':
            startDate.setFullYear(startDate.getFullYear() - 1);
            break;
        case 'ALL':
            startDate = new Date('2020-01-01');
            break;
        default:
            startDate.setMonth(startDate.getMonth() - 6);
    }

    return {
        startDate: formatDate(startDate),
        endDate: formatDate(endDate)
    };
}

/**
 * 날짜 포맷팅
 */
function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * 상관관계 매트릭스 로드
 */
function loadCorrelationMatrix(startDate, endDate) {
    $.ajax({
        url: `/api/analysis/correlation?startDate=${startDate}&endDate=${endDate}`,
        method: 'GET',
        success: function(data) {
            correlationData = data;
            renderHeatmap(data);
            updateSummaryStats(data);
        },
        error: function(xhr, status, error) {
            console.error('상관관계 데이터 로드 실패:', error);
            showHeatmapEmpty();
        }
    });
}

/**
 * 섹터별 요약 로드
 */
function loadSectorSummary(startDate, endDate) {
    $.ajax({
        url: `/api/analysis/correlation/sector-summary?startDate=${startDate}&endDate=${endDate}`,
        method: 'GET',
        success: function(data) {
            sectorData = data;
            renderSectorSummary(data);
            renderRecommendations(data);
        },
        error: function(xhr, status, error) {
            console.error('섹터 데이터 로드 실패:', error);
            $('#sector-summary').html('<div class="text-muted text-center py-3">데이터를 불러올 수 없습니다.</div>');
            $('#recommendations').html('<div class="text-muted text-center py-3">데이터를 불러올 수 없습니다.</div>');
        }
    });
}

/**
 * 요약 통계 업데이트
 */
function updateSummaryStats(data) {
    if (!data || !data.symbols || data.symbols.length === 0) {
        $('#avg-correlation').text('-');
        $('#diversification-score').text('-');
        $('#stock-count').text('0');
        return;
    }

    const avgCorr = data.averageCorrelation != null ? data.averageCorrelation.toFixed(2) : '-';
    const divScore = data.diversificationScore != null ? Math.round(data.diversificationScore) + '점' : '-';

    $('#avg-correlation').text(avgCorr);
    $('#diversification-score').text(divScore);
    $('#stock-count').text(data.symbols.length);
}

/**
 * 히트맵 렌더링
 */
function renderHeatmap(data) {
    if (!data || !data.symbols || data.symbols.length === 0) {
        showHeatmapEmpty();
        return;
    }

    $('#heatmap-empty').hide();
    $('#heatmap-table').show();

    const symbols = data.symbols;
    const names = data.names || symbols;
    const matrix = data.matrix;

    // 헤더 생성
    let headerHtml = '<tr><th></th>';
    for (let i = 0; i < symbols.length; i++) {
        headerHtml += `<th class="correlation-header" title="${names[i]}">${symbols[i]}</th>`;
    }
    headerHtml += '</tr>';
    $('#heatmap-header').html(headerHtml);

    // 바디 생성
    let bodyHtml = '';
    for (let i = 0; i < symbols.length; i++) {
        bodyHtml += `<tr><th class="correlation-header" title="${names[i]}">${symbols[i]}</th>`;
        for (let j = 0; j < symbols.length; j++) {
            const value = matrix[i][j];
            const color = getCorrelationColor(value);
            const textColor = getTextColor(value);
            const displayValue = value != null ? value.toFixed(2) : '-';

            if (i === j) {
                // 대각선은 클릭 불가
                bodyHtml += `<td class="correlation-cell" style="background-color: ${color}; color: ${textColor};">${displayValue}</td>`;
            } else {
                bodyHtml += `<td class="correlation-cell"
                    style="background-color: ${color}; color: ${textColor};"
                    onclick="selectPair('${symbols[i]}', '${symbols[j]}')"
                    title="${names[i]} vs ${names[j]}: ${displayValue}">${displayValue}</td>`;
            }
        }
        bodyHtml += '</tr>';
    }
    $('#heatmap-body').html(bodyHtml);
}

/**
 * 빈 히트맵 표시
 */
function showHeatmapEmpty() {
    $('#heatmap-table').hide();
    $('#heatmap-empty').show();
}

/**
 * 상관계수에 따른 배경색 반환
 * -1.0: 녹색 (역상관) -> 0: 노란색 (무상관) -> +1.0: 빨간색 (정상관)
 */
function getCorrelationColor(value) {
    if (value == null) return '#f8f9fa';

    // -1 ~ 1 범위를 0 ~ 1로 정규화
    const normalized = (value + 1) / 2;

    let r, g, b;

    if (normalized < 0.5) {
        // 녹색 -> 노란색
        const t = normalized * 2;
        r = Math.round(34 + (251 - 34) * t);
        g = Math.round(197 + (191 - 197) * t);
        b = Math.round(94 + (36 - 94) * t);
    } else {
        // 노란색 -> 빨간색
        const t = (normalized - 0.5) * 2;
        r = Math.round(251 + (239 - 251) * t);
        g = Math.round(191 + (68 - 191) * t);
        b = Math.round(36 + (68 - 36) * t);
    }

    return `rgb(${r}, ${g}, ${b})`;
}

/**
 * 배경색에 따른 텍스트 색상 반환
 */
function getTextColor(value) {
    if (value == null) return '#6c757d';
    // 대비를 위해 대부분 흰색 사용, 노란색 계열에서만 검정색
    if (value > -0.3 && value < 0.3) return '#000';
    return '#fff';
}

/**
 * 섹터별 요약 렌더링
 */
function renderSectorSummary(data) {
    if (!data || !data.sectorSummaries || data.sectorSummaries.length === 0) {
        $('#sector-summary').html('<div class="text-muted text-center py-3">섹터 데이터가 없습니다.</div>');
        return;
    }

    let html = '<div class="list-group list-group-flush">';

    data.sectorSummaries.forEach(sector => {
        const correlation = sector.internalCorrelation != null ? sector.internalCorrelation.toFixed(2) : '-';
        const corrColor = getCorrelationColor(sector.internalCorrelation);

        html += `
            <div class="list-group-item d-flex justify-content-between align-items-center">
                <div>
                    <strong>${sector.sectorLabel || sector.sector}</strong>
                    <small class="text-muted d-block">${sector.stockCount}개 종목</small>
                </div>
                <div class="text-end">
                    <span class="badge" style="background-color: ${corrColor}; color: ${getTextColor(sector.internalCorrelation)};">
                        ${correlation}
                    </span>
                    <small class="text-muted d-block">내부 상관계수</small>
                </div>
            </div>
        `;
    });

    html += '</div>';
    $('#sector-summary').html(html);
}

/**
 * 분산투자 추천 렌더링
 */
function renderRecommendations(data) {
    if (!data || !data.diversificationRecommendations || data.diversificationRecommendations.length === 0) {
        $('#recommendations').html('<div class="text-muted text-center py-3">추천 데이터가 없습니다.</div>');
        return;
    }

    let html = '<div class="list-group list-group-flush">';

    data.diversificationRecommendations.slice(0, 5).forEach(rec => {
        const correlation = rec.correlation != null ? rec.correlation.toFixed(2) : '-';
        const corrColor = getCorrelationColor(rec.correlation);

        html += `
            <div class="list-group-item recommendation-card">
                <div class="d-flex justify-content-between align-items-center">
                    <div>
                        <strong>${rec.sector1}</strong> + <strong>${rec.sector2}</strong>
                    </div>
                    <span class="badge" style="background-color: ${corrColor}; color: ${getTextColor(rec.correlation)};">
                        ${correlation}
                    </span>
                </div>
                <small class="text-muted">${rec.recommendation || '분산투자에 적합한 조합입니다.'}</small>
            </div>
        `;
    });

    html += '</div>';
    $('#recommendations').html(html);
}

/**
 * 종목 쌍 선택
 */
function selectPair(symbol1, symbol2) {
    // 같은 종목이면 무시
    if (symbol1 === symbol2) return;

    // 알파벳 순서로 정렬 (일관성)
    if (symbol1 > symbol2) {
        [symbol1, symbol2] = [symbol2, symbol1];
    }

    selectedPair = { symbol1, symbol2 };

    // 분석 카드 표시
    $('#pair-analysis-card').addClass('active');

    // 제목 업데이트
    const name1 = getStockName(symbol1);
    const name2 = getStockName(symbol2);
    $('#pair-title').text(`${symbol1} (${name1}) vs ${symbol2} (${name2})`);

    // 데이터 로드
    const period = $('#period-selector').val();
    const { startDate, endDate } = getPeriodDates(period);

    loadPairAnalysis(symbol1, symbol2, startDate, endDate);
    loadRollingCorrelation(symbol1, symbol2, startDate, endDate);
}

/**
 * 종목명 조회
 */
function getStockName(symbol) {
    if (correlationData && correlationData.symbols && correlationData.names) {
        const idx = correlationData.symbols.indexOf(symbol);
        if (idx >= 0 && correlationData.names[idx]) {
            return correlationData.names[idx];
        }
    }
    return symbol;
}

/**
 * 종목 쌍 상세 분석 로드
 */
function loadPairAnalysis(symbol1, symbol2, startDate, endDate) {
    $.ajax({
        url: `/api/analysis/correlation/pair?symbol1=${symbol1}&symbol2=${symbol2}&startDate=${startDate}&endDate=${endDate}`,
        method: 'GET',
        success: function(data) {
            updatePairSummary(data);
            updateComparisonChart(data);
        },
        error: function(xhr, status, error) {
            console.error('종목 쌍 분석 로드 실패:', error);
        }
    });
}

/**
 * 롤링 상관관계 로드
 */
function loadRollingCorrelation(symbol1, symbol2, startDate, endDate, windowDays = 30) {
    $.ajax({
        url: `/api/analysis/correlation/rolling?symbol1=${symbol1}&symbol2=${symbol2}&startDate=${startDate}&endDate=${endDate}&windowDays=${windowDays}`,
        method: 'GET',
        success: function(data) {
            updateRollingChart(data);
            updateRollingStats(data);
        },
        error: function(xhr, status, error) {
            console.error('롤링 상관관계 로드 실패:', error);
        }
    });
}

/**
 * 종목 쌍 요약 업데이트
 */
function updatePairSummary(data) {
    // 상관계수
    const correlation = data.correlation != null ? data.correlation.toFixed(3) : '-';
    $('#pair-correlation').text(correlation);

    // 분산투자 효과
    const benefit = data.diversificationBenefit != null ?
        (data.diversificationBenefit > 0 ? '+' : '') + data.diversificationBenefit.toFixed(1) + '%' : '-';
    $('#pair-benefit').text(benefit);

    // 종목별 평균 수익
    const avgReturn1 = data.avgReturn1 != null ?
        (data.avgReturn1 > 0 ? '+' : '') + data.avgReturn1.toFixed(2) + '%' : '-';
    const avgReturn2 = data.avgReturn2 != null ?
        (data.avgReturn2 > 0 ? '+' : '') + data.avgReturn2.toFixed(2) + '%' : '-';

    $('#pair-avg-return1').text(avgReturn1);
    $('#pair-avg-return2').text(avgReturn2);
    $('#pair-label1').text(`${data.symbol1 || '종목1'} 평균수익`);
    $('#pair-label2').text(`${data.symbol2 || '종목2'} 평균수익`);

    // 변동성
    const vol1 = data.volatility1 != null ? data.volatility1.toFixed(2) + '%' : '-';
    const vol2 = data.volatility2 != null ? data.volatility2.toFixed(2) + '%' : '-';
    $('#pair-vol1').text(vol1);
    $('#pair-vol2').text(vol2);
}

/**
 * 롤링 상관관계 통계 업데이트
 */
function updateRollingStats(data) {
    $('#rolling-avg').text(data.averageCorrelation != null ? data.averageCorrelation.toFixed(3) : '-');
    $('#rolling-max').text(data.maxCorrelation != null ? data.maxCorrelation.toFixed(3) : '-');
    $('#rolling-min').text(data.minCorrelation != null ? data.minCorrelation.toFixed(3) : '-');
    $('#rolling-vol').text(data.correlationVolatility != null ? data.correlationVolatility.toFixed(3) : '-');
}

/**
 * 차트 초기화
 */
function initCharts() {
    // 롤링 상관관계 차트
    const rollingCtx = document.getElementById('rolling-chart').getContext('2d');
    rollingChart = new Chart(rollingCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: '롤링 상관계수',
                data: [],
                borderColor: '#3b82f6',
                backgroundColor: 'rgba(59, 130, 246, 0.1)',
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'month',
                        displayFormats: { month: 'yyyy-MM' }
                    }
                },
                y: {
                    min: -1,
                    max: 1,
                    ticks: {
                        callback: function(value) {
                            return value.toFixed(1);
                        }
                    }
                }
            }
        }
    });

    // 누적 수익률 비교 차트
    const comparisonCtx = document.getElementById('comparison-chart').getContext('2d');
    comparisonChart = new Chart(comparisonCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: '종목1',
                    data: [],
                    borderColor: '#22c55e',
                    backgroundColor: 'transparent',
                    tension: 0.4
                },
                {
                    label: '종목2',
                    data: [],
                    borderColor: '#ef4444',
                    backgroundColor: 'transparent',
                    tension: 0.4
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'top' }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'month',
                        displayFormats: { month: 'yyyy-MM' }
                    }
                },
                y: {
                    ticks: {
                        callback: function(value) {
                            return value.toFixed(1) + '%';
                        }
                    }
                }
            }
        }
    });
}

/**
 * 롤링 상관관계 차트 업데이트
 */
function updateRollingChart(data) {
    if (!data || !data.dates || data.dates.length === 0) {
        rollingChart.data.labels = [];
        rollingChart.data.datasets[0].data = [];
        rollingChart.update();
        return;
    }

    const chartData = data.dates.map((date, i) => ({
        x: new Date(date),
        y: data.correlations[i]
    }));

    rollingChart.data.datasets[0].data = chartData;
    rollingChart.update();
}

/**
 * 누적 수익률 비교 차트 업데이트
 */
function updateComparisonChart(data) {
    if (!data || !data.dates || data.dates.length === 0) {
        comparisonChart.data.datasets[0].data = [];
        comparisonChart.data.datasets[1].data = [];
        comparisonChart.update();
        return;
    }

    const chartData1 = data.dates.map((date, i) => ({
        x: new Date(date),
        y: data.cumulativeReturns1 ? data.cumulativeReturns1[i] : 0
    }));

    const chartData2 = data.dates.map((date, i) => ({
        x: new Date(date),
        y: data.cumulativeReturns2 ? data.cumulativeReturns2[i] : 0
    }));

    comparisonChart.data.datasets[0].label = data.symbol1 || '종목1';
    comparisonChart.data.datasets[0].data = chartData1;
    comparisonChart.data.datasets[1].label = data.symbol2 || '종목2';
    comparisonChart.data.datasets[1].data = chartData2;
    comparisonChart.update();
}

/**
 * 종목 쌍 분석 닫기
 */
function closePairAnalysis() {
    $('#pair-analysis-card').removeClass('active');
    selectedPair = { symbol1: null, symbol2: null };
}

/**
 * 로그아웃
 */
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('username');
    window.location.href = 'login.html';
}
