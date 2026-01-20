package com.trading.journal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendDto {
    private Long id;
    private Long stockId;
    private String stockSymbol;
    private String stockName;
    private LocalDate exDividendDate;
    private LocalDate paymentDate;
    private BigDecimal dividendPerShare;
    private BigDecimal quantity;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal taxRate; // 세율 (%)
    private String memo;
}
