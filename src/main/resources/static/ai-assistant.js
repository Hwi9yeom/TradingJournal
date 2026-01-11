/**
 * AI 트레이딩 어시스턴트 JavaScript
 */

// 전역 변수
let sessionId = generateSessionId();
let lastAnalysisResult = null;
let isStreaming = false;

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    initializeDateInputs();
    checkOllamaHealth();

    // 주기적 상태 확인 (30초마다)
    setInterval(checkOllamaHealth, 30000);
});

/**
 * 세션 ID 생성
 */
function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

/**
 * 날짜 입력 필드 초기화
 */
function initializeDateInputs() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 3);

    document.getElementById('analysisEndDate').value = endDate.toISOString().split('T')[0];
    document.getElementById('analysisStartDate').value = startDate.toISOString().split('T')[0];
}

/**
 * Ollama 서버 상태 확인
 */
async function checkOllamaHealth() {
    try {
        const response = await fetch('/api/ai/health');
        const data = await response.json();

        const statusIndicator = document.getElementById('ollamaStatus');
        const statusText = document.getElementById('ollamaStatusText');

        if (data.status === 'UP') {
            statusIndicator.className = 'status-indicator connected';
            statusText.textContent = 'AI 연결됨';
        } else {
            statusIndicator.className = 'status-indicator disconnected';
            statusText.textContent = 'AI 연결 안됨';
        }
    } catch (error) {
        const statusIndicator = document.getElementById('ollamaStatus');
        const statusText = document.getElementById('ollamaStatusText');
        statusIndicator.className = 'status-indicator disconnected';
        statusText.textContent = 'AI 연결 안됨';
    }
}

/**
 * 성과 분석 실행
 */
async function analyzePerformance() {
    const startDate = document.getElementById('analysisStartDate').value;
    const endDate = document.getElementById('analysisEndDate').value;

    if (!startDate || !endDate) {
        alert('분석 기간을 선택해주세요.');
        return;
    }

    showAnalysisModal('성과 분석');

    try {
        const response = await fetch(`/api/ai/analyze/performance?accountId=1&startDate=${startDate}&endDate=${endDate}`);
        const data = await response.json();

        lastAnalysisResult = data;
        displayAnalysisResult(data, 'performance');
        addInsightCard('performance', '성과 분석', data.summary);
    } catch (error) {
        displayAnalysisError(error.message);
    }
}

/**
 * 리스크 분석 실행
 */
async function analyzeRisk() {
    showAnalysisModal('리스크 분석');

    try {
        const response = await fetch('/api/ai/analyze/risk?accountId=1');
        const data = await response.json();

        lastAnalysisResult = data;
        displayAnalysisResult(data, 'risk');
        addInsightCard('risk', '리스크 분석', data.summary);
    } catch (error) {
        displayAnalysisError(error.message);
    }
}

/**
 * 분석 모달 표시
 */
function showAnalysisModal(title) {
    document.getElementById('analysisModalTitle').innerHTML =
        `<i class="bi bi-graph-up me-2"></i>${title} 결과`;
    document.getElementById('analysisModalBody').innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">분석 중...</span>
            </div>
            <p class="mt-3 text-muted">AI가 분석 중입니다. 잠시만 기다려주세요...</p>
        </div>
    `;

    const modal = new bootstrap.Modal(document.getElementById('analysisModal'));
    modal.show();
}

/**
 * 분석 결과 표시
 */
function displayAnalysisResult(data, type) {
    const iconClass = type === 'performance' ? 'graph-up-arrow text-success' : 'shield-exclamation text-danger';

    // Markdown-like 포맷팅 적용
    let formattedContent = formatAIResponse(data.summary || data.rawResponse);

    document.getElementById('analysisModalBody').innerHTML = `
        <div class="analysis-result">
            <div class="d-flex align-items-center mb-3">
                <i class="bi bi-${iconClass} fs-3 me-2"></i>
                <div>
                    <h6 class="mb-0">${type === 'performance' ? '성과 분석' : '리스크 분석'} 완료</h6>
                    <small class="text-muted">${new Date(data.analyzedAt).toLocaleString()}</small>
                </div>
            </div>
            <div class="bg-light p-3 rounded">
                ${formattedContent}
            </div>
        </div>
    `;
}

/**
 * 분석 에러 표시
 */
function displayAnalysisError(message) {
    document.getElementById('analysisModalBody').innerHTML = `
        <div class="alert alert-danger">
            <i class="bi bi-exclamation-triangle me-2"></i>
            분석 중 오류가 발생했습니다: ${message}
        </div>
        <p class="text-muted">
            Ollama 서버가 실행 중인지 확인해주세요.<br>
            <code>ollama serve</code> 명령으로 서버를 시작할 수 있습니다.
        </p>
    `;
}

/**
 * 인사이트 카드 추가
 */
function addInsightCard(type, title, content) {
    const insightsList = document.getElementById('insightsList');
    const truncatedContent = content.length > 150 ? content.substring(0, 150) + '...' : content;

    // 기존 빈 상태 메시지 제거
    if (insightsList.querySelector('.text-center')) {
        insightsList.innerHTML = '';
    }

    const card = document.createElement('div');
    card.className = `insight-card ${type} p-3 border-bottom`;
    card.innerHTML = `
        <div class="d-flex justify-content-between align-items-start mb-2">
            <h6 class="mb-0">${title}</h6>
            <small class="text-muted">${new Date().toLocaleTimeString()}</small>
        </div>
        <p class="mb-0 small text-muted">${truncatedContent}</p>
    `;

    insightsList.insertBefore(card, insightsList.firstChild);

    // 최대 5개까지만 유지
    while (insightsList.children.length > 5) {
        insightsList.removeChild(insightsList.lastChild);
    }
}

/**
 * 메시지 전송
 */
async function sendMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();

    if (!message || isStreaming) return;

    input.value = '';
    addMessageToChat('user', message);

    isStreaming = true;
    updateSendButton(true);

    // 타이핑 인디케이터 표시
    const typingId = showTypingIndicator();

    try {
        // SSE 스트리밍 사용
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: sessionId,
                message: message
            })
        });

        // 타이핑 인디케이터 제거
        removeTypingIndicator(typingId);

        if (!response.ok) {
            throw new Error('응답 오류');
        }

        // 어시스턴트 메시지 컨테이너 생성
        const assistantMessageId = addMessageToChat('assistant', '', true);

        // 스트리밍 응답 처리
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let fullResponse = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');

            for (const line of lines) {
                if (line.startsWith('data:')) {
                    const data = line.substring(5).trim();
                    if (data) {
                        fullResponse += data;
                        updateAssistantMessage(assistantMessageId, formatAIResponse(fullResponse));
                    }
                }
            }
        }

    } catch (error) {
        removeTypingIndicator(typingId);
        addMessageToChat('assistant', '죄송합니다. 응답 생성 중 오류가 발생했습니다. Ollama 서버가 실행 중인지 확인해주세요.');
    } finally {
        isStreaming = false;
        updateSendButton(false);
    }
}

/**
 * 채팅에 메시지 추가
 */
function addMessageToChat(role, content, streaming = false) {
    const chatMessages = document.getElementById('chatMessages');
    const messageId = 'msg_' + Date.now();

    const messageDiv = document.createElement('div');
    messageDiv.id = messageId;
    messageDiv.className = `message ${role}`;
    messageDiv.innerHTML = content || (streaming ? '<span class="streaming-placeholder"></span>' : '');

    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;

    return messageId;
}

/**
 * 어시스턴트 메시지 업데이트
 */
function updateAssistantMessage(messageId, content) {
    const messageDiv = document.getElementById(messageId);
    if (messageDiv) {
        messageDiv.innerHTML = content;
        const chatMessages = document.getElementById('chatMessages');
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
}

/**
 * 타이핑 인디케이터 표시
 */
function showTypingIndicator() {
    const chatMessages = document.getElementById('chatMessages');
    const indicatorId = 'typing_' + Date.now();

    const indicator = document.createElement('div');
    indicator.id = indicatorId;
    indicator.className = 'message assistant';
    indicator.innerHTML = `
        <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
        </div>
    `;

    chatMessages.appendChild(indicator);
    chatMessages.scrollTop = chatMessages.scrollHeight;

    return indicatorId;
}

/**
 * 타이핑 인디케이터 제거
 */
function removeTypingIndicator(indicatorId) {
    const indicator = document.getElementById(indicatorId);
    if (indicator) {
        indicator.remove();
    }
}

/**
 * 전송 버튼 상태 업데이트
 */
function updateSendButton(isSending) {
    const sendBtn = document.getElementById('sendBtn');
    const chatInput = document.getElementById('chatInput');

    if (isSending) {
        sendBtn.disabled = true;
        sendBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span>';
        chatInput.disabled = true;
    } else {
        sendBtn.disabled = false;
        sendBtn.innerHTML = '<i class="bi bi-send"></i>';
        chatInput.disabled = false;
        chatInput.focus();
    }
}

/**
 * AI 응답 포맷팅
 */
function formatAIResponse(text) {
    if (!text) return '';

    // 줄바꿈 처리
    let formatted = text.replace(/\n/g, '<br>');

    // 굵은 글씨 (**text**)
    formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // 리스트 항목 (- 또는 *)
    formatted = formatted.replace(/^[-*]\s+(.+)/gm, '<li>$1</li>');

    // 숫자 리스트 (1. 2. 등)
    formatted = formatted.replace(/^\d+\.\s+(.+)/gm, '<li>$1</li>');

    // 연속된 li 태그를 ul로 감싸기
    formatted = formatted.replace(/(<li>.*?<\/li>)+/g, '<ul class="mb-2">$&</ul>');

    // 코드 블록
    formatted = formatted.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');

    // 인라인 코드
    formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>');

    return formatted;
}

/**
 * 대화 초기화
 */
function clearChat() {
    if (!confirm('대화 기록을 모두 삭제하시겠습니까?')) return;

    // 서버 세션 초기화
    fetch(`/api/ai/chat/${sessionId}`, { method: 'DELETE' });

    // 새 세션 생성
    sessionId = generateSessionId();

    // 채팅 UI 초기화
    const chatMessages = document.getElementById('chatMessages');
    chatMessages.innerHTML = `
        <div class="message assistant">
            안녕하세요! 저는 AI 트레이딩 어시스턴트입니다. 거래 분석, 리스크 관리, 전략 최적화 등에 대해 질문해주세요.
            <br><br>
            <strong>예시 질문:</strong>
            <ul class="mb-0 mt-2">
                <li>최근 3개월 거래 성과를 분석해줘</li>
                <li>현재 리스크 상태가 어떤가요?</li>
                <li>승률을 높이려면 어떻게 해야 할까요?</li>
                <li>손절 전략에 대해 조언해줘</li>
            </ul>
        </div>
    `;
}

/**
 * 분석 결과를 채팅으로 복사
 */
function copyAnalysisToChat() {
    if (lastAnalysisResult) {
        const modal = bootstrap.Modal.getInstance(document.getElementById('analysisModal'));
        modal.hide();

        // 분석 결과를 채팅에 추가
        addMessageToChat('assistant', formatAIResponse(lastAnalysisResult.summary || lastAnalysisResult.rawResponse));

        // 후속 질문 유도
        setTimeout(() => {
            document.getElementById('chatInput').focus();
            document.getElementById('chatInput').placeholder = '분석 결과에 대해 추가 질문을 해보세요...';
        }, 300);
    }
}
