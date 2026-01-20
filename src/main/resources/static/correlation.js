/**
 * 상관관계 분석 페이지 JavaScript
 *
 * @fileoverview 포트폴리오 종목 간 상관관계 분석 기능을 제공합니다.
 * - 상관관계 매트릭스 히트맵 시각화
 * - 섹터별 요약 및 분산투자 추천
 * - 종목 쌍 상세 분석 (롤링 상관관계, 누적 수익률 비교)
 *
 * @requires utils.js - getDateRangeForApi, formatPercent 등 공통 유틸리티
 * @requires Chart.js - 차트 렌더링
 * @requires jQuery - DOM 조작 및 AJAX
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * 상관계수 색상 계산을 위한 RGB 값
 * @constant {Object}
 */
const CORRELATION_COLORS = {
    /** 녹색 (역상관, -1.0) - 분산투자에 유리 */
    NEGATIVE: { r: 34, g: 197, b: 94 },
    /** 노란색 (무상관, 0.0) - 중립 */
    NEUTRAL: { r: 251, g: 191, b: 36 },
    /** 빨간색 (정상관, +1.0) - 집중 위험 */
    POSITIVE: { r: 239, g: 68, b: 68 }
};

/**
 * 상관계수 강도 임계값
 * @constant {Object}
 */
const CORRELATION_THRESHOLDS = {
    /** 강한 상관관계 임계값 */
    STRONG: 0.7,
    /** 중간 상관관계 임계값 */
    MODERATE: 0.4,
    /** 약한 상관관계 임계값 */
    WEAK: 0.2,
    /** 텍스트 색상 전환 임계값 (노란색 근처에서 검정 텍스트 사용) */
    TEXT_THRESHOLD: 0.3
};

/**
 * null 값일 때 사용하는 기본 색상
 * @constant {Object}
 */
const DEFAULT_COLORS = {
    /** 데이터 없음 배경색 */
    NULL_BACKGROUND: '#f8f9fa',
    /** 데이터 없음 텍스트색 */
    NULL_TEXT: '#6c757d'
};

/**
 * 차트 기본 설정
 * @constant {Object}
 */
const CHART_CONFIG = {
    /** 차트 선 색상 */
    ROLLING_LINE_COLOR: '#3b82f6',
    ROLLING_FILL_COLOR: 'rgba(59, 130, 246, 0.1)',
    COMPARISON_COLOR_1: '#22c55e',
    COMPARISON_COLOR_2: '#ef4444',
    /** 선 곡선 정도 */
    LINE_TENSION: 0.4
};

// ============================================================================
// GLOBAL STATE
// ============================================================================

/** @type {Object|null} 상관관계 매트릭스 데이터 */
let correlationData = null;

/** @type {Object|null} 섹터 요약 데이터 */
let sectorData = null;

/** @type {Chart|null} 롤링 상관관계 차트 인스턴스 */
let rollingChart = null;

/** @type {Chart|null} 누적 수익률 비교 차트 인스턴스 */
let comparisonChart = null;

/** @type {{symbol1: string|null, symbol2: string|null}} 선택된 종목 쌍 */
let selectedPair = { symbol1: null, symbol2: null };

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * 페이지 로드 시 초기화
 */
$(document).ready(function() {
    initCharts();
    loadAllData();

    // 기간 선택 변경 이벤트
    $('#period-selector').on('change', function() {
        loadAllData();
    });
});

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * 모든 데이터 로드
 * utils.js의 getDateRangeForApi를 사용하여 기간 계산
 */
function loadAllData() {
    const period = $('#period-selector').val();
    const { startDate, endDate } = getDateRangeForApi(period);

    loadCorrelationMatrix(startDate, endDate);
    loadSectorSummary(startDate, endDate);
}

/**
 * 상관관계 매트릭스 로드
 * @param {string} startDate - 시작일 (YYYY-MM-DD)
 * @param {string} endDate - 종료일 (YYYY-MM-DD)
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
 * @param {string} startDate - 시작일 (YYYY-MM-DD)
 * @param {string} endDate - 종료일 (YYYY-MM-DD)
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

// ============================================================================
// CORRELATION COLOR HELPERS
// ============================================================================

/**
 * 상관계수에 따른 배경색 반환
 * -1.0: 녹색 (역상관) -> 0: 노란색 (무상관) -> +1.0: 빨간색 (정상관)
 *
 * @param {number|null} value - 상관계수 (-1 ~ 1)
 * @returns {string} RGB 색상 문자열 (e.g., 'rgb(251, 191, 36)')
 */
function getCorrelationColor(value) {
    if (value == null) return DEFAULT_COLORS.NULL_BACKGROUND;

    // -1 ~ 1 범위를 0 ~ 1로 정규화
    const normalized = (value + 1) / 2;

    let r, g, b;
    const { NEGATIVE, NEUTRAL, POSITIVE } = CORRELATION_COLORS;

    if (normalized < 0.5) {
        // 녹색 -> 노란색 (역상관 -> 무상관)
        const t = normalized * 2;
        r = Math.round(NEGATIVE.r + (NEUTRAL.r - NEGATIVE.r) * t);
        g = Math.round(NEGATIVE.g + (NEUTRAL.g - NEGATIVE.g) * t);
        b = Math.round(NEGATIVE.b + (NEUTRAL.b - NEGATIVE.b) * t);
    } else {
        // 노란색 -> 빨간색 (무상관 -> 정상관)
        const t = (normalized - 0.5) * 2;
        r = Math.round(NEUTRAL.r + (POSITIVE.r - NEUTRAL.r) * t);
        g = Math.round(NEUTRAL.g + (POSITIVE.g - NEUTRAL.g) * t);
        b = Math.round(NEUTRAL.b + (POSITIVE.b - NEUTRAL.b) * t);
    }

    return `rgb(${r}, ${g}, ${b})`;
}

/**
 * 상관계수에 따른 텍스트 색상 반환
 * 노란색 계열(중앙)에서는 검정색, 양 끝(녹색/빨간색)에서는 흰색
 *
 * @param {number|null} value - 상관계수 (-1 ~ 1)
 * @returns {string} 텍스트 색상 ('#000' 또는 '#fff')
 */
function getTextColor(value) {
    if (value == null) return DEFAULT_COLORS.NULL_TEXT;

    const threshold = CORRELATION_THRESHOLDS.TEXT_THRESHOLD;
    // 대비를 위해 노란색 계열에서만 검정색 사용
    if (value > -threshold && value < threshold) return '#000';
    return '#fff';
}

/**
 * 상관계수 강도에 따른 레이블 반환
 *
 * @param {number|null} value - 상관계수 (-1 ~ 1)
 * @returns {string} 상관관계 강도 레이블
 */
function getCorrelationLabel(value) {
    if (value == null) return '데이터 없음';

    const absValue = Math.abs(value);
    const { STRONG, MODERATE, WEAK } = CORRELATION_THRESHOLDS;

    let strength;
    if (absValue >= STRONG) {
        strength = '강한';
    } else if (absValue >= MODERATE) {
        strength = '중간';
    } else if (absValue >= WEAK) {
        strength = '약한';
    } else {
        return '무상관';
    }

    const direction = value > 0 ? '정상관' : '역상관';
    return `${strength} ${direction}`;
}

// ============================================================================
// SUMMARY STATS
// ============================================================================

/**
 * 요약 통계 업데이트
 * @param {Object} data - 상관관계 데이터
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

// ============================================================================
// HEATMAP RENDERING
// ============================================================================

/**
 * 히트맵 렌더링
 * @param {Object} data - 상관관계 매트릭스 데이터
 * @param {string[]} data.symbols - 종목 심볼 배열
 * @param {string[]} [data.names] - 종목명 배열
 * @param {number[][]} data.matrix - 상관계수 매트릭스
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

// ============================================================================
// SECTOR & RECOMMENDATIONS RENDERING
// ============================================================================

/**
 * 섹터별 요약 렌더링
 * @param {Object} data - 섹터 요약 데이터
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
 * @param {Object} data - 추천 데이터
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

// ============================================================================
// PAIR ANALYSIS
// ============================================================================

/**
 * 종목 쌍 선택
 * @param {string} symbol1 - 첫 번째 종목 심볼
 * @param {string} symbol2 - 두 번째 종목 심볼
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
    const { startDate, endDate } = getDateRangeForApi(period);

    loadPairAnalysis(symbol1, symbol2, startDate, endDate);
    loadRollingCorrelation(symbol1, symbol2, startDate, endDate);
}

/**
 * 종목명 조회
 * @param {string} symbol - 종목 심볼
 * @returns {string} 종목명 (없으면 심볼 반환)
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
 * @param {string} symbol1 - 첫 번째 종목 심볼
 * @param {string} symbol2 - 두 번째 종목 심볼
 * @param {string} startDate - 시작일 (YYYY-MM-DD)
 * @param {string} endDate - 종료일 (YYYY-MM-DD)
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
 * @param {string} symbol1 - 첫 번째 종목 심볼
 * @param {string} symbol2 - 두 번째 종목 심볼
 * @param {string} startDate - 시작일 (YYYY-MM-DD)
 * @param {string} endDate - 종료일 (YYYY-MM-DD)
 * @param {number} [windowDays=30] - 롤링 윈도우 일수
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
 * @param {Object} data - 종목 쌍 분석 데이터
 */
function updatePairSummary(data) {
    // 상관계수
    const correlation = data.correlation != null ? data.correlation.toFixed(3) : '-';
    $('#pair-correlation').text(correlation);

    // 분산투자 효과
    const benefit = data.diversificationBenefit != null ?
        (data.diversificationBenefit > 0 ? '+' : '') + data.diversificationBenefit.toFixed(1) + '%' : '-';
    $('#pair-benefit').text(benefit);

    // 종목별 평균 수익 - formatPercent 사용
    const avgReturn1 = data.avgReturn1 != null ? formatPercent(data.avgReturn1) : '-';
    const avgReturn2 = data.avgReturn2 != null ? formatPercent(data.avgReturn2) : '-';

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
 * @param {Object} data - 롤링 상관관계 데이터
 */
function updateRollingStats(data) {
    $('#rolling-avg').text(data.averageCorrelation != null ? data.averageCorrelation.toFixed(3) : '-');
    $('#rolling-max').text(data.maxCorrelation != null ? data.maxCorrelation.toFixed(3) : '-');
    $('#rolling-min').text(data.minCorrelation != null ? data.minCorrelation.toFixed(3) : '-');
    $('#rolling-vol').text(data.correlationVolatility != null ? data.correlationVolatility.toFixed(3) : '-');
}

/**
 * 종목 쌍 분석 닫기
 */
function closePairAnalysis() {
    $('#pair-analysis-card').removeClass('active');
    selectedPair = { symbol1: null, symbol2: null };
}

// ============================================================================
// CHART INITIALIZATION & UPDATES
// ============================================================================

/**
 * 롤링 상관관계 차트 기본 옵션 생성
 * @returns {Object} Chart.js 옵션 객체
 */
function createRollingChartOptions() {
    return {
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
    };
}

/**
 * 누적 수익률 비교 차트 기본 옵션 생성
 * @returns {Object} Chart.js 옵션 객체
 */
function createComparisonChartOptions() {
    return {
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
    };
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
                borderColor: CHART_CONFIG.ROLLING_LINE_COLOR,
                backgroundColor: CHART_CONFIG.ROLLING_FILL_COLOR,
                fill: true,
                tension: CHART_CONFIG.LINE_TENSION
            }]
        },
        options: createRollingChartOptions()
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
                    borderColor: CHART_CONFIG.COMPARISON_COLOR_1,
                    backgroundColor: 'transparent',
                    tension: CHART_CONFIG.LINE_TENSION
                },
                {
                    label: '종목2',
                    data: [],
                    borderColor: CHART_CONFIG.COMPARISON_COLOR_2,
                    backgroundColor: 'transparent',
                    tension: CHART_CONFIG.LINE_TENSION
                }
            ]
        },
        options: createComparisonChartOptions()
    });
}

/**
 * 롤링 상관관계 차트 업데이트
 * @param {Object} data - 롤링 상관관계 데이터
 * @param {string[]} data.dates - 날짜 배열
 * @param {number[]} data.correlations - 상관계수 배열
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
 * @param {Object} data - 종목 쌍 분석 데이터
 * @param {string[]} data.dates - 날짜 배열
 * @param {number[]} data.cumulativeReturns1 - 종목1 누적 수익률
 * @param {number[]} data.cumulativeReturns2 - 종목2 누적 수익률
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

// ============================================================================
// AUTHENTICATION
// ============================================================================

/**
 * 로그아웃
 */
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('username');
    window.location.href = 'login.html';
}
