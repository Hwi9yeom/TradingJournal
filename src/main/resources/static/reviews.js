const API_BASE_URL = '/api';
let strategies = [];
let emotions = [];
let currentPage = 0;
let reviewModal;

$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    reviewModal = new bootstrap.Modal(document.getElementById('reviewModal'));

    loadStrategies();
    loadEmotions();
    loadStatistics();
    loadReviews(0);

    // 별점 클릭 이벤트
    $('.rating-star').on('click', function() {
        const value = $(this).data('value');
        setRating(value);
    });

    // 전략 필터 변경
    $('#strategy-filter').on('change', function() {
        loadReviews(0);
    });
});

function loadStrategies() {
    $.ajax({
        url: `${API_BASE_URL}/reviews/strategies`,
        method: 'GET',
        success: function(data) {
            strategies = data;
            let options = '<option value="">전체 전략</option>';
            let formOptions = '<option value="">선택...</option>';

            data.forEach(s => {
                options += `<option value="${s.value}">${s.label}</option>`;
                formOptions += `<option value="${s.value}">${s.label} - ${s.description}</option>`;
            });

            $('#strategy-filter').html(options);
            $('#review-strategy').html(formOptions);
        }
    });
}

function loadEmotions() {
    $.ajax({
        url: `${API_BASE_URL}/reviews/emotions`,
        method: 'GET',
        success: function(data) {
            emotions = data;
            let options = '<option value="">선택...</option>';

            data.forEach(e => {
                options += `<option value="${e.value}">${e.label}</option>`;
            });

            $('#review-emotion-before').html(options);
            $('#review-emotion-after').html(options);
        }
    });
}

function loadStatistics() {
    $.ajax({
        url: `${API_BASE_URL}/reviews/statistics`,
        method: 'GET',
        success: function(data) {
            updateStatisticsDisplay(data);
        },
        error: function(xhr) {
            console.error('Failed to load statistics:', xhr);
        }
    });
}

function updateStatisticsDisplay(stats) {
    $('#total-reviews').text(stats.totalReviews);
    $('#review-rate').text(parseFloat(stats.reviewRate || 0).toFixed(1) + '%');

    // 평균 평점
    const avgRating = stats.averageRating || 0;
    $('#avg-rating').text(avgRating.toFixed(1));
    let starsHtml = '';
    for (let i = 1; i <= 5; i++) {
        const starClass = i <= Math.round(avgRating) ? 'text-warning' : 'text-muted';
        starsHtml += `<i class="bi bi-star-fill ${starClass}"></i>`;
    }
    $('#avg-rating-stars').html(starsHtml);

    // 계획 준수
    const total = stats.followedPlanCount + stats.notFollowedPlanCount;
    if (total > 0) {
        const rate = (stats.followedPlanCount / total * 100).toFixed(0);
        $('#plan-followed').text(rate + '%');
        $('#plan-stats').text(`${stats.followedPlanCount}/${total}건`);
    }

    // 주요 전략
    if (stats.strategyStats) {
        let topStrategy = null;
        let maxCount = 0;
        for (const [key, value] of Object.entries(stats.strategyStats)) {
            if (value.count > maxCount) {
                maxCount = value.count;
                topStrategy = value;
            }
        }
        if (topStrategy) {
            $('#top-strategy').text(topStrategy.strategyLabel);
            $('#strategy-count').text(topStrategy.count + '건');
        }

        // 전략별 테이블
        let tableHtml = '';
        for (const [key, value] of Object.entries(stats.strategyStats)) {
            tableHtml += `
                <tr>
                    <td>${value.strategyLabel}</td>
                    <td class="text-end">${value.count}</td>
                    <td class="text-end ${parseFloat(value.winRate) >= 50 ? 'text-success' : 'text-danger'}">
                        ${parseFloat(value.winRate).toFixed(1)}%
                    </td>
                </tr>
            `;
        }
        $('#strategy-stats-table').html(tableHtml || '<tr><td colspan="3" class="text-center text-muted">데이터 없음</td></tr>');
    }

    // 감정별 테이블
    if (stats.emotionStats) {
        let tableHtml = '';
        for (const [key, value] of Object.entries(stats.emotionStats)) {
            tableHtml += `
                <tr>
                    <td>${value.emotionLabel}</td>
                    <td class="text-end">${value.count}</td>
                    <td class="text-end ${parseFloat(value.winRate) >= 50 ? 'text-success' : 'text-danger'}">
                        ${parseFloat(value.winRate).toFixed(1)}%
                    </td>
                </tr>
            `;
        }
        $('#emotion-stats-table').html(tableHtml || '<tr><td colspan="3" class="text-center text-muted">데이터 없음</td></tr>');
    }

    // 최근 교훈
    if (stats.recentLessons && stats.recentLessons.length > 0) {
        let lessonsHtml = '';
        stats.recentLessons.forEach(lesson => {
            const icon = lesson.win ? 'bi-check-circle text-success' : 'bi-x-circle text-danger';
            lessonsHtml += `
                <div class="border-bottom pb-2 mb-2">
                    <div class="d-flex justify-content-between">
                        <small class="text-muted">${lesson.stockName}</small>
                        <i class="bi ${icon}"></i>
                    </div>
                    <p class="mb-0 small">${lesson.lessonsLearned}</p>
                </div>
            `;
        });
        $('#recent-lessons').html(lessonsHtml);
    }
}

function loadReviews(page) {
    currentPage = page;

    $.ajax({
        url: `${API_BASE_URL}/reviews`,
        method: 'GET',
        data: { page: page, size: 10 },
        success: function(data) {
            displayReviews(data.content);
            displayPagination(data);
        },
        error: function(xhr) {
            console.error('Failed to load reviews:', xhr);
            $('#reviews-list').html('<div class="text-center py-5 text-muted">복기 데이터를 불러올 수 없습니다.</div>');
        }
    });
}

function displayReviews(reviews) {
    if (!reviews || reviews.length === 0) {
        $('#reviews-list').html(`
            <div class="text-center py-5 text-muted">
                <i class="bi bi-journal-x fs-1"></i>
                <p class="mt-2">아직 작성된 복기가 없습니다.</p>
                <p class="small">거래 관리에서 매도 거래를 선택하여 복기를 작성해보세요.</p>
            </div>
        `);
        return;
    }

    let html = '<div class="list-group list-group-flush">';
    reviews.forEach(review => {
        const isWin = parseFloat(review.realizedPnl || 0) > 0;
        const pnlClass = isWin ? 'text-success' : 'text-danger';
        const borderClass = isWin ? 'border-start border-success border-3' : 'border-start border-danger border-3';

        let starsHtml = '';
        for (let i = 1; i <= 5; i++) {
            starsHtml += i <= (review.ratingScore || 0)
                ? '<i class="bi bi-star-fill text-warning"></i>'
                : '<i class="bi bi-star text-muted"></i>';
        }

        html += `
            <div class="list-group-item review-card ${borderClass}" onclick="openReviewModal(${review.transactionId}, ${review.id})">
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <h6 class="mb-1">${review.stockName || '-'}</h6>
                        <small class="text-muted">${review.stockSymbol || ''} | ${formatDate(review.transactionDate)}</small>
                    </div>
                    <div class="text-end">
                        <div class="${pnlClass} fw-bold">${formatCurrency(review.realizedPnl || 0)}</div>
                        <small class="${pnlClass}">${formatPercent(review.profitPercent)}</small>
                    </div>
                </div>
                <div class="mt-2">
                    ${review.strategyLabel ? `<span class="badge bg-primary me-1">${review.strategyLabel}</span>` : ''}
                    ${review.emotionBeforeLabel ? `<span class="badge bg-secondary me-1">${review.emotionBeforeLabel}</span>` : ''}
                    ${review.followedPlan === true ? '<span class="badge bg-success">계획준수</span>' : ''}
                    ${review.followedPlan === false ? '<span class="badge bg-warning text-dark">계획이탈</span>' : ''}
                </div>
                <div class="mt-2 d-flex justify-content-between align-items-center">
                    <div>${starsHtml}</div>
                    <small class="text-muted">${formatDate(review.reviewedAt)}</small>
                </div>
                ${review.lessonsLearned ? `<div class="mt-2 small text-muted"><i class="bi bi-lightbulb"></i> ${truncate(review.lessonsLearned, 50)}</div>` : ''}
            </div>
        `;
    });
    html += '</div>';
    $('#reviews-list').html(html);
}

function displayPagination(data) {
    if (data.totalPages <= 1) {
        $('#pagination-nav').html('');
        return;
    }

    let html = '<ul class="pagination pagination-sm justify-content-center mb-0">';

    // 이전 버튼
    html += `<li class="page-item ${data.first ? 'disabled' : ''}">
        <a class="page-link" href="#" onclick="loadReviews(${data.number - 1}); return false;">이전</a>
    </li>`;

    // 페이지 번호
    for (let i = 0; i < data.totalPages; i++) {
        if (i === data.number || Math.abs(i - data.number) < 3) {
            html += `<li class="page-item ${i === data.number ? 'active' : ''}">
                <a class="page-link" href="#" onclick="loadReviews(${i}); return false;">${i + 1}</a>
            </li>`;
        }
    }

    // 다음 버튼
    html += `<li class="page-item ${data.last ? 'disabled' : ''}">
        <a class="page-link" href="#" onclick="loadReviews(${data.number + 1}); return false;">다음</a>
    </li>`;

    html += '</ul>';
    $('#pagination-nav').html(html);
}

function openReviewModal(transactionId, reviewId) {
    $('#transaction-id').val(transactionId);
    $('#review-id').val(reviewId || '');

    // 폼 초기화
    $('#review-form')[0].reset();
    setRating(0);

    if (reviewId) {
        // 기존 복기 수정
        $('#reviewModalTitle').text('거래 복기 수정');
        loadReviewData(reviewId);
    } else {
        // 새 복기 작성
        $('#reviewModalTitle').text('거래 복기 작성');
        loadTransactionInfo(transactionId);
    }

    reviewModal.show();
}

function loadReviewData(reviewId) {
    $.ajax({
        url: `${API_BASE_URL}/reviews/${reviewId}`,
        method: 'GET',
        success: function(data) {
            // 거래 정보
            $('#modal-stock-name').text(data.stockName || '-');
            $('#modal-stock-symbol').text(data.stockSymbol || '');
            $('#modal-pnl').text(formatCurrency(data.realizedPnl || 0))
                .removeClass('text-success text-danger')
                .addClass(parseFloat(data.realizedPnl || 0) >= 0 ? 'text-success' : 'text-danger');
            $('#modal-pnl-percent').text(formatPercent(data.profitPercent));

            // 복기 내용
            $('#review-strategy').val(data.strategy || '');
            $('#review-emotion-before').val(data.emotionBefore || '');
            $('#review-emotion-after').val(data.emotionAfter || '');
            $('#review-entry-reason').val(data.entryReason || '');
            $('#review-exit-reason').val(data.exitReason || '');
            $('#review-note').val(data.reviewNote || '');
            $('#review-lessons').val(data.lessonsLearned || '');
            $('#review-tags').val(data.tags || '');
            setRating(data.ratingScore || 0);

            if (data.followedPlan === true) {
                $('#plan-yes').prop('checked', true);
            } else if (data.followedPlan === false) {
                $('#plan-no').prop('checked', true);
            }
        }
    });
}

function loadTransactionInfo(transactionId) {
    // 거래 정보를 로드하여 모달에 표시
    $.ajax({
        url: `${API_BASE_URL}/transactions/${transactionId}`,
        method: 'GET',
        success: function(data) {
            $('#modal-stock-name').text(data.stockName || '-');
            $('#modal-stock-symbol').text(data.stockSymbol || '');
            $('#modal-pnl').text(formatCurrency(data.realizedPnl || 0))
                .removeClass('text-success text-danger')
                .addClass(parseFloat(data.realizedPnl || 0) >= 0 ? 'text-success' : 'text-danger');

            const profitPercent = data.costBasis && parseFloat(data.costBasis) > 0
                ? (parseFloat(data.realizedPnl || 0) / parseFloat(data.costBasis) * 100)
                : 0;
            $('#modal-pnl-percent').text(formatPercent(profitPercent));
        },
        error: function() {
            $('#modal-stock-name').text('거래 정보 로드 실패');
        }
    });
}

function setRating(value) {
    $('#review-rating').val(value);
    $('.rating-star').each(function() {
        const starValue = $(this).data('value');
        $(this).toggleClass('active', starValue <= value);
    });
}

function saveReview() {
    const reviewId = $('#review-id').val();
    const transactionId = $('#transaction-id').val();

    const followedPlanVal = $('input[name="followed-plan"]:checked').val();

    const data = {
        strategy: $('#review-strategy').val() || null,
        emotionBefore: $('#review-emotion-before').val() || null,
        emotionAfter: $('#review-emotion-after').val() || null,
        entryReason: $('#review-entry-reason').val(),
        exitReason: $('#review-exit-reason').val(),
        reviewNote: $('#review-note').val(),
        lessonsLearned: $('#review-lessons').val(),
        tags: $('#review-tags').val(),
        ratingScore: parseInt($('#review-rating').val()) || null,
        followedPlan: followedPlanVal === 'true' ? true : (followedPlanVal === 'false' ? false : null)
    };

    const url = reviewId
        ? `${API_BASE_URL}/reviews/${reviewId}`
        : `${API_BASE_URL}/reviews/transaction/${transactionId}`;
    const method = reviewId ? 'PUT' : 'POST';

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        data: JSON.stringify(data),
        success: function() {
            reviewModal.hide();
            loadReviews(currentPage);
            loadStatistics();
            alert(reviewId ? '복기가 수정되었습니다.' : '복기가 저장되었습니다.');
        },
        error: function(xhr) {
            alert('저장 실패: ' + (xhr.responseJSON?.message || xhr.statusText));
        }
    });
}

// 유틸리티 함수
function formatCurrency(value) {
    return '₩' + Math.round(value || 0).toLocaleString();
}

function formatPercent(value) {
    const num = parseFloat(value || 0);
    return (num >= 0 ? '+' : '') + num.toFixed(2) + '%';
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' });
}

function truncate(str, len) {
    if (!str) return '';
    return str.length > len ? str.substring(0, len) + '...' : str;
}
