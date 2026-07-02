package com.privateflow.modules.api.ai;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptVersionService {

  private final PromptVersionRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;

  public PromptVersionService(PromptVersionRepository repository, ApplicationEventPublisher eventPublisher, WsPushService wsPushService) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
  }

  public PromptVersionPage list(String type) {
    String key = configKey(type);
    return new PromptVersionPage(key, repository.currentVersion(key), repository.list(key));
  }

  @Transactional
  public Map<String, Object> restore(String type, PromptRestoreRequest request) {
    String key = configKey(type);
    PromptVersion target = repository.find(key, request.version())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "prompt version not found"));
    repository.updateConfig(key, target.content());
    int newVersion = repository.create(key, target.content(), request.operator(), "restore from version " + request.version());
    publish(key);
    return Map.of("restored", true, "configKey", key, "version", newVersion);
  }

  public void snapshotIfPrompt(String key, String value, String operator, String note) {
    if (isPromptKey(key)) {
      repository.create(key, value, operator, note);
    }
  }

  public boolean isPromptKey(String key) {
    return "skill.system_prompt_format".equals(key)
        || "skill.system_prompt_red_lines".equals(key)
        || "image.recognition_prompt".equals(key);
  }

  private String configKey(String type) {
    return switch (type) {
      case "format" -> "skill.system_prompt_format";
      case "red-lines" -> "skill.system_prompt_red_lines";
      case "image" -> "image.recognition_prompt";
      default -> throw new ApiException(ApiErrorCodes.BAD_REQUEST, "unsupported prompt type");
    };
  }

  private void publish(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key, "operator", AuthContext.username())));
  }
}
