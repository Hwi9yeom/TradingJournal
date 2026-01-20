package com.trading.journal.dto;

import com.trading.journal.entity.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private Long id;

    // Account 정보
    private Long accountId;
    private String accountName;

    @NotBlank(message = "Stock symbol is required")
    private String stockSymbol;

    private String stockName;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Commission cannot be negative")
    private BigDecimal commission;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;

    private String notes;

    private BigDecimal totalAmount;

    // FIFO 계산 결과 (매도 거래에서만 의미 있음)
    /** 실현 손익 */
    private BigDecimal realizedPnl;

    /** FIFO 기반 매도 원가 */
    private BigDecimal costBasis;

    // 리스크 관리 필드
    /** 손절가 */
    @PositiveOrZero(message = "Stop loss price cannot be negative")
    private BigDecimal stopLossPrice;

    /** 익절가 */
    @PositiveOrZero(message = "Take profit price cannot be negative")
    private BigDecimal takeProfitPrice;

    /** 초기 리스크 금액 */
    private BigDecimal initialRiskAmount;

    /** 리스크/리워드 비율 */
    private BigDecimal riskRewardRatio;

    /** R-multiple */
    private BigDecimal rMultiple;

    /** 손절가 대비 현재가 % (계산 필드) */
    private BigDecimal stopLossPercent;

    /** 익절가 대비 현재가 % (계산 필드) */
    private BigDecimal takeProfitPercent;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
