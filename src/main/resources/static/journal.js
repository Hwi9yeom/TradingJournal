/**
 * Trading Journal - 일지 페이지 JavaScript
 */

// 전역 변수
let currentDate = new Date();
let currentJournalId = null;
let emotions = [];
let tags = [];
let calendarDate = new Date();
let journalDatesInMonth = new Set();

// 페이지 로드 시 초기화
$(document).ready(function() {
    initEmotions();
    initEventListeners();
    loadJournalForDate(currentDate);
});

/**
 * 감정 상태 목록 로드
 */
function initEmotions() {
    fetchWithAuth('/api/journal/emotions')
        .then(response => response.json())
        .then(data => {
            emotions = data;
            renderEmotionButtons();
        })
        .catch(error => {
            console.error('감정 목록 로드 실패:', error);
            // 기본 감정 사용
            emotions = [
                { value: 'CONFIDENT', label: '자신감', description: '확신을 가지고 거래' },
                { value: 'CALM', label: '침착', description: '안정적인 상태' },
                { value: 'NEUTRAL', label: '보통', description: '특별한 감정 없음' },
                { value: 'ANXIOUS', label: '불안', description: '걱정되는 상태' },
                { value: 'FEARFUL', label: '두려움', description: '손실 두려움' },
                { value: 'GREEDY', label: '탐욕', description: '과욕 상태' },
                { value: 'FRUSTRATED', label: '좌절', description: '실망한 상태' },
                { value: 'EXCITED', label: '흥분', description: '과도하게 흥분' }
            ];
            renderEmotionButtons();
        });
}

/**
 * 감정 버튼 렌더링
 */
function renderEmotionButtons() {
    const morningContainer = $('#morning-emotion');
    const eveningContainer = $('#evening-emotion');

    morningContainer.empty();
    eveningContainer.empty();

    emotions.forEach(e => {
        const btn = `<button class="emotion-btn" data-value="${e.value}" title="${e.description}">${e.label}</button>`;
        morningContainer.append(btn);
        eveningContainer.append($(btn).clone());
    });
}

/**
 * 이벤트 리스너 초기화
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
            if (value && !tags.includes(value)) {
                tags.push(value);
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
        calendarDate = new Date(currentDate);
        renderCalendar();
    });
}

/**
 * 날짜 변경
 */
function changeDate(delta) {
    currentDate.setDate(currentDate.getDate() + delta);
    loadJournalForDate(currentDate);
}

/**
 * 오늘로 이동
 */
function goToToday() {
    currentDate = new Date();
    loadJournalForDate(currentDate);
}

/**
 * 특정 날짜의 일지 로드
 */
function loadJournalForDate(date) {
    const dateStr = formatDate(date);
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
 * 날짜 표시 업데이트
 */
function updateDateDisplay(date) {
    const dateStr = formatDate(date);
    const dayNames = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
    const dayOfWeek = dayNames[date.getDay()];

    $('#current-date').text(dateStr);
    $('#day-of-week').text(`(${dayOfWeek})`);
}

/**
 * 폼에 데이터 채우기
 */
function populateForm(data) {
    currentJournalId = data.id || null;

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
    tags = data.tags ? data.tags.split(',').map(t => t.trim()).filter(t => t) : [];
    renderTags();

    // 거래 요약
    $('#trade-count').text(data.tradeSummaryCount || 0);
    const profit = data.tradeSummaryProfit || 0;
    $('#trade-profit')
        .text(formatCurrency(profit))
        .removeClass('positive negative')
        .addClass(profit >= 0 ? 'positive' : 'negative');
    $('#trade-win-rate').text((data.tradeSummaryWinRate || 0) + '%');

    // 삭제 버튼
    $('#delete-btn').toggle(currentJournalId != null);
}

/**
 * 폼 초기화
 */
function clearForm() {
    currentJournalId = null;
    $('#market-overview').val('');
    $('#trading-plan').val('');
    $('#execution-review').val('');
    $('#lessons-learned').val('');
    $('#tomorrow-plan').val('');
    $('#morning-emotion .emotion-btn').removeClass('selected');
    $('#evening-emotion .emotion-btn').removeClass('selected');
    $('#focus-score .score-btn').removeClass('selected');
    $('#discipline-score .score-btn').removeClass('selected');
    tags = [];
    renderTags();
    $('#trade-count').text('-');
    $('#trade-profit').text('-').removeClass('positive negative');
    $('#trade-win-rate').text('-');
    $('#delete-btn').hide();
}

/**
 * 태그 렌더링
 */
function renderTags() {
    const container = $('#tags-container');
    container.find('.tag').remove();

    tags.forEach((tag, index) => {
        const tagEl = $(`<span class="tag">${tag}<span class="remove" data-index="${index}">&times;</span></span>`);
        container.prepend(tagEl);
    });

    // 태그 삭제 이벤트
    container.find('.remove').click(function() {
        const index = $(this).data('index');
        tags.splice(index, 1);
        renderTags();
    });
}

/**
 * 일지 저장
 */
function saveJournal() {
    const dateStr = formatDate(currentDate);

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
        tags: tags.join(',')
    };

    const url = currentJournalId ? `/api/journal/${currentJournalId}` : '/api/journal';
    const method = currentJournalId ? 'PUT' : 'POST';

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
        currentJournalId = result.id;
        $('#delete-btn').show();
        showToast('일지가 저장되었습니다.', 'success');
    })
    .catch(error => {
        console.error('저장 실패:', error);
        showToast('저장에 실패했습니다.', 'error');
    });
}

/**
 * 일지 삭제
 */
function deleteJournal() {
    if (!currentJournalId) return;

    if (!confirm('정말 이 일지를 삭제하시겠습니까?')) return;

    fetchWithAuth(`/api/journal/${currentJournalId}`, { method: 'DELETE' })
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

/**
 * 통계 로드
 */
function loadStatistics() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 3);

    fetchWithAuth(`/api/journal/statistics?startDate=${formatDate(startDate)}&endDate=${formatDate(endDate)}`)
        .then(response => response.json())
        .then(data => {
            renderStatistics(data);
        })
        .catch(error => {
            console.error('통계 로드 실패:', error);
        });
}

/**
 * 통계 렌더링
 */
function renderStatistics(data) {
    // 요약 카드
    $('#stats-total-journals').text(data.totalJournals || 0);
    $('#stats-avg-focus').text(data.avgFocusScore ? data.avgFocusScore.toFixed(1) : '-');
    $('#stats-avg-discipline').text(data.avgDisciplineScore ? data.avgDisciplineScore.toFixed(1) : '-');

    // 연속 작성일 계산
    let streak = 0;
    if (data.emotionTrends && data.emotionTrends.length > 0) {
        const sortedDates = data.emotionTrends
            .map(t => new Date(t.date))
            .sort((a, b) => b - a);

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (sortedDates.length > 0) {
            const latestDate = sortedDates[0];
            latestDate.setHours(0, 0, 0, 0);

            const diffDays = Math.floor((today - latestDate) / (1000 * 60 * 60 * 24));
            if (diffDays <= 1) {
                streak = 1;
                for (let i = 1; i < sortedDates.length; i++) {
                    const prev = sortedDates[i - 1];
                    const curr = sortedDates[i];
                    prev.setHours(0, 0, 0, 0);
                    curr.setHours(0, 0, 0, 0);

                    const diff = Math.floor((prev - curr) / (1000 * 60 * 60 * 24));
                    if (diff === 1) {
                        streak++;
                    } else {
                        break;
                    }
                }
            }
        }
    }
    $('#stats-streak').text(streak + '일');

    // 오전 감정 분포
    renderEmotionStats('#morning-emotion-stats', data.morningEmotionCounts);

    // 오후 감정 분포
    renderEmotionStats('#evening-emotion-stats', data.eveningEmotionCounts);

    // 최근 교훈
    renderRecentLessons(data.recentLessons);
}

/**
 * 감정 분포 렌더링
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
            const emotionData = emotions.find(e => e.value === emotion) || { label: emotion };

            container.append(`
                <div class="d-flex align-items-center mb-2">
                    <span class="me-2" style="width: 60px;">${emotionData.label}</span>
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
 * 최근 교훈 렌더링
 */
function renderRecentLessons(lessons) {
    const container = $('#recent-lessons');
    container.empty();

    if (!lessons || lessons.length === 0) {
        container.html('<div class="text-center text-muted py-4">기록된 교훈이 없습니다.</div>');
        return;
    }

    lessons.forEach((lesson, index) => {
        container.append(`
            <div class="p-3 mb-2" style="background: #fffdf5; border-left: 4px solid #f5af19; border-radius: 0 8px 8px 0;">
                <i class="bi bi-lightbulb text-warning me-2"></i>
                ${lesson}
            </div>
        `);
    });
}

/**
 * 히스토리 로드
 */
function loadHistory() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 30);

    fetchWithAuth(`/api/journal/list?startDate=${formatDate(startDate)}&endDate=${formatDate(endDate)}`)
        .then(response => response.json())
        .then(data => {
            renderHistory(data);
        })
        .catch(error => {
            console.error('히스토리 로드 실패:', error);
        });
}

/**
 * 히스토리 렌더링
 */
function renderHistory(journals) {
    const tbody = $('#journal-history');
    tbody.empty();

    if (!journals || journals.length === 0) {
        tbody.html('<tr><td colspan="8" class="text-center text-muted py-4">최근 일지가 없습니다.</td></tr>');
        return;
    }

    journals.forEach(j => {
        const morningLabel = emotions.find(e => e.value === j.morningEmotion)?.label || '-';
        const eveningLabel = emotions.find(e => e.value === j.eveningEmotion)?.label || '-';
        const profit = j.tradeSummaryProfit || 0;
        const profitClass = profit >= 0 ? 'positive' : 'negative';

        tbody.append(`
            <tr style="cursor: pointer;" onclick="goToDate('${j.journalDate}')">
                <td>${j.journalDate} (${j.dayOfWeek})</td>
                <td>${morningLabel}</td>
                <td>${eveningLabel}</td>
                <td>${j.focusScore || '-'}</td>
                <td>${j.disciplineScore || '-'}</td>
                <td>${j.tradeSummaryCount || 0}건</td>
                <td class="${profitClass}">${formatCurrency(profit)}</td>
                <td>${j.hasContent ? '<i class="bi bi-check-circle text-success"></i>' : '<i class="bi bi-circle text-muted"></i>'}</td>
            </tr>
        `);
    });
}

/**
 * 특정 날짜로 이동
 */
function goToDate(dateStr) {
    currentDate = new Date(dateStr);
    loadJournalForDate(currentDate);

    // 탭 변경
    $('button[data-bs-target="#tab-write"]').tab('show');

    // 모달 닫기
    $('#calendarModal').modal('hide');
}

/**
 * 달력 월 변경
 */
function changeCalendarMonth(delta) {
    calendarDate.setMonth(calendarDate.getMonth() + delta);
    renderCalendar();
}

/**
 * 달력 렌더링
 */
function renderCalendar() {
    const year = calendarDate.getFullYear();
    const month = calendarDate.getMonth();

    $('#cal-month-year').text(`${year}년 ${month + 1}월`);

    // 해당 월의 일지 목록 로드
    const startDate = new Date(year, month, 1);
    const endDate = new Date(year, month + 1, 0);

    fetchWithAuth(`/api/journal/list?startDate=${formatDate(startDate)}&endDate=${formatDate(endDate)}`)
        .then(response => response.json())
        .then(journals => {
            journalDatesInMonth = new Set(journals.map(j => j.journalDate));
            renderCalendarDays(year, month);
        })
        .catch(() => {
            journalDatesInMonth = new Set();
            renderCalendarDays(year, month);
        });
}

/**
 * 달력 날짜 렌더링
 */
function renderCalendarDays(year, month) {
    const container = $('#calendar-days');
    container.empty();

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

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
    const remainingDays = 42 - container.children().length;
    for (let day = 1; day <= remainingDays; day++) {
        const date = new Date(year, month + 1, day);
        container.append(createCalendarDay(date, true));
    }
}

/**
 * 달력 날짜 요소 생성
 */
function createCalendarDay(date, isOtherMonth) {
    const dateStr = formatDate(date);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    date.setHours(0, 0, 0, 0);

    const isToday = date.getTime() === today.getTime();
    const isSelected = date.getTime() === currentDate.getTime();
    const hasJournal = journalDatesInMonth.has(dateStr);

    let classes = 'calendar-day';
    if (isOtherMonth) classes += ' other-month';
    if (isToday) classes += ' today';
    if (isSelected) classes += ' selected';
    if (hasJournal) classes += ' has-journal';

    return `<div class="${classes}" onclick="goToDate('${dateStr}')">${date.getDate()}</div>`;
}

/**
 * 날짜 포맷
 */
function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * 통화 포맷
 */
function formatCurrency(value) {
    if (value === null || value === undefined) return '-';
    const num = Number(value);
    const prefix = num >= 0 ? '+' : '';
    return prefix + num.toLocaleString('ko-KR') + '원';
}

/**
 * 토스트 표시
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
 * 인증 포함 fetch
 */
function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('token');
    const headers = options.headers || {};

    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }

    return fetch(url, { ...options, headers });
}
