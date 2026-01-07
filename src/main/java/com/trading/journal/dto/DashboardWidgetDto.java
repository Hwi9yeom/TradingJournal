package com.trading.journal.dto;

import com.trading.journal.entity.WidgetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 대시보드 위젯 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardWidgetDto {
    private Long id;
    private String widgetKey;
    private WidgetType widgetType;
    private String title;
    private Integer gridX;
    private Integer gridY;
    private Integer width;
    private Integer height;
    private Boolean visible;
    private Integer displayOrder;
    private String settings;

    // 추가 표시 필드
    private String widgetTypeLabel;
    private String iconClass;
    private String defaultTitle;
}
