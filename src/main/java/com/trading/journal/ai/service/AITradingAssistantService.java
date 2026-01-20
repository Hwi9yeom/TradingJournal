package com.trading.journal.ai.service;

import com.trading.journal.ai.client.OllamaClient;
import com.trading.journal.ai.dto.*;
import com.trading.journal.dto.*;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import com.trading.journal.service.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** AI 트레이딩 어시스턴트 서비스 성과 분석, 거래 복기, 리스크 경고, 전략 최적화 기능 제공 */
@Service
public class AITradingAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AITradingAssistantService.class);

    private final OllamaClient ollamaClient;
    private final PromptTemplateService promptTemplate;
    private final AnalysisService analysisService;
    private final RiskMetricsService riskMetricsService;
    private final TradingPatternService tradingPatternService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final RiskDashboardService riskDashboardService;
    private final BacktestService backtestService;
    private final TransactionRepository transactionRepository;

    // 채팅 세션 관리 (세션 ID -> 대화 기록)
    private final Map<String, List<ChatMessageDto>> chatSessions = new ConcurrentHashMap<>();

    public AITradingAssistantService(
            OllamaClient ollamaClient,
            PromptTemplateService promptTemplate,
            AnalysisService analysisService,
            RiskMetricsService riskMetricsService,
            TradingPatternService tradingPatternService,
            PortfolioAnalysisService portfolioAnalysisService,
            RiskDashboardService riskDashboardService,
            BacktestService backtestService,
            TransactionRepository transactionRepository) {
        this.ollamaClient = ollamaClient;
        this.promptTemplate = promptTemplate;
        this.analysisService = analysisService;
        this.riskMetricsService = riskMetricsService;
        this.tradingPatternService = tradingPatternService;
        this.portfolioAnalysisService = portfolioAnalysisService;
        this.riskDashboardService = riskDashboardService;
        this.backtestService = backtestService;
        this.transactionRepository = transactionRepository;
    }

    /** 성과 분석 AI */
    public Mono<AIAnalysisResponseDto> analyzePerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("AI 성과 분석 시작: accountId={}, period={} ~ {}", accountId, startDate, endDate);

        try {
            // 데이터 수집 (AnalysisService는 accountId를 받지 않음)
            PeriodAnalysisDto periodAnalysis = analysisService.analyzePeriod(startDate, endDate);
            RiskMetricsDto riskMetrics =
                    riskMetricsService.calculateRiskMetrics(accountId, startDate, endDate);
            TradingPatternDto tradingPattern =
                    tradingPatternService.analyzePatterns(accountId, startDate, endDate);

            // 프롬프트 생성
            String prompt =
                    promptTemplate.buildPerformanceAnalysisPrompt(
                            periodAnalysis, riskMetrics, tradingPattern);
            String systemPrompt = promptTemplate.getSystemPrompt();

            // LLM 호출
            return ollamaClient
                    .generate(prompt, systemPrompt)
                    .map(
                            response -> {
                                AIAnalysisResponseDto result = new AIAnalysisResponseDto();
                                result.setAnalysisType("PERFORMANCE");
                                result.setSummary(response.getContent());
                                result.setRawResponse(response.getContent());
                                return result;
                            })
                    .doOnSuccess(r -> log.info("AI 성과 분석 완료"))
                    .doOnError(e -> log.error("AI 성과 분석 실패: {}", e.getMessage()));

        } catch (Exception e) {
            log.error("성과 분석 데이터 수집 실패: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /** 거래 복기 자동 생성 */
    public Mono<AIReviewGenerationDto> generateTradeReview(Long transactionId) {
        log.info("AI 거래 복기 생성 시작: transactionId={}", transactionId);

        // 매도 거래 조회
        Transaction sellTransaction =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "거래를 찾을 수 없습니다: " + transactionId));

        if (sellTransaction.getType() != TransactionType.SELL) {
            return Mono.error(new IllegalArgumentException("매도 거래만 복기 생성이 가능합니다"));
        }

        Stock stock = sellTransaction.getStock();
        String stockSymbol = stock != null ? stock.getSymbol() : "UNKNOWN";
        String stockName = stock != null ? stock.getName() : "Unknown";

        // 관련 매수 거래 찾기 (같은 종목의 가장 최근 매수)
        Transaction buyTransaction =
                transactionRepository
                        .findFirstByAccountAndStockAndTypeAndTransactionDateBeforeOrderByTransactionDateDesc(
                                sellTransaction.getAccount(),
                                stock,
                                TransactionType.BUY,
                                sellTransaction.getTransactionDate())
                        .orElse(null);

        // DTO 변환
        TransactionDto sellDto = convertToDto(sellTransaction);
        TransactionDto buyDto = buyTransaction != null ? convertToDto(buyTransaction) : null;

        // 프롬프트 생성
        String prompt = promptTemplate.buildTradeReviewPrompt(sellDto, buyDto);
        String systemPrompt = promptTemplate.getSystemPrompt();

        // LLM 호출
        return ollamaClient
                .generate(prompt, systemPrompt)
                .map(
                        response -> {
                            AIReviewGenerationDto result = new AIReviewGenerationDto();
                            result.setTransactionId(transactionId);
                            result.setStockSymbol(stockSymbol);
                            result.setStockName(stockName);
                            result.setTradeSummary(response.getContent());
                            result.setRawResponse(response.getContent());
                            return result;
                        })
                .doOnSuccess(r -> log.info("AI 거래 복기 생성 완료: {}", stockSymbol))
                .doOnError(e -> log.error("AI 거래 복기 생성 실패: {}", e.getMessage()));
    }

    /** 리스크 경고 분석 */
    public Mono<AIAnalysisResponseDto> analyzeRiskWarnings(Long accountId) {
        log.info("AI 리스크 분석 시작: accountId={}", accountId);

        try {
            // 최근 3개월 데이터
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(3);

            // 데이터 수집
            RiskMetricsDto riskMetrics =
                    riskMetricsService.calculateRiskMetrics(accountId, startDate, endDate);
            PortfolioSummaryDto portfolioSummary = portfolioAnalysisService.getPortfolioSummary();
            RiskDashboardDto riskDashboard = riskDashboardService.getRiskDashboard(accountId);

            // 프롬프트 생성
            String prompt =
                    promptTemplate.buildRiskWarningPrompt(
                            riskMetrics, portfolioSummary, riskDashboard);
            String systemPrompt = promptTemplate.getSystemPrompt();

            // LLM 호출
            return ollamaClient
                    .generate(prompt, systemPrompt)
                    .map(
                            response -> {
                                AIAnalysisResponseDto result = new AIAnalysisResponseDto();
                                result.setAnalysisType("RISK");
                                result.setSummary(response.getContent());
                                result.setRawResponse(response.getContent());
                                return result;
                            })
                    .doOnSuccess(r -> log.info("AI 리스크 분석 완료"))
                    .doOnError(e -> log.error("AI 리스크 분석 실패: {}", e.getMessage()));

        } catch (Exception e) {
            log.error("리스크 분석 데이터 수집 실패: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /** 전략 최적화 제안 */
    public Mono<AISuggestionDto> suggestStrategyOptimization(Long backtestId) {
        log.info("AI 전략 최적화 분석 시작: backtestId={}", backtestId);

        try {
            // 백테스트 결과 조회
            BacktestResultDto backtestResult = backtestService.getResult(backtestId);

            // 프롬프트 생성
            String prompt = promptTemplate.buildStrategyOptimizationPrompt(backtestResult);
            String systemPrompt = promptTemplate.getSystemPrompt();

            // LLM 호출
            return ollamaClient
                    .generate(prompt, systemPrompt)
                    .map(
                            response -> {
                                AISuggestionDto result = new AISuggestionDto();
                                result.setBacktestId(backtestId);
                                result.setStrategyName(backtestResult.getStrategyName());
                                result.setCurrentStrategyAssessment(response.getContent());
                                result.setRawResponse(response.getContent());
                                return result;
                            })
                    .doOnSuccess(r -> log.info("AI 전략 최적화 분석 완료"))
                    .doOnError(e -> log.error("AI 전략 최적화 분석 실패: {}", e.getMessage()));

        } catch (Exception e) {
            log.error("전략 최적화 분석 실패: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /** 자유 대화 (스트리밍) */
    public Flux<String> chat(String sessionId, String userMessage) {
        log.info("AI 채팅 시작: sessionId={}", sessionId);

        // 세션 대화 기록 가져오기
        List<ChatMessageDto> messages =
                chatSessions.computeIfAbsent(
                        sessionId,
                        k -> {
                            List<ChatMessageDto> newSession = new ArrayList<>();
                            newSession.add(ChatMessageDto.system(promptTemplate.getSystemPrompt()));
                            return newSession;
                        });

        // 사용자 메시지 추가
        messages.add(ChatMessageDto.user(userMessage));

        // 스트리밍 호출
        return ollamaClient
                .chatStream(messages)
                .map(
                        response -> {
                            String content = response.getContent();
                            // 응답 완료시 어시스턴트 메시지 저장
                            if (Boolean.TRUE.equals(response.getDone()) && content != null) {
                                messages.add(ChatMessageDto.assistant(content));
                            }
                            return content != null ? content : "";
                        })
                .filter(content -> !content.isEmpty())
                .doOnComplete(() -> log.debug("AI 채팅 스트림 완료"))
                .doOnError(e -> log.error("AI 채팅 실패: {}", e.getMessage()));
    }

    /** 자유 대화 (동기) */
    public Mono<String> chatSync(String sessionId, String userMessage) {
        log.info("AI 채팅 (동기) 시작: sessionId={}", sessionId);

        List<ChatMessageDto> messages =
                chatSessions.computeIfAbsent(
                        sessionId,
                        k -> {
                            List<ChatMessageDto> newSession = new ArrayList<>();
                            newSession.add(ChatMessageDto.system(promptTemplate.getSystemPrompt()));
                            return newSession;
                        });

        messages.add(ChatMessageDto.user(userMessage));

        return ollamaClient
                .chat(messages)
                .map(
                        response -> {
                            String content = response.getContent();
                            if (content != null) {
                                messages.add(ChatMessageDto.assistant(content));
                            }
                            return content != null ? content : "";
                        })
                .doOnSuccess(r -> log.info("AI 채팅 완료"))
                .doOnError(e -> log.error("AI 채팅 실패: {}", e.getMessage()));
    }

    /** 채팅 세션 초기화 */
    public void clearChatSession(String sessionId) {
        chatSessions.remove(sessionId);
        log.info("채팅 세션 초기화: sessionId={}", sessionId);
    }

    /** Ollama 서버 상태 확인 */
    public Mono<Boolean> healthCheck() {
        return ollamaClient.healthCheck();
    }

    /** Transaction 엔티티 -> DTO 변환 */
    private TransactionDto convertToDto(Transaction tx) {
        Stock stock = tx.getStock();
        return TransactionDto.builder()
                .id(tx.getId())
                .accountId(tx.getAccount() != null ? tx.getAccount().getId() : null)
                .stockSymbol(stock != null ? stock.getSymbol() : null)
                .stockName(stock != null ? stock.getName() : null)
                .type(tx.getType())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .commission(tx.getCommission())
                .transactionDate(tx.getTransactionDate())
                .notes(tx.getNotes())
                .realizedPnl(tx.getRealizedPnl())
                .stopLossPrice(tx.getStopLossPrice())
                .takeProfitPrice(tx.getTakeProfitPrice())
                .rMultiple(tx.getRMultiple())
                .build();
    }
}
