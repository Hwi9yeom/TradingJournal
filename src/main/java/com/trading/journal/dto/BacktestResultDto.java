package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 백테스트 결과 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDto {

    private Long id;

    // === 기본 정보 ===
    private String strategyName;
    private String strategyType;
    private Map<String, Object> strategyConfig;
    private String symbol;
    private LocalDate startDate;
    private LocalDate endDate;

    // === 자본 정보 ===
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal totalProfit;

    // === 수익률 지표 ===
    private BigDecimal totalReturn; // 총 수익률 (%)
    private BigDecimal cagr; // 연평균 수익률 (%)
    private BigDecimal monthlyReturn; // 월평균 수익률 (%)

    // === 리스크 지표 ===
    private BigDecimal maxDrawdown; // 최대 낙폭 (%)
    private BigDecimal maxDrawdownDuration; // 최대 낙폭 기간 (일)
    private BigDecimal sharpeRatio; // 샤프 비율
    private BigDecimal sortinoRatio; // 소르티노 비율
    private BigDecimal calmarRatio; // 칼마 비율
    private BigDecimal volatility; // 변동성 (%)

    // === 거래 통계 ===
    private Integer totalTrades; // 총 거래 횟수
    private Integer winningTrades; // 승리 거래
    private Integer losingTrades; // 패배 거래
    private BigDecimal winRate; // 승률 (%)
    private BigDecimal avgWin; // 평균 수익 거래 금액
    private BigDecimal avgLoss; // 평균 손실 거래 금액
    private BigDecimal avgWinPercent; // 평균 수익률 (%)
    private BigDecimal avgLossPercent; // 평균 손실률 (%)
    private BigDecimal profitFactor; // 손익비
    private BigDecimal expectancy; // 기대값

    // === 연속 거래 통계 ===
    private Integer maxWinStreak; // 최대 연승
    private Integer maxLossStreak; // 최대 연패

    // === 보유 기간 통계 ===
    private BigDecimal avgHoldingDays; // 평균 보유 기간 (일)
    private BigDecimal avgWinHoldingDays; // 수익 거래 평균 보유 기간
    private BigDecimal avgLossHoldingDays; // 손실 거래 평균 보유 기간

    // === 개별 거래 목록 ===
    private List<TradeDto> trades;

    // === 차트 데이터 ===
    private List<String> equityLabels; // 자산 곡선 날짜
    private List<BigDecimal> equityCurve; // 자산 곡선
    private List<BigDecimal> drawdownCurve; // 낙폭 곡선
    private List<BigDecimal> benchmarkCurve; // 벤치마크 곡선 (Buy & Hold)

    // === 월별 성과 ===
    private List<MonthlyPerformance> monthlyPerformance;

    // === 실행 정보 ===
    private LocalDateTime executedAt;
    private Long executionTimeMs;

    /** 개별 거래 정보 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeDto {
        private Integer tradeNumber;
        private String symbol;
        private LocalDate entryDate;
        private LocalDate exitDate;
        private BigDecimal entryPrice;
        private BigDecimal exitPrice;
        private BigDecimal quantity;
        private BigDecimal profit;
        private BigDecimal profitPercent;
        private Integer holdingDays;
        private String entrySignal;
        private String exitSignal;
        private BigDecimal portfolioValueAtEntry;
        private BigDecimal portfolioValueAtExit;
    }

    /** 월별 성과 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPerformance {
        private String month; // YYYY-MM
        private BigDecimal returnPct; // 월간 수익률 (%)
        private Integer tradeCount; // 거래 횟수
        private BigDecimal profit; // 손익 금액
    }
}
