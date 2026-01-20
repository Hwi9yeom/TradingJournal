package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummaryDto {
    private BigDecimal totalInvestment;
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalProfitLossPercent;
    private BigDecimal totalDayChange;
    private BigDecimal totalDayChangePercent;

    /** 실현 손익 (FIFO 기반 매도 거래의 손익 합계) */
    private BigDecimal totalRealizedPnl;

    private List<PortfolioDto> holdings;
    private LocalDateTime lastUpdated;
}
