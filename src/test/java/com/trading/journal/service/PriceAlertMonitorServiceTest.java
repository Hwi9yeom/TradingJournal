package com.trading.journal.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.repository.PriceAlertRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 가격 알림 폴러의 차단 스위치 회귀 테스트.
 *
 * <p>가격 소스가 레이트 리밋(HTTP 429)에 걸렸을 때 폴링을 멈출 수 있어야 한다. 멈추지 못하면 실패한 조회가 리밋을 계속 갱신해 회복을 막는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("가격 알림 모니터링 서비스 테스트")
class PriceAlertMonitorServiceTest {

    @Mock private PriceAlertRepository priceAlertRepository;
    @Mock private StockPriceService stockPriceService;
    @Mock private AlertBroadcastService alertBroadcastService;

    @InjectMocks private PriceAlertMonitorService priceAlertMonitorService;

    private void setEnabled(boolean enabled) {
        ReflectionTestUtils.setField(priceAlertMonitorService, "monitoringEnabled", enabled);
    }

    @Test
    @DisplayName("비활성화하면 알림 조회조차 하지 않는다 (외부 시세 호출 0회)")
    void monitor_DisabledSkipsEverything() {
        setEnabled(false);

        priceAlertMonitorService.monitorPriceAlerts();

        verifyNoInteractions(priceAlertRepository);
        verifyNoInteractions(stockPriceService);
    }

    @Test
    @DisplayName("활성 상태에서는 알림을 조회한다")
    void monitor_EnabledQueriesAlerts() {
        setEnabled(true);
        when(priceAlertRepository.findByIsActiveTrueAndIsTriggeredFalse()).thenReturn(List.of());

        priceAlertMonitorService.monitorPriceAlerts();

        verify(priceAlertRepository).findByIsActiveTrueAndIsTriggeredFalse();
    }

    @Test
    @DisplayName("활성 알림이 없으면 시세를 조회하지 않는다")
    void monitor_NoAlertsSkipsPriceLookup() {
        setEnabled(true);
        when(priceAlertRepository.findByIsActiveTrueAndIsTriggeredFalse()).thenReturn(List.of());

        priceAlertMonitorService.monitorPriceAlerts();

        verify(stockPriceService, never()).getCurrentPrice(any());
    }
}
