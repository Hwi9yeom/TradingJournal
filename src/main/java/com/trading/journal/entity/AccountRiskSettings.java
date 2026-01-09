package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 계정별 리스크 관리 설정
 */
@Entity
@Table(name = "account_risk_settings", indexes = {
    @Index(name = "idx_risk_settings_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRiskSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", unique = true)
    private Account account;

    /** 거래당 최대 리스크 % (기본 2%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxRiskPerTradePercent = new BigDecimal("2.00");

    /** 일일 최대 손실 한도 % (기본 6%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxDailyLossPercent = new BigDecimal("6.00");

    /** 주간 최대 손실 한도 % (기본 10%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxWeeklyLossPercent = new BigDecimal("10.00");

    /** 최대 오픈 포지션 수 (기본 10) */
    @Builder.Default
    private Integer maxOpenPositions = 10;

    /** 최대 포지션 크기 % (기본 20%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxPositionSizePercent = new BigDecimal("20.00");

    /** 섹터당 최대 집중도 % (기본 30%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxSectorConcentrationPercent = new BigDecimal("30.00");

    /** 종목당 최대 집중도 % (기본 15%) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxStockConcentrationPercent = new BigDecimal("15.00");

    /** 계좌 자본금 */
    @Column(precision = 19, scale = 4)
    private BigDecimal accountCapital;

    /** 일일 손실 한도 알림 활성화 */
    @Builder.Default
    private Boolean dailyLossAlertEnabled = true;

    /** 집중도 한도 알림 활성화 */
    @Builder.Default
    private Boolean concentrationAlertEnabled = true;

    /** Kelly Criterion 비율 (기본 0.5 = Half Kelly) */
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal kellyFraction = new BigDecimal("0.50");

    private LocalDateTime createdAt;

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
}
