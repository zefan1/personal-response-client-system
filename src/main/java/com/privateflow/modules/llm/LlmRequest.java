package com.privateflow.modules.llm;

import java.util.List;

public record LlmRequest(
    String systemPrompt,
    String userPrompt,
    List<LlmMessage> messages,
    Double temperature,
    Integer maxTokens
) {
  public static LlmRequest singleTurn(String systemPrompt, String userPrompt) {
    return new LlmRequest(systemPrompt, userPrompt, List.of(), null, null);
  }
}
