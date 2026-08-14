package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 시장 심리 지표 기록.
 *
 * <p>지표 값 자체는 외부 사이트에서 눈으로 확인해 옮겨 적는다(대부분 공개 API가 없다). 저장된 값은 {@code SentimentEvaluator}가 지표별 임계값으로
 * 판정해 공포/탐욕 구간으로 정규화한다.
 *
 * <p>같은 지표를 하루에 두 번 적으면 덮어쓴다 — 하루 한 스냅샷이 목적이다.
 */
@Entity
@Table(
        name = "market_sentiments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"indicator", "recorded_date"}),
        indexes = {
            @Index(name = "idx_sentiment_date", columnList = "recorded_date"),
            @Index(name = "idx_sentiment_indicator", columnList = "indicator")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSentiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지표 종류 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SentimentIndicator indicator;

    /** 기록 일자 */
    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    /** 지표 값 (스케일은 지표마다 다르다) */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    /** 메모 (관찰 맥락, 예외 상황 등) */
    @Column(length = 1000)
    private String notes;

    /** 생성 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
