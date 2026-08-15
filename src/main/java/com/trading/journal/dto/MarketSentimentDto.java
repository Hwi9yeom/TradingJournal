package com.trading.journal.dto;

import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.entity.SentimentMarket;
import com.trading.journal.entity.SentimentZone;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 시장 심리 지표 기록 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSentimentDto {
    private Long id;
    private SentimentIndicator indicator;
    private LocalDate recordedDate;
    private BigDecimal value;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 지표 메타데이터 / 판정 결과 (서버 계산) ----

    /** 지표 표시 이름 */
    private String indicatorLabel;

    /** 소속 시장 */
    private SentimentMarket market;

    /** 값의 단위 */
    private String unit;

    /** 참고 출처 URL */
    private String sourceUrl;

    /** 지표 해석 메모 */
    private String interpretation;

    /** 판정 구간 */
    private SentimentZone zone;

    /** 판정 구간 라벨 */
    private String zoneLabel;

    /** 판정 구간별 대응 가이드 */
    private String zoneAction;

    /** 정규화 점수 (-2 ~ +2) */
    private Integer zoneScore;

    /** 기록 경과일 (오늘 기준). 오래된 값 경고용 */
    private Long daysSinceRecorded;
}
