package com.trading.journal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 목표 배분 배치 설정용 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetAllocationBatchDto {

    private Long accountId;

    @NotEmpty(message = "목표 배분 목록이 비어있습니다")
    @Valid
    private List<TargetAllocationDto> allocations;

    /** 기존 배분 덮어쓰기 여부 */
    @Builder.Default private Boolean replaceExisting = false;
}
