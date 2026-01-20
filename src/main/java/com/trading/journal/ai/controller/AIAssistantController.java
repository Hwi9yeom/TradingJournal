package com.trading.journal.ai.controller;

import com.trading.journal.ai.dto.*;
import com.trading.journal.ai.service.AITradingAssistantService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** AI 어시스턴트 REST API 컨트롤러 */
@RestController
@RequestMapping("/api/ai")
public class AIAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AIAssistantController.class);

    private final AITradingAssistantService aiService;

    public AIAssistantController(AITradingAssistantService aiService) {
        this.aiService = aiService;
    }

    /**
     * 성과 AI 분석 GET /api/ai/analyze/performance?accountId=1&startDate=2025-01-01&endDate=2025-12-31
     */
    @GetMapping("/analyze/performance")
    public Mono<ResponseEntity<AIAnalysisResponseDto>> analyzePerformance(
            @RequestParam(defaultValue = "1") Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("성과 분석 API 호출: accountId={}, {} ~ {}", accountId, startDate, endDate);

        return aiService
                .analyzePerformance(accountId, startDate, endDate)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        e -> {
                            log.error("성과 분석 API 오류: {}", e.getMessage());
                            AIAnalysisResponseDto errorResponse = new AIAnalysisResponseDto();
                            errorResponse.setAnalysisType("PERFORMANCE");
                            errorResponse.setSummary("분석 중 오류가 발생했습니다: " + e.getMessage());
                            return Mono.just(
                                    ResponseEntity.internalServerError().body(errorResponse));
                        });
    }

    /** 거래 복기 자동 생성 POST /api/ai/generate/review/{transactionId} */
    @PostMapping("/generate/review/{transactionId}")
    public Mono<ResponseEntity<AIReviewGenerationDto>> generateTradeReview(
            @PathVariable Long transactionId) {

        log.info("거래 복기 생성 API 호출: transactionId={}", transactionId);

        return aiService
                .generateTradeReview(transactionId)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        e -> {
                            log.error("거래 복기 생성 API 오류: {}", e.getMessage());
                            AIReviewGenerationDto errorResponse = new AIReviewGenerationDto();
                            errorResponse.setTransactionId(transactionId);
                            errorResponse.setTradeSummary("복기 생성 중 오류가 발생했습니다: " + e.getMessage());
                            return Mono.just(
                                    ResponseEntity.internalServerError().body(errorResponse));
                        });
    }

    /** 리스크 경고 분석 GET /api/ai/analyze/risk?accountId=1 */
    @GetMapping("/analyze/risk")
    public Mono<ResponseEntity<AIAnalysisResponseDto>> analyzeRiskWarnings(
            @RequestParam(defaultValue = "1") Long accountId) {

        log.info("리스크 분석 API 호출: accountId={}", accountId);

        return aiService
                .analyzeRiskWarnings(accountId)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        e -> {
                            log.error("리스크 분석 API 오류: {}", e.getMessage());
                            AIAnalysisResponseDto errorResponse = new AIAnalysisResponseDto();
                            errorResponse.setAnalysisType("RISK");
                            errorResponse.setSummary("리스크 분석 중 오류가 발생했습니다: " + e.getMessage());
                            return Mono.just(
                                    ResponseEntity.internalServerError().body(errorResponse));
                        });
    }

    /** 전략 최적화 제안 GET /api/ai/suggest/strategy/{backtestId} */
    @GetMapping("/suggest/strategy/{backtestId}")
    public Mono<ResponseEntity<AISuggestionDto>> suggestStrategyOptimization(
            @PathVariable Long backtestId) {

        log.info("전략 최적화 API 호출: backtestId={}", backtestId);

        return aiService
                .suggestStrategyOptimization(backtestId)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        e -> {
                            log.error("전략 최적화 API 오류: {}", e.getMessage());
                            AISuggestionDto errorResponse = new AISuggestionDto();
                            errorResponse.setBacktestId(backtestId);
                            errorResponse.setCurrentStrategyAssessment(
                                    "전략 분석 중 오류가 발생했습니다: " + e.getMessage());
                            return Mono.just(
                                    ResponseEntity.internalServerError().body(errorResponse));
                        });
    }

    /** AI 채팅 (SSE 스트리밍) POST /api/ai/chat */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("채팅 API 호출: sessionId={}", sessionId);

        return aiService
                .chat(sessionId, request.getMessage())
                .onErrorResume(
                        e -> {
                            log.error("채팅 API 오류: {}", e.getMessage());
                            return Flux.just("죄송합니다. 응답 생성 중 오류가 발생했습니다: " + e.getMessage());
                        });
    }

    /** AI 채팅 (동기 방식) POST /api/ai/chat/sync */
    @PostMapping("/chat/sync")
    public Mono<ResponseEntity<ChatResponse>> chatSync(@RequestBody ChatRequest request) {

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("동기 채팅 API 호출: sessionId={}", sessionId);

        final String finalSessionId = sessionId;

        return aiService
                .chatSync(sessionId, request.getMessage())
                .map(
                        response -> {
                            ChatResponse chatResponse = new ChatResponse();
                            chatResponse.setSessionId(finalSessionId);
                            chatResponse.setMessage(response);
                            return ResponseEntity.ok(chatResponse);
                        })
                .onErrorResume(
                        e -> {
                            log.error("동기 채팅 API 오류: {}", e.getMessage());
                            ChatResponse errorResponse = new ChatResponse();
                            errorResponse.setSessionId(finalSessionId);
                            errorResponse.setMessage(
                                    "죄송합니다. 응답 생성 중 오류가 발생했습니다: " + e.getMessage());
                            return Mono.just(
                                    ResponseEntity.internalServerError().body(errorResponse));
                        });
    }

    /** 채팅 세션 초기화 DELETE /api/ai/chat/{sessionId} */
    @DeleteMapping("/chat/{sessionId}")
    public ResponseEntity<Void> clearChatSession(@PathVariable String sessionId) {
        log.info("채팅 세션 초기화 API 호출: sessionId={}", sessionId);
        aiService.clearChatSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /** Ollama 서버 상태 확인 GET /api/ai/health */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return aiService
                .healthCheck()
                .map(
                        isHealthy -> {
                            Map<String, Object> status =
                                    Map.of(
                                            "status", isHealthy ? "UP" : "DOWN",
                                            "ollama", isHealthy ? "connected" : "disconnected");
                            return isHealthy
                                    ? ResponseEntity.ok(status)
                                    : ResponseEntity.status(503).body(status);
                        });
    }

    /** 채팅 요청 DTO */
    public static class ChatRequest {
        private String sessionId;
        private String message;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /** 채팅 응답 DTO */
    public static class ChatResponse {
        private String sessionId;
        private String message;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
