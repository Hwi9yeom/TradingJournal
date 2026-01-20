package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 롤링 상관관계 분석 결과 DTO 시간에 따른 두 종목 간 상관관계 변화를 추적 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingCorrelationDto {

    private String symbol1;
    private String symbol2;
    private String name1;
    private String name2;

    /** 롤링 윈도우 각 시점의 날짜 */
    private List<LocalDate> dates;

    /** 각 시점의 상관계수 (-1 ~ +1) */
    private List<BigDecimal> correlations;

    /** 롤링 윈도우 크기 (일) */
    private int windowDays;

    /** 분석 기간 (일) */
    private int periodDays;

    /** 현재 상관계수 (가장 최근) */
    private BigDecimal currentCorrelation;

    /** 평균 상관계수 */
    private BigDecimal averageCorrelation;

    /** 최고 상관계수 */
    private BigDecimal maxCorrelation;

    /** 최저 상관계수 */
    private BigDecimal minCorrelation;

    /** 상관계수 변동성 (표준편차) */
    private BigDecimal correlationVolatility;
}
