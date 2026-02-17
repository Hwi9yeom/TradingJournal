package com.trading.journal.ai.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** AI 거래 복기 자동 생성 결과 DTO */
public class AIReviewGenerationDto {

    private Long transactionId;
    private String stockSymbol;
    private String stockName;

    // AI가 생성한 복기 내용
    private String entryReason;
    private String exitReason;
    private String marketConditionAnalysis;
    private String executionQualityAssessment;
    private String emotionalStateGuess;
    private String lessonsLearned;
    private List<String> improvements = new ArrayList<>();
    private List<String> suggestedTags = new ArrayList<>();

    // 거래 요약 (AI 해석)
    private String tradeSummary;
    private Integer suggestedRating;

    private LocalDateTime generatedAt;
    private String rawResponse;
    private String disclaimer;

    public AIReviewGenerationDto() {
        this.generatedAt = LocalDateTime.now();
        this.disclaimer =
                "[면책 조항] 본 정보는 AI가 생성한 참고 자료이며, "
                        + "투자 조언이 아닙니다. 투자 결정은 본인의 판단과 책임 하에 이루어져야 합니다. "
                        + "과거 성과가 미래 수익을 보장하지 않습니다.";
    }

    public void addImprovement(String improvement) {
        this.improvements.add(improvement);
    }

    public void addSuggestedTag(String tag) {
        this.suggestedTags.add(tag);
    }

    // Getters and Setters
    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public String getEntryReason() {
        return entryReason;
    }

    public void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    public String getMarketConditionAnalysis() {
        return marketConditionAnalysis;
    }

    public void setMarketConditionAnalysis(String marketConditionAnalysis) {
        this.marketConditionAnalysis = marketConditionAnalysis;
    }

    public String getExecutionQualityAssessment() {
        return executionQualityAssessment;
    }

    public void setExecutionQualityAssessment(String executionQualityAssessment) {
        this.executionQualityAssessment = executionQualityAssessment;
    }

    public String getEmotionalStateGuess() {
        return emotionalStateGuess;
    }

    public void setEmotionalStateGuess(String emotionalStateGuess) {
        this.emotionalStateGuess = emotionalStateGuess;
    }

    public String getLessonsLearned() {
        return lessonsLearned;
    }

    public void setLessonsLearned(String lessonsLearned) {
        this.lessonsLearned = lessonsLearned;
    }

    public List<String> getImprovements() {
        return improvements;
    }

    public void setImprovements(List<String> improvements) {
        this.improvements = improvements;
    }

    public List<String> getSuggestedTags() {
        return suggestedTags;
    }

    public void setSuggestedTags(List<String> suggestedTags) {
        this.suggestedTags = suggestedTags;
    }

    public String getTradeSummary() {
        return tradeSummary;
    }

    public void setTradeSummary(String tradeSummary) {
        this.tradeSummary = tradeSummary;
    }

    public Integer getSuggestedRating() {
        return suggestedRating;
    }

    public void setSuggestedRating(Integer suggestedRating) {
        this.suggestedRating = suggestedRating;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
