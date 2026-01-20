package com.trading.journal.dto;

import com.trading.journal.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AccountDto {
    private Long id;

    @NotBlank(message = "계좌 이름은 필수입니다")
    private String name;

    @NotNull(message = "계좌 유형은 필수입니다")
    private AccountType accountType;

    private String description;

    private Boolean isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 요약 정보 (조회 시 포함)
    private BigDecimal totalInvestment;
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal profitLossPercent;
    private Integer holdingCount;
}
