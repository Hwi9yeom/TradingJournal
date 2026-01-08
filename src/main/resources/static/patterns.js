/**
 * 거래 패턴 분석 JavaScript
 */
$(document).ready(function() {
    let charts = {};

    // 초기화
    init();

    function init() {
        // 기본 날짜 설정 (최근 1년)
        const endDate = new Date();
        const startDate = new Date();
        startDate.setFullYear(startDate.getFullYear() - 1);

        $('#startDate').val(formatDateInput(startDate));
        $('#endDate').val(formatDateInput(endDate));

        // 이벤트 바인딩
        $('#btnAnalyze').click(loadPatternAnalysis);

        // 초기 분석 실행
        loadPatternAnalysis();
    }

    function loadPatternAnalysis() {
        const startDate = $('#startDate').val();
        const endDate = $('#endDate').val();

        if (!startDate || !endDate) {
            alert('날짜를 선택해주세요');
            return;
        }

        $.get('/api/analysis/patterns', { startDate, endDate })
            .done(function(data) {
                updateSummaryCards(data);
                updateStreakTimeline(data.streakAnalysis);
                renderDayOfWeekChart(data.dayOfWeekPerformance);
                renderMonthlyChart(data.monthlySeasonality);
                updateHoldingPeriodStats(data.holdingPeriodAnalysis);
                renderHoldingPeriodChart(data.holdingPeriodAnalysis);
                updateTradeSizeStats(data.tradeSizeAnalysis);
                renderTradeSizeChart(data.tradeSizeAnalysis);
                renderTradeSizePerformance(data.tradeSizeAnalysis);
            })
            .fail(function(err) {
                console.error('패턴 분석 로드 실패:', err);
                alert('패턴 분석을 불러오는데 실패했습니다');
            });
    }

    function updateSummaryCards(data) {
        $('#totalTrades').text(data.totalTrades || 0);

        const streak = data.streakAnalysis;
        if (streak) {
            const currentStreak = streak.currentStreak || 0;
            let streakText = '0';
            if (currentStreak > 0) {
                streakText = '+' + currentStreak + ' 연승';
            } else if (currentStreak < 0) {
                streakText = Math.abs(currentStreak) + ' 연패';
            }
            $('#currentStreak')
                .text(streakText)
                .removeClass('streak-positive streak-negative')
                .addClass(currentStreak > 0 ? 'streak-positive' : currentStreak < 0 ? 'streak-negative' : '');

            $('#maxWinStreak').text(streak.maxWinStreak || 0);
            $('#maxLossStreak').text(streak.maxLossStreak || 0);
        }
    }

    function updateStreakTimeline(streakAnalysis) {
        const container = $('#streakTimeline');
        container.empty();

        if (!streakAnalysis || !streakAnalysis.recentStreaks || streakAnalysis.recentStreaks.length === 0) {
            container.html('<div class="text-muted">스트릭 데이터가 없습니다</div>');
            return;
        }

        streakAnalysis.recentStreaks.forEach(function(streak) {
            const isWin = streak.isWinStreak || streak.winStreak;
            const block = $('<div class="streak-block ' + (isWin ? 'streak-win' : 'streak-loss') + '"></div>')
                .attr('title', formatDate(streak.startDate) + ' ~ ' + formatDate(streak.endDate) + '\n' + formatCurrency(streak.totalPnl))
                .text(streak.length);
            container.append(block);
        });
    }

    function renderDayOfWeekChart(dayOfWeekData) {
        if (!dayOfWeekData || dayOfWeekData.length === 0) return;

        const ctx = document.getElementById('dayOfWeekChart');
        if (charts.dayOfWeek) charts.dayOfWeek.destroy();

        const labels = dayOfWeekData.map(function(d) { return d.dayOfWeekLabel; });
        const winRates = dayOfWeekData.map(function(d) { return d.winRate || 0; });
        const avgReturns = dayOfWeekData.map(function(d) { return d.avgReturn || 0; });

        charts.dayOfWeek = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: '승률 (%)',
                        data: winRates,
                        backgroundColor: 'rgba(102, 126, 234, 0.7)',
                        yAxisID: 'y'
                    },
                    {
                        label: '평균 수익률 (%)',
                        data: avgReturns,
                        type: 'line',
                        borderColor: '#28a745',
                        backgroundColor: 'transparent',
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        type: 'linear',
                        position: 'left',
                        title: { display: true, text: '승률 (%)' },
                        min: 0,
                        max: 100
                    },
                    y1: {
                        type: 'linear',
                        position: 'right',
                        title: { display: true, text: '수익률 (%)' },
                        grid: { drawOnChartArea: false }
                    }
                }
            }
        });
    }

    function renderMonthlyChart(monthlyData) {
        if (!monthlyData || monthlyData.length === 0) return;

        const ctx = document.getElementById('monthlyChart');
        if (charts.monthly) charts.monthly.destroy();

        const labels = monthlyData.map(function(m) { return m.monthLabel; });
        const winRates = monthlyData.map(function(m) { return m.winRate || 0; });
        const tradeCounts = monthlyData.map(function(m) { return m.tradeCount || 0; });

        charts.monthly = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: '거래 수',
                        data: tradeCounts,
                        backgroundColor: 'rgba(108, 117, 125, 0.5)',
                        yAxisID: 'y'
                    },
                    {
                        label: '승률 (%)',
                        data: winRates,
                        type: 'line',
                        borderColor: '#667eea',
                        backgroundColor: 'transparent',
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        type: 'linear',
                        position: 'left',
                        title: { display: true, text: '거래 수' }
                    },
                    y1: {
                        type: 'linear',
                        position: 'right',
                        title: { display: true, text: '승률 (%)' },
                        min: 0,
                        max: 100,
                        grid: { drawOnChartArea: false }
                    }
                }
            }
        });
    }

    function updateHoldingPeriodStats(holdingData) {
        if (!holdingData) return;

        $('#avgHoldingDays').text((holdingData.avgHoldingDays || 0) + ' 일');
        $('#avgWinHoldingDays').text((holdingData.avgWinHoldingDays || 0) + ' 일');
        $('#avgLossHoldingDays').text((holdingData.avgLossHoldingDays || 0) + ' 일');
    }

    function renderHoldingPeriodChart(holdingData) {
        if (!holdingData || !holdingData.distribution) return;

        const ctx = document.getElementById('holdingPeriodChart');
        if (charts.holdingPeriod) charts.holdingPeriod.destroy();

        const distribution = holdingData.distribution;
        const labels = distribution.map(function(d) { return d.label; });
        const counts = distribution.map(function(d) { return d.count || 0; });

        charts.holdingPeriod = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data: counts,
                    backgroundColor: [
                        '#667eea', '#764ba2', '#f093fb',
                        '#f5576c', '#4facfe', '#00f2fe'
                    ]
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: { boxWidth: 12, font: { size: 10 } }
                    }
                }
            }
        });
    }

    function updateTradeSizeStats(tradeSizeData) {
        if (!tradeSizeData) return;

        $('#avgTradeAmount').text(formatCurrency(tradeSizeData.avgTradeAmount));
        $('#stdDeviation').text(formatCurrency(tradeSizeData.stdDeviation));
    }

    function renderTradeSizeChart(tradeSizeData) {
        if (!tradeSizeData || !tradeSizeData.distribution) return;

        const ctx = document.getElementById('tradeSizeChart');
        if (charts.tradeSize) charts.tradeSize.destroy();

        const distribution = tradeSizeData.distribution;
        const labels = distribution.map(function(d) { return d.label; });
        const percentages = distribution.map(function(d) { return d.percentage || 0; });

        charts.tradeSize = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: '비중 (%)',
                    data: percentages,
                    backgroundColor: 'rgba(102, 126, 234, 0.7)'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        title: { display: true, text: '비중 (%)' }
                    }
                }
            }
        });
    }

    function renderTradeSizePerformance(tradeSizeData) {
        const container = $('#tradeSizePerformance');
        container.empty();

        if (!tradeSizeData || !tradeSizeData.distribution) {
            container.html('<div class="text-muted">데이터가 없습니다</div>');
            return;
        }

        const distribution = tradeSizeData.distribution;

        distribution.forEach(function(bucket) {
            const winRate = bucket.winRate || 0;
            const barColor = winRate >= 50 ? 'bg-success' : 'bg-danger';

            container.append(
                '<div class="mb-3">' +
                    '<div class="progress-label">' +
                        '<span>' + bucket.label + '</span>' +
                        '<span>' + bucket.count + '건 (' + winRate.toFixed(1) + '%)</span>' +
                    '</div>' +
                    '<div class="progress" style="height: 8px;">' +
                        '<div class="progress-bar ' + barColor + '" style="width: ' + winRate + '%"></div>' +
                    '</div>' +
                '</div>'
            );
        });
    }

    // 유틸리티 함수
    function formatDateInput(date) {
        return date.toISOString().split('T')[0];
    }

    function formatDate(dateStr) {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('ko-KR');
    }

    function formatCurrency(value) {
        if (value == null) return '₩0';
        return new Intl.NumberFormat('ko-KR', {
            style: 'currency',
            currency: 'KRW',
            maximumFractionDigits: 0
        }).format(value);
    }
});
