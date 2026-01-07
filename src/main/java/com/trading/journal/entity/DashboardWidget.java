package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 대시보드 위젯 설정
 */
@Entity
@Table(name = "dashboard_widgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 위젯 고유 키 (프론트엔드 식별용)
     */
    @Column(nullable = false)
    private String widgetKey;

    /**
     * 위젯 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WidgetType widgetType;

    /**
     * 위젯 제목 (사용자 정의 가능)
     */
    private String title;

    /**
     * 그리드 X 위치 (0부터 시작)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer gridX = 0;

    /**
     * 그리드 Y 위치 (0부터 시작)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer gridY = 0;

    /**
     * 위젯 너비 (그리드 단위, 1-12)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer width = 4;

    /**
     * 위젯 높이 (그리드 단위)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer height = 2;

    /**
     * 표시 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean visible = true;

    /**
     * 순서 (정렬용)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * 추가 설정 (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String settings;

    /**
     * 소속 대시보드 설정
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_config_id")
    private DashboardConfig dashboardConfig;
}
