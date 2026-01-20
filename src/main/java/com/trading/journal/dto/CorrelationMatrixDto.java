package com.trading.journal.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 종목 간 상관관계 매트릭스 DTO 보유 종목들의 수익률 상관관계를 분석하여 분산투자 효과 측정 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrelationMatrixDto {
    /** 종목 심볼 목록 (행/열 라벨) */
    private List<String> symbols;

    /** 종목명 목록 */
    private List<String> names;

    /** 상관관계 매트릭스 (2D 배열, -1 ~ 1 범위) */
    private List<List<BigDecimal>> matrix;

    /** 분석 기간 (일) */
    private Integer periodDays;

    /** 분석에 사용된 데이터 포인트 수 */
    private Integer dataPoints;

    /** 평균 상관계수 */
    private BigDecimal averageCorrelation;

    /** 분산투자 효과 점수 (0-100, 낮을수록 분산 효과 좋음) */
    private BigDecimal diversificationScore;
}
