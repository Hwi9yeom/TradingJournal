package com.trading.journal.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** 트레이딩 일지 엔티티 일별 거래 기록, 시장 분석, 감정 상태, 교훈 등을 기록 */
@Entity
@Table(
        name = "trading_journals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "journal_date"}),
        indexes = {
            @Index(name = "idx_journal_date", columnList = "journal_date"),
            @Index(name = "idx_journal_account", columnList = "account_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계좌 ID (null이면 전체 계좌) */
    @Column(name = "account_id")
    private Long accountId;

    /** 일지 날짜 */
    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    /** 시장 개요 */
    @Column(columnDefinition = "TEXT")
    private String marketOverview;

    /** 오늘의 거래 계획 */
    @Column(columnDefinition = "TEXT")
    private String tradingPlan;

    /** 실행 리뷰 */
    @Column(columnDefinition = "TEXT")
    private String executionReview;

    /** 오전 감정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmotionState morningEmotion;

    /** 오후 감정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmotionState eveningEmotion;

    /** 집중도 점수 (1-5) */
    @Column private Integer focusScore;

    /** 규율 준수 점수 (1-5) */
    @Column private Integer disciplineScore;

    /** 오늘의 교훈 */
    @Column(columnDefinition = "TEXT")
    private String lessonsLearned;

    /** 내일 계획 */
    @Column(columnDefinition = "TEXT")
    private String tomorrowPlan;

    /** 태그 (쉼표 구분) */
    @Column(length = 500)
    private String tags;

    /** 거래 요약 - 총 거래 수 */
    @Column private Integer tradeSummaryCount;

    /** 거래 요약 - 총 손익 */
    @Column private java.math.BigDecimal tradeSummaryProfit;

    /** 거래 요약 - 승률 */
    @Column private java.math.BigDecimal tradeSummaryWinRate;

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
