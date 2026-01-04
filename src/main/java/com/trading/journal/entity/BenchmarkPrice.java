package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 벤치마크 지수 가격 데이터
 */
@Entity
@Table(name = "benchmark_prices",
       uniqueConstraints = @UniqueConstraint(columnNames = {"benchmark", "price_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 벤치마크 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenchmarkType benchmark;

    /** 가격 날짜 */
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    /** 시가 */
    @Column(precision = 15, scale = 2)
    private BigDecimal openPrice;

    /** 고가 */
    @Column(precision = 15, scale = 2)
    private BigDecimal highPrice;

    /** 저가 */
    @Column(precision = 15, scale = 2)
    private BigDecimal lowPrice;

    /** 종가 */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal closePrice;

    /** 일간 수익률 (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal dailyReturn;

    /** 거래량 */
    @Column
    private Long volume;
}
