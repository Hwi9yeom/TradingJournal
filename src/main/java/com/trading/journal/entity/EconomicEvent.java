package com.trading.journal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 경제 캘린더 이벤트 엔티티 */
@Entity
@Table(
        name = "economic_events",
        indexes = {
            @Index(name = "idx_event_time", columnList = "eventTime"),
            @Index(name = "idx_event_type", columnList = "eventType"),
            @Index(name = "idx_event_country", columnList = "country"),
            @Index(name = "idx_event_symbol", columnList = "symbol"),
            @Index(name = "idx_event_importance", columnList = "importance")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_event_unique",
                    columnNames = {"eventTime", "eventName", "country", "symbol"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EconomicEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트 발생 시간 (UTC) */
    @Column(nullable = false)
    private LocalDateTime eventTime;

    /** 국가 코드 (US, KR, EU, JP, CN 등) */
    @Column(nullable = false, length = 10)
    private String country;

    /** 이벤트 이름 */
    @Column(nullable = false)
    private String eventName;

    /** 이벤트 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EconomicEventType eventType;

    /** 이벤트 중요도 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EventImportance importance = EventImportance.MEDIUM;

    /** 실제 값 */
    private String actual;

    /** 예상 값 */
    private String forecast;

    /** 이전 값 */
    private String previous;

    /** 단위 (%, M, B 등) */
    private String unit;

    /** 영향받는 통화 (USD, KRW 등) */
    @Column(length = 10)
    private String currency;

    /** 관련 종목 심볼 (실적발표/배당의 경우) */
    @Column(length = 20)
    private String symbol;

    /** 실적발표 - 예상 EPS */
    private Double epsEstimate;

    /** 실적발표 - 실제 EPS */
    private Double epsActual;

    /** 실적발표 - 예상 매출 */
    private Double revenueEstimate;

    /** 실적발표 - 실제 매출 */
    private Double revenueActual;

    /** 외부 소스 ID (Finnhub 등) */
    private String externalId;

    /** 데이터 소스 */
    @Column(length = 50)
    @Builder.Default
    private String source = "FINNHUB";

    /** 사용자 메모 */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** 알림 설정 여부 */
    @Builder.Default private Boolean alertEnabled = false;

    /** 생성 시간 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시간 */
    @UpdateTimestamp private LocalDateTime updatedAt;

    /** 이벤트가 지났는지 확인 */
    public boolean isPast() {
        return eventTime != null && LocalDateTime.now().isAfter(eventTime);
    }

    /** 오늘 이벤트인지 확인 */
    public boolean isToday() {
        if (eventTime == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return eventTime.toLocalDate().equals(now.toLocalDate());
    }

    /** 실적발표 서프라이즈 계산 (%) */
    public Double getEpsSurprise() {
        if (epsEstimate == null || epsActual == null || epsEstimate == 0) {
            return null;
        }
        return ((epsActual - epsEstimate) / Math.abs(epsEstimate)) * 100;
    }

    /** 매출 서프라이즈 계산 (%) */
    public Double getRevenueSurprise() {
        if (revenueEstimate == null || revenueActual == null || revenueEstimate == 0) {
            return null;
        }
        return ((revenueActual - revenueEstimate) / Math.abs(revenueEstimate)) * 100;
    }
}
