package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 리스크 지표 종합 DTO VaR, Sortino, Calmar, Information Ratio 등 고급 리스크 메트릭스 포함 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskMetricsDto {
    /** 샤프 비율 (Sharpe Ratio) - (수익률 - 무위험이자율) / 표준편차 */
    private BigDecimal sharpeRatio;

    /** 소르티노 비율 (Sortino Ratio) - (수익률 - 무위험이자율) / 하락편차 */
    private BigDecimal sortinoRatio;

    /** 칼마 비율 (Calmar Ratio) - CAGR / 최대낙폭 */
    private BigDecimal calmarRatio;

    /** 정보 비율 (Information Ratio) - 초과수익 / 추적오차 */
    private BigDecimal informationRatio;

    /** 95% 신뢰수준 VaR */
    private VaRDto var95;

    /** 99% 신뢰수준 VaR */
    private VaRDto var99;

    /** 최대 낙폭 (Maximum Drawdown, %) */
    private BigDecimal maxDrawdown;

    /** 변동성 (연환산 표준편차, %) */
    private BigDecimal volatility;

    /** 하락 변동성 (Downside Deviation, %) */
    private BigDecimal downsideDeviation;

    /** 베타 (시장 대비 민감도) */
    private BigDecimal beta;

    /** 알파 (초과 수익률) */
    private BigDecimal alpha;

    /** 연평균 수익률 (CAGR, %) */
    private BigDecimal cagr;

    /** 승률 (%) */
    private BigDecimal winRate;

    /** 손익비 (Profit Factor) */
    private BigDecimal profitFactor;

    /** 리스크 등급 (LOW, MEDIUM, HIGH) */
    private RiskLevel riskLevel;

    /** 분석 시작일 */
    private LocalDate startDate;

    /** 분석 종료일 */
    private LocalDate endDate;

    /** 총 거래일 수 */
    private Integer tradingDays;

    /** VaR (Value at Risk) 상세 DTO Historical VaR 방식으로 계산된 최대 예상 손실 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VaRDto {
        /** 일간 VaR (%) - 하루 동안 예상 최대 손실 */
        private BigDecimal dailyVaR;

        /** 주간 VaR (%) - 일주일 동안 예상 최대 손실 */
        private BigDecimal weeklyVaR;

        /** 월간 VaR (%) - 한 달 동안 예상 최대 손실 */
        private BigDecimal monthlyVaR;

        /** 금액 기준 일간 VaR */
        private BigDecimal dailyVaRAmount;

        /** 신뢰 수준 (0.95 또는 0.99) */
        private BigDecimal confidenceLevel;
    }

    /** 리스크 등급 Enum */
    public enum RiskLevel {
        LOW("낮음", "안정적인 포트폴리오"),
        MEDIUM("중간", "적절한 리스크 수준"),
        HIGH("높음", "공격적인 포트폴리오");

        private final String label;
        private final String description;

        RiskLevel(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }
}
