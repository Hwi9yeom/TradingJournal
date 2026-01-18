package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 전략 템플릿 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyTemplateDto {

    private Long id;
    private Long accountId;
    private String name;
    private String description;
    private String strategyType;
    private String strategyTypeLabel;   // 전략 종류 한글명
    private Map<String, Object> parameters;  // JSON → Map 변환
    private BigDecimal positionSizePercent;
    private BigDecimal stopLossPercent;
    private BigDecimal takeProfitPercent;
    private BigDecimal commissionRate;
    private Boolean isDefault;
    private Integer usageCount;
    private String color;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 템플릿 생성/수정 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateRequest {
        private Long accountId;
        private String name;
        private String description;
        private String strategyType;
        private Map<String, Object> parameters;
        private BigDecimal positionSizePercent;
        private BigDecimal stopLossPercent;
        private BigDecimal takeProfitPercent;
        private BigDecimal commissionRate;
        private Boolean isDefault;
        private String color;
    }

    /**
     * 템플릿 목록 아이템 (간소화)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateListItem {
        private Long id;
        private String name;
        private String strategyType;
        private String strategyTypeLabel;
        private Integer usageCount;
        private Boolean isDefault;
        private String color;
    }

    /**
     * 백테스트 설정으로 변환
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacktestConfig {
        private String strategyType;
        private Map<String, Object> parameters;
        private BigDecimal positionSizePercent;
        private BigDecimal stopLossPercent;
        private BigDecimal takeProfitPercent;
        private BigDecimal commissionRate;
    }

    /**
     * 전략 종류 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyTypeInfo {
        private String type;
        private String label;
        private String description;
        private Map<String, ParameterInfo> defaultParameters;
    }

    /**
     * 파라미터 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterInfo {
        private String name;
        private String label;
        private String type;        // number, boolean, select
        private Object defaultValue;
        private Object min;
        private Object max;
        private Object step;
    }
}
