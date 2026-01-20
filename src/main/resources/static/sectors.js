/**
 * Trading Journal - Sector Analysis Module
 * Provides sector allocation, performance, and rotation analysis
 *
 * @fileoverview Handles sector analysis functionality including:
 * - Sector allocation pie chart
 * - Sector performance bar chart
 * - Sector rotation stacked bar chart
 * - Sector details table
 * - Stock grid by sector
 *
 * @requires utils.js - For formatCurrency, formatPercent, getDateRangeForApi
 * @requires Chart.js - For chart rendering
 * @requires jQuery - For DOM manipulation and AJAX
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Base URL for API endpoints
 * @constant {string}
 */
const API_BASE_URL = '/api';

/**
 * Default chart colors for sector visualization
 * @constant {string[]}
 */
const SECTOR_CHART_COLORS = [
    '#3b82f6', // blue
    '#22c55e', // green
    '#f59e0b', // amber
    '#ef4444', // red
    '#8b5cf6', // violet
    '#ec4899', // pink
    '#14b8a6', // teal
    '#f97316', // orange
    '#6366f1', // indigo
    '#84cc16', // lime
    '#06b6d4', // cyan
    '#a855f7'  // purple
];

/**
 * Mapping of sector codes to display colors
 * @constant {Object<string, string>}
 */
const SECTOR_COLOR_MAP = {
    'TECH': '#3b82f6',
    'HEALTH': '#22c55e',
    'FINANCE': '#f59e0b',
    'CONSUMER_DISC': '#ef4444',
    'CONSUMER_STAP': '#8b5cf6',
    'INDUSTRIAL': '#ec4899',
    'ENERGY': '#14b8a6',
    'MATERIALS': '#f97316',
    'UTILITIES': '#6366f1',
    'REAL_ESTATE': '#84cc16',
    'COMMUNICATION': '#06b6d4',
    'OTHER': '#9ca3af'
};

/**
 * Mapping of sector codes to Korean labels
 * @constant {Object<string, string>}
 */
const SECTOR_LABEL_MAP = {
    'TECH': '정보기술',
    'HEALTH': '헬스케어',
    'FINANCE': '금융',
    'CONSUMER_DISC': '경기소비재',
    'CONSUMER_STAP': '필수소비재',
    'INDUSTRIAL': '산업재',
    'ENERGY': '에너지',
    'MATERIALS': '소재',
    'UTILITIES': '유틸리티',
    'REAL_ESTATE': '부동산',
    'COMMUNICATION': '통신',
    'OTHER': '기타'
};

/**
 * CSS colors for positive/negative performance indicators
 * @constant {Object}
 */
const PERFORMANCE_COLORS = {
    POSITIVE: 'rgba(34, 197, 94, 0.8)',
    NEGATIVE: 'rgba(239, 68, 68, 0.8)'
};

/**
 * Default fallback color for unknown sectors
 * @constant {string}
 */
const DEFAULT_SECTOR_COLOR = '#9ca3af';

/**
 * Empty state chart color
 * @constant {string}
 */
const EMPTY_STATE_COLOR = '#e5e7eb';

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Application state object containing chart instances and data
 * @type {Object}
 */
const sectorState = {
    /** @type {Chart|null} Sector allocation doughnut chart instance */
    allocationChart: null,
    /** @type {Chart|null} Sector performance bar chart instance */
    performanceChart: null,
    /** @type {Chart|null} Sector rotation stacked bar chart instance */
    rotationChart: null,
    /** @type {string} Current selected period for analysis */
    currentPeriod: '1Y',
    /** @type {Array} List of all available sectors */
    allSectors: []
};

// ============================================================================
// CHART CONFIGURATION
// ============================================================================

/**
 * Common chart options for responsive behavior
 * @constant {Object}
 */
const COMMON_CHART_OPTIONS = {
    responsive: true,
    maintainAspectRatio: false
};

/**
 * Get configuration for the sector allocation doughnut chart
 * @returns {Object} Chart.js configuration object
 */
function getAllocationChartConfig() {
    return {
        type: 'doughnut',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: getSectorColors(12),
                borderWidth: 2,
                borderColor: '#fff'
            }]
        },
        options: {
            ...COMMON_CHART_OPTIONS,
            plugins: {
                legend: {
                    position: 'right',
                    labels: {
                        boxWidth: 12,
                        padding: 10
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return context.label + ': ' + context.parsed.toFixed(1) + '%';
                        }
                    }
                }
            }
        }
    };
}

/**
 * Get configuration for the sector performance bar chart
 * @returns {Object} Chart.js configuration object
 */
function getPerformanceChartConfig() {
    return {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: '총손익',
                data: [],
                backgroundColor: [],
                borderWidth: 0
            }]
        },
        options: {
            ...COMMON_CHART_OPTIONS,
            indexAxis: 'y',
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return '손익: ' + formatCurrency(context.parsed.x);
                        }
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        callback: function(value) {
                            return formatCurrencyShort(value);
                        }
                    }
                }
            }
        }
    };
}

/**
 * Get configuration for the sector rotation stacked bar chart
 * @returns {Object} Chart.js configuration object
 */
function getRotationChartConfig() {
    return {
        type: 'bar',
        data: {
            labels: [],
            datasets: []
        },
        options: {
            ...COMMON_CHART_OPTIONS,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        boxWidth: 12
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return context.dataset.label + ': ' + context.parsed.y.toFixed(1) + '%';
                        }
                    }
                }
            },
            scales: {
                x: {
                    stacked: true
                },
                y: {
                    stacked: true,
                    max: 100,
                    ticks: {
                        callback: function(value) {
                            return value + '%';
                        }
                    }
                }
            }
        }
    };
}

// ============================================================================
// INITIALIZATION
// ============================================================================

$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    loadSectorsList();
    initializeCharts();
    loadSectorAnalysis(sectorState.currentPeriod);

    // 종목 검색 필터
    $('#stock-search').on('input', function() {
        const query = $(this).val().toLowerCase();
        $('#stock-sector-list tr').each(function() {
            const text = $(this).text().toLowerCase();
            $(this).toggle(text.includes(query));
        });
    });
});

/**
 * Load the list of available sectors from the API
 */
function loadSectorsList() {
    $.ajax({
        url: `${API_BASE_URL}/analysis/sectors/list`,
        method: 'GET',
        success: function(data) {
            sectorState.allSectors = data;
        }
    });
}

/**
 * Initialize all chart instances with their configurations
 */
function initializeCharts() {
    // 섹터 배분 파이 차트
    const allocationCtx = document.getElementById('sectorAllocationChart').getContext('2d');
    sectorState.allocationChart = new Chart(allocationCtx, getAllocationChartConfig());

    // 섹터 성과 바 차트
    const performanceCtx = document.getElementById('sectorPerformanceChart').getContext('2d');
    sectorState.performanceChart = new Chart(performanceCtx, getPerformanceChartConfig());

    // 섹터 로테이션 스택 바 차트
    const rotationCtx = document.getElementById('sectorRotationChart').getContext('2d');
    sectorState.rotationChart = new Chart(rotationCtx, getRotationChartConfig());
}

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load sector analysis data for the specified period
 * @param {string} period - The period identifier ('3M', '6M', '1Y', 'ALL')
 */
function loadSectorAnalysis(period) {
    sectorState.currentPeriod = period;
    const range = getDateRangeForApi(period);

    $.ajax({
        url: `${API_BASE_URL}/analysis/sectors`,
        method: 'GET',
        data: {
            startDate: range.startDate,
            endDate: range.endDate
        },
        success: function(data) {
            updateSectorDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load sector analysis:', xhr);
            showEmptySectorState();
        }
    });
}

// ============================================================================
// DISPLAY UPDATES
// ============================================================================

/**
 * Update all sector display components with new data
 * @param {Object} data - The sector analysis data from API
 */
function updateSectorDisplay(data) {
    // 요약 지표 업데이트
    $('#diversification-rating')
        .text(data.diversificationRating || '-')
        .removeClass('text-success text-warning text-danger')
        .addClass(getDiversificationClass(data.diversificationRating));

    $('#hhi-value').text(data.sectorConcentrationIndex || '-');

    // 최고/최저 성과 섹터
    const topPerf = data.sectorPerformance && data.sectorPerformance.length > 0 ? data.sectorPerformance[0] : null;
    const worstPerf = data.sectorPerformance && data.sectorPerformance.length > 0 ?
        data.sectorPerformance[data.sectorPerformance.length - 1] : null;

    if (topPerf) {
        $('#top-sector').text(topPerf.sectorLabel);
        $('#top-sector-return').text(formatCurrency(topPerf.totalReturn));
    }

    if (worstPerf && worstPerf !== topPerf) {
        $('#worst-sector').text(worstPerf.sectorLabel);
        $('#worst-sector-return').text(formatCurrency(worstPerf.totalReturn));
    }

    // 통계 배지
    $('#total-stocks').text('종목: ' + (data.totalStocks || 0));
    $('#classified-stocks').text('분류: ' + (data.classifiedStocks || 0));
    $('#unclassified-stocks').text('미분류: ' + (data.unclassifiedStocks || 0));

    // 차트 업데이트
    updateAllocationChart(data.currentAllocation || []);
    updatePerformanceChart(data.sectorPerformance || []);
    updateRotationChart(data.rotationHistory || []);

    // 상세 테이블
    updateDetailsTable(data.sectorPerformance || [], data.currentAllocation || []);

    // 종목 그리드
    updateStocksGrid(data.currentAllocation || []);
}

/**
 * Update the sector allocation doughnut chart
 * @param {Array} allocations - Array of allocation data objects
 */
function updateAllocationChart(allocations) {
    const chart = sectorState.allocationChart;

    if (!allocations || allocations.length === 0) {
        chart.data.labels = ['데이터 없음'];
        chart.data.datasets[0].data = [100];
        chart.data.datasets[0].backgroundColor = [EMPTY_STATE_COLOR];
    } else {
        chart.data.labels = allocations.map(a => a.sectorLabel);
        chart.data.datasets[0].data = allocations.map(a => parseFloat(a.weight));
        chart.data.datasets[0].backgroundColor = getSectorColors(allocations.length);
    }
    chart.update();
}

/**
 * Update the sector performance bar chart
 * @param {Array} performances - Array of performance data objects
 */
function updatePerformanceChart(performances) {
    const chart = sectorState.performanceChart;

    if (!performances || performances.length === 0) {
        chart.data.labels = [];
        chart.data.datasets[0].data = [];
    } else {
        chart.data.labels = performances.map(p => p.sectorLabel);
        chart.data.datasets[0].data = performances.map(p => parseFloat(p.totalReturn));
        chart.data.datasets[0].backgroundColor = performances.map(p =>
            parseFloat(p.totalReturn) >= 0 ? PERFORMANCE_COLORS.POSITIVE : PERFORMANCE_COLORS.NEGATIVE);
    }
    chart.update();
}

/**
 * Update the sector rotation stacked bar chart
 * @param {Array} rotations - Array of rotation history data objects
 */
function updateRotationChart(rotations) {
    const chart = sectorState.rotationChart;

    if (!rotations || rotations.length === 0) {
        chart.data.labels = [];
        chart.data.datasets = [];
        chart.update();
        return;
    }

    // 모든 섹터 수집
    const allSectorTypes = new Set();
    rotations.forEach(r => {
        if (r.sectorWeights) {
            Object.keys(r.sectorWeights).forEach(s => allSectorTypes.add(s));
        }
    });

    const sectorLabels = Array.from(allSectorTypes);
    const colors = getSectorColors(sectorLabels.length);

    // 데이터셋 생성
    const datasets = sectorLabels.map((sector, idx) => ({
        label: getSectorLabel(sector),
        data: rotations.map(r => r.sectorWeights ? parseFloat(r.sectorWeights[sector] || 0) : 0),
        backgroundColor: colors[idx],
        borderWidth: 0
    }));

    chart.data.labels = rotations.map(r => r.period);
    chart.data.datasets = datasets;
    chart.update();
}

/**
 * Update the sector details table with performance and allocation data
 * @param {Array} performances - Array of performance data objects
 * @param {Array} allocations - Array of allocation data objects
 */
function updateDetailsTable(performances, allocations) {
    const $tbody = $('#sector-details-table');

    if (!performances || performances.length === 0) {
        $tbody.html(`
            <tr>
                <td colspan="8" class="text-center text-muted py-4">
                    <i class="bi bi-inbox fs-3 d-block mb-2"></i>
                    분석 데이터가 없습니다.
                </td>
            </tr>
        `);
        return;
    }

    // 배분 정보를 맵으로 변환
    const allocationMap = {};
    allocations.forEach(a => {
        allocationMap[a.sector] = a;
    });

    let html = '';
    performances.forEach(p => {
        const alloc = allocationMap[p.sector] || {};
        const returnClass = parseFloat(p.totalReturn) >= 0 ? 'text-success' : 'text-danger';
        const winRateClass = parseFloat(p.winRate) >= 50 ? 'text-success' : 'text-danger';

        html += `
            <tr class="sector-card">
                <td>
                    <span class="badge" style="background-color: ${getSectorColor(p.sector)}">&nbsp;</span>
                    <strong class="ms-2">${p.sectorLabel}</strong>
                </td>
                <td class="text-end">${alloc.weight ? alloc.weight + '%' : '-'}</td>
                <td class="text-end">${alloc.value ? formatCurrency(alloc.value) : '-'}</td>
                <td class="text-end">${alloc.stockCount || 0}</td>
                <td class="text-end ${returnClass} fw-bold">${formatCurrency(p.totalReturn)}</td>
                <td class="text-end ${winRateClass}">${p.winRate}%</td>
                <td class="text-end">${p.tradeCount || 0}</td>
                <td class="text-end">${p.contribution}%</td>
            </tr>
        `;
    });

    $tbody.html(html);
}

/**
 * Update the stocks grid organized by sector
 * @param {Array} allocations - Array of allocation data objects with stocks
 */
function updateStocksGrid(allocations) {
    const $grid = $('#sector-stocks-grid');

    if (!allocations || allocations.length === 0) {
        $grid.html('<div class="col-12 text-center text-muted py-4">보유 종목이 없습니다.</div>');
        return;
    }

    let html = '';
    allocations.forEach(alloc => {
        const stocks = alloc.stocks || [];
        if (stocks.length === 0) return;

        html += `
            <div class="col-md-4">
                <div class="card h-100">
                    <div class="card-header py-2" style="background-color: ${getSectorColor(alloc.sector)}20; border-left: 4px solid ${getSectorColor(alloc.sector)}">
                        <h6 class="mb-0">${alloc.sectorLabel} (${stocks.length}종목)</h6>
                    </div>
                    <div class="card-body p-0">
                        <ul class="list-group list-group-flush">
        `;

        stocks.forEach(stock => {
            const pnlClass = parseFloat(stock.profitLoss) >= 0 ? 'text-success' : 'text-danger';
            html += `
                <li class="list-group-item d-flex justify-content-between align-items-center py-2">
                    <div>
                        <strong>${stock.name}</strong>
                        <small class="text-muted d-block">${stock.symbol}</small>
                    </div>
                    <div class="text-end">
                        <span class="${pnlClass}">${formatCurrency(stock.profitLoss)}</span>
                        <small class="text-muted d-block">${formatPercent(stock.profitLossPercent)}</small>
                    </div>
                </li>
            `;
        });

        html += `
                        </ul>
                    </div>
                </div>
            </div>
        `;
    });

    $grid.html(html || '<div class="col-12 text-center text-muted py-4">보유 종목이 없습니다.</div>');
}

// ============================================================================
// EVENT HANDLERS
// ============================================================================

/**
 * Handle period button click to update sector analysis
 * @param {string} period - The period identifier ('3M', '6M', '1Y', 'ALL')
 */
function updateSectorAnalysis(period) {
    // 버튼 활성화 상태 변경
    $(event.target).closest('.btn-group').find('button').removeClass('active');
    event.target.classList.add('active');

    loadSectorAnalysis(period);
}

/**
 * Display empty state for all sector components
 */
function showEmptySectorState() {
    $('#diversification-rating, #hhi-value, #top-sector, #worst-sector').text('-');
    $('#top-sector-return, #worst-sector-return').text('-');
    $('#sector-details-table').html(`
        <tr>
            <td colspan="8" class="text-center text-muted py-4">
                데이터를 불러올 수 없습니다.
            </td>
        </tr>
    `);
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get an array of sector colors for chart visualization
 * @param {number} count - Number of colors needed
 * @returns {string[]} Array of color hex codes
 */
function getSectorColors(count) {
    return SECTOR_CHART_COLORS.slice(0, count);
}

/**
 * Get the color for a specific sector
 * @param {string} sector - The sector code
 * @returns {string} Color hex code for the sector
 */
function getSectorColor(sector) {
    return SECTOR_COLOR_MAP[sector] || DEFAULT_SECTOR_COLOR;
}

/**
 * Get the Korean label for a sector code
 * @param {string} sector - The sector code
 * @returns {string} Korean label for the sector
 */
function getSectorLabel(sector) {
    return SECTOR_LABEL_MAP[sector] || sector;
}

/**
 * Get the CSS class for diversification rating display
 * @param {string|null} rating - The diversification rating text
 * @returns {string} CSS class name for styling
 */
function getDiversificationClass(rating) {
    if (!rating) return '';
    if (rating.includes('우수')) return 'text-success';
    if (rating.includes('보통')) return 'text-warning';
    return 'text-danger';
}

/**
 * Format a currency value in short Korean notation (억, 만)
 * @param {number} value - The value to format
 * @returns {string} Formatted currency string
 */
function formatCurrencyShort(value) {
    const num = parseFloat(value);
    if (Math.abs(num) >= 100000000) {
        return (num / 100000000).toFixed(1) + '억';
    } else if (Math.abs(num) >= 10000) {
        return (num / 10000).toFixed(0) + '만';
    }
    return num.toLocaleString();
}
