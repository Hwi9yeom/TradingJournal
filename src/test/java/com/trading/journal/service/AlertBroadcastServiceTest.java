package com.trading.journal.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.entity.PriceAlert;
import com.trading.journal.websocket.WebSocketSessionRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AlertBroadcastService")
class AlertBroadcastServiceTest {

    private WebSocketSessionRegistry sessionRegistry;
    private AlertBroadcastService service;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(WebSocketSessionRegistry.class);
        service = new AlertBroadcastService(sessionRegistry, new ObjectMapper());
    }

    @Test
    @DisplayName("가격 알림은 소유자 userId에게만 전송한다")
    void broadcastPriceAlert_sendsToOwnerOnly() {
        PriceAlert alert =
                PriceAlert.builder()
                        .id(1L)
                        .userId(42L)
                        .stockId(10L)
                        .symbol("AAPL")
                        .alertType(PriceAlert.PriceAlertType.PRICE_ABOVE)
                        .condition(PriceAlert.PriceAlertCondition.GREATER_THAN)
                        .thresholdPrice(new BigDecimal("150"))
                        .currentPrice(new BigDecimal("155"))
                        .triggeredAt(LocalDateTime.now())
                        .build();

        service.broadcastPriceAlert(alert);

        verify(sessionRegistry).sendToUser(eq(42L), anyString());
        verify(sessionRegistry, never()).broadcast(anyString());
    }
}
