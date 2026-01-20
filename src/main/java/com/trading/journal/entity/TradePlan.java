package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.*;

/** 트레이드 플랜 엔티티 거래 전 계획을 수립하고 추적하는 엔티티 */
@Entity
@Table(
        name = "trade_plans",
        indexes = {
            @Index(name = "idx_trade_plan_status", columnList = "status"),
            @Index(name = "idx_trade_plan_account", columnList = "account_id"),
            @Index(name = "idx_trade_plan_stock", columnList = "stock_id"),
            @Index(name = "idx_trade_plan_valid_until", columnList = "validUntil"),
            @Index(name = "idx_trade_plan_created", columnList = "createdAt")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 계좌 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    /** 대상 종목 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /** 플랜 유형 (LONG/SHORT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradePlanType planType;

    /** 플랜 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TradePlanStatus status = TradePlanStatus.PLANNED;

    /** 플랜 이름/제목 (선택) */
    @Column(length = 200)
    private String title;

    // ===== 가격 계획 =====

    /** 계획 진입가 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedEntryPrice;

    /** 계획 손절가 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    /** 계획 익절가 */
    @Column(precision = 19, scale = 4)
    private BigDecimal plannedTakeProfitPrice;

    /** 2차 익절가 (선택) */
    @Column(precision = 19, scale = 4)
    private BigDecimal plannedTakeProfit2Price;

    // ===== 수량 및 리스크 =====

    /** 계획 수량 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedQuantity;

    /** 계획 포지션 금액 */
    @Column(precision = 19, scale = 4)
    private BigDecimal plannedPositionValue;

    /** 계획 리스크 금액 */
    @Column(precision = 19, scale = 4)
    private BigDecimal plannedRiskAmount;

    /** 계획 리스크 % (자본 대비) */
    @Column(precision = 10, scale = 4)
    private BigDecimal plannedRiskPercent;

    /** 계획 리스크/리워드 비율 */
    @Column(precision = 10, scale = 4)
    private BigDecimal plannedRiskRewardRatio;

    // ===== 조건 기록 =====

    /** 사용할 전략 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TradeStrategy strategy;

    /** 진입 조건 */
    @Column(columnDefinition = "TEXT")
    private String entryConditions;

    /** 청산 조건 */
    @Column(columnDefinition = "TEXT")
    private String exitConditions;

    /** 무효화 조건 (언제 플랜을 취소할지) */
    @Column(columnDefinition = "TEXT")
    private String invalidationConditions;

    /** 체크리스트 (JSON 형식) */
    @Column(columnDefinition = "TEXT")
    private String checklist;

    /** 메모/추가 분석 */
    @Column(columnDefinition = "TEXT")
    private String notes;

    // ===== 유효성 =====

    /** 유효 기한 */
    private LocalDateTime validUntil;

    /** 시장 컨텍스트 (작성 시점의 시장 상황) */
    @Column(length = 500)
    private String marketContext;

    // ===== 실행 정보 =====

    /** 실행된 거래 ID */
    private Long executedTransactionId;

    /** 실행 일시 */
    private LocalDateTime executedAt;

    /** 실제 진입가 */
    @Column(precision = 19, scale = 4)
    private BigDecimal actualEntryPrice;

    /** 실제 수량 */
    @Column(precision = 19, scale = 4)
    private BigDecimal actualQuantity;

    /** 실행 노트 (계획과 다르게 실행한 이유 등) */
    @Column(columnDefinition = "TEXT")
    private String executionNotes;

    // ===== 결과 추적 (거래 완료 후) =====

    /** 결과 거래 ID (청산 거래) */
    private Long resultTransactionId;

    /** 실현 손익 */
    @Column(precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    /** 실현 R-Multiple */
    @Column(precision = 10, scale = 4)
    private BigDecimal actualRMultiple;

    /** 계획 준수 여부 */
    private Boolean followedPlan;

    // ===== 메타 정보 =====

    @Version private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateDerivedFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateDerivedFields();
    }

    /** 파생 필드 계산 (리스크 금액, R:R 비율 등) */
    public void calculateDerivedFields() {
        if (plannedEntryPrice != null && plannedStopLossPrice != null && plannedQuantity != null) {
            // 리스크 금액 = |진입가 - 손절가| × 수량
            BigDecimal riskPerShare = plannedEntryPrice.subtract(plannedStopLossPrice).abs();
            this.plannedRiskAmount = riskPerShare.multiply(plannedQuantity);

            // 포지션 금액
            this.plannedPositionValue = plannedEntryPrice.multiply(plannedQuantity);

            // R:R 비율
            if (plannedTakeProfitPrice != null && riskPerShare.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rewardPerShare =
                        plannedTakeProfitPrice.subtract(plannedEntryPrice).abs();
                this.plannedRiskRewardRatio =
                        rewardPerShare.divide(riskPerShare, 4, RoundingMode.HALF_UP);
            }
        }
    }

    /** 만료 여부 확인 */
    public boolean isExpired() {
        return validUntil != null
                && LocalDateTime.now().isAfter(validUntil)
                && status == TradePlanStatus.PLANNED;
    }
}
