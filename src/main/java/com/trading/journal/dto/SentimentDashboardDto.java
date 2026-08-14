package com.trading.journal.dto;

import com.trading.journal.entity.SentimentZone;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 시장 심리 종합 대시보드 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentimentDashboardDto {

    /** 종합 판정 구간 (기록된 지표 전체 평균). 기록이 없으면 null */
    private SentimentZone overallZone;

    /** 종합 판정 라벨 */
    private String overallLabel;

    /** 종합 대응 가이드 */
    private String overallAction;

    /** 종합 평균 점수 (-2 ~ +2) */
    private BigDecimal overallScore;

    /** 시장별 평균 점수 (US_STOCK / CRYPTO) */
    private Map<String, BigDecimal> scoreByMarket;

    /** 시장별 판정 구간 */
    private Map<String, SentimentZone> zoneByMarket;

    /** 지표별 최신 기록 (기록이 있는 지표만) */
    private List<MarketSentimentDto> latestByIndicator;

    /** 아직 한 번도 기록되지 않은 지표 이름 목록 */
    private List<String> missingIndicators;

    /** 판정 기준일 이전이라 오래된 것으로 간주된 지표 이름 목록 */
    private List<String> staleIndicators;
}
