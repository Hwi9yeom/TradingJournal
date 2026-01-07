package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자별 대시보드 설정
 */
@Entity
@Table(name = "dashboard_configs", indexes = {
        @Index(name = "idx_dashboard_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 설정 이름 (여러 레이아웃 저장 가능)
     */
    @Column(nullable = false)
    @Builder.Default
    private String configName = "default";

    /**
     * 활성화 여부 (현재 사용 중인 설정)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * 그리드 컬럼 수 (기본 12)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer gridColumns = 12;

    /**
     * 컴팩트 모드 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean compactMode = false;

    /**
     * 자동 새로고침 간격 (초, 0이면 비활성)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer refreshInterval = 60;

    /**
     * 테마 (light/dark)
     */
    @Builder.Default
    private String theme = "light";

    /**
     * 위젯 목록
     */
    @OneToMany(mappedBy = "dashboardConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DashboardWidget> widgets = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * 위젯 추가
     */
    public void addWidget(DashboardWidget widget) {
        widgets.add(widget);
        widget.setDashboardConfig(this);
    }

    /**
     * 위젯 제거
     */
    public void removeWidget(DashboardWidget widget) {
        widgets.remove(widget);
        widget.setDashboardConfig(null);
    }

    /**
     * 모든 위젯 교체
     */
    public void replaceWidgets(List<DashboardWidget> newWidgets) {
        widgets.clear();
        for (DashboardWidget widget : newWidgets) {
            addWidget(widget);
        }
    }
}
