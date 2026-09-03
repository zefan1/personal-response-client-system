package com.privateflow.modules.api.ai;

import com.privateflow.modules.llm.LlmScene;
import java.util.List;

public record LlmEnvironmentTestRequest(
    LlmScene scene,
    String leadType,
    String testMessage,
    List<LlmEnvironmentTestMessage> messages
) {
  public LlmEnvironmentTestRequest(LlmScene scene, String leadType, String testMessage) {
    this(scene, leadType, testMessage, List.of());
  }
}
