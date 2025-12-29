package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_date", columnList = "transactionDate"),
    @Index(name = "idx_stock_id", columnList = "stock_id"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_stock_date", columnList = "stock_id, transactionDate"),
    @Index(name = "idx_type_date", columnList = "type, transactionDate"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_updated_at", columnList = "updatedAt"),
    @Index(name = "idx_transaction_account_id", columnList = "account_id"),
    @Index(name = "idx_transaction_account_stock", columnList = "account_id, stock_id"),
    @Index(name = "idx_transaction_account_date", columnList = "account_id, transactionDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
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
    
    public BigDecimal getTotalAmount() {
        BigDecimal amount = price.multiply(quantity);
        if (commission != null) {
            amount = type == TransactionType.BUY ? 
                amount.add(commission) : amount.subtract(commission);
        }
        return amount;
    }
}