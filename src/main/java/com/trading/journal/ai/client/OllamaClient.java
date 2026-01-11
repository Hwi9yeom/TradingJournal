package com.trading.journal.ai.client;

import com.trading.journal.ai.config.OllamaConfig;
import com.trading.journal.ai.dto.ChatMessageDto;
import com.trading.journal.ai.dto.OllamaRequestDto;
import com.trading.journal.ai.dto.OllamaResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Ollama LLM 서버 클라이언트
 * Generate API와 Chat API를 지원
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;
    private final OllamaConfig config;

    public OllamaClient(WebClient ollamaWebClient, OllamaConfig config) {
        this.webClient = ollamaWebClient;
        this.config = config;
    }

    /**
     * Generate API - 단일 프롬프트 처리 (동기)
     */
    public Mono<OllamaResponseDto> generate(String prompt) {
        return generate(prompt, null);
    }

    /**
     * Generate API - 시스템 프롬프트 포함 (동기)
     */
    public Mono<OllamaResponseDto> generate(String prompt, String systemPrompt) {
        OllamaRequestDto request = OllamaRequestDto.forGenerate(config.getModel(), prompt, false);
        request.setSystem(systemPrompt);
        request.setOptions(Map.of(
                "temperature", config.getTemperature(),
                "num_predict", config.getMaxTokens()
        ));

        log.debug("Ollama generate request: model={}, prompt length={}", config.getModel(), prompt.length());

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponseDto.class)
                .doOnSuccess(response -> log.debug("Ollama generate response received"))
                .doOnError(error -> log.error("Ollama generate error: {}", error.getMessage()));
    }

    /**
     * Generate API - 스트리밍 응답
     */
    public Flux<OllamaResponseDto> generateStream(String prompt, String systemPrompt) {
        OllamaRequestDto request = OllamaRequestDto.forGenerate(config.getModel(), prompt, true);
        request.setSystem(systemPrompt);
        request.setOptions(Map.of(
                "temperature", config.getTemperature(),
                "num_predict", config.getMaxTokens()
        ));

        log.debug("Ollama generate stream request: model={}", config.getModel());

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(OllamaResponseDto.class)
                .doOnComplete(() -> log.debug("Ollama stream completed"))
                .doOnError(error -> log.error("Ollama stream error: {}", error.getMessage()));
    }

    /**
     * Chat API - 대화 기록 기반 (동기)
     */
    public Mono<OllamaResponseDto> chat(List<ChatMessageDto> messages) {
        OllamaRequestDto request = OllamaRequestDto.forChat(config.getModel(), messages, false);
        request.setOptions(Map.of(
                "temperature", config.getTemperature(),
                "num_predict", config.getMaxTokens()
        ));

        log.debug("Ollama chat request: model={}, messages count={}", config.getModel(), messages.size());

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponseDto.class)
                .doOnSuccess(response -> log.debug("Ollama chat response received"))
                .doOnError(error -> log.error("Ollama chat error: {}", error.getMessage()));
    }

    /**
     * Chat API - 스트리밍 응답
     */
    public Flux<OllamaResponseDto> chatStream(List<ChatMessageDto> messages) {
        OllamaRequestDto request = OllamaRequestDto.forChat(config.getModel(), messages, true);
        request.setOptions(Map.of(
                "temperature", config.getTemperature(),
                "num_predict", config.getMaxTokens()
        ));

        log.debug("Ollama chat stream request: model={}, messages count={}", config.getModel(), messages.size());

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(OllamaResponseDto.class)
                .doOnComplete(() -> log.debug("Ollama chat stream completed"))
                .doOnError(error -> log.error("Ollama chat stream error: {}", error.getMessage()));
    }

    /**
     * Ollama 서버 상태 확인
     */
    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri("/")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    /**
     * 사용 가능한 모델 목록 조회
     */
    public Mono<String> listModels() {
        return webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class);
    }
}
