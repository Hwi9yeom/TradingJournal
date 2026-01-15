package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 종목 쌍 상관관계 상세 분석 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairCorrelationDto {

    private String symbol1;
    private String symbol2;
    private String name1;
    private String name2;
    private String sector1;
    private String sector2;

    /**
     * 현재 상관계수
     */
    private BigDecimal correlation;

    /**
     * 분석 기간
     */
    private LocalDate startDate;
    private LocalDate endDate;
    private int periodDays;

    /**
     * 일별 수익률 데이터 (차트용)
     */
    private List<LocalDate> dates;
    private List<BigDecimal> returns1;
    private List<BigDecimal> returns2;

    /**
     * 누적 수익률 데이터 (차트용)
     */
    private List<BigDecimal> cumulativeReturns1;
    private List<BigDecimal> cumulativeReturns2;

    /**
     * 통계
     */
    private BigDecimal avgReturn1;
    private BigDecimal avgReturn2;
    private BigDecimal volatility1;
    private BigDecimal volatility2;

    /**
     * 분산투자 효과 점수 (이 두 종목 조합의)
     */
    private BigDecimal diversificationBenefit;
}
