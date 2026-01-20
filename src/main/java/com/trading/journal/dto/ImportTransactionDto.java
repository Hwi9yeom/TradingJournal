package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportTransactionDto {
    private String date; // 거래일자
    private String stockCode; // 종목코드
    private String stockName; // 종목명
    private String transactionType; // 거래구분 (매수/매도)
    private String quantity; // 수량
    private String price; // 단가
    private String amount; // 금액
    private String commission; // 수수료
    private String tax; // 세금
    private String notes; // 비고
}
