package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisclosureDto {
    private Long id;
    private String stockSymbol;
    private String stockName;
    private String reportNumber;
    private String corpCode;
    private String corpName;
    private String reportName;
    private LocalDateTime receivedDate;
    private String submitter;
    private String reportType;
    private String viewUrl;
    private String summary;
    private Boolean isImportant;
    private Boolean isRead;
    private LocalDateTime createdAt;
    
    // DART API 응답 매핑용 필드
    private String rcpNo; // 접수번호
    private String flrNm; // 공시 제출인명
    private String rcpDt; // 접수일자 (YYYYMMDD)
    private String rmk; // 비고
}