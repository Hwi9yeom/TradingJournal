/**
 * 시장 심리 지표 JavaScript
 * Market Sentiment Module
 */

// =============================================================================
// STATE
// =============================================================================

/** @type {Array<Object>} 지표 카탈로그 (메타데이터) */
let indicatorCatalog = [];

/** @type {Object|null} 최근 조회한 대시보드 응답 */
let dashboardData = null;

/** 판정 구간별 색상 */
const ZONE_COLORS = {
    EXTREME_FEAR: '#22c55e',
    FEAR: '#a3e635',
    NEUTRAL: '#94a3b8',
    GREED: '#fbbf24',
    EXTREME_GREED: '#ef4444'
};

// =============================================================================
// INITIALIZATION
// =============================================================================

$(document).ready(function() {
    $('#sentiment-date').val(new Date().toISOString().split('T')[0]);

    $('#sentiment-form').on('submit', function(e) {
        e.preventDefault();
        saveSentiment();
    });

    $('#sentiment-indicator').on('change', updateIndicatorHint);

    loadCatalog();
    loadHistory();
});

// =============================================================================
// DATA LOADING
// =============================================================================

/** 지표 카탈로그 조회 후 선택 목록과 카드 골격을 만든다 */
function loadCatalog() {
    $.ajax({
        url: '/api/sentiment/catalog',
        method: 'GET',
        success: function(data) {
            indicatorCatalog = data || [];
            renderIndicatorOptions();
            updateIndicatorHint();
            loadDashboard();
        },
        error: function(xhr) {
            console.error('지표 목록 조회 실패:', xhr);
            showToast('지표 목록을 불러오지 못했습니다.', 'danger');
        }
    });
}

/** 종합 대시보드 조회 */
function loadDashboard() {
    $.ajax({
        url: '/api/sentiment/dashboard',
        method: 'GET',
        success: function(data) {
            dashboardData = data;
            renderComposite(data);
            renderIndicatorCards(data);
        },
        error: function(xhr) {
            console.error('종합 심리 조회 실패:', xhr);
            showToast('종합 심리를 불러오지 못했습니다.', 'danger');
        }
    });
}

/** 기록 이력 조회 */
function loadHistory() {
    $.ajax({
        url: '/api/sentiment',
        method: 'GET',
        success: renderHistory,
        error: function(xhr) {
            console.error('기록 이력 조회 실패:', xhr);
        }
    });
}

// =============================================================================
// RENDERING
// =============================================================================

/** 지표 선택 옵션 렌더링 */
function renderIndicatorOptions() {
    const options = indicatorCatalog
        .map(i => `<option value="${i.indicator}">${escapeHtml(i.indicatorLabel)}</option>`)
        .join('');
    $('#sentiment-indicator').html(options);
}

/** 선택한 지표의 단위/해석 안내 갱신 */
function updateIndicatorHint() {
    const selected = $('#sentiment-indicator').val();
    const meta = indicatorCatalog.find(i => i.indicator === selected);

    if (!meta) {
        $('#unit-hint').text('');
        $('#indicator-hint').text('');
        return;
    }

    $('#unit-hint').text(`(${meta.unit})`);
    $('#indicator-hint').html(
        `${escapeHtml(meta.interpretation)} · ` +
        `<a href="${escapeHtml(meta.sourceUrl)}" target="_blank" rel="noopener noreferrer">출처 확인 <i class="bi bi-box-arrow-up-right"></i></a>`
    );
}

/**
 * 종합 심리 게이지 렌더링.
 * @param {Object} data - SentimentDashboardDto
 */
function renderComposite(data) {
    if (!data.overallZone) {
        $('#overall-zone').text('기록 없음').css('color', 'var(--text-muted)');
        $('#overall-action').text('지표를 하나 이상 기록하면 종합 판정이 계산됩니다.');
        $('#gauge-marker').css('left', '50%');
    } else {
        $('#overall-zone').text(data.overallLabel).css('color', ZONE_COLORS[data.overallZone]);
        $('#overall-action').text(data.overallAction);
        // -2..+2 점수를 0..100% 위치로 환산
        const percent = ((Number(data.overallScore) + 2) / 4) * 100;
        $('#gauge-marker').css('left', `${percent}%`);
    }

    renderMarketZone('#us-zone', data, 'US_STOCK');
    renderMarketZone('#crypto-zone', data, 'CRYPTO');
    renderCoverageWarning(data);
}

/**
 * 시장별 판정 표시.
 * @param {string} selector - 대상 엘리먼트
 * @param {Object} data - SentimentDashboardDto
 * @param {string} market - 시장 키
 */
function renderMarketZone(selector, data, market) {
    const zone = (data.zoneByMarket || {})[market];
    const score = (data.scoreByMarket || {})[market];

    if (!zone) {
        $(selector).text('-').css('color', 'var(--text-muted)');
        return;
    }

    const label = zoneLabelOf(zone);
    $(selector).text(`${label} (${Number(score).toFixed(2)})`).css('color', ZONE_COLORS[zone]);
}

/**
 * 미기록/오래된 지표 경고.
 * @param {Object} data - SentimentDashboardDto
 */
function renderCoverageWarning(data) {
    const parts = [];

    if (data.staleIndicators && data.staleIndicators.length) {
        parts.push(`오래된 기록(종합에서 제외): ${data.staleIndicators.join(', ')}`);
    }
    if (data.missingIndicators && data.missingIndicators.length) {
        parts.push(`미기록: ${data.missingIndicators.join(', ')}`);
    }

    $('#coverage-warning').text(parts.join(' · '));
}

/**
 * 지표 카드 렌더링 (시장별로 분리).
 * @param {Object} data - SentimentDashboardDto
 */
function renderIndicatorCards(data) {
    const latestMap = {};
    (data.latestByIndicator || []).forEach(d => {
        latestMap[d.indicator] = d;
    });

    const usCards = [];
    const cryptoCards = [];

    indicatorCatalog.forEach(meta => {
        const card = indicatorCardHtml(meta, latestMap[meta.indicator]);
        if (meta.market === 'CRYPTO') {
            cryptoCards.push(card);
        } else {
            usCards.push(card);
        }
    });

    $('#us-indicators').html(usCards.join(''));
    $('#crypto-indicators').html(cryptoCards.join(''));
}

/**
 * 지표 카드 HTML.
 * @param {Object} meta - 카탈로그 항목
 * @param {Object|undefined} latest - 최신 기록 (없을 수 있음)
 * @returns {string}
 */
function indicatorCardHtml(meta, latest) {
    const color = latest ? ZONE_COLORS[latest.zone] : 'var(--text-muted)';
    const valueText = latest ? `${Number(latest.value)} ${escapeHtml(meta.unit)}` : '미기록';
    const zoneText = latest ? escapeHtml(latest.zoneLabel) : '-';
    const dateText = latest
        ? `${escapeHtml(latest.recordedDate)} (${latest.daysSinceRecorded}일 전)`
        : '';

    return `
        <div class="glass-card indicator-card" style="border-left-color: ${color};">
            <div class="glass-card-body">
                <div style="display: flex; justify-content: space-between; align-items: baseline;">
                    <strong>${escapeHtml(meta.indicatorLabel)}</strong>
                    <span style="color: ${color}; font-weight: 600;">${zoneText}</span>
                </div>
                <div style="font-size: var(--font-size-xl); font-weight: 700; margin: var(--space-2) 0;">${valueText}</div>
                <div style="color: var(--text-muted); font-size: var(--font-size-xs);">${dateText}</div>
                <div style="color: var(--text-secondary); font-size: var(--font-size-xs); margin-top: var(--space-2);">${escapeHtml(meta.interpretation)}</div>
                ${latest ? `<div style="color: ${color}; font-size: var(--font-size-xs); margin-top: var(--space-2);">${escapeHtml(latest.zoneAction)}</div>` : ''}
                <a href="${escapeHtml(meta.sourceUrl)}" target="_blank" rel="noopener noreferrer" style="font-size: var(--font-size-xs);">출처 확인 <i class="bi bi-box-arrow-up-right"></i></a>
            </div>
        </div>
    `;
}

/**
 * 기록 이력 테이블 렌더링.
 * @param {Array<Object>} records - MarketSentimentDto 배열
 */
function renderHistory(records) {
    const $tbody = $('#sentiment-history');

    if (!records || !records.length) {
        $tbody.html('<tr><td colspan="6" class="text-center" style="color: var(--text-muted); padding: var(--space-6);">기록이 없습니다.</td></tr>');
        return;
    }

    $tbody.html(records.map(r => `
        <tr>
            <td>${escapeHtml(r.recordedDate)}</td>
            <td>${escapeHtml(r.indicatorLabel)}</td>
            <td class="text-right">${Number(r.value)} ${escapeHtml(r.unit)}</td>
            <td style="color: ${ZONE_COLORS[r.zone]};">${escapeHtml(r.zoneLabel)}</td>
            <td>${escapeHtml(r.notes || '')}</td>
            <td>
                <button class="btn-glass" style="padding: var(--space-1) var(--space-2); color: var(--color-negative);" onclick="deleteSentiment(${r.id})" title="삭제">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join(''));
}

/**
 * 구간 코드 → 한글 라벨.
 * @param {string} zone
 * @returns {string}
 */
function zoneLabelOf(zone) {
    switch (zone) {
        case 'EXTREME_FEAR': return '극단적 공포';
        case 'FEAR': return '공포';
        case 'NEUTRAL': return '중립';
        case 'GREED': return '탐욕';
        case 'EXTREME_GREED': return '극단적 탐욕';
        default: return '-';
    }
}

// =============================================================================
// MUTATIONS
// =============================================================================

/** 지표 값 기록 */
function saveSentiment() {
    const value = $('#sentiment-value').val();
    if (value === '') {
        showToast('지표 값을 입력해주세요.', 'warning');
        return;
    }

    const payload = {
        indicator: $('#sentiment-indicator').val(),
        value: parseFloat(value),
        recordedDate: $('#sentiment-date').val() || null,
        notes: $('#sentiment-notes').val()
    };

    $.ajax({
        url: '/api/sentiment',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(payload),
        success: function(saved) {
            showToast(`${saved.indicatorLabel}: ${saved.zoneLabel}`, 'success');
            $('#sentiment-value').val('');
            $('#sentiment-notes').val('');
            loadDashboard();
            loadHistory();
        },
        error: function(xhr) {
            console.error('기록 실패:', xhr);
            showToast('기록에 실패했습니다: ' + (xhr.responseJSON?.message || xhr.statusText), 'danger');
        }
    });
}

/**
 * 기록 삭제.
 * @param {number} id - 기록 ID
 */
function deleteSentiment(id) {
    if (!confirm('이 기록을 삭제하시겠습니까?')) {
        return;
    }

    $.ajax({
        url: `/api/sentiment/${id}`,
        method: 'DELETE',
        success: function() {
            showToast('기록이 삭제되었습니다.', 'success');
            loadDashboard();
            loadHistory();
        },
        error: function(xhr) {
            console.error('삭제 실패:', xhr);
            showToast('삭제에 실패했습니다.', 'danger');
        }
    });
}

// =============================================================================
// UTILITIES
// =============================================================================

/**
 * Show a toast notification, falling back through the available toast systems.
 * @param {string} message - Message to display
 * @param {string} type - Toast type (success, danger, warning, info)
 */
function showToast(message, type) {
    const mappedType = type === 'danger' ? 'error' : type;

    if (typeof ToastNotification !== 'undefined') {
        ToastNotification.show(message, mappedType);
        return;
    }

    if (typeof showGlassToast === 'function') {
        showGlassToast(message, mappedType);
        return;
    }

    alert(message);
}
