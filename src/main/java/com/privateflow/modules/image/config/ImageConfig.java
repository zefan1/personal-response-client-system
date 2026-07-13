package com.privateflow.modules.image.config;

public record ImageConfig(
    String apiBaseUrl,
    String apiKey,
    int timeoutMs,
    int maxSizeBytes,
    int maxDimensionPx,
    int compressQuality,
    String recognitionPrompt,
    String model,
    int consecutiveFailuresAlert
) {
  public float jpegQuality() {
    return Math.max(0.60f, Math.min(0.95f, compressQuality / 100.0f));
  }
}
