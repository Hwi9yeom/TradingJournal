package com.trading.journal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 스트레스 테스트 요청 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StressTestRequestDto {

    /** 계좌 ID (필수) */
    @NotNull(message = "계좌 ID는 필수입니다")
    @Positive(message = "계좌 ID는 양수여야 합니다")
    private Long accountId;

    /** 시나리오 코드 (scenarioId와 둘 중 하나만 필요) 예: "COVID_2020", "DOTCOM_BUBBLE" */
    private String scenarioCode;

    /** 시나리오 ID (scenarioCode와 둘 중 하나만 필요) */
    private Long scenarioId;

    /** 사용자 정의 충격 비율 (%) - 선택적 시나리오의 기본값을 오버라이드 예: -25.0 = 25% 하락 시나리오 */
    private BigDecimal customShockPercent;

    /** 요청 검증 scenarioCode 또는 scenarioId 중 최소 하나는 제공되어야 함 */
    public boolean hasValidScenario() {
        return (scenarioCode != null && !scenarioCode.isBlank()) || scenarioId != null;
    }
}
