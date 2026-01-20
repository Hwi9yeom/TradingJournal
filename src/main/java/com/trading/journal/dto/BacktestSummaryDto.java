package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 백테스트 목록 조회용 요약 DTO 히스토리 조회 시 불필요한 상세 데이터 변환을 방지하기 위해 사용 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestSummaryDto {

    private Long id;

    // === 기본 정보 ===
    private String strategyName;
    private String strategyType;
    private String symbol;
    private LocalDate startDate;
    private LocalDate endDate;

    // === 핵심 성과 지표 ===
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal totalReturn; // 총 수익률 (%)
    private BigDecimal cagr; // 연평균 수익률 (%)
    private BigDecimal maxDrawdown; // 최대 낙폭 (%)
    private BigDecimal sharpeRatio; // 샤프 비율
    private BigDecimal profitFactor; // 손익비

    // === 거래 요약 ===
    private Integer totalTrades; // 총 거래 횟수
    private BigDecimal winRate; // 승률 (%)

    // === 실행 정보 ===
    private LocalDateTime executedAt;
    private Long executionTimeMs;
}
