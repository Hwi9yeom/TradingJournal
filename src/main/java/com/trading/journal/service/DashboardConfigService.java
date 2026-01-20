package com.trading.journal.service;

import com.trading.journal.dto.DashboardConfigDto;
import com.trading.journal.dto.DashboardWidgetDto;
import com.trading.journal.entity.DashboardConfig;
import com.trading.journal.entity.DashboardWidget;
import com.trading.journal.entity.WidgetType;
import com.trading.journal.repository.DashboardConfigRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 설정 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardConfigService {

    private final DashboardConfigRepository dashboardConfigRepository;

    /** 사용자의 활성 대시보드 설정 조회 (없으면 기본 생성) */
    @Transactional
    public DashboardConfigDto getActiveDashboardConfig(Long userId) {
        DashboardConfig config =
                dashboardConfigRepository
                        .findActiveWithWidgets(userId)
                        .orElseGet(() -> createDefaultConfig(userId));

        return convertToDto(config);
    }

    /** 대시보드 설정 저장 */
    @Transactional
    public DashboardConfigDto saveDashboardConfig(Long userId, DashboardConfigDto dto) {
        DashboardConfig config =
                dashboardConfigRepository
                        .findByUserIdAndActiveTrue(userId)
                        .orElseGet(
                                () ->
                                        DashboardConfig.builder()
                                                .userId(userId)
                                                .configName(
                                                        dto.getConfigName() != null
                                                                ? dto.getConfigName()
                                                                : "default")
                                                .active(true)
                                                .build());

        // 기본 설정 업데이트
        if (dto.getGridColumns() != null) config.setGridColumns(dto.getGridColumns());
        if (dto.getCompactMode() != null) config.setCompactMode(dto.getCompactMode());
        if (dto.getRefreshInterval() != null) config.setRefreshInterval(dto.getRefreshInterval());
        if (dto.getTheme() != null) config.setTheme(dto.getTheme());

        // 위젯 업데이트
        if (dto.getWidgets() != null) {
            List<DashboardWidget> newWidgets =
                    dto.getWidgets().stream()
                            .map(this::convertToEntity)
                            .collect(Collectors.toList());
            config.replaceWidgets(newWidgets);
        }

        DashboardConfig saved = dashboardConfigRepository.save(config);
        log.info("대시보드 설정 저장: userId={}, configId={}", userId, saved.getId());

        return convertToDto(saved);
    }

    /** 위젯 추가 */
    @Transactional
    public DashboardConfigDto addWidget(Long userId, DashboardWidgetDto widgetDto) {
        DashboardConfig config =
                dashboardConfigRepository
                        .findByUserIdAndActiveTrue(userId)
                        .orElseGet(() -> createDefaultConfig(userId));

        DashboardWidget widget = convertToEntity(widgetDto);
        widget.setDisplayOrder(config.getWidgets().size());
        config.addWidget(widget);

        DashboardConfig saved = dashboardConfigRepository.save(config);
        log.info("위젯 추가: userId={}, widgetType={}", userId, widgetDto.getWidgetType());

        return convertToDto(saved);
    }

    /** 위젯 제거 */
    @Transactional
    public DashboardConfigDto removeWidget(Long userId, String widgetKey) {
        DashboardConfig config =
                dashboardConfigRepository
                        .findByUserIdAndActiveTrue(userId)
                        .orElseThrow(() -> new IllegalArgumentException("대시보드 설정을 찾을 수 없습니다"));

        config.getWidgets().removeIf(w -> w.getWidgetKey().equals(widgetKey));

        DashboardConfig saved = dashboardConfigRepository.save(config);
        log.info("위젯 제거: userId={}, widgetKey={}", userId, widgetKey);

        return convertToDto(saved);
    }

    /** 위젯 순서/위치 업데이트 */
    @Transactional
    public DashboardConfigDto updateWidgetPositions(Long userId, List<DashboardWidgetDto> widgets) {
        DashboardConfig config =
                dashboardConfigRepository
                        .findByUserIdAndActiveTrue(userId)
                        .orElseThrow(() -> new IllegalArgumentException("대시보드 설정을 찾을 수 없습니다"));

        // 기존 위젯들의 위치 업데이트
        Map<String, DashboardWidget> existingWidgets =
                config.getWidgets().stream()
                        .collect(Collectors.toMap(DashboardWidget::getWidgetKey, w -> w));

        for (DashboardWidgetDto dto : widgets) {
            DashboardWidget widget = existingWidgets.get(dto.getWidgetKey());
            if (widget != null) {
                widget.setGridX(dto.getGridX());
                widget.setGridY(dto.getGridY());
                widget.setWidth(dto.getWidth());
                widget.setHeight(dto.getHeight());
                widget.setDisplayOrder(dto.getDisplayOrder());
                widget.setVisible(dto.getVisible());
            }
        }

        DashboardConfig saved = dashboardConfigRepository.save(config);
        log.info("위젯 위치 업데이트: userId={}", userId);

        return convertToDto(saved);
    }

    /** 대시보드 설정 초기화 (기본값으로) */
    @Transactional
    public DashboardConfigDto resetToDefault(Long userId) {
        dashboardConfigRepository
                .findByUserIdAndActiveTrue(userId)
                .ifPresent(
                        config -> {
                            config.setActive(false);
                            dashboardConfigRepository.save(config);
                        });

        DashboardConfig newConfig = createDefaultConfig(userId);
        log.info("대시보드 설정 초기화: userId={}", userId);

        return convertToDto(newConfig);
    }

    /** 사용 가능한 위젯 목록 */
    public List<DashboardWidgetDto> getAvailableWidgets() {
        return Arrays.stream(WidgetType.values())
                .map(
                        type ->
                                DashboardWidgetDto.builder()
                                        .widgetType(type)
                                        .widgetTypeLabel(getWidgetTypeLabel(type))
                                        .iconClass(getWidgetIcon(type))
                                        .defaultTitle(getDefaultTitle(type))
                                        .width(getDefaultWidth(type))
                                        .height(getDefaultHeight(type))
                                        .build())
                .collect(Collectors.toList());
    }

    /** 기본 대시보드 설정 생성 */
    private DashboardConfig createDefaultConfig(Long userId) {
        DashboardConfig config =
                DashboardConfig.builder()
                        .userId(userId)
                        .configName("default")
                        .active(true)
                        .gridColumns(12)
                        .compactMode(false)
                        .refreshInterval(60)
                        .theme("light")
                        .build();

        // 기본 위젯 추가
        List<DashboardWidget> defaultWidgets = createDefaultWidgets();
        for (DashboardWidget widget : defaultWidgets) {
            config.addWidget(widget);
        }

        return dashboardConfigRepository.save(config);
    }

    /** 기본 위젯 생성 */
    private List<DashboardWidget> createDefaultWidgets() {
        List<DashboardWidget> widgets = new ArrayList<>();
        int order = 0;

        // 첫 번째 행: 요약 카드들
        widgets.add(
                createWidget(
                        "portfolio-summary", WidgetType.PORTFOLIO_SUMMARY, 0, 0, 3, 2, order++));
        widgets.add(createWidget("profit-loss", WidgetType.PROFIT_LOSS_CARD, 3, 0, 3, 2, order++));
        widgets.add(
                createWidget(
                        "today-performance", WidgetType.TODAY_PERFORMANCE, 6, 0, 3, 2, order++));
        widgets.add(createWidget("goals-progress", WidgetType.GOALS_PROGRESS, 9, 0, 3, 2, order++));

        // 두 번째 행: 차트
        widgets.add(createWidget("equity-curve", WidgetType.EQUITY_CURVE, 0, 2, 8, 4, order++));
        widgets.add(createWidget("allocation-pie", WidgetType.ALLOCATION_PIE, 8, 2, 4, 4, order++));

        // 세 번째 행: 목록
        widgets.add(createWidget("holdings-list", WidgetType.HOLDINGS_LIST, 0, 6, 6, 4, order++));
        widgets.add(
                createWidget(
                        "recent-transactions",
                        WidgetType.RECENT_TRANSACTIONS,
                        6,
                        6,
                        6,
                        4,
                        order++));

        return widgets;
    }

    private DashboardWidget createWidget(
            String key, WidgetType type, int x, int y, int w, int h, int order) {
        return DashboardWidget.builder()
                .widgetKey(key)
                .widgetType(type)
                .title(getDefaultTitle(type))
                .gridX(x)
                .gridY(y)
                .width(w)
                .height(h)
                .visible(true)
                .displayOrder(order)
                .build();
    }

    /** Entity를 DTO로 변환 */
    private DashboardConfigDto convertToDto(DashboardConfig config) {
        List<DashboardWidgetDto> widgetDtos =
                config.getWidgets().stream()
                        .map(this::convertWidgetToDto)
                        .sorted(Comparator.comparing(DashboardWidgetDto::getDisplayOrder))
                        .collect(Collectors.toList());

        return DashboardConfigDto.builder()
                .id(config.getId())
                .userId(config.getUserId())
                .configName(config.getConfigName())
                .active(config.getActive())
                .gridColumns(config.getGridColumns())
                .compactMode(config.getCompactMode())
                .refreshInterval(config.getRefreshInterval())
                .theme(config.getTheme())
                .widgets(widgetDtos)
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private DashboardWidgetDto convertWidgetToDto(DashboardWidget widget) {
        return DashboardWidgetDto.builder()
                .id(widget.getId())
                .widgetKey(widget.getWidgetKey())
                .widgetType(widget.getWidgetType())
                .title(widget.getTitle())
                .gridX(widget.getGridX())
                .gridY(widget.getGridY())
                .width(widget.getWidth())
                .height(widget.getHeight())
                .visible(widget.getVisible())
                .displayOrder(widget.getDisplayOrder())
                .settings(widget.getSettings())
                .widgetTypeLabel(getWidgetTypeLabel(widget.getWidgetType()))
                .iconClass(getWidgetIcon(widget.getWidgetType()))
                .defaultTitle(getDefaultTitle(widget.getWidgetType()))
                .build();
    }

    private DashboardWidget convertToEntity(DashboardWidgetDto dto) {
        return DashboardWidget.builder()
                .widgetKey(
                        dto.getWidgetKey() != null
                                ? dto.getWidgetKey()
                                : UUID.randomUUID().toString())
                .widgetType(dto.getWidgetType())
                .title(
                        dto.getTitle() != null
                                ? dto.getTitle()
                                : getDefaultTitle(dto.getWidgetType()))
                .gridX(dto.getGridX() != null ? dto.getGridX() : 0)
                .gridY(dto.getGridY() != null ? dto.getGridY() : 0)
                .width(
                        dto.getWidth() != null
                                ? dto.getWidth()
                                : getDefaultWidth(dto.getWidgetType()))
                .height(
                        dto.getHeight() != null
                                ? dto.getHeight()
                                : getDefaultHeight(dto.getWidgetType()))
                .visible(dto.getVisible() != null ? dto.getVisible() : true)
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .settings(dto.getSettings())
                .build();
    }

    private String getWidgetTypeLabel(WidgetType type) {
        return switch (type) {
            case PORTFOLIO_SUMMARY -> "포트폴리오 요약";
            case TODAY_PERFORMANCE -> "오늘의 성과";
            case PROFIT_LOSS_CARD -> "총 손익";
            case HOLDINGS_COUNT -> "보유 종목 수";
            case EQUITY_CURVE -> "누적 수익률";
            case DRAWDOWN_CHART -> "낙폭 차트";
            case ALLOCATION_PIE -> "자산 배분";
            case MONTHLY_RETURNS -> "월별 수익률";
            case SECTOR_ALLOCATION -> "섹터별 비중";
            case HOLDINGS_LIST -> "보유 종목";
            case RECENT_TRANSACTIONS -> "최근 거래";
            case TOP_PERFORMERS -> "상위 수익 종목";
            case WORST_PERFORMERS -> "하위 수익 종목";
            case GOALS_PROGRESS -> "목표 진행";
            case ACTIVE_ALERTS -> "활성 알림";
            case RISK_METRICS -> "리스크 지표";
            case TRADING_STATS -> "거래 통계";
            case STREAK_INDICATOR -> "연승/연패";
        };
    }

    private String getWidgetIcon(WidgetType type) {
        return switch (type) {
            case PORTFOLIO_SUMMARY -> "bi-wallet2";
            case TODAY_PERFORMANCE -> "bi-calendar-day";
            case PROFIT_LOSS_CARD -> "bi-cash-stack";
            case HOLDINGS_COUNT -> "bi-collection";
            case EQUITY_CURVE -> "bi-graph-up";
            case DRAWDOWN_CHART -> "bi-graph-down";
            case ALLOCATION_PIE -> "bi-pie-chart";
            case MONTHLY_RETURNS -> "bi-bar-chart";
            case SECTOR_ALLOCATION -> "bi-diagram-3";
            case HOLDINGS_LIST -> "bi-list-ul";
            case RECENT_TRANSACTIONS -> "bi-clock-history";
            case TOP_PERFORMERS -> "bi-trophy";
            case WORST_PERFORMERS -> "bi-emoji-frown";
            case GOALS_PROGRESS -> "bi-bullseye";
            case ACTIVE_ALERTS -> "bi-bell";
            case RISK_METRICS -> "bi-shield-exclamation";
            case TRADING_STATS -> "bi-calculator";
            case STREAK_INDICATOR -> "bi-lightning";
        };
    }

    private String getDefaultTitle(WidgetType type) {
        return getWidgetTypeLabel(type);
    }

    private int getDefaultWidth(WidgetType type) {
        return switch (type) {
            case PORTFOLIO_SUMMARY,
                            TODAY_PERFORMANCE,
                            PROFIT_LOSS_CARD,
                            HOLDINGS_COUNT,
                            GOALS_PROGRESS,
                            ACTIVE_ALERTS,
                            RISK_METRICS,
                            TRADING_STATS,
                            STREAK_INDICATOR ->
                    3;
            case ALLOCATION_PIE, SECTOR_ALLOCATION -> 4;
            case EQUITY_CURVE, DRAWDOWN_CHART, MONTHLY_RETURNS -> 8;
            case HOLDINGS_LIST, RECENT_TRANSACTIONS, TOP_PERFORMERS, WORST_PERFORMERS -> 6;
        };
    }

    private int getDefaultHeight(WidgetType type) {
        return switch (type) {
            case PORTFOLIO_SUMMARY,
                            TODAY_PERFORMANCE,
                            PROFIT_LOSS_CARD,
                            HOLDINGS_COUNT,
                            STREAK_INDICATOR ->
                    2;
            case ALLOCATION_PIE,
                            GOALS_PROGRESS,
                            ACTIVE_ALERTS,
                            RISK_METRICS,
                            TRADING_STATS,
                            SECTOR_ALLOCATION ->
                    3;
            case EQUITY_CURVE,
                            DRAWDOWN_CHART,
                            MONTHLY_RETURNS,
                            HOLDINGS_LIST,
                            RECENT_TRANSACTIONS,
                            TOP_PERFORMERS,
                            WORST_PERFORMERS ->
                    4;
        };
    }
}
