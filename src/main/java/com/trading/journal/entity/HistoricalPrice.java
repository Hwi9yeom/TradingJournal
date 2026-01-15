package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주식 과거 가격 데이터 (Yahoo Finance API 캐싱용)
 * 외부 API 호출 횟수를 줄이고 백테스팅 성능을 향상시킵니다.
 */
@Entity
@Table(name = "historical_prices",
       uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "price_date"}),
       indexes = {
           @Index(name = "idx_historical_symbol", columnList = "symbol"),
           @Index(name = "idx_historical_date", columnList = "price_date"),
           @Index(name = "idx_historical_symbol_date", columnList = "symbol, price_date")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목 심볼 (예: AAPL, 005930.KS) */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 가격 날짜 */
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    /** 시가 */
    @Column(precision = 15, scale = 4)
    private BigDecimal openPrice;

    /** 고가 */
    @Column(precision = 15, scale = 4)
    private BigDecimal highPrice;

    /** 저가 */
    @Column(precision = 15, scale = 4)
    private BigDecimal lowPrice;

    /** 종가 */
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal closePrice;

    /** 수정 종가 (배당/분할 반영) */
    @Column(precision = 15, scale = 4)
    private BigDecimal adjClose;

    /** 거래량 */
    @Column
    private Long volume;

    /** 데이터 생성 시간 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 데이터 업데이트 시간 */
    @Column(name = "updated_at")
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
