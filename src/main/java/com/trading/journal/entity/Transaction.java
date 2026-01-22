package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "transactions",
        indexes = {
            // 핵심 인덱스만 유지 (11개 → 5개로 축소)
            // 쓰기 성능 향상을 위해 불필요한 인덱스 제거
            @Index(name = "idx_transaction_date", columnList = "transactionDate"),
            @Index(name = "idx_stock_date", columnList = "stock_id, transactionDate"),
            @Index(name = "idx_transaction_account_stock", columnList = "account_id, stock_id"),
            @Index(
                    name = "idx_transaction_account_date",
                    columnList = "account_id, transactionDate"),
            @Index(
                    name = "idx_transaction_fifo",
                    columnList = "account_id, stock_id, type, transactionDate, remainingQuantity")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal commission;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    private String notes;

    // FIFO 계산 관련 필드
    /** 실현 손익 (매도 거래에서만 값이 있음) */
    @Column(precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    /** FIFO 기반 매도 원가 (매도 거래에서 사용된 매수 원가 합계) */
    @Column(precision = 19, scale = 4)
    private BigDecimal costBasis;

    /** 매수 거래의 잔여 수량 (FIFO 소진 추적용) */
    @Column(precision = 19, scale = 4)
    private BigDecimal remainingQuantity;

    // 리스크 관리 필드
    /** 손절가 */
    @Column(precision = 19, scale = 4)
    private BigDecimal stopLossPrice;

    /** 익절가 */
    @Column(precision = 19, scale = 4)
    private BigDecimal takeProfitPrice;

    /** 초기 리스크 금액: (진입가 - 손절가) × 수량 */
    @Column(precision = 19, scale = 4)
    private BigDecimal initialRiskAmount;

    /** 리스크/리워드 비율: (익절가 - 진입가) / (진입가 - 손절가) */
    @Column(precision = 10, scale = 4)
    private BigDecimal riskRewardRatio;

    /** R-multiple: 실현손익 / 초기리스크 */
    @Column(precision = 10, scale = 4)
    private BigDecimal rMultiple;

    public BigDecimal getTotalAmount() {
        BigDecimal amount = price.multiply(quantity);
        if (commission != null) {
            amount =
                    type == TransactionType.BUY
                            ? amount.add(commission)
                            : amount.subtract(commission);
        }
        return amount;
    }
}
