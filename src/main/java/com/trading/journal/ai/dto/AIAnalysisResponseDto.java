package com.trading.journal.ai.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 분석 결과 응답 DTO
 */
public class AIAnalysisResponseDto {

    private String analysisType;
    private String summary;
    private List<String> insights = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private LocalDateTime analyzedAt;
    private String rawResponse;

    public AIAnalysisResponseDto() {
        this.analyzedAt = LocalDateTime.now();
    }

    public static AIAnalysisResponseDto fromRawResponse(String rawResponse, String analysisType) {
        AIAnalysisResponseDto dto = new AIAnalysisResponseDto();
        dto.setAnalysisType(analysisType);
        dto.setRawResponse(rawResponse);
        dto.setSummary(rawResponse);
        return dto;
    }

    public void addInsight(String insight) {
        this.insights.add(insight);
    }

    public void addRecommendation(String recommendation) {
        this.recommendations.add(recommendation);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    // Getters and Setters
    public String getAnalysisType() {
        return analysisType;
    }

    public void setAnalysisType(String analysisType) {
        this.analysisType = analysisType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getInsights() {
        return insights;
    }

    public void setInsights(List<String> insights) {
        this.insights = insights;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
}
