package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate exDividendDate; // 배당락일

    @Column(nullable = false)
    private LocalDate paymentDate; // 지급일

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dividendPerShare; // 주당 배당금

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity; // 보유 수량

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount; // 총 배당금 (세전)

    @Column(precision = 10, scale = 2)
    private BigDecimal taxAmount; // 세금

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount; // 실수령액 (세후)

    @Column(length = 500)
    private String memo; // 메모

    @Column(nullable = false)
    private LocalDateTime createdAt;

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