package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "portfolios",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_portfolio_account_stock",
                    columnNames = {"account_id", "stock_id"})
        },
        indexes = {
            @Index(name = "idx_portfolio_stock_id", columnList = "stock_id"),
            @Index(name = "idx_portfolio_account_id", columnList = "account_id"),
            @Index(name = "idx_portfolio_account_stock", columnList = "account_id, stock_id"),
            @Index(name = "idx_portfolio_updated_at", columnList = "updatedAt")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal averagePrice;

    @Column(nullable = false)
    private BigDecimal totalInvestment;

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
