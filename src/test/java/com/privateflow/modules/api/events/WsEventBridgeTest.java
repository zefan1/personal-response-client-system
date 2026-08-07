package com.privateflow.modules.api.events;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.privateflow.common.events.CustomerTagsUpdatedEvent;
import com.privateflow.common.events.ImageServiceStatusEvent;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.tags.CustomerTagDecisionResult;
import com.privateflow.modules.tags.CustomerTagUpdateResult;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WsEventBridgeTest {

  @Test
  void broadcastsStructuredCustomerTagChanges() {
    WsPushService wsPushService = mock(WsPushService.class);
    SystemAlertRepository alertRepository = mock(SystemAlertRepository.class);
    WsEventBridge bridge = new WsEventBridge(wsPushService, alertRepository);
    CustomerTagsUpdatedEvent event = new CustomerTagsUpdatedEvent(
        "18800001111",
        7L,
        4,
        "MANUAL",
        new CustomerTagUpdateResult(
            4,
            true,
            List.of(new CustomerTagDecisionResult(1L, "intent_level", "REPLACE", true, "人工标签修改完成"))));

    bridge.onCustomerTagsUpdated(event);

    verify(wsPushService).broadcastWs(argThat(message -> {
      WsMessage wsMessage = (WsMessage) message;
      return "CUSTOMER_TAGS_UPDATED".equals(wsMessage.type())
          && wsMessage.payload().toString().contains("18800001111")
          && wsMessage.payload().toString().contains("intent_level");
    }));
  }

  @Test
  void recordsImageFailuresWithAnUnderstandableChineseAlert() {
    WsPushService wsPushService = mock(WsPushService.class);
    SystemAlertRepository alertRepository = mock(SystemAlertRepository.class);
    WsEventBridge bridge = new WsEventBridge(wsPushService, alertRepository);

    bridge.onImageStatus(new ImageServiceStatusEvent(
        "DOWN", 3, "Image recognition timed out", Instant.parse("2026-08-06T07:00:00Z")));

    verify(alertRepository).activate(
        eq("IMAGE_SERVICE_DOWN"),
        eq("WARN"),
        eq("识图服务暂时不可用，请检查服务配置和网络连接。"),
        eq("识图服务"),
        argThat(detail -> !String.valueOf(detail).contains("ImageServiceStatusEvent")));
  }
}
