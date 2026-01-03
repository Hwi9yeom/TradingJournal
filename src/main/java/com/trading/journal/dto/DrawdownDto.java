package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Drawdown (최대 낙폭) 분석 DTO
 * 고점 대비 하락률을 시각화하여 리스크 분석에 활용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawdownDto {
    /** 날짜 레이블 목록 (yyyy-MM-dd 형식) */
    private List<String> labels;

    /** Drawdown 값 목록 (%, 음수) */
    private List<BigDecimal> drawdowns;

    /** 최대 낙폭 (Maximum Drawdown, %) */
    private BigDecimal maxDrawdown;

    /** 최대 낙폭 발생일 */
    private LocalDate maxDrawdownDate;

    /** 최대 낙폭 시작일 (고점) */
    private LocalDate peakDate;

    /** 최대 낙폭에서 회복까지 걸린 일수 */
    private Integer recoveryDays;

    /** 현재 Drawdown 상태 (%) */
    private BigDecimal currentDrawdown;

    /** Drawdown 이벤트 목록 (5% 이상 하락 구간) */
    private List<DrawdownEvent> majorDrawdowns;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DrawdownEvent {
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate recoveryDate;
        private BigDecimal maxDrawdown;
        private Integer duration;
        private Integer recoveryDays;
    }
}
