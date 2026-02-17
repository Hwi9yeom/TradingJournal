package com.trading.journal.ai.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** AI 분석 결과 응답 DTO */
public class AIAnalysisResponseDto {

    private String analysisType;
    private String summary;
    private List<String> insights = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private LocalDateTime analyzedAt;
    private String rawResponse;
    private String disclaimer;

    public AIAnalysisResponseDto() {
        this.analyzedAt = LocalDateTime.now();
        this.disclaimer =
                "[면책 조항] 본 정보는 AI가 생성한 참고 자료이며, "
                        + "투자 조언이 아닙니다. 투자 결정은 본인의 판단과 책임 하에 이루어져야 합니다. "
                        + "과거 성과가 미래 수익을 보장하지 않습니다.";
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

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
