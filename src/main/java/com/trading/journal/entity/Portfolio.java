package com.trading.journal.entity;

import com.trading.journal.security.converter.EncryptedBigDecimalConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
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
public class Portfolio extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal quantity;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal averagePrice;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal totalInvestment;
}
