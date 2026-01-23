package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

/** 포트폴리오 목표 배분 엔티티 */
@Entity
@Table(
        name = "target_allocations",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_target_allocation_account_stock",
                    columnNames = {"account_id", "stock_id"})
        },
        indexes = {
            @Index(name = "idx_target_allocation_account_id", columnList = "account_id"),
            @Index(name = "idx_target_allocation_stock_id", columnList = "stock_id"),
            @Index(name = "idx_target_allocation_is_active", columnList = "isActive")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetAllocation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    /** 목표 배분율 (%) */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal targetPercent;

    /** 드리프트 임계값 (%) - 이 값 이상 차이나면 리밸런싱 추천 */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal driftThresholdPercent = BigDecimal.valueOf(5);

    /** 활성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 우선순위 (낮을수록 우선) */
    @Builder.Default private Integer priority = 0;

    /** 메모 */
    @Column(length = 500)
    private String notes;
}
