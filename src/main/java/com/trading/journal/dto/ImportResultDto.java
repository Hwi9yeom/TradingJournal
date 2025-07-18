package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {
    private int totalRows;
    private int successCount;
    private int failureCount;
    @Builder.Default
    private List<ImportErrorDto> errors = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportErrorDto {
        private int rowNumber;
        private String message;
        private ImportTransactionDto data;
    }
}