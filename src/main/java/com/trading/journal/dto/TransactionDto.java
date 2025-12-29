package com.trading.journal.dto;

import com.trading.journal.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}