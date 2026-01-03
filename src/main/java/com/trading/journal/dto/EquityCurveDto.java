package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Equity Curve (누적 수익 곡선) DTO
 * 시간에 따른 포트폴리오 가치와 누적 수익률을 추적
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquityCurveDto {
    /** 날짜 레이블 목록 (yyyy-MM-dd 형식) */
    private List<String> labels;

    /** 포트폴리오 가치 목록 */
    private List<BigDecimal> values;

    /** 누적 수익률 목록 (%) */
    private List<BigDecimal> cumulativeReturns;

    /** 초기 투자금액 */
    private BigDecimal initialInvestment;

    /** 최종 포트폴리오 가치 */
    private BigDecimal finalValue;

    /** 전체 수익률 (%) */
    private BigDecimal totalReturn;

    /** 연평균 수익률 (CAGR) (%) */
    private BigDecimal cagr;
}
