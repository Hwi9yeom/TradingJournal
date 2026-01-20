package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioDto {
    private Long id;
    private String stockSymbol;
    private String stockName;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal totalInvestment;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private BigDecimal dayChange;
    private BigDecimal dayChangePercent;
    private LocalDateTime lastUpdated;
}
