/**
 * Trading Journal - Reviews Module
 * Manages trade review functionality including listing, creation, and statistics.
 *
 * @fileoverview Trade review management with:
 * - Review listing with pagination
 * - Review creation and editing
 * - Statistics display
 * - Rating system
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * API base URL for review endpoints
 * @constant {string}
 */
const API_BASE_URL = '/api';

/**
 * Page size for review pagination
 * @constant {number}
 */
const PAGE_SIZE = 10;

/**
 * Maximum rating score
 * @constant {number}
 */
const MAX_RATING = 5;

/**
 * Win rate threshold for displaying success color
 * @constant {number}
 */
const WIN_RATE_THRESHOLD = 50;

/**
 * CSS classes for review cards based on outcome
 * @constant {Object}
 */
const REVIEW_CARD_CLASSES = {
    WIN: 'border-start border-success border-3',
    LOSS: 'border-start border-danger border-3'
};

/**
 * Badge configurations for review display
 * @constant {Object}
 */
const BADGES = {
    PLAN_FOLLOWED: { class: 'bg-success', text: '계획준수' },
    PLAN_DEVIATED: { class: 'bg-warning text-dark', text: '계획이탈' }
};

// ============================================================================
// STATE
// ============================================================================

/**
 * Application state object for reviews module
 * @type {Object}
 */
const reviewsState = {
    /** @type {Array} Available trading strategies */
    strategies: [],
    /** @type {Array} Available emotion options */
    emotions: [],
    /** @type {number} Current page index (0-based) */
    currentPage: 0,
    /** @type {bootstrap.Modal|null} Bootstrap modal instance */
    reviewModal: null
};

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Initialize the reviews module on document ready.
 * Sets up modal, loads data, and attaches event listeners.
 */
$(document).ready(function() {
    if (!checkAuth()) {
        return;
    }

    reviewsState.reviewModal = new bootstrap.Modal(document.getElementById('reviewModal'));

    // Load initial data
    loadStrategies();
    loadEmotions();
    loadStatistics();
    loadReviews(0);

    // Rating star click handler
    $('.rating-star').on('click', function() {
        const value = $(this).data('value');
        setRating(value);
    });

    // Strategy filter change handler
    $('#strategy-filter').on('change', function() {
        loadReviews(0);
    });
});

// ============================================================================
// DATA LOADING
// ============================================================================

/**
 * Load available trading strategies from API.
 * Populates both filter dropdown and form select elements.
 */
function loadStrategies() {
    $.ajax({
        url: `${API_BASE_URL}/reviews/strategies`,
        method: 'GET',
        success: function(data) {
            reviewsState.strategies = data;
            const filterOptions = buildSelectOptions(data, '전체 전략', false);
            const formOptions = buildSelectOptions(data, '선택...', true);

            $('#strategy-filter').html(filterOptions);
            $('#review-strategy').html(formOptions);
        },
        error: function(xhr) {
            handleAjaxError(xhr, '전략 목록을 불러올 수 없습니다.');
        }
    });
}

/**
 * Load available emotion options from API.
 * Populates emotion select elements in the review form.
 */
function loadEmotions() {
    $.ajax({
        url: `${API_BASE_URL}/reviews/emotions`,
        method: 'GET',
        success: function(data) {
            reviewsState.emotions = data;
            const options = buildSelectOptions(data, '선택...', false);

            $('#review-emotion-before').html(options);
            $('#review-emotion-after').html(options);
        },
        error: function(xhr) {
            handleAjaxError(xhr, '감정 목록을 불러올 수 없습니다.');
        }
    });
}

/**
 * Load review statistics from API.
 */
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

/**
 * Load reviews with pagination.
 * @param {number} page - Page number (0-based)
 */
function loadReviews(page) {
    reviewsState.currentPage = page;

    $.ajax({
        url: `${API_BASE_URL}/reviews`,
        method: 'GET',
        data: { page: page, size: PAGE_SIZE },
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

// ============================================================================
// DISPLAY FUNCTIONS
// ============================================================================

/**
 * Update statistics display with data from API.
 * @param {Object} stats - Statistics data object
 */
function updateStatisticsDisplay(stats) {
    // Total reviews and review rate
    $('#total-reviews').text(stats.totalReviews);
    $('#review-rate').text(parseFloat(stats.reviewRate || 0).toFixed(1) + '%');

    // Average rating with stars
    const avgRating = stats.averageRating || 0;
    $('#avg-rating').text(avgRating.toFixed(1));
    $('#avg-rating-stars').html(generateStarsHtml(Math.round(avgRating)));

    // Plan adherence statistics
    updatePlanAdherenceDisplay(stats);

    // Top strategy display
    updateTopStrategyDisplay(stats.strategyStats);

    // Strategy statistics table
    if (stats.strategyStats) {
        const tableHtml = buildStrategyStatsTable(stats.strategyStats);
        $('#strategy-stats-table').html(tableHtml || createEmptyTableRow(3));
    }

    // Emotion statistics table
    if (stats.emotionStats) {
        const tableHtml = buildEmotionStatsTable(stats.emotionStats);
        $('#emotion-stats-table').html(tableHtml || createEmptyTableRow(3));
    }

    // Recent lessons
    if (stats.recentLessons && stats.recentLessons.length > 0) {
        $('#recent-lessons').html(buildRecentLessonsHtml(stats.recentLessons));
    }
}

/**
 * Update plan adherence display section.
 * @param {Object} stats - Statistics data object
 */
function updatePlanAdherenceDisplay(stats) {
    const total = stats.followedPlanCount + stats.notFollowedPlanCount;
    if (total > 0) {
        const rate = (stats.followedPlanCount / total * 100).toFixed(0);
        $('#plan-followed').text(rate + '%');
        $('#plan-stats').text(`${stats.followedPlanCount}/${total}건`);
    }
}

/**
 * Update top strategy display section.
 * @param {Object} strategyStats - Strategy statistics object
 */
function updateTopStrategyDisplay(strategyStats) {
    if (!strategyStats) return;

    let topStrategy = null;
    let maxCount = 0;

    for (const [key, value] of Object.entries(strategyStats)) {
        if (value.count > maxCount) {
            maxCount = value.count;
            topStrategy = value;
        }
    }

    if (topStrategy) {
        $('#top-strategy').text(topStrategy.strategyLabel);
        $('#strategy-count').text(topStrategy.count + '건');
    }
}

/**
 * Display reviews list.
 * @param {Array} reviews - Array of review objects
 */
function displayReviews(reviews) {
    if (!reviews || reviews.length === 0) {
        $('#reviews-list').html(createEmptyReviewsHtml());
        return;
    }

    let html = '<div class="list-group list-group-flush">';
    reviews.forEach(review => {
        html += buildReviewCardHtml(review);
    });
    html += '</div>';

    $('#reviews-list').html(html);
}

/**
 * Display pagination controls.
 * @param {Object} data - Paginated response data from API
 */
function displayPagination(data) {
    if (data.totalPages <= 1) {
        $('#pagination-nav').html('');
        return;
    }

    let html = '<ul class="pagination pagination-sm justify-content-center mb-0">';

    // Previous button
    html += `<li class="page-item ${data.first ? 'disabled' : ''}">
        <a class="page-link" href="#" onclick="loadReviews(${data.number - 1}); return false;">이전</a>
    </li>`;

    // Page numbers (show up to 5 pages around current)
    for (let i = 0; i < data.totalPages; i++) {
        if (i === data.number || Math.abs(i - data.number) < 3) {
            html += `<li class="page-item ${i === data.number ? 'active' : ''}">
                <a class="page-link" href="#" onclick="loadReviews(${i}); return false;">${i + 1}</a>
            </li>`;
        }
    }

    // Next button
    html += `<li class="page-item ${data.last ? 'disabled' : ''}">
        <a class="page-link" href="#" onclick="loadReviews(${data.number + 1}); return false;">다음</a>
    </li>`;

    html += '</ul>';
    $('#pagination-nav').html(html);
}

// ============================================================================
// MODAL FUNCTIONS
// ============================================================================

/**
 * Open the review modal for creating or editing a review.
 * @param {number} transactionId - Transaction ID
 * @param {number|null} reviewId - Review ID for editing, null for new review
 */
function openReviewModal(transactionId, reviewId) {
    $('#transaction-id').val(transactionId);
    $('#review-id').val(reviewId || '');

    // Reset form
    $('#review-form')[0].reset();
    setRating(0);

    if (reviewId) {
        // Edit existing review
        $('#reviewModalTitle').text('거래 복기 수정');
        loadReviewData(reviewId);
    } else {
        // Create new review
        $('#reviewModalTitle').text('거래 복기 작성');
        loadTransactionInfo(transactionId);
    }

    reviewsState.reviewModal.show();
}

/**
 * Load existing review data into the form.
 * @param {number} reviewId - Review ID to load
 */
function loadReviewData(reviewId) {
    $.ajax({
        url: `${API_BASE_URL}/reviews/${reviewId}`,
        method: 'GET',
        success: function(data) {
            // Display transaction info
            updateModalTransactionInfo(data);

            // Populate form fields
            $('#review-strategy').val(data.strategy || '');
            $('#review-emotion-before').val(data.emotionBefore || '');
            $('#review-emotion-after').val(data.emotionAfter || '');
            $('#review-entry-reason').val(data.entryReason || '');
            $('#review-exit-reason').val(data.exitReason || '');
            $('#review-note').val(data.reviewNote || '');
            $('#review-lessons').val(data.lessonsLearned || '');
            $('#review-tags').val(data.tags || '');
            setRating(data.ratingScore || 0);

            // Set plan followed radio
            if (data.followedPlan === true) {
                $('#plan-yes').prop('checked', true);
            } else if (data.followedPlan === false) {
                $('#plan-no').prop('checked', true);
            }
        },
        error: function(xhr) {
            handleAjaxError(xhr, '복기 데이터를 불러올 수 없습니다.');
        }
    });
}

/**
 * Load transaction info for new review creation.
 * @param {number} transactionId - Transaction ID to load
 */
function loadTransactionInfo(transactionId) {
    $.ajax({
        url: `${API_BASE_URL}/transactions/${transactionId}`,
        method: 'GET',
        success: function(data) {
            const profitPercent = calculateProfitPercent(data.realizedPnl, data.costBasis);
            updateModalTransactionInfo({
                ...data,
                profitPercent: profitPercent
            });
        },
        error: function() {
            $('#modal-stock-name').text('거래 정보 로드 실패');
        }
    });
}

/**
 * Update modal transaction info display.
 * @param {Object} data - Transaction or review data
 */
function updateModalTransactionInfo(data) {
    const pnl = parseFloat(data.realizedPnl || 0);
    const pnlClass = pnl >= 0 ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;

    $('#modal-stock-name').text(data.stockName || '-');
    $('#modal-stock-symbol').text(data.stockSymbol || '');
    $('#modal-pnl')
        .text(formatCurrency(pnl))
        .removeClass(`${CSS_CLASSES.SUCCESS} ${CSS_CLASSES.DANGER}`)
        .addClass(pnlClass);
    $('#modal-pnl-percent').text(formatPercent(data.profitPercent));
}

// ============================================================================
// RATING FUNCTIONS
// ============================================================================

/**
 * Set the rating value and update star display.
 * @param {number} value - Rating value (0-5)
 */
function setRating(value) {
    $('#review-rating').val(value);
    $('.rating-star').each(function() {
        const starValue = $(this).data('value');
        $(this).toggleClass('active', starValue <= value);
    });
}

// ============================================================================
// SAVE FUNCTIONS
// ============================================================================

/**
 * Save review data (create or update).
 * Validates and sends data to API.
 */
function saveReview() {
    const reviewId = $('#review-id').val();
    const transactionId = $('#transaction-id').val();

    const data = buildReviewData();

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
            reviewsState.reviewModal.hide();
            loadReviews(reviewsState.currentPage);
            loadStatistics();
            ToastNotification.success(reviewId ? '복기가 수정되었습니다.' : '복기가 저장되었습니다.');
        },
        error: function(xhr) {
            handleAjaxError(xhr, '저장에 실패했습니다.');
        }
    });
}

/**
 * Build review data object from form inputs.
 * @returns {Object} Review data object
 */
function buildReviewData() {
    const followedPlanVal = $('input[name="followed-plan"]:checked').val();

    return {
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
}

// ============================================================================
// HTML BUILDER FUNCTIONS
// ============================================================================

/**
 * Build select options HTML from data array.
 * @param {Array} data - Array of option objects with value/label properties
 * @param {string} placeholder - Placeholder text for first option
 * @param {boolean} includeDescription - Whether to include description in label
 * @returns {string} HTML string of option elements
 */
function buildSelectOptions(data, placeholder, includeDescription) {
    let options = `<option value="">${escapeHtml(placeholder)}</option>`;
    data.forEach(item => {
        const label = includeDescription && item.description
            ? `${item.label} - ${item.description}`
            : item.label;
        options += `<option value="${escapeHtml(item.value)}">${escapeHtml(label)}</option>`;
    });
    return options;
}

/**
 * Generate star rating HTML.
 * @param {number} rating - Rating value (0-5)
 * @returns {string} HTML string of star icons
 */
function generateStarsHtml(rating) {
    let starsHtml = '';
    for (let i = 1; i <= MAX_RATING; i++) {
        const starClass = i <= rating ? 'text-warning' : 'text-muted';
        starsHtml += `<i class="bi bi-star-fill ${starClass}"></i>`;
    }
    return starsHtml;
}

/**
 * Build a single review card HTML.
 * @param {Object} review - Review object
 * @returns {string} HTML string for review card
 */
function buildReviewCardHtml(review) {
    const isWin = parseFloat(review.realizedPnl || 0) > 0;
    const pnlClass = isWin ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;
    const borderClass = isWin ? REVIEW_CARD_CLASSES.WIN : REVIEW_CARD_CLASSES.LOSS;

    const starsHtml = generateStarsHtml(review.ratingScore || 0);
    const badgesHtml = buildReviewBadgesHtml(review);
    const lessonsHtml = review.lessonsLearned
        ? `<div class="mt-2 small text-muted"><i class="bi bi-lightbulb"></i> ${escapeHtml(truncateText(review.lessonsLearned, 50))}</div>`
        : '';

    return `
        <div class="list-group-item review-card ${borderClass}" onclick="openReviewModal(${review.transactionId}, ${review.id})">
            <div class="d-flex justify-content-between align-items-start">
                <div>
                    <h6 class="mb-1">${escapeHtml(review.stockName || '-')}</h6>
                    <small class="text-muted">${escapeHtml(review.stockSymbol || '')} | ${formatDate(review.transactionDate)}</small>
                </div>
                <div class="text-end">
                    <div class="${pnlClass} fw-bold">${formatCurrency(review.realizedPnl || 0)}</div>
                    <small class="${pnlClass}">${formatPercent(review.profitPercent)}</small>
                </div>
            </div>
            <div class="mt-2">${badgesHtml}</div>
            <div class="mt-2 d-flex justify-content-between align-items-center">
                <div>${starsHtml}</div>
                <small class="text-muted">${formatDate(review.reviewedAt)}</small>
            </div>
            ${lessonsHtml}
        </div>
    `;
}

/**
 * Build badges HTML for a review card.
 * @param {Object} review - Review object
 * @returns {string} HTML string of badges
 */
function buildReviewBadgesHtml(review) {
    let html = '';

    if (review.strategyLabel) {
        html += `<span class="badge bg-primary me-1">${escapeHtml(review.strategyLabel)}</span>`;
    }
    if (review.emotionBeforeLabel) {
        html += `<span class="badge bg-secondary me-1">${escapeHtml(review.emotionBeforeLabel)}</span>`;
    }
    if (review.followedPlan === true) {
        html += `<span class="badge ${BADGES.PLAN_FOLLOWED.class}">${BADGES.PLAN_FOLLOWED.text}</span>`;
    }
    if (review.followedPlan === false) {
        html += `<span class="badge ${BADGES.PLAN_DEVIATED.class}">${BADGES.PLAN_DEVIATED.text}</span>`;
    }

    return html;
}

/**
 * Build strategy statistics table HTML.
 * @param {Object} strategyStats - Strategy statistics object
 * @returns {string} HTML string for table rows
 */
function buildStrategyStatsTable(strategyStats) {
    let tableHtml = '';
    for (const [key, value] of Object.entries(strategyStats)) {
        const winRate = parseFloat(value.winRate);
        const winRateClass = winRate >= WIN_RATE_THRESHOLD ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;
        tableHtml += `
            <tr>
                <td>${escapeHtml(value.strategyLabel)}</td>
                <td class="text-end">${value.count}</td>
                <td class="text-end ${winRateClass}">${winRate.toFixed(1)}%</td>
            </tr>
        `;
    }
    return tableHtml;
}

/**
 * Build emotion statistics table HTML.
 * @param {Object} emotionStats - Emotion statistics object
 * @returns {string} HTML string for table rows
 */
function buildEmotionStatsTable(emotionStats) {
    let tableHtml = '';
    for (const [key, value] of Object.entries(emotionStats)) {
        const winRate = parseFloat(value.winRate);
        const winRateClass = winRate >= WIN_RATE_THRESHOLD ? CSS_CLASSES.SUCCESS : CSS_CLASSES.DANGER;
        tableHtml += `
            <tr>
                <td>${escapeHtml(value.emotionLabel)}</td>
                <td class="text-end">${value.count}</td>
                <td class="text-end ${winRateClass}">${winRate.toFixed(1)}%</td>
            </tr>
        `;
    }
    return tableHtml;
}

/**
 * Build recent lessons HTML.
 * @param {Array} lessons - Array of lesson objects
 * @returns {string} HTML string for lessons display
 */
function buildRecentLessonsHtml(lessons) {
    let html = '';
    lessons.forEach(lesson => {
        const icon = lesson.win ? 'bi-check-circle text-success' : 'bi-x-circle text-danger';
        html += `
            <div class="border-bottom pb-2 mb-2">
                <div class="d-flex justify-content-between">
                    <small class="text-muted">${escapeHtml(lesson.stockName)}</small>
                    <i class="bi ${icon}"></i>
                </div>
                <p class="mb-0 small">${escapeHtml(lesson.lessonsLearned)}</p>
            </div>
        `;
    });
    return html;
}

/**
 * Create empty reviews state HTML.
 * @returns {string} HTML string for empty state
 */
function createEmptyReviewsHtml() {
    return `
        <div class="text-center py-5 text-muted">
            <i class="bi bi-journal-x fs-1"></i>
            <p class="mt-2">아직 작성된 복기가 없습니다.</p>
            <p class="small">거래 관리에서 매도 거래를 선택하여 복기를 작성해보세요.</p>
        </div>
    `;
}

/**
 * Create empty table row HTML.
 * @param {number} colSpan - Number of columns to span
 * @returns {string} HTML string for empty table row
 */
function createEmptyTableRow(colSpan) {
    return `<tr><td colspan="${colSpan}" class="text-center text-muted">데이터 없음</td></tr>`;
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Calculate profit percentage from P&L and cost basis.
 * @param {number|null} realizedPnl - Realized profit/loss
 * @param {number|null} costBasis - Cost basis
 * @returns {number} Profit percentage
 */
function calculateProfitPercent(realizedPnl, costBasis) {
    const pnl = parseFloat(realizedPnl || 0);
    const cost = parseFloat(costBasis || 0);
    return cost > 0 ? (pnl / cost * 100) : 0;
}
