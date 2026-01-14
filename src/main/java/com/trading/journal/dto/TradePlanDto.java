package com.trading.journal.dto;

import com.trading.journal.entity.TradePlanStatus;
import com.trading.journal.entity.TradePlanType;
import com.trading.journal.entity.TradeStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 트레이드 플랜 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradePlanDto {

    private Long id;

    // 연결 정보
    private Long accountId;
    private String accountName;
    private Long stockId;

    @NotBlank(message = "종목 심볼은 필수입니다")
    private String stockSymbol;
    private String stockName;

    // 플랜 기본 정보
    @NotNull(message = "플랜 유형은 필수입니다")
    private TradePlanType planType;
    private String planTypeLabel;

    private TradePlanStatus status;
    private String statusLabel;

    private String title;

    // 가격 계획
    @NotNull(message = "진입가는 필수입니다")
    @Positive(message = "진입가는 양수여야 합니다")
    private BigDecimal plannedEntryPrice;

    @NotNull(message = "손절가는 필수입니다")
    @Positive(message = "손절가는 양수여야 합니다")
    private BigDecimal plannedStopLossPrice;

    @Positive(message = "익절가는 양수여야 합니다")
    private BigDecimal plannedTakeProfitPrice;

    private BigDecimal plannedTakeProfit2Price;

    // 수량 (직접 입력 또는 자동 계산)
    @NotNull(message = "수량은 필수입니다")
    @Positive(message = "수량은 양수여야 합니다")
    private BigDecimal plannedQuantity;

    // 계산된 값들 (Response용)
    private BigDecimal plannedPositionValue;
    private BigDecimal plannedRiskAmount;
    private BigDecimal plannedRiskPercent;
    private BigDecimal plannedRiskRewardRatio;
    private BigDecimal stopLossPercent;  // 진입가 대비 손절 %
    private BigDecimal takeProfitPercent; // 진입가 대비 익절 %

    // 조건 기록
    private TradeStrategy strategy;
    private String strategyLabel;
    private String entryConditions;
    private String exitConditions;
    private String invalidationConditions;
    private List<ChecklistItem> checklist;
    private String notes;

    // 유효성
    private LocalDateTime validUntil;
    private String marketContext;
    private Boolean isExpired;
    private Long daysUntilExpiry;

    // 실행 정보
    private Long executedTransactionId;
    private LocalDateTime executedAt;
    private BigDecimal actualEntryPrice;
    private BigDecimal actualQuantity;
    private String executionNotes;
    private BigDecimal entrySlippage; // 계획 대비 실제 진입가 차이 %

    // 결과 추적
    private Long resultTransactionId;
    private BigDecimal realizedPnl;
    private BigDecimal actualRMultiple;
    private Boolean followedPlan;

    // 메타
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 체크리스트 항목
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChecklistItem {
        private String text;
        private Boolean checked;
    }

    /**
     * 플랜 실행 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecuteRequest {
        @NotNull(message = "실제 진입가는 필수입니다")
        @Positive(message = "진입가는 양수여야 합니다")
        private BigDecimal actualEntryPrice;

        @NotNull(message = "실제 수량은 필수입니다")
        @Positive(message = "수량은 양수여야 합니다")
        private BigDecimal actualQuantity;

        private BigDecimal commission;
        private String executionNotes;
        private LocalDateTime transactionDate;
    }

    /**
     * 플랜 결과 업데이트 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResultUpdateRequest {
        @NotNull(message = "결과 거래 ID는 필수입니다")
        private Long resultTransactionId;
        private Boolean followedPlan;
    }

    /**
     * 플랜 통계 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanStatisticsDto {
        private int totalPlans;
        private int plannedCount;
        private int executedCount;
        private int cancelledCount;
        private int expiredCount;

        private BigDecimal executionRate;      // 실행률
        private BigDecimal planAdherenceRate;  // 계획 준수율
        private BigDecimal avgRiskRewardRatio; // 평균 R:R 비율
        private BigDecimal avgActualRMultiple; // 평균 실현 R-multiple

        private BigDecimal totalPlannedRisk;   // 총 계획 리스크
        private BigDecimal totalRealizedPnl;   // 총 실현 손익

        // 전략별 통계
        private List<StrategyPlanStats> strategyStats;

        // 최근 플랜
        private List<TradePlanDto> recentPlans;

        // 대기 중인 플랜 (만료 임박 순)
        private List<TradePlanDto> pendingPlans;
    }

    /**
     * 전략별 플랜 통계
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyPlanStats {
        private TradeStrategy strategy;
        private String strategyLabel;
        private int planCount;
        private int executedCount;
        private BigDecimal executionRate;
        private BigDecimal avgRMultiple;
        private BigDecimal winRate;
    }
}
