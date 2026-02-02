package com.trading.journal.service;

import com.trading.journal.entity.PriceAlert;
import com.trading.journal.entity.PriceAlert.PriceAlertCondition;
import com.trading.journal.entity.Stock;
import com.trading.journal.repository.PriceAlertRepository;
import com.trading.journal.repository.StockRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가격 알림 모니터링 서비스 - 주기적으로 가격 알림 조건 체크 및 브로드캐스트 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertMonitorService {

    private final PriceAlertRepository priceAlertRepository;
    private final StockRepository stockRepository;
    private final AlertBroadcastService alertBroadcastService;

    /**
     * 가격 알림 모니터링 (1분마다 실행)
     *
     * <p>활성 상태인 모든 가격 알림을 조회하여 현재 가격과 비교하고, 조건이 충족되면 알림을 트리거하고 브로드캐스트합니다.
     */
    @Scheduled(fixedRate = 60000) // 1분 = 60,000ms
    @Transactional
    public void monitorPriceAlerts() {
        log.debug("Starting price alert monitoring cycle...");

        try {
            List<PriceAlert> activeAlerts =
                    priceAlertRepository.findByIsActiveTrueAndIsTriggeredFalse();

            if (activeAlerts.isEmpty()) {
                log.debug("No active price alerts to monitor");
                return;
            }

            log.info("Monitoring {} active price alerts", activeAlerts.size());

            int triggeredCount = 0;
            for (PriceAlert alert : activeAlerts) {
                if (checkAndTriggerAlert(alert)) {
                    triggeredCount++;
                }
            }

            if (triggeredCount > 0) {
                log.info("Triggered {} price alerts in this cycle", triggeredCount);
            }

        } catch (Exception e) {
            log.error("Error during price alert monitoring: {}", e.getMessage(), e);
        }
    }

    /**
     * 개별 알림 체크 및 트리거
     *
     * @param alert 체크할 가격 알림
     * @return 알림이 트리거되었는지 여부
     */
    private boolean checkAndTriggerAlert(PriceAlert alert) {
        try {
            // 현재 가격 조회 (실제 구현에서는 외부 API 또는 실시간 데이터 소스 사용)
            BigDecimal currentPrice = getCurrentPrice(alert.getSymbol());

            if (currentPrice == null) {
                log.warn("Could not retrieve current price for symbol: {}", alert.getSymbol());
                return false;
            }

            // 조건 확인
            boolean conditionMet = evaluateCondition(alert, currentPrice);

            if (conditionMet) {
                // 알림 트리거
                alert.trigger(currentPrice);
                priceAlertRepository.save(alert);

                // WebSocket 브로드캐스트
                alertBroadcastService.broadcastPriceAlert(alert);

                // 알림 전송 완료 표시
                alert.markNotificationSent();
                priceAlertRepository.save(alert);

                log.info(
                        "Alert triggered - Symbol: {}, Type: {}, Threshold: {}, Current: {}",
                        alert.getSymbol(),
                        alert.getAlertType(),
                        alert.getThresholdPrice(),
                        currentPrice);

                return true;
            }

        } catch (Exception e) {
            log.error("Error checking alert ID {}: {}", alert.getId(), e.getMessage(), e);
        }

        return false;
    }

    /**
     * 조건 평가
     *
     * @param alert 가격 알림
     * @param currentPrice 현재 가격
     * @return 조건 충족 여부
     */
    private boolean evaluateCondition(PriceAlert alert, BigDecimal currentPrice) {
        BigDecimal threshold = alert.getThresholdPrice();
        PriceAlertCondition condition = alert.getCondition();

        return switch (condition) {
            case GREATER_THAN -> currentPrice.compareTo(threshold) > 0;
            case LESS_THAN -> currentPrice.compareTo(threshold) < 0;
            case EQUALS -> currentPrice.compareTo(threshold) == 0;
            case PERCENT_UP -> {
                BigDecimal percentChange = calculatePercentChange(threshold, currentPrice);
                yield percentChange.compareTo(threshold) >= 0;
            }
            case PERCENT_DOWN -> {
                BigDecimal percentChange = calculatePercentChange(threshold, currentPrice);
                yield percentChange.compareTo(threshold.negate()) <= 0;
            }
        };
    }

    /**
     * 현재 가격 조회
     *
     * <p>TODO: 실제 구현에서는 외부 API (Yahoo Finance, Alpha Vantage 등) 또는 실시간 데이터 소스를 사용해야 합니다. 현재는 Stock
     * 엔티티에서 캐시된 가격을 반환하거나 랜덤 가격을 생성합니다.
     *
     * @param symbol 종목 코드
     * @return 현재 가격 (실패 시 null)
     */
    private BigDecimal getCurrentPrice(String symbol) {
        try {
            // Stock 엔티티에서 종목 조회
            Stock stock = stockRepository.findBySymbol(symbol).orElse(null);

            if (stock == null) {
                log.warn("Stock not found for symbol: {}", symbol);
                return null;
            }

            // TODO: 실제 구현 - 외부 API 호출
            // 예: yahooFinanceClient.getCurrentPrice(symbol)
            // 예: alphaVantageClient.getQuote(symbol)

            // 임시: 랜덤 가격 생성 (개발/테스트용)
            // 실제 배포 시 제거하고 실제 API 호출로 대체
            BigDecimal basePrice = BigDecimal.valueOf(100.0);
            double randomFactor = 0.95 + (Math.random() * 0.1); // 95% ~ 105%
            BigDecimal randomPrice =
                    basePrice
                            .multiply(BigDecimal.valueOf(randomFactor))
                            .setScale(2, BigDecimal.ROUND_HALF_UP);

            log.debug("Generated random price for {}: {}", symbol, randomPrice);
            return randomPrice;

        } catch (Exception e) {
            log.error("Error getting current price for {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 변동률 계산
     *
     * @param basePrice 기준 가격
     * @param currentPrice 현재 가격
     * @return 변동률 (%)
     */
    private BigDecimal calculatePercentChange(BigDecimal basePrice, BigDecimal currentPrice) {
        if (basePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice
                .subtract(basePrice)
                .divide(basePrice, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 특정 종목의 알림 강제 체크 (수동 트리거용)
     *
     * @param symbol 종목 코드
     */
    @Transactional
    public void checkAlertsForSymbol(String symbol) {
        List<PriceAlert> alerts = priceAlertRepository.findBySymbolAndIsActiveTrue(symbol);
        log.info("Manually checking {} alerts for symbol: {}", alerts.size(), symbol);

        for (PriceAlert alert : alerts) {
            checkAndTriggerAlert(alert);
        }
    }

    /**
     * 특정 사용자의 알림 강제 체크 (수동 트리거용)
     *
     * @param userId 사용자 ID
     */
    @Transactional
    public void checkAlertsForUser(Long userId) {
        List<PriceAlert> alerts = priceAlertRepository.findByUserIdAndIsActiveTrue(userId);
        log.info("Manually checking {} alerts for user: {}", alerts.size(), userId);

        for (PriceAlert alert : alerts) {
            checkAndTriggerAlert(alert);
        }
    }
}
