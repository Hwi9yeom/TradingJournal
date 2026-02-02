package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/** 가격 알림 엔티티 */
@Entity
@Table(
        name = "price_alert",
        indexes = {
            @Index(name = "idx_price_alert_user", columnList = "userId"),
            @Index(name = "idx_price_alert_symbol", columnList = "symbol"),
            @Index(name = "idx_price_alert_active", columnList = "isActive"),
            @Index(name = "idx_price_alert_triggered", columnList = "isTriggered")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(nullable = false)
    private Long userId;

    /** 종목 ID */
    @Column(nullable = false)
    private Long stockId;

    /** 종목 코드 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 알림 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceAlertType alertType;

    /** 임계값 (목표 가격) */
    @Column(precision = 15, scale = 2)
    private BigDecimal thresholdPrice;

    /** 현재 가격 */
    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice;

    /** 조건 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceAlertCondition condition;

    /** 활성 상태 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** 트리거 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isTriggered = false;

    /** 트리거된 시간 */
    private LocalDateTime triggeredAt;

    /** 알림 전송 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    /** 알림 트리거 */
    public void trigger(BigDecimal currentPrice) {
        this.isTriggered = true;
        this.triggeredAt = LocalDateTime.now();
        this.currentPrice = currentPrice;
        this.isActive = false; // 트리거되면 비활성화
    }

    /** 알림 전송 완료 */
    public void markNotificationSent() {
        this.notificationSent = true;
    }

    /** 알림 재활성화 */
    public void reactivate() {
        this.isActive = true;
        this.isTriggered = false;
        this.triggeredAt = null;
        this.notificationSent = false;
    }

    /** 가격 알림 유형 */
    public enum PriceAlertType {
        PRICE_ABOVE("가격 상승"),
        PRICE_BELOW("가격 하락"),
        PERCENT_CHANGE("퍼센트 변동"),
        VOLUME_SPIKE("거래량 급증");

        private final String description;

        PriceAlertType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /** 가격 알림 조건 */
    public enum PriceAlertCondition {
        GREATER_THAN("이상"),
        LESS_THAN("이하"),
        EQUALS("같음"),
        PERCENT_UP("% 상승"),
        PERCENT_DOWN("% 하락");

        private final String description;

        PriceAlertCondition(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
