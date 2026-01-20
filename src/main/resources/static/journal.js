/**
 * Trading Journal - 일지 페이지 JavaScript
 * @fileoverview 거래 일지 작성, 조회, 통계 기능을 제공합니다.
 *
 * 주요 기능:
 * - 일지 작성 및 편집
 * - 감정 상태 및 점수 기록
 * - 태그 관리
 * - 달력 기반 일지 조회
 * - 통계 및 히스토리 조회
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * 기본 감정 상태 목록 (API 로드 실패 시 사용)
 * @constant {Array<{value: string, label: string, description: string}>}
 */
const DEFAULT_EMOTIONS = [
    { value: 'CONFIDENT', label: '자신감', description: '확신을 가지고 거래' },
    { value: 'CALM', label: '침착', description: '안정적인 상태' },
    { value: 'NEUTRAL', label: '보통', description: '특별한 감정 없음' },
    { value: 'ANXIOUS', label: '불안', description: '걱정되는 상태' },
    { value: 'FEARFUL', label: '두려움', description: '손실 두려움' },
    { value: 'GREEDY', label: '탐욕', description: '과욕 상태' },
    { value: 'FRUSTRATED', label: '좌절', description: '실망한 상태' },
    { value: 'EXCITED', label: '흥분', description: '과도하게 흥분' }
];

/**
 * 요일 표시명
 * @constant {string[]}
 */
const DAY_NAMES = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];

/**
 * 달력 표시 행 수 (6주 * 7일)
 * @constant {number}
 */
const CALENDAR_TOTAL_DAYS = 42;

/**
 * 통계 기본 조회 기간 (개월)
 * @constant {number}
 */
const STATISTICS_DEFAULT_MONTHS = 3;

/**
 * 히스토리 기본 조회 기간 (일)
 * @constant {number}
 */
const HISTORY_DEFAULT_DAYS = 30;

/**
 * 하루의 밀리초
 * @constant {number}
 */
const MS_PER_DAY = 1000 * 60 * 60 * 24;

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * 일지 페이지 전역 상태 객체
 * @type {Object}
 * @property {Date} currentDate - 현재 선택된 날짜
 * @property {number|null} currentJournalId - 현재 편집 중인 일지 ID
 * @property {Array} emotions - 감정 상태 목록
 * @property {string[]} tags - 현재 일지의 태그 배열
 * @property {Date} calendarDate - 달력에서 표시 중인 월
 * @property {Set<string>} journalDatesInMonth - 해당 월의 일지가 있는 날짜들
 */
const journalState = {
    currentDate: new Date(),
    currentJournalId: null,
    emotions: [],
    tags: [],
    calendarDate: new Date(),
    journalDatesInMonth: new Set()
};

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * 페이지 로드 시 초기화
 */
$(document).ready(function() {
    initEmotions();
    initEventListeners();
    loadJournalForDate(journalState.currentDate);
});

/**
 * 감정 상태 목록을 API에서 로드합니다.
 * 실패 시 기본 감정 목록을 사용합니다.
 * @returns {void}
 */
function initEmotions() {
    fetchWithAuth('/api/journal/emotions')
        .then(response => response.json())
        .then(data => {
            journalState.emotions = data;
            renderEmotionButtons();
        })
        .catch(error => {
            console.error('감정 목록 로드 실패:', error);
            journalState.emotions = DEFAULT_EMOTIONS;
            renderEmotionButtons();
        });
}

/**
 * 모든 이벤트 리스너를 초기화합니다.
 * @returns {void}
 */
function initEventListeners() {
    // 날짜 네비게이션
    $('#prev-day').click(() => changeDate(-1));
    $('#next-day').click(() => changeDate(1));
    $('#today-btn').click(() => goToToday());

    // 감정 선택
    $(document).on('click', '#morning-emotion .emotion-btn', function() {
        $('#morning-emotion .emotion-btn').removeClass('selected');
        $(this).addClass('selected');
    });

    $(document).on('click', '#evening-emotion .emotion-btn', function() {
        $('#evening-emotion .emotion-btn').removeClass('selected');
        $(this).addClass('selected');
    });

    // 점수 선택
    $(document).on('click', '#focus-score .score-btn', function() {
        $('#focus-score .score-btn').removeClass('selected');
        $(this).addClass('selected');
    });

    $(document).on('click', '#discipline-score .score-btn', function() {
        $('#discipline-score .score-btn').removeClass('selected');
        $(this).addClass('selected');
    });

    // 태그 입력
    $('#tag-input').on('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            const value = $(this).val().trim();
            if (value && !journalState.tags.includes(value)) {
                journalState.tags.push(value);
                renderTags();
            }
            $(this).val('');
        }
    });

    // 저장
    $('#save-btn').click(saveJournal);

    // 삭제
    $('#delete-btn').click(deleteJournal);

    // 달력 네비게이션
    $('#cal-prev-month').click(() => changeCalendarMonth(-1));
    $('#cal-next-month').click(() => changeCalendarMonth(1));

    // 탭 변경
    $('button[data-bs-toggle="tab"]').on('shown.bs.tab', function(e) {
        const target = $(e.target).attr('data-bs-target');
        if (target === '#tab-stats') {
            loadStatistics();
        } else if (target === '#tab-history') {
            loadHistory();
        }
    });

    // 달력 모달 열릴 때
    $('#calendarModal').on('show.bs.modal', function() {
        journalState.calendarDate = new Date(journalState.currentDate);
        renderCalendar();
    });
}

// ============================================================================
// EMOTION RENDERING
// ============================================================================

/**
 * 감정 버튼을 렌더링합니다.
 * 오전/오후 감정 선택 영역에 버튼을 생성합니다.
 * @returns {void}
 */
function renderEmotionButtons() {
    const morningContainer = $('#morning-emotion');
    const eveningContainer = $('#evening-emotion');

    morningContainer.empty();
    eveningContainer.empty();

    journalState.emotions.forEach(e => {
        const btn = `<button class="emotion-btn" data-value="${escapeHtml(e.value)}" title="${escapeHtml(e.description)}">${escapeHtml(e.label)}</button>`;
        morningContainer.append(btn);
        eveningContainer.append($(btn).clone());
    });
}

// ============================================================================
// DATE NAVIGATION
// ============================================================================

/**
 * 현재 날짜를 변경합니다.
 * @param {number} delta - 변경할 일수 (-1: 이전, 1: 다음)
 * @returns {void}
 */
function changeDate(delta) {
    journalState.currentDate.setDate(journalState.currentDate.getDate() + delta);
    loadJournalForDate(journalState.currentDate);
}

/**
 * 오늘 날짜로 이동합니다.
 * @returns {void}
 */
function goToToday() {
    journalState.currentDate = new Date();
    loadJournalForDate(journalState.currentDate);
}

/**
 * 특정 날짜로 이동합니다.
 * @param {string} dateStr - YYYY-MM-DD 형식의 날짜 문자열
 * @returns {void}
 */
function goToDate(dateStr) {
    journalState.currentDate = new Date(dateStr);
    loadJournalForDate(journalState.currentDate);

    // 탭 변경
    $('button[data-bs-target="#tab-write"]').tab('show');

    // 모달 닫기
    $('#calendarModal').modal('hide');
}

// ============================================================================
// JOURNAL CRUD OPERATIONS
// ============================================================================

/**
 * 특정 날짜의 일지를 로드합니다.
 * @param {Date} date - 로드할 날짜
 * @returns {void}
 */
function loadJournalForDate(date) {
    const dateStr = formatDateForApi(date);
    updateDateDisplay(date);

    fetchWithAuth(`/api/journal/date/${dateStr}`)
        .then(response => response.json())
        .then(data => {
            populateForm(data);
        })
        .catch(error => {
            console.error('일지 로드 실패:', error);
            clearForm();
        });
}

/**
 * 날짜 표시 영역을 업데이트합니다.
 * @param {Date} date - 표시할 날짜
 * @returns {void}
 */
function updateDateDisplay(date) {
    const dateStr = formatDateForApi(date);
    const dayOfWeek = DAY_NAMES[date.getDay()];

    $('#current-date').text(dateStr);
    $('#day-of-week').text(`(${dayOfWeek})`);
}

/**
 * 폼에 일지 데이터를 채웁니다.
 * @param {Object} data - 일지 데이터
 * @returns {void}
 */
function populateForm(data) {
    journalState.currentJournalId = data.id || null;

    // 텍스트 필드
    $('#market-overview').val(data.marketOverview || '');
    $('#trading-plan').val(data.tradingPlan || '');
    $('#execution-review').val(data.executionReview || '');
    $('#lessons-learned').val(data.lessonsLearned || '');
    $('#tomorrow-plan').val(data.tomorrowPlan || '');

    // 감정 선택
    $('#morning-emotion .emotion-btn').removeClass('selected');
    if (data.morningEmotion) {
        $(`#morning-emotion .emotion-btn[data-value="${data.morningEmotion}"]`).addClass('selected');
    }

    $('#evening-emotion .emotion-btn').removeClass('selected');
    if (data.eveningEmotion) {
        $(`#evening-emotion .emotion-btn[data-value="${data.eveningEmotion}"]`).addClass('selected');
    }

    // 점수 선택
    $('#focus-score .score-btn').removeClass('selected');
    if (data.focusScore) {
        $(`#focus-score .score-btn[data-score="${data.focusScore}"]`).addClass('selected');
    }

    $('#discipline-score .score-btn').removeClass('selected');
    if (data.disciplineScore) {
        $(`#discipline-score .score-btn[data-score="${data.disciplineScore}"]`).addClass('selected');
    }

    // 태그
    journalState.tags = data.tags ? data.tags.split(',').map(t => t.trim()).filter(t => t) : [];
    renderTags();

    // 거래 요약
    $('#trade-count').text(data.tradeSummaryCount || 0);
    const profit = data.tradeSummaryProfit || 0;
    $('#trade-profit')
        .text(formatJournalCurrency(profit))
        .removeClass('positive negative')
        .addClass(profit >= 0 ? 'positive' : 'negative');
    $('#trade-win-rate').text((data.tradeSummaryWinRate || 0) + '%');

    // 삭제 버튼
    $('#delete-btn').toggle(journalState.currentJournalId != null);
}

/**
 * 폼을 초기화합니다.
 * @returns {void}
 */
function clearForm() {
    journalState.currentJournalId = null;
    $('#market-overview').val('');
    $('#trading-plan').val('');
    $('#execution-review').val('');
    $('#lessons-learned').val('');
    $('#tomorrow-plan').val('');
    $('#morning-emotion .emotion-btn').removeClass('selected');
    $('#evening-emotion .emotion-btn').removeClass('selected');
    $('#focus-score .score-btn').removeClass('selected');
    $('#discipline-score .score-btn').removeClass('selected');
    journalState.tags = [];
    renderTags();
    $('#trade-count').text('-');
    $('#trade-profit').text('-').removeClass('positive negative');
    $('#trade-win-rate').text('-');
    $('#delete-btn').hide();
}

/**
 * 일지를 저장합니다.
 * 새 일지인 경우 POST, 기존 일지인 경우 PUT 요청을 보냅니다.
 * @returns {void}
 */
function saveJournal() {
    const dateStr = formatDateForApi(journalState.currentDate);

    const data = {
        journalDate: dateStr,
        marketOverview: $('#market-overview').val(),
        tradingPlan: $('#trading-plan').val(),
        executionReview: $('#execution-review').val(),
        lessonsLearned: $('#lessons-learned').val(),
        tomorrowPlan: $('#tomorrow-plan').val(),
        morningEmotion: $('#morning-emotion .emotion-btn.selected').data('value') || null,
        eveningEmotion: $('#evening-emotion .emotion-btn.selected').data('value') || null,
        focusScore: parseInt($('#focus-score .score-btn.selected').data('score')) || null,
        disciplineScore: parseInt($('#discipline-score .score-btn.selected').data('score')) || null,
        tags: journalState.tags.join(',')
    };

    const url = journalState.currentJournalId ? `/api/journal/${journalState.currentJournalId}` : '/api/journal';
    const method = journalState.currentJournalId ? 'PUT' : 'POST';

    fetchWithAuth(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    .then(response => {
        if (!response.ok) throw new Error('저장 실패');
        return response.json();
    })
    .then(result => {
        journalState.currentJournalId = result.id;
        $('#delete-btn').show();
        showToast('일지가 저장되었습니다.', 'success');
    })
    .catch(error => {
        console.error('저장 실패:', error);
        showToast('저장에 실패했습니다.', 'error');
    });
}

/**
 * 일지를 삭제합니다.
 * @returns {void}
 */
function deleteJournal() {
    if (!journalState.currentJournalId) return;

    if (!confirm('정말 이 일지를 삭제하시겠습니까?')) return;

    fetchWithAuth(`/api/journal/${journalState.currentJournalId}`, { method: 'DELETE' })
        .then(response => {
            if (!response.ok) throw new Error('삭제 실패');
            clearForm();
            showToast('일지가 삭제되었습니다.', 'success');
        })
        .catch(error => {
            console.error('삭제 실패:', error);
            showToast('삭제에 실패했습니다.', 'error');
        });
}

// ============================================================================
// TAG MANAGEMENT
// ============================================================================

/**
 * 태그를 렌더링합니다.
 * @returns {void}
 */
function renderTags() {
    const container = $('#tags-container');
    container.find('.tag').remove();

    journalState.tags.forEach((tag, index) => {
        const tagEl = $(`<span class="tag">${escapeHtml(tag)}<span class="remove" data-index="${index}">&times;</span></span>`);
        container.prepend(tagEl);
    });

    // 태그 삭제 이벤트
    container.find('.remove').click(function() {
        const index = $(this).data('index');
        journalState.tags.splice(index, 1);
        renderTags();
    });
}

// ============================================================================
// STATISTICS
// ============================================================================

/**
 * 통계를 로드합니다.
 * @returns {void}
 */
function loadStatistics() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - STATISTICS_DEFAULT_MONTHS);

    fetchWithAuth(`/api/journal/statistics?startDate=${formatDateForApi(startDate)}&endDate=${formatDateForApi(endDate)}`)
        .then(response => response.json())
        .then(data => {
            renderStatistics(data);
        })
        .catch(error => {
            console.error('통계 로드 실패:', error);
        });
}

/**
 * 통계를 렌더링합니다.
 * @param {Object} data - 통계 데이터
 * @returns {void}
 */
function renderStatistics(data) {
    // 요약 카드
    $('#stats-total-journals').text(data.totalJournals || 0);
    $('#stats-avg-focus').text(data.avgFocusScore ? data.avgFocusScore.toFixed(1) : '-');
    $('#stats-avg-discipline').text(data.avgDisciplineScore ? data.avgDisciplineScore.toFixed(1) : '-');

    // 연속 작성일 계산
    const streak = calculateStreak(data.emotionTrends);
    $('#stats-streak').text(streak + '일');

    // 오전 감정 분포
    renderEmotionStats('#morning-emotion-stats', data.morningEmotionCounts);

    // 오후 감정 분포
    renderEmotionStats('#evening-emotion-stats', data.eveningEmotionCounts);

    // 최근 교훈
    renderRecentLessons(data.recentLessons);
}

/**
 * 연속 작성일을 계산합니다.
 * @param {Array<{date: string}>|null} emotionTrends - 감정 트렌드 데이터
 * @returns {number} 연속 작성일 수
 */
function calculateStreak(emotionTrends) {
    if (!emotionTrends || emotionTrends.length === 0) {
        return 0;
    }

    const sortedDates = emotionTrends
        .map(t => new Date(t.date))
        .sort((a, b) => b - a);

    const today = normalizeDate(new Date());

    if (sortedDates.length === 0) {
        return 0;
    }

    const latestDate = normalizeDate(sortedDates[0]);
    const diffDays = Math.floor((today - latestDate) / MS_PER_DAY);

    if (diffDays > 1) {
        return 0;
    }

    let streak = 1;
    for (let i = 1; i < sortedDates.length; i++) {
        const prev = normalizeDate(sortedDates[i - 1]);
        const curr = normalizeDate(sortedDates[i]);
        const diff = Math.floor((prev - curr) / MS_PER_DAY);

        if (diff === 1) {
            streak++;
        } else {
            break;
        }
    }

    return streak;
}

/**
 * 감정 분포를 렌더링합니다.
 * @param {string} selector - 컨테이너 선택자
 * @param {Object|null} emotionCounts - 감정별 카운트 객체
 * @returns {void}
 */
function renderEmotionStats(selector, emotionCounts) {
    const container = $(selector);
    container.empty();

    if (!emotionCounts || Object.keys(emotionCounts).length === 0) {
        container.html('<div class="text-center text-muted py-4">데이터 없음</div>');
        return;
    }

    const total = Object.values(emotionCounts).reduce((a, b) => a + b, 0);

    Object.entries(emotionCounts)
        .sort((a, b) => b[1] - a[1])
        .forEach(([emotion, count]) => {
            const percent = ((count / total) * 100).toFixed(1);
            const emotionData = journalState.emotions.find(e => e.value === emotion) || { label: emotion };

            container.append(`
                <div class="d-flex align-items-center mb-2">
                    <span class="me-2" style="width: 60px;">${escapeHtml(emotionData.label)}</span>
                    <div class="progress flex-grow-1" style="height: 20px;">
                        <div class="progress-bar" role="progressbar" style="width: ${percent}%; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                            ${percent}%
                        </div>
                    </div>
                    <span class="ms-2 text-muted">${count}회</span>
                </div>
            `);
        });
}

/**
 * 최근 교훈을 렌더링합니다.
 * @param {string[]|null} lessons - 교훈 배열
 * @returns {void}
 */
function renderRecentLessons(lessons) {
    const container = $('#recent-lessons');
    container.empty();

    if (!lessons || lessons.length === 0) {
        container.html('<div class="text-center text-muted py-4">기록된 교훈이 없습니다.</div>');
        return;
    }

    lessons.forEach((lesson) => {
        container.append(`
            <div class="p-3 mb-2" style="background: #fffdf5; border-left: 4px solid #f5af19; border-radius: 0 8px 8px 0;">
                <i class="bi bi-lightbulb text-warning me-2"></i>
                ${escapeHtml(lesson)}
            </div>
        `);
    });
}

// ============================================================================
// HISTORY
// ============================================================================

/**
 * 히스토리를 로드합니다.
 * @returns {void}
 */
function loadHistory() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - HISTORY_DEFAULT_DAYS);

    fetchWithAuth(`/api/journal/list?startDate=${formatDateForApi(startDate)}&endDate=${formatDateForApi(endDate)}`)
        .then(response => response.json())
        .then(data => {
            renderHistory(data);
        })
        .catch(error => {
            console.error('히스토리 로드 실패:', error);
        });
}

/**
 * 히스토리를 렌더링합니다.
 * @param {Array} journals - 일지 목록
 * @returns {void}
 */
function renderHistory(journals) {
    const tbody = $('#journal-history');
    tbody.empty();

    if (!journals || journals.length === 0) {
        tbody.html('<tr><td colspan="8" class="text-center text-muted py-4">최근 일지가 없습니다.</td></tr>');
        return;
    }

    journals.forEach(j => {
        const morningLabel = journalState.emotions.find(e => e.value === j.morningEmotion)?.label || '-';
        const eveningLabel = journalState.emotions.find(e => e.value === j.eveningEmotion)?.label || '-';
        const profit = j.tradeSummaryProfit || 0;
        const profitClass = profit >= 0 ? 'positive' : 'negative';

        tbody.append(`
            <tr style="cursor: pointer;" onclick="goToDate('${escapeHtml(j.journalDate)}')">
                <td>${escapeHtml(j.journalDate)} (${escapeHtml(j.dayOfWeek)})</td>
                <td>${escapeHtml(morningLabel)}</td>
                <td>${escapeHtml(eveningLabel)}</td>
                <td>${j.focusScore || '-'}</td>
                <td>${j.disciplineScore || '-'}</td>
                <td>${j.tradeSummaryCount || 0}건</td>
                <td class="${profitClass}">${formatJournalCurrency(profit)}</td>
                <td>${j.hasContent ? '<i class="bi bi-check-circle text-success"></i>' : '<i class="bi bi-circle text-muted"></i>'}</td>
            </tr>
        `);
    });
}

// ============================================================================
// CALENDAR
// ============================================================================

/**
 * 달력 월을 변경합니다.
 * @param {number} delta - 변경할 개월 수 (-1: 이전, 1: 다음)
 * @returns {void}
 */
function changeCalendarMonth(delta) {
    journalState.calendarDate.setMonth(journalState.calendarDate.getMonth() + delta);
    renderCalendar();
}

/**
 * 달력을 렌더링합니다.
 * @returns {void}
 */
function renderCalendar() {
    const year = journalState.calendarDate.getFullYear();
    const month = journalState.calendarDate.getMonth();

    $('#cal-month-year').text(`${year}년 ${month + 1}월`);

    // 해당 월의 일지 목록 로드
    const startDate = new Date(year, month, 1);
    const endDate = new Date(year, month + 1, 0);

    fetchWithAuth(`/api/journal/list?startDate=${formatDateForApi(startDate)}&endDate=${formatDateForApi(endDate)}`)
        .then(response => response.json())
        .then(journals => {
            journalState.journalDatesInMonth = new Set(journals.map(j => j.journalDate));
            renderCalendarDays(year, month);
        })
        .catch(() => {
            journalState.journalDatesInMonth = new Set();
            renderCalendarDays(year, month);
        });
}

/**
 * 달력의 날짜들을 렌더링합니다.
 * @param {number} year - 연도
 * @param {number} month - 월 (0-11)
 * @returns {void}
 */
function renderCalendarDays(year, month) {
    const container = $('#calendar-days');
    container.empty();

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);

    // 이전 달 날짜
    const startDayOfWeek = firstDay.getDay();
    const prevMonthLastDay = new Date(year, month, 0).getDate();

    for (let i = startDayOfWeek - 1; i >= 0; i--) {
        const day = prevMonthLastDay - i;
        const date = new Date(year, month - 1, day);
        container.append(createCalendarDay(date, true));
    }

    // 현재 달 날짜
    for (let day = 1; day <= lastDay.getDate(); day++) {
        const date = new Date(year, month, day);
        container.append(createCalendarDay(date, false));
    }

    // 다음 달 날짜
    const remainingDays = CALENDAR_TOTAL_DAYS - container.children().length;
    for (let day = 1; day <= remainingDays; day++) {
        const date = new Date(year, month + 1, day);
        container.append(createCalendarDay(date, true));
    }
}

/**
 * 달력의 날짜 요소를 생성합니다.
 * @param {Date} date - 날짜
 * @param {boolean} isOtherMonth - 다른 달 여부
 * @returns {string} HTML 문자열
 */
function createCalendarDay(date, isOtherMonth) {
    const dateStr = formatDateForApi(date);
    const today = normalizeDate(new Date());
    const normalizedDate = normalizeDate(new Date(date));
    const normalizedCurrentDate = normalizeDate(new Date(journalState.currentDate));

    const isToday = normalizedDate.getTime() === today.getTime();
    const isSelected = normalizedDate.getTime() === normalizedCurrentDate.getTime();
    const hasJournal = journalState.journalDatesInMonth.has(dateStr);

    let classes = 'calendar-day';
    if (isOtherMonth) classes += ' other-month';
    if (isToday) classes += ' today';
    if (isSelected) classes += ' selected';
    if (hasJournal) classes += ' has-journal';

    return `<div class="${classes}" onclick="goToDate('${dateStr}')">${date.getDate()}</div>`;
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * 날짜의 시간 부분을 제거하여 정규화합니다.
 * @param {Date} date - 정규화할 날짜
 * @returns {Date} 시간이 00:00:00으로 설정된 날짜
 */
function normalizeDate(date) {
    const normalized = new Date(date);
    normalized.setHours(0, 0, 0, 0);
    return normalized;
}

/**
 * 통화 포맷 (일지 페이지 전용)
 * @param {number|null|undefined} value - 포맷할 값
 * @returns {string} 포맷된 통화 문자열
 */
function formatJournalCurrency(value) {
    if (value === null || value === undefined) return '-';
    const num = Number(value);
    const prefix = num >= 0 ? '+' : '';
    return prefix + num.toLocaleString('ko-KR') + '원';
}

/**
 * 토스트 메시지를 표시합니다.
 * @param {string} message - 표시할 메시지
 * @param {('success'|'error')} type - 메시지 타입
 * @returns {void}
 */
function showToast(message, type) {
    const toast = $('#notification-toast');
    const toastBody = $('#toast-message');

    toastBody.text(message);

    if (type === 'success') {
        toast.removeClass('bg-danger').addClass('bg-success text-white');
    } else if (type === 'error') {
        toast.removeClass('bg-success').addClass('bg-danger text-white');
    }

    const bsToast = new bootstrap.Toast(toast[0]);
    bsToast.show();
}

/**
 * 인증 토큰을 포함하여 fetch 요청을 보냅니다.
 * @param {string} url - 요청 URL
 * @param {Object} [options={}] - fetch 옵션
 * @returns {Promise<Response>} fetch 응답
 */
function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('token');
    const headers = options.headers || {};

    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }

    return fetch(url, { ...options, headers });
}
