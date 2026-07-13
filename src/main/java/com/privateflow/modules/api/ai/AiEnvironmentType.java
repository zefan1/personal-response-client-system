package com.privateflow.modules.api.ai;

public enum AiEnvironmentType {
  SKILL("skill", "skill.api_base_url", "skill.api_key"),
  IMAGE("image", "image.api_base_url", "image.api_key"),
  LLM("llm", "llm.api_base_url", "llm.api_key");

  private final String provider;
  private final String baseUrlConfigKey;
  private final String apiKeyConfigKey;

  AiEnvironmentType(String provider, String baseUrlConfigKey, String apiKeyConfigKey) {
    this.provider = provider;
    this.baseUrlConfigKey = baseUrlConfigKey;
    this.apiKeyConfigKey = apiKeyConfigKey;
  }

  public String provider() {
    return provider;
  }

  public String baseUrlConfigKey() {
    return baseUrlConfigKey;
  }

  public String apiKeyConfigKey() {
    return apiKeyConfigKey;
  }
}
