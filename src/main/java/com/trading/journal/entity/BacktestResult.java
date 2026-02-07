package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/** 백테스트 결과 엔티티 */
@Entity
@Table(name = "backtest_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 전략 이름 */
    @Column(nullable = false, length = 100)
    private String strategyName;

    /** 전략 설정 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String strategyConfig;

    /** 전략 타입 (인덱싱용 - MOVING_AVERAGE, RSI, BOLLINGER_BAND, MOMENTUM, MACD) */
    @Column(name = "strategy_type", length = 50)
    private String strategyType;

    /** 백테스트 대상 종목 */
    @Column(length = 50)
    private String symbol;

    /** 시작일 */
    @Column(nullable = false)
    private LocalDate startDate;

    /** 종료일 */
    @Column(nullable = false)
    private LocalDate endDate;

    /** 초기 자본금 */
    @Column(precision = 15, scale = 2)
    private BigDecimal initialCapital;

    /** 최종 자본금 */
    @Column(precision = 15, scale = 2)
    private BigDecimal finalCapital;

    /** 총 수익률 (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal totalReturn;

    /** 연평균 수익률 (CAGR) (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal cagr;

    /** 최대 낙폭 (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal maxDrawdown;

    /** 샤프 비율 */
    @Column(precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    /** 소르티노 비율 */
    @Column(precision = 10, scale = 4)
    private BigDecimal sortinoRatio;

    /** 총 거래 횟수 */
    @Column private Integer totalTrades;

    /** 승리 거래 횟수 */
    @Column private Integer winningTrades;

    /** 패배 거래 횟수 */
    @Column private Integer losingTrades;

    /** 승률 (%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal winRate;

    /** 평균 수익 거래 금액 */
    @Column(precision = 15, scale = 2)
    private BigDecimal avgWin;

    /** 평균 손실 거래 금액 */
    @Column(precision = 15, scale = 2)
    private BigDecimal avgLoss;

    /** 손익비 (Profit Factor) */
    @Column(precision = 10, scale = 4)
    private BigDecimal profitFactor;

    /** 최대 연승 */
    @Column private Integer maxWinStreak;

    /** 최대 연패 */
    @Column private Integer maxLossStreak;

    /** 평균 보유 기간 (일) */
    @Column(precision = 10, scale = 2)
    private BigDecimal avgHoldingDays;

    /** 백테스트 거래 목록 */
    @OneToMany(
            mappedBy = "backtestResult",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<BacktestTrade> trades = new ArrayList<>();

    /** 실행 시각 */
    @Column private LocalDateTime executedAt;

    /** 실행 소요 시간 (밀리초) */
    @Column private Long executionTimeMs;

    // === 캐싱된 계산 결과 (JSON) ===

    /** 월별 성과 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String monthlyPerformanceJson;

    /** Equity Curve 라벨 (JSON) */
    @Column(columnDefinition = "TEXT")
    private String equityLabelsJson;

    /** Equity Curve (JSON) */
    @Column(columnDefinition = "TEXT")
    private String equityCurveJson;

    /** Drawdown Curve (JSON) */
    @Column(columnDefinition = "TEXT")
    private String drawdownCurveJson;

    /** Benchmark Curve (JSON) */
    @Column(columnDefinition = "TEXT")
    private String benchmarkCurveJson;

    /** 정규화된 Equity Curve (0-100%, 비교 분석용) */
    @Column(columnDefinition = "TEXT")
    private String normalizedEquityCurveJson;

    @PrePersist
    protected void onCreate() {
        executedAt = LocalDateTime.now();
    }
}
