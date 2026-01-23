package com.trading.journal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 목표 배분 CRUD용 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetAllocationDto {

    private Long id;

    private Long accountId;

    @NotNull(message = "종목 ID는 필수입니다")
    private Long stockId;

    /** 종목 심볼 (응답용) */
    private String stockSymbol;

    /** 종목명 (응답용) */
    private String stockName;

    @NotNull(message = "목표 배분율은 필수입니다")
    @DecimalMin(value = "0.01", message = "목표 배분율은 0.01% 이상이어야 합니다")
    @DecimalMax(value = "100", message = "목표 배분율은 100% 이하여야 합니다")
    private BigDecimal targetPercent;

    @DecimalMin(value = "0", message = "드리프트 임계값은 0 이상이어야 합니다")
    @DecimalMax(value = "100", message = "드리프트 임계값은 100% 이하여야 합니다")
    @Builder.Default
    private BigDecimal driftThresholdPercent = BigDecimal.valueOf(5);

    @Builder.Default private Boolean isActive = true;

    @Min(value = 0, message = "우선순위는 0 이상이어야 합니다")
    @Builder.Default
    private Integer priority = 0;

    @Size(max = 500, message = "메모는 500자 이하여야 합니다")
    private String notes;
}
