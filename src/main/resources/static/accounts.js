const API_BASE_URL = '/api';

$(document).ready(function() {
    loadAccounts();
    loadAccountsSummary();
    setupAccountEventHandlers();
});

function setupAccountEventHandlers() {
    $('#account-form').on('submit', function(e) {
        e.preventDefault();
        createAccount();
    });
}

// 계좌 목록 조회
function loadAccounts() {
    $.ajax({
        url: `${API_BASE_URL}/accounts`,
        method: 'GET',
        success: function(accounts) {
            updateAccountList(accounts);
            updateAccountPortfolios(accounts);
            $('#total-accounts').text(accounts.length);
        },
        error: function(xhr) {
            console.error('Failed to load accounts:', xhr);
            showAlert('danger', '계좌 목록을 불러오는데 실패했습니다.');
        }
    });
}

// 계좌 요약 정보 조회
function loadAccountsSummary() {
    $.ajax({
        url: `${API_BASE_URL}/accounts/summary`,
        method: 'GET',
        success: function(summary) {
            $('#total-investment').text(formatCurrency(summary.totalInvestment || 0));
            $('#total-value').text(formatCurrency(summary.totalCurrentValue || 0));
            const profitRate = summary.totalProfitLossPercent || 0;
            const profitRateEl = $('#total-profit-rate');
            profitRateEl.text(formatPercent(profitRate));
            profitRateEl.removeClass('text-success text-danger');
            if (profitRate > 0) {
                profitRateEl.addClass('text-success');
            } else if (profitRate < 0) {
                profitRateEl.addClass('text-danger');
            }
        },
        error: function(xhr) {
            console.error('Failed to load accounts summary:', xhr);
        }
    });
}

// 계좌 목록 테이블 업데이트
function updateAccountList(accounts) {
    const tbody = $('#account-list');
    tbody.empty();

    if (accounts.length === 0) {
        tbody.append('<tr><td colspan="8" class="text-center text-muted">등록된 계좌가 없습니다.</td></tr>');
        return;
    }

    accounts.forEach(account => {
        const row = $('<tr>');
        row.append(`<td><strong>${account.name}</strong></td>`);
        row.append(`<td><span class="badge ${getAccountTypeBadge(account.accountType)}">${getAccountTypeLabel(account.accountType)}</span></td>`);
        row.append(`<td>${account.description || '-'}</td>`);
        row.append(`<td>${formatCurrency(account.totalInvestment || 0)}</td>`);
        row.append(`<td>${formatCurrency(account.totalCurrentValue || 0)}</td>`);

        const profitRate = account.profitLossPercent || 0;
        const profitCell = $('<td>');
        profitCell.text(formatPercent(profitRate));
        profitCell.addClass(profitRate >= 0 ? 'text-success' : 'text-danger');
        row.append(profitCell);

        row.append(`<td>${account.isDefault ? '<i class="bi bi-check-circle-fill text-success"></i>' : ''}</td>`);

        const actions = $('<td>');
        actions.append(`<button class="btn btn-sm btn-outline-primary me-1" onclick="editAccount(${account.id})"><i class="bi bi-pencil"></i></button>`);
        if (!account.isDefault) {
            actions.append(`<button class="btn btn-sm btn-outline-warning me-1" onclick="setDefaultAccount(${account.id})" title="기본 계좌로 설정"><i class="bi bi-star"></i></button>`);
            actions.append(`<button class="btn btn-sm btn-outline-danger" onclick="deleteAccount(${account.id})"><i class="bi bi-trash"></i></button>`);
        }
        row.append(actions);

        tbody.append(row);
    });
}

// 계좌별 포트폴리오 카드 업데이트
function updateAccountPortfolios(accounts) {
    const container = $('#account-portfolios');
    container.empty();

    if (accounts.length === 0) {
        container.append('<div class="col-12 text-center text-muted">계좌가 없습니다.</div>');
        return;
    }

    accounts.forEach(account => {
        const profitRate = account.profitLossPercent || 0;
        const profitColor = profitRate >= 0 ? 'success' : 'danger';

        const card = `
            <div class="col-md-4 mb-3">
                <div class="card h-100">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <span>
                            <strong>${account.name}</strong>
                            ${account.isDefault ? '<i class="bi bi-star-fill text-warning ms-1"></i>' : ''}
                        </span>
                        <span class="badge ${getAccountTypeBadge(account.accountType)}">${getAccountTypeLabel(account.accountType)}</span>
                    </div>
                    <div class="card-body">
                        <div class="row text-center">
                            <div class="col-6">
                                <small class="text-muted">투자금액</small>
                                <p class="mb-0 fw-bold">${formatCurrency(account.totalInvestment || 0)}</p>
                            </div>
                            <div class="col-6">
                                <small class="text-muted">평가금액</small>
                                <p class="mb-0 fw-bold">${formatCurrency(account.totalCurrentValue || 0)}</p>
                            </div>
                        </div>
                        <hr>
                        <div class="text-center">
                            <small class="text-muted">수익률</small>
                            <p class="mb-0 h5 text-${profitColor}">${formatPercent(profitRate)}</p>
                        </div>
                        <hr>
                        <div class="d-flex justify-content-between">
                            <small class="text-muted">보유 종목</small>
                            <small>${account.holdingsCount || 0}개</small>
                        </div>
                    </div>
                    <div class="card-footer">
                        <a href="index.html?accountId=${account.id}" class="btn btn-sm btn-outline-primary w-100">
                            <i class="bi bi-box-arrow-up-right"></i> 거래내역 보기
                        </a>
                    </div>
                </div>
            </div>
        `;
        container.append(card);
    });
}

// 계좌 생성
function createAccount() {
    const account = {
        name: $('#account-name').val(),
        accountType: $('#account-type').val(),
        description: $('#account-description').val() || null
    };

    $.ajax({
        url: `${API_BASE_URL}/accounts`,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(account),
        success: function() {
            $('#account-form')[0].reset();
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 추가되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 추가 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// 계좌 수정 모달 열기
function editAccount(id) {
    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'GET',
        success: function(account) {
            $('#edit-account-id').val(account.id);
            $('#edit-account-name').val(account.name);
            $('#edit-account-type').val(account.accountType);
            $('#edit-account-description').val(account.description || '');
            new bootstrap.Modal($('#editAccountModal')).show();
        },
        error: function(xhr) {
            showAlert('danger', '계좌 정보를 불러오는데 실패했습니다.');
        }
    });
}

// 계좌 수정
function updateAccount() {
    const id = $('#edit-account-id').val();
    const account = {
        name: $('#edit-account-name').val(),
        accountType: $('#edit-account-type').val(),
        description: $('#edit-account-description').val() || null
    };

    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify(account),
        success: function() {
            bootstrap.Modal.getInstance($('#editAccountModal')).hide();
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 수정되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 수정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// 계좌 삭제
function deleteAccount(id) {
    if (!confirm('정말 이 계좌를 삭제하시겠습니까?\n\n주의: 해당 계좌의 거래 내역과 포트폴리오도 모두 삭제됩니다.')) {
        return;
    }

    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}`,
        method: 'DELETE',
        success: function() {
            loadAccounts();
            loadAccountsSummary();
            showAlert('success', '계좌가 삭제되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '계좌 삭제 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// 기본 계좌 설정
function setDefaultAccount(id) {
    $.ajax({
        url: `${API_BASE_URL}/accounts/${id}/default`,
        method: 'PUT',
        success: function() {
            loadAccounts();
            showAlert('success', '기본 계좌가 변경되었습니다.');
        },
        error: function(xhr) {
            showAlert('danger', '기본 계좌 설정 실패: ' + (xhr.responseJSON?.message || '알 수 없는 오류'));
        }
    });
}

// 유틸리티 함수
function getAccountTypeBadge(type) {
    switch(type) {
        case 'ISA': return 'bg-info';
        case 'PENSION': return 'bg-warning text-dark';
        case 'GENERAL': return 'bg-secondary';
        case 'CUSTOM': return 'bg-primary';
        default: return 'bg-secondary';
    }
}

function getAccountTypeLabel(type) {
    switch(type) {
        case 'ISA': return 'ISA';
        case 'PENSION': return '연금';
        case 'GENERAL': return '일반';
        case 'CUSTOM': return '사용자정의';
        default: return type;
    }
}

function showAlert(type, message) {
    const alertHtml = `
        <div class="alert alert-${type} alert-dismissible fade show position-fixed"
             style="top: 80px; right: 20px; z-index: 1050; min-width: 300px;" role="alert">
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    `;
    $('body').append(alertHtml);
    setTimeout(() => {
        $('.alert').alert('close');
    }, 3000);
}

// 포맷 함수 (app.js에 없을 경우 대비)
if (typeof formatCurrency !== 'function') {
    function formatCurrency(value) {
        return '₩' + new Intl.NumberFormat('ko-KR').format(Math.round(value || 0));
    }
}

if (typeof formatPercent !== 'function') {
    function formatPercent(value) {
        return (value || 0).toFixed(2) + '%';
    }
}
