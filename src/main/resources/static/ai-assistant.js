/**
 * AI Trading Assistant JavaScript
 *
 * @fileoverview Provides AI assistant functionality including:
 * - Chat interface with SSE streaming support
 * - Performance and risk analysis
 * - Ollama server health monitoring
 * - Insight card management
 *
 * @requires utils.js - For escapeHtml and other utility functions
 */

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * API endpoints for AI assistant
 * @constant {Object}
 */
const AI_API = {
    HEALTH: '/api/ai/health',
    CHAT: '/api/ai/chat',
    PERFORMANCE: '/api/ai/analyze/performance',
    RISK: '/api/ai/analyze/risk'
};

/**
 * Analysis types configuration
 * @constant {Object}
 */
const ANALYSIS_TYPES = {
    PERFORMANCE: {
        key: 'performance',
        title: '성과 분석',
        icon: 'graph-up-arrow',
        iconColor: 'text-success'
    },
    RISK: {
        key: 'risk',
        title: '리스크 분석',
        icon: 'shield-exclamation',
        iconColor: 'text-danger'
    }
};

/**
 * UI messages and labels
 * @constant {Object}
 */
const UI_MESSAGES = {
    AI_CONNECTED: 'AI 연결됨',
    AI_DISCONNECTED: 'AI 연결 안됨',
    ANALYZING: 'AI가 분석 중입니다. 잠시만 기다려주세요...',
    ANALYSIS_ERROR: '분석 중 오류가 발생했습니다',
    RESPONSE_ERROR: '죄송합니다. 응답 생성 중 오류가 발생했습니다. Ollama 서버가 실행 중인지 확인해주세요.',
    SELECT_PERIOD: '분석 기간을 선택해주세요.',
    CLEAR_CONFIRM: '대화 기록을 모두 삭제하시겠습니까?',
    FOLLOW_UP_PLACEHOLDER: '분석 결과에 대해 추가 질문을 해보세요...',
    WELCOME_MESSAGE: `안녕하세요! 저는 AI 트레이딩 어시스턴트입니다. 거래 분석, 리스크 관리, 전략 최적화 등에 대해 질문해주세요.
        <br><br>
        <strong>예시 질문:</strong>
        <ul class="mb-0 mt-2">
            <li>최근 3개월 거래 성과를 분석해줘</li>
            <li>현재 리스크 상태가 어떤가요?</li>
            <li>승률을 높이려면 어떻게 해야 할까요?</li>
            <li>손절 전략에 대해 조언해줘</li>
        </ul>`
};

/**
 * Configuration values
 * @constant {Object}
 */
const CONFIG = {
    HEALTH_CHECK_INTERVAL: 30000,
    DEFAULT_ACCOUNT_ID: 1,
    MAX_INSIGHT_CARDS: 5,
    INSIGHT_CONTENT_LENGTH: 150,
    DEFAULT_ANALYSIS_MONTHS: 3,
    FOCUS_DELAY: 300
};

// ============================================================================
// STATE MANAGEMENT
// ============================================================================

/**
 * Application state object
 * @type {Object}
 */
const chatState = {
    /** @type {string} - Current session identifier */
    sessionId: generateSessionId(),
    /** @type {Object|null} - Last analysis result for reference */
    lastAnalysisResult: null,
    /** @type {boolean} - Whether a streaming response is in progress */
    isStreaming: false
};

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Initialize the AI assistant on page load.
 * Sets up date inputs and starts health monitoring.
 */
document.addEventListener('DOMContentLoaded', function() {
    initializeDateInputs();
    checkOllamaHealth();

    // Periodic health check
    setInterval(checkOllamaHealth, CONFIG.HEALTH_CHECK_INTERVAL);
});

/**
 * Generate a unique session identifier.
 * @returns {string} Session ID in format 'session_{timestamp}_{random}'
 */
function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

/**
 * Initialize date input fields with default values.
 * Sets end date to today and start date to 3 months ago.
 */
function initializeDateInputs() {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - CONFIG.DEFAULT_ANALYSIS_MONTHS);

    document.getElementById('analysisEndDate').value = endDate.toISOString().split('T')[0];
    document.getElementById('analysisStartDate').value = startDate.toISOString().split('T')[0];
}

// ============================================================================
// HEALTH MONITORING
// ============================================================================

/**
 * Check Ollama server health status.
 * Updates UI indicators based on connection status.
 * @returns {Promise<void>}
 */
async function checkOllamaHealth() {
    const statusIndicator = document.getElementById('ollamaStatus');
    const statusText = document.getElementById('ollamaStatusText');

    try {
        const response = await fetch(AI_API.HEALTH);
        const data = await response.json();

        if (data.status === 'UP') {
            statusIndicator.className = 'status-indicator connected';
            statusText.textContent = UI_MESSAGES.AI_CONNECTED;
        } else {
            setDisconnectedStatus(statusIndicator, statusText);
        }
    } catch (error) {
        setDisconnectedStatus(statusIndicator, statusText);
    }
}

/**
 * Set disconnected status on UI elements.
 * @param {HTMLElement} indicator - Status indicator element
 * @param {HTMLElement} textElement - Status text element
 */
function setDisconnectedStatus(indicator, textElement) {
    indicator.className = 'status-indicator disconnected';
    textElement.textContent = UI_MESSAGES.AI_DISCONNECTED;
}

// ============================================================================
// ANALYSIS FUNCTIONS
// ============================================================================

/**
 * Execute performance analysis for the selected date range.
 * @returns {Promise<void>}
 */
async function analyzePerformance() {
    const startDate = document.getElementById('analysisStartDate').value;
    const endDate = document.getElementById('analysisEndDate').value;

    if (!startDate || !endDate) {
        alert(UI_MESSAGES.SELECT_PERIOD);
        return;
    }

    showAnalysisModal(ANALYSIS_TYPES.PERFORMANCE.title);

    try {
        const url = `${AI_API.PERFORMANCE}?accountId=${CONFIG.DEFAULT_ACCOUNT_ID}&startDate=${startDate}&endDate=${endDate}`;
        const response = await fetch(url);
        const data = await response.json();

        chatState.lastAnalysisResult = data;
        displayAnalysisResult(data, ANALYSIS_TYPES.PERFORMANCE);
        addInsightCard(ANALYSIS_TYPES.PERFORMANCE.key, ANALYSIS_TYPES.PERFORMANCE.title, data.summary);
    } catch (error) {
        displayAnalysisError(error.message);
    }
}

/**
 * Execute risk analysis for the current account.
 * @returns {Promise<void>}
 */
async function analyzeRisk() {
    showAnalysisModal(ANALYSIS_TYPES.RISK.title);

    try {
        const response = await fetch(`${AI_API.RISK}?accountId=${CONFIG.DEFAULT_ACCOUNT_ID}`);
        const data = await response.json();

        chatState.lastAnalysisResult = data;
        displayAnalysisResult(data, ANALYSIS_TYPES.RISK);
        addInsightCard(ANALYSIS_TYPES.RISK.key, ANALYSIS_TYPES.RISK.title, data.summary);
    } catch (error) {
        displayAnalysisError(error.message);
    }
}

/**
 * Display the analysis modal with loading state.
 * @param {string} title - Modal title
 */
function showAnalysisModal(title) {
    document.getElementById('analysisModalTitle').innerHTML =
        `<i class="bi bi-graph-up me-2"></i>${title} 결과`;
    document.getElementById('analysisModalBody').innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">분석 중...</span>
            </div>
            <p class="mt-3 text-muted">${UI_MESSAGES.ANALYZING}</p>
        </div>
    `;

    const modal = new bootstrap.Modal(document.getElementById('analysisModal'));
    modal.show();
}

/**
 * Display analysis result in the modal.
 * @param {Object} data - Analysis result data
 * @param {Object} analysisType - Analysis type configuration
 */
function displayAnalysisResult(data, analysisType) {
    const formattedContent = formatAIResponse(data.summary || data.rawResponse);

    document.getElementById('analysisModalBody').innerHTML = `
        <div class="analysis-result">
            <div class="d-flex align-items-center mb-3">
                <i class="bi bi-${analysisType.icon} ${analysisType.iconColor} fs-3 me-2"></i>
                <div>
                    <h6 class="mb-0">${analysisType.title} 완료</h6>
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
 * Display analysis error in the modal.
 * @param {string} message - Error message
 */
function displayAnalysisError(message) {
    document.getElementById('analysisModalBody').innerHTML = `
        <div class="alert alert-danger">
            <i class="bi bi-exclamation-triangle me-2"></i>
            ${UI_MESSAGES.ANALYSIS_ERROR}: ${escapeHtml(message)}
        </div>
        <p class="text-muted">
            Ollama 서버가 실행 중인지 확인해주세요.<br>
            <code>ollama serve</code> 명령으로 서버를 시작할 수 있습니다.
        </p>
    `;
}

// ============================================================================
// INSIGHT CARDS
// ============================================================================

/**
 * Add an insight card to the insights list.
 * @param {string} type - Insight type (performance, risk)
 * @param {string} title - Card title
 * @param {string} content - Card content
 */
function addInsightCard(type, title, content) {
    const insightsList = document.getElementById('insightsList');
    const truncatedContent = content.length > CONFIG.INSIGHT_CONTENT_LENGTH
        ? content.substring(0, CONFIG.INSIGHT_CONTENT_LENGTH) + '...'
        : content;

    // Remove empty state message if present
    if (insightsList.querySelector('.text-center')) {
        insightsList.innerHTML = '';
    }

    const card = document.createElement('div');
    card.className = `insight-card ${type} p-3 border-bottom`;
    card.innerHTML = `
        <div class="d-flex justify-content-between align-items-start mb-2">
            <h6 class="mb-0">${escapeHtml(title)}</h6>
            <small class="text-muted">${new Date().toLocaleTimeString()}</small>
        </div>
        <p class="mb-0 small text-muted">${escapeHtml(truncatedContent)}</p>
    `;

    insightsList.insertBefore(card, insightsList.firstChild);

    // Maintain maximum card count
    while (insightsList.children.length > CONFIG.MAX_INSIGHT_CARDS) {
        insightsList.removeChild(insightsList.lastChild);
    }
}

// ============================================================================
// CHAT FUNCTIONS
// ============================================================================

/**
 * Send a chat message and handle streaming response.
 * @returns {Promise<void>}
 */
async function sendMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();

    if (!message || chatState.isStreaming) return;

    input.value = '';
    addMessageToChat('user', message);

    chatState.isStreaming = true;
    updateSendButton(true);

    const typingId = showTypingIndicator();

    try {
        const response = await fetch(AI_API.CHAT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: chatState.sessionId,
                message: message
            })
        });

        removeTypingIndicator(typingId);

        if (!response.ok) {
            throw new Error('응답 오류');
        }

        const assistantMessageId = addMessageToChat('assistant', '', true);
        await processStreamingResponse(response, assistantMessageId);

    } catch (error) {
        removeTypingIndicator(typingId);
        addMessageToChat('assistant', UI_MESSAGES.RESPONSE_ERROR);
    } finally {
        chatState.isStreaming = false;
        updateSendButton(false);
    }
}

/**
 * Process SSE streaming response and update message content.
 * @param {Response} response - Fetch response object
 * @param {string} messageId - ID of the message element to update
 * @returns {Promise<void>}
 */
async function processStreamingResponse(response, messageId) {
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
                    updateAssistantMessage(messageId, formatAIResponse(fullResponse));
                }
            }
        }
    }
}

/**
 * Add a message to the chat container.
 * @param {('user'|'assistant')} role - Message role
 * @param {string} content - Message content (HTML)
 * @param {boolean} [streaming=false] - Whether this is a streaming message placeholder
 * @returns {string} The message element ID
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
 * Update an assistant message with new content.
 * @param {string} messageId - Message element ID
 * @param {string} content - New HTML content
 */
function updateAssistantMessage(messageId, content) {
    const messageDiv = document.getElementById(messageId);
    if (messageDiv) {
        messageDiv.innerHTML = content;
        const chatMessages = document.getElementById('chatMessages');
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
}

// ============================================================================
// TYPING INDICATOR
// ============================================================================

/**
 * Show typing indicator in chat.
 * @returns {string} The indicator element ID
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
 * Remove typing indicator from chat.
 * @param {string} indicatorId - Indicator element ID
 */
function removeTypingIndicator(indicatorId) {
    const indicator = document.getElementById(indicatorId);
    if (indicator) {
        indicator.remove();
    }
}

/**
 * Update send button state based on streaming status.
 * @param {boolean} isSending - Whether a message is being sent
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

// ============================================================================
// AI RESPONSE FORMATTING
// ============================================================================

/**
 * Format AI response text with Markdown-like styling.
 * Converts markdown syntax to HTML for display.
 * @param {string} text - Raw response text
 * @returns {string} Formatted HTML string
 */
function formatAIResponse(text) {
    if (!text) return '';

    let formatted = text;

    // Line breaks
    formatted = formatted.replace(/\n/g, '<br>');

    // Bold text (**text**)
    formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // List items (- or *)
    formatted = formatted.replace(/^[-*]\s+(.+)/gm, '<li>$1</li>');

    // Numbered list items (1. 2. etc.)
    formatted = formatted.replace(/^\d+\.\s+(.+)/gm, '<li>$1</li>');

    // Wrap consecutive li tags in ul
    formatted = formatted.replace(/(<li>.*?<\/li>)+/g, '<ul class="mb-2">$&</ul>');

    // Code blocks
    formatted = formatted.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');

    // Inline code
    formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>');

    return formatted;
}

// ============================================================================
// CHAT MANAGEMENT
// ============================================================================

/**
 * Clear chat history and reset session.
 */
function clearChat() {
    if (!confirm(UI_MESSAGES.CLEAR_CONFIRM)) return;

    // Delete server session
    fetch(`${AI_API.CHAT}/${chatState.sessionId}`, { method: 'DELETE' });

    // Generate new session
    chatState.sessionId = generateSessionId();

    // Reset chat UI
    const chatMessages = document.getElementById('chatMessages');
    chatMessages.innerHTML = `
        <div class="message assistant">
            ${UI_MESSAGES.WELCOME_MESSAGE}
        </div>
    `;
}

/**
 * Copy last analysis result to chat window.
 */
function copyAnalysisToChat() {
    if (chatState.lastAnalysisResult) {
        const modal = bootstrap.Modal.getInstance(document.getElementById('analysisModal'));
        modal.hide();

        // Add analysis result to chat
        addMessageToChat('assistant', formatAIResponse(
            chatState.lastAnalysisResult.summary || chatState.lastAnalysisResult.rawResponse
        ));

        // Prompt for follow-up questions
        setTimeout(() => {
            const chatInput = document.getElementById('chatInput');
            chatInput.focus();
            chatInput.placeholder = UI_MESSAGES.FOLLOW_UP_PLACEHOLDER;
        }, CONFIG.FOCUS_DELAY);
    }
}
