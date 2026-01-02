package com.trading.journal.dto;

import com.trading.journal.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * FIFO 계산 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FifoResult {

    /** 실현 손익 */
    private BigDecimal realizedPnl;

    /** FIFO 기반 총 매수 원가 */
    private BigDecimal costBasis;

    /** 소진된 매수 거래 목록 */
    private List<BuyConsumption> consumptions;

    /**
     * 매수 거래 소진 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BuyConsumption {
        /** 소진된 매수 거래 */
        private Transaction buyTransaction;

        /** 소진된 수량 */
        private BigDecimal consumedQuantity;

        /** 소진된 원가 (수량 × 단가) */
        private BigDecimal consumedCost;
    }
}
