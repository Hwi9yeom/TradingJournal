package com.trading.journal.ai.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 전략 최적화 제안 DTO
 */
public class AISuggestionDto {

    private Long backtestId;
    private String strategyName;

    // 현재 전략 평가
    private String currentStrategyAssessment;
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();

    // 최적화 제안
    private List<ParameterSuggestion> parameterSuggestions = new ArrayList<>();
    private List<String> generalRecommendations = new ArrayList<>();
    private List<String> riskWarnings = new ArrayList<>();

    // 예상 개선 효과
    private String expectedImprovement;

    private LocalDateTime generatedAt;
    private String rawResponse;

    public AISuggestionDto() {
        this.generatedAt = LocalDateTime.now();
    }

    public void addStrength(String strength) {
        this.strengths.add(strength);
    }

    public void addWeakness(String weakness) {
        this.weaknesses.add(weakness);
    }

    public void addParameterSuggestion(String parameter, String currentValue, String suggestedValue, String reason) {
        this.parameterSuggestions.add(new ParameterSuggestion(parameter, currentValue, suggestedValue, reason));
    }

    public void addRecommendation(String recommendation) {
        this.generalRecommendations.add(recommendation);
    }

    public void addRiskWarning(String warning) {
        this.riskWarnings.add(warning);
    }

    /**
     * 파라미터 변경 제안
     */
    public static class ParameterSuggestion {
        private String parameterName;
        private String currentValue;
        private String suggestedValue;
        private String reason;

        public ParameterSuggestion() {}

        public ParameterSuggestion(String parameterName, String currentValue, String suggestedValue, String reason) {
            this.parameterName = parameterName;
            this.currentValue = currentValue;
            this.suggestedValue = suggestedValue;
            this.reason = reason;
        }

        // Getters and Setters
        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public String getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(String currentValue) {
            this.currentValue = currentValue;
        }

        public String getSuggestedValue() {
            return suggestedValue;
        }

        public void setSuggestedValue(String suggestedValue) {
            this.suggestedValue = suggestedValue;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    // Getters and Setters
    public Long getBacktestId() {
        return backtestId;
    }

    public void setBacktestId(Long backtestId) {
        this.backtestId = backtestId;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getCurrentStrategyAssessment() {
        return currentStrategyAssessment;
    }

    public void setCurrentStrategyAssessment(String currentStrategyAssessment) {
        this.currentStrategyAssessment = currentStrategyAssessment;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(List<String> weaknesses) {
        this.weaknesses = weaknesses;
    }

    public List<ParameterSuggestion> getParameterSuggestions() {
        return parameterSuggestions;
    }

    public void setParameterSuggestions(List<ParameterSuggestion> parameterSuggestions) {
        this.parameterSuggestions = parameterSuggestions;
    }

    public List<String> getGeneralRecommendations() {
        return generalRecommendations;
    }

    public void setGeneralRecommendations(List<String> generalRecommendations) {
        this.generalRecommendations = generalRecommendations;
    }

    public List<String> getRiskWarnings() {
        return riskWarnings;
    }

    public void setRiskWarnings(List<String> riskWarnings) {
        this.riskWarnings = riskWarnings;
    }

    public String getExpectedImprovement() {
        return expectedImprovement;
    }

    public void setExpectedImprovement(String expectedImprovement) {
        this.expectedImprovement = expectedImprovement;
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
}
