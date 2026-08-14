package com.trading.journal.entity;

import com.trading.journal.security.converter.EncryptedBigDecimalConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 개별 저축 기록 (저축 일지).
 *
 * <p>"이번 달 얼마 모았나"를 손으로 적는 대신 입금 건별로 남긴다. 같은 달의 합계가 {@link MonthlyBudget#getActualSavings()}의 근거가
 * 된다.
 *
 * <p>금액은 다른 자산 금액과 동일하게 암호화 저장되므로 DB 레벨 집계는 불가능하며, 합계는 애플리케이션에서 계산한다.
 */
@Entity
@Table(
        name = "savings_records",
        indexes = {
            @Index(name = "idx_savings_date", columnList = "saved_date"),
            @Index(name = "idx_savings_account", columnList = "account_id"),
            @Index(name = "idx_savings_category", columnList = "category")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계좌 ID (null이면 전체 가계) */
    @Column(name = "account_id")
    private Long accountId;

    /** 저축 실행일 */
    @Column(name = "saved_date", nullable = false)
    private LocalDate savedDate;

    /** 저축 금액 */
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal amount;

    /** 분류 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SavingsCategory category = SavingsCategory.REGULAR_SAVINGS;

    /** 저축처 (은행/증권사/상품명 등) */
    @Column(length = 100)
    private String institution;

    /** 메모 */
    @Column(length = 1000)
    private String memo;

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
}
