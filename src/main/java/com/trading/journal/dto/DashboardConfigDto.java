package com.trading.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대시보드 설정 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardConfigDto {
    private Long id;
    private Long userId;
    private String configName;
    private Boolean active;
    private Integer gridColumns;
    private Boolean compactMode;
    private Integer refreshInterval;
    private String theme;
    private List<DashboardWidgetDto> widgets;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
