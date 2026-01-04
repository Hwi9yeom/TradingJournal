package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 백테스트 개별 거래 엔티티
 */
@Entity
@Table(name = "backtest_trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 백테스트 결과 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backtest_result_id", nullable = false)
    private BacktestResult backtestResult;

    /** 거래 순번 */
    @Column
    private Integer tradeNumber;

    /** 종목 심볼 */
    @Column(nullable = false, length = 50)
    private String symbol;

    /** 진입일 */
    @Column(nullable = false)
    private LocalDate entryDate;

    /** 청산일 */
    @Column
    private LocalDate exitDate;

    /** 진입 가격 */
    @Column(precision = 15, scale = 2)
    private BigDecimal entryPrice;

    /** 청산 가격 */
    @Column(precision = 15, scale = 2)
    private BigDecimal exitPrice;

    /** 수량 */
    @Column(precision = 15, scale = 4)
    private BigDecimal quantity;

    /** 손익 금액 */
    @Column(precision = 15, scale = 2)
    private BigDecimal profit;

    /** 손익률 (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal profitPercent;

    /** 진입 시그널 */
    @Column(length = 100)
    private String entrySignal;

    /** 청산 시그널 */
    @Column(length = 100)
    private String exitSignal;

    /** 보유 기간 (일) */
    @Column
    private Integer holdingDays;

    /** 진입 시 포트폴리오 가치 */
    @Column(precision = 15, scale = 2)
    private BigDecimal portfolioValueAtEntry;

    /** 청산 시 포트폴리오 가치 */
    @Column(precision = 15, scale = 2)
    private BigDecimal portfolioValueAtExit;
}
