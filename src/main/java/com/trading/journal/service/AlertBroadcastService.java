package com.trading.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.entity.PriceAlert;
import com.trading.journal.websocket.WebSocketSessionRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 알림 브로드캐스트 서비스 - WebSocket을 통한 실시간 알림 전송 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertBroadcastService {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 가격 알림 브로드캐스트
     *
     * @param alert 트리거된 가격 알림
     */
    public void broadcastPriceAlert(PriceAlert alert) {
        try {
            Map<String, Object> message = createPriceAlertMessage(alert);
            String json = objectMapper.writeValueAsString(message);

            sessionRegistry.broadcast(json);

            log.info(
                    "Price alert broadcast - Symbol: {}, Type: {}, Price: {}",
                    alert.getSymbol(),
                    alert.getAlertType(),
                    alert.getCurrentPrice());

        } catch (Exception e) {
            log.error("Failed to broadcast price alert: {}", e.getMessage(), e);
        }
    }

    /**
     * 다중 알림 브로드캐스트
     *
     * @param alerts 트리거된 가격 알림 목록
     */
    public void broadcastMultipleAlerts(Iterable<PriceAlert> alerts) {
        alerts.forEach(this::broadcastPriceAlert);
    }

    /**
     * 커스텀 알림 메시지 브로드캐스트
     *
     * @param messageType 메시지 타입
     * @param data 메시지 데이터
     */
    public void broadcastCustomMessage(String messageType, Map<String, Object> data) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", messageType);
            message.put("timestamp", LocalDateTime.now().toString());
            message.put("data", data);

            String json = objectMapper.writeValueAsString(message);
            sessionRegistry.broadcast(json);

            log.info("Custom message broadcast - Type: {}", messageType);

        } catch (Exception e) {
            log.error("Failed to broadcast custom message: {}", e.getMessage(), e);
        }
    }

    /**
     * 시스템 알림 브로드캐스트
     *
     * @param title 알림 제목
     * @param message 알림 메시지
     * @param severity 심각도 (info, warning, error)
     */
    public void broadcastSystemAlert(String title, String message, String severity) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("message", message);
        data.put("severity", severity);

        broadcastCustomMessage("SYSTEM_ALERT", data);
    }

    /**
     * 가격 알림 메시지 생성
     *
     * @param alert 가격 알림
     * @return 메시지 맵
     */
    private Map<String, Object> createPriceAlertMessage(PriceAlert alert) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PRICE_ALERT");
        message.put("alertId", alert.getId());
        message.put("userId", alert.getUserId());
        message.put("symbol", alert.getSymbol());
        message.put("alertType", alert.getAlertType().name());
        message.put("alertTypeDescription", alert.getAlertType().getDescription());
        message.put("condition", alert.getCondition().name());
        message.put("conditionDescription", alert.getCondition().getDescription());
        message.put("thresholdPrice", alert.getThresholdPrice());
        message.put("currentPrice", alert.getCurrentPrice());
        message.put("triggeredAt", formatDateTime(alert.getTriggeredAt()));

        // 알림 제목 및 메시지 생성
        String title = createAlertTitle(alert);
        String messageText = createAlertMessage(alert);

        message.put("title", title);
        message.put("message", messageText);

        // 활성 세션 수 포함
        message.put("activeConnections", sessionRegistry.getActiveSessionCount());

        return message;
    }

    /**
     * 알림 제목 생성
     *
     * @param alert 가격 알림
     * @return 알림 제목
     */
    private String createAlertTitle(PriceAlert alert) {
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> String.format("%s 가격 상승 알림", alert.getSymbol());
            case PRICE_BELOW -> String.format("%s 가격 하락 알림", alert.getSymbol());
            case PERCENT_CHANGE -> String.format("%s 변동률 알림", alert.getSymbol());
            case VOLUME_SPIKE -> String.format("%s 거래량 급증 알림", alert.getSymbol());
        };
    }

    /**
     * 알림 메시지 생성
     *
     * @param alert 가격 알림
     * @return 알림 메시지
     */
    private String createAlertMessage(PriceAlert alert) {
        BigDecimal threshold = alert.getThresholdPrice();
        BigDecimal current = alert.getCurrentPrice();

        return switch (alert.getAlertType()) {
            case PRICE_ABOVE ->
                    String.format(
                            "%s의 현재 가격(%.2f)이 목표 가격(%.2f)을 초과했습니다.",
                            alert.getSymbol(), current, threshold);
            case PRICE_BELOW ->
                    String.format(
                            "%s의 현재 가격(%.2f)이 목표 가격(%.2f) 이하로 떨어졌습니다.",
                            alert.getSymbol(), current, threshold);
            case PERCENT_CHANGE ->
                    String.format(
                            "%s의 가격 변동률이 %.2f%%에 도달했습니다.",
                            alert.getSymbol(), calculatePercentChange(threshold, current));
            case VOLUME_SPIKE -> String.format("%s의 거래량이 급증했습니다.", alert.getSymbol());
        };
    }

    /**
     * 변동률 계산
     *
     * @param threshold 기준 가격
     * @param current 현재 가격
     * @return 변동률 (%)
     */
    private BigDecimal calculatePercentChange(BigDecimal threshold, BigDecimal current) {
        if (threshold.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(threshold)
                .divide(threshold, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 날짜/시간 포맷팅
     *
     * @param dateTime 날짜/시간
     * @return 포맷팅된 문자열
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }
}
