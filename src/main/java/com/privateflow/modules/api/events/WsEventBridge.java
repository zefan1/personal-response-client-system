package com.privateflow.modules.api.events;

import com.privateflow.common.events.FollowupWsMessageReadyEvent;
import com.privateflow.common.events.ImageServiceStatusEvent;
import com.privateflow.common.events.ProfileSuggestionsReadyEvent;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WsEventBridge {

  private final WsPushService wsPushService;
  private final SystemAlertRepository alertRepository;

  public WsEventBridge(WsPushService wsPushService, SystemAlertRepository alertRepository) {
    this.wsPushService = wsPushService;
    this.alertRepository = alertRepository;
  }

  @EventListener
  public void onFollowup(FollowupWsMessageReadyEvent event) {
    wsPushService.pushWsMessage(event.userId(), WsMessage.unsaved(event.type(), event.payload()));
  }

  @EventListener
  public void onProfileSuggestions(ProfileSuggestionsReadyEvent event) {
    wsPushService.broadcastWs(WsMessage.unsaved("PROFILE_SUGGESTIONS", Map.of(
        "phone", event.phone(),
        "suggestionCount", event.suggestionCount(),
        "suggestions", event.suggestions() == null ? List.of() : event.suggestions())));
  }

  @EventListener
  public void onImageStatus(ImageServiceStatusEvent event) {
    wsPushService.broadcastWs(WsMessage.unsaved("IMAGE_SERVICE_STATUS", Map.of(
        "status", event.status(),
        "failedCount", event.failedCount(),
        "lastErrorMsg", event.lastErrorMsg() == null ? "" : event.lastErrorMsg(),
        "timestamp", event.timestamp().toString())));
    if ("DOWN".equalsIgnoreCase(event.status())) {
      alertRepository.activate("IMAGE_SERVICE_DOWN", "WARN", event.lastErrorMsg(), "C", event.toString());
    } else if ("UP".equalsIgnoreCase(event.status())) {
      alertRepository.resolve("IMAGE_SERVICE_DOWN");
    }
  }
}
