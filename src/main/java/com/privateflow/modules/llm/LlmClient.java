package com.privateflow.modules.llm;

public interface LlmClient {
  LlmResponse generate(LlmRequest request);

  LlmResponse generate(LlmRequest request, LlmConfig config);
}
