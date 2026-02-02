package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 스트레스 테스트 결과 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StressTestResultDto {

    // === 시나리오 정보 ===
    /** 시나리오 ID */
    private Long scenarioId;

    /** 시나리오 코드 */
    private String scenarioCode;

    /** 시나리오 이름 */
    private String scenarioName;

    /** 시나리오 설명 */
    private String scenarioDescription;

    /** 적용된 충격 비율 (%) */
    private BigDecimal shockPercent;

    // === 포트폴리오 가치 ===
    /** 스트레스 테스트 전 포트폴리오 가치 */
    private BigDecimal portfolioValueBefore;

    /** 스트레스 테스트 후 포트폴리오 가치 */
    private BigDecimal portfolioValueAfter;

    // === 영향 지표 ===
    /** 절대 손실 금액 */
    private BigDecimal absoluteLoss;

    /** 손실 비율 (%) */
    private BigDecimal percentageLoss;

    /** 최대 손실 금액 (가장 영향을 많이 받은 포지션) */
    private BigDecimal maxPositionLoss;

    /** 최소 손실 금액 (가장 영향을 적게 받은 포지션) */
    private BigDecimal minPositionLoss;

    /** 평균 포지션 손실 비율 (%) */
    private BigDecimal avgPositionLossPercent;

    // === 포지션별 영향 ===
    /** 포지션별 영향 상세 */
    private List<PositionImpact> positionImpacts;

    // === 섹터별 영향 ===
    /** 섹터별 영향 상세 */
    private List<SectorImpact> sectorImpacts;

    // === 실행 정보 ===
    /** 테스트 실행 시간 */
    private LocalDateTime executedAt;

    /** 계좌 ID */
    private Long accountId;

    /** 계좌 이름 */
    private String accountName;

    /** 포지션별 영향 상세 정보 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionImpact {
        /** 종목 심볼 */
        private String symbol;

        /** 종목명 */
        private String stockName;

        /** 섹터 */
        private String sector;

        /** 보유 수량 */
        private BigDecimal quantity;

        /** 스트레스 테스트 전 포지션 가치 */
        private BigDecimal valueBefore;

        /** 스트레스 테스트 후 포지션 가치 */
        private BigDecimal valueAfter;

        /** 절대 손실 금액 */
        private BigDecimal absoluteLoss;

        /** 손실 비율 (%) */
        private BigDecimal impactPercent;

        /** 포트폴리오 전체 손실에서 차지하는 비중 (%) */
        private BigDecimal contributionToTotalLoss;
    }

    /** 섹터별 영향 상세 정보 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorImpact {
        /** 섹터명 */
        private String sectorName;

        /** 섹터에 적용된 충격 비율 (%) */
        private BigDecimal sectorShockPercent;

        /** 섹터 내 포지션 수 */
        private Integer positionCount;

        /** 섹터 총 가치 (테스트 전) */
        private BigDecimal totalValueBefore;

        /** 섹터 총 가치 (테스트 후) */
        private BigDecimal totalValueAfter;

        /** 섹터 절대 손실 금액 */
        private BigDecimal absoluteLoss;

        /** 섹터 손실 비율 (%) */
        private BigDecimal lossPercent;

        /** 포트폴리오 전체 손실에서 차지하는 비중 (%) */
        private BigDecimal contributionToTotalLoss;
    }
}
