package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(
        name = "stock_fundamentals",
        indexes = {
            @Index(name = "idx_fundamentals_symbol", columnList = "symbol"),
            @Index(name = "idx_fundamentals_sector", columnList = "sector"),
            @Index(name = "idx_fundamentals_pe_ratio", columnList = "pe_ratio"),
            @Index(name = "idx_fundamentals_market_cap", columnList = "market_cap")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockFundamentals extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(length = 200)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sector sector;

    @Column(length = 100)
    private String industry;

    // Valuation Metrics
    @Column(precision = 15, scale = 2)
    private BigDecimal marketCap;

    @Column(precision = 10, scale = 2)
    private BigDecimal peRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal pbRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal psRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal pegRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal evToEbitda;

    // Profitability Metrics
    @Column(precision = 10, scale = 4)
    private BigDecimal returnOnEquity;

    @Column(precision = 10, scale = 4)
    private BigDecimal returnOnAssets;

    @Column(precision = 10, scale = 4)
    private BigDecimal profitMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal operatingMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal grossMargin;

    // Dividend Metrics
    @Column(precision = 10, scale = 4)
    private BigDecimal dividendYield;

    @Column(precision = 10, scale = 4)
    private BigDecimal payoutRatio;

    @Column(precision = 10, scale = 2)
    private BigDecimal dividendPerShare;

    // Growth Metrics
    @Column(precision = 10, scale = 4)
    private BigDecimal revenueGrowth;

    @Column(precision = 10, scale = 4)
    private BigDecimal epsGrowth;

    @Column(precision = 10, scale = 4)
    private BigDecimal bookValueGrowth;

    // Financial Health
    @Column(precision = 10, scale = 4)
    private BigDecimal debtToEquity;

    @Column(precision = 10, scale = 4)
    private BigDecimal currentRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal quickRatio;

    // Per Share Metrics
    @Column(precision = 15, scale = 2)
    private BigDecimal earningsPerShare;

    @Column(precision = 15, scale = 2)
    private BigDecimal bookValuePerShare;

    @Column(precision = 15, scale = 2)
    private BigDecimal revenuePerShare;

    // Market Metrics
    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(precision = 15, scale = 2)
    private BigDecimal fiftyTwoWeekLow;

    @Column(precision = 15, scale = 0)
    private BigDecimal averageVolume;

    @Column(precision = 10, scale = 4)
    private BigDecimal beta;

    // Company Size
    @Column(precision = 15, scale = 2)
    private BigDecimal totalRevenue;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAssets;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalDebt;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalCash;

    @Column private LocalDate lastUpdated;

    @Column(length = 20)
    private String fiscalYearEnd;
}
