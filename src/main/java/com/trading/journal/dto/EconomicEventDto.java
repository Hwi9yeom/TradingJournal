package com.trading.journal.dto;

import com.trading.journal.entity.EconomicEventType;
import com.trading.journal.entity.EventImportance;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 경제 이벤트 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EconomicEventDto {
    private Long id;
    private LocalDateTime eventTime;
    private String country;
    private String eventName;
    private EconomicEventType eventType;
    private EventImportance importance;
    private String actual;
    private String forecast;
    private String previous;
    private String unit;
    private String currency;
    private String symbol;
    private Double epsEstimate;
    private Double epsActual;
    private Double revenueEstimate;
    private Double revenueActual;
    private String notes;
    private Boolean alertEnabled;
    private LocalDateTime createdAt;

    // 추가 표시 필드
    private String eventTypeLabel;
    private String importanceLabel;
    private String countryFlag;
    private String timeFormatted;
    private Boolean isPast;
    private Boolean isToday;
    private Double epsSurprise;
    private Double revenueSurprise;

    /** 보유 종목 여부 (포트폴리오 연동) */
    private Boolean isInPortfolio;
}
