package com.privateflow.modules.llm;

public record LlmResponse(
    boolean success,
    String content,
    String model,
    String protocol,
    long elapsedMs,
    String errorCode,
    String message
) {
  public static LlmResponse ok(String content, String model, String protocol, long elapsedMs) {
    return new LlmResponse(true, content, model, protocol, elapsedMs, null, null);
  }

  public static LlmResponse failed(String errorCode, String message, String model, String protocol, long elapsedMs) {
    return new LlmResponse(false, "", model, protocol, elapsedMs, errorCode, message);
  }
}
