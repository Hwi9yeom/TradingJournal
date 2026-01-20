package com.trading.journal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "stocks",
        indexes = {
            @Index(name = "idx_stock_symbol", columnList = "symbol"),
            @Index(name = "idx_stock_name", columnList = "name"),
            @Index(name = "idx_stock_exchange", columnList = "exchange")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false)
    private String name;

    private String exchange;

    /** 섹터 분류 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sector sector;

    /** 세부 산업 */
    @Column(length = 100)
    private String industry;

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
