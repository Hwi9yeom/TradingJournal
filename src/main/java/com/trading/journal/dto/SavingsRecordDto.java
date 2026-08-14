package com.trading.journal.dto;

import com.trading.journal.entity.SavingsCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 저축 기록 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsRecordDto {
    private Long id;
    private Long accountId;
    private LocalDate savedDate;
    private BigDecimal amount;
    private SavingsCategory category;
    private String institution;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 표시용 분류 라벨 */
    private String categoryLabel;

    /** 표시용 월 라벨 (예: 2026-07) */
    private String monthLabel;
}
