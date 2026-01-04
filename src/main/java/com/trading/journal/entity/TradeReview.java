package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 거래 복기/일지 엔티티
 * 각 거래에 대한 진입/청산 이유, 감정 상태, 교훈 등을 기록
 */
@Entity
@Table(name = "trade_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 매도 거래 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** 거래 전략 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TradeStrategy strategy;

    /** 진입 이유 */
    @Column(columnDefinition = "TEXT")
    private String entryReason;

    /** 청산 이유 */
    @Column(columnDefinition = "TEXT")
    private String exitReason;

    /** 거래 전 감정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmotionState emotionBefore;

    /** 거래 후 감정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmotionState emotionAfter;

    /** 복기 내용 */
    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    /** 배운 점/교훈 */
    @Column(columnDefinition = "TEXT")
    private String lessonsLearned;

    /** 자기 평가 점수 (1-5) */
    @Column
    private Integer ratingScore;

    /** 태그 (쉼표로 구분) */
    @Column(length = 500)
    private String tags;

    /** 거래가 계획대로 실행되었는지 */
    @Column
    private Boolean followedPlan;

    /** 스크린샷 경로 */
    @Column(length = 500)
    private String screenshotPath;

    /** 복기 작성 시간 */
    @Column
    private LocalDateTime reviewedAt;

    /** 생성 시간 */
    @Column
    private LocalDateTime createdAt;

    /** 수정 시간 */
    @Column
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
