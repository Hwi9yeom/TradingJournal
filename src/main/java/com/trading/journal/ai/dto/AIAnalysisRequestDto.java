package com.trading.journal.ai.dto;

import java.time.LocalDate;

/** AI 분석 요청 DTO */
public class AIAnalysisRequestDto {

    public enum AnalysisType {
        PERFORMANCE, // 성과 분석
        RISK, // 리스크 분석
        REVIEW, // 거래 복기
        STRATEGY // 전략 최적화
    }

    private AnalysisType type;
    private Long accountId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long transactionId;
    private Long backtestId;
    private String additionalContext;

    public AIAnalysisRequestDto() {}

    public static AIAnalysisRequestDto forPerformance(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        AIAnalysisRequestDto dto = new AIAnalysisRequestDto();
        dto.setType(AnalysisType.PERFORMANCE);
        dto.setAccountId(accountId);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        return dto;
    }

    public static AIAnalysisRequestDto forRisk(Long accountId) {
        AIAnalysisRequestDto dto = new AIAnalysisRequestDto();
        dto.setType(AnalysisType.RISK);
        dto.setAccountId(accountId);
        return dto;
    }

    public static AIAnalysisRequestDto forReview(Long transactionId) {
        AIAnalysisRequestDto dto = new AIAnalysisRequestDto();
        dto.setType(AnalysisType.REVIEW);
        dto.setTransactionId(transactionId);
        return dto;
    }

    public static AIAnalysisRequestDto forStrategy(Long backtestId) {
        AIAnalysisRequestDto dto = new AIAnalysisRequestDto();
        dto.setType(AnalysisType.STRATEGY);
        dto.setBacktestId(backtestId);
        return dto;
    }

    // Getters and Setters
    public AnalysisType getType() {
        return type;
    }

    public void setType(AnalysisType type) {
        this.type = type;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getBacktestId() {
        return backtestId;
    }

    public void setBacktestId(Long backtestId) {
        this.backtestId = backtestId;
    }

    public String getAdditionalContext() {
        return additionalContext;
    }

    public void setAdditionalContext(String additionalContext) {
        this.additionalContext = additionalContext;
    }
}
