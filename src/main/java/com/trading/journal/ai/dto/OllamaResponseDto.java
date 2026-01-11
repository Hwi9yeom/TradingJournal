package com.trading.journal.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ollama API 응답 DTO
 */
public class OllamaResponseDto {

    private String model;

    @JsonProperty("created_at")
    private String createdAt;

    private String response;

    private Boolean done;

    private String context;

    @JsonProperty("total_duration")
    private Long totalDuration;

    @JsonProperty("load_duration")
    private Long loadDuration;

    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    @JsonProperty("prompt_eval_duration")
    private Long promptEvalDuration;

    @JsonProperty("eval_count")
    private Integer evalCount;

    @JsonProperty("eval_duration")
    private Long evalDuration;

    // For chat API
    private ChatMessageDto message;

    public OllamaResponseDto() {}

    // Getters and Setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public Long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Long getLoadDuration() {
        return loadDuration;
    }

    public void setLoadDuration(Long loadDuration) {
        this.loadDuration = loadDuration;
    }

    public Integer getPromptEvalCount() {
        return promptEvalCount;
    }

    public void setPromptEvalCount(Integer promptEvalCount) {
        this.promptEvalCount = promptEvalCount;
    }

    public Long getPromptEvalDuration() {
        return promptEvalDuration;
    }

    public void setPromptEvalDuration(Long promptEvalDuration) {
        this.promptEvalDuration = promptEvalDuration;
    }

    public Integer getEvalCount() {
        return evalCount;
    }

    public void setEvalCount(Integer evalCount) {
        this.evalCount = evalCount;
    }

    public Long getEvalDuration() {
        return evalDuration;
    }

    public void setEvalDuration(Long evalDuration) {
        this.evalDuration = evalDuration;
    }

    public ChatMessageDto getMessage() {
        return message;
    }

    public void setMessage(ChatMessageDto message) {
        this.message = message;
    }

    /**
     * 응답 텍스트 반환 (generate API는 response, chat API는 message.content)
     */
    public String getContent() {
        if (response != null) {
            return response;
        }
        if (message != null) {
            return message.getContent();
        }
        return null;
    }
}
