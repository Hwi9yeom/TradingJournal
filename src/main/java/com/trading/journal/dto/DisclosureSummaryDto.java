package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisclosureSummaryDto {
    private Long totalCount;
    private Long unreadCount;
    private Long importantCount;
    private List<DisclosureDto> recentDisclosures;
    private List<DisclosureDto> importantDisclosures;
    private List<DisclosureDto> unreadDisclosures;
}