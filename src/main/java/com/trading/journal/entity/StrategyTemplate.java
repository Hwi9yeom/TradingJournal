package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/** 전략 템플릿 엔티티 백테스트 전략 설정을 템플릿으로 저장하여 재사용 */
@Entity
@Table(
        name = "strategy_templates",
        indexes = {
            @Index(name = "idx_template_account", columnList = "account_id"),
            @Index(name = "idx_template_strategy", columnList = "strategy_type")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계좌 ID (null이면 전체 계좌에서 사용 가능) */
    @Column(name = "account_id")
    private Long accountId;

    /** 템플릿 이름 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 템플릿 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 전략 종류 (MA_CROSS, RSI, BOLLINGER, MOMENTUM, MACD 등) */
    @Column(name = "strategy_type", nullable = false, length = 50)
    private String strategyType;

    /** 전략 파라미터 (JSON 형식) */
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    /** 포지션 사이즈 (%) */
    @Column(name = "position_size_percent", precision = 5, scale = 2)
    private BigDecimal positionSizePercent;

    /** 손절 (%) */
    @Column(name = "stop_loss_percent", precision = 5, scale = 2)
    private BigDecimal stopLossPercent;

    /** 익절 (%) */
    @Column(name = "take_profit_percent", precision = 5, scale = 2)
    private BigDecimal takeProfitPercent;

    /** 수수료율 (%) */
    @Column(name = "commission_rate", precision = 5, scale = 4)
    private BigDecimal commissionRate;

    /** 기본 템플릿 여부 */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /** 사용 횟수 */
    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    /** 아이콘/색상 (UI 표시용) */
    @Column(length = 20)
    private String color;

    /** 생성 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 사용 횟수 증가 */
    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }
}
