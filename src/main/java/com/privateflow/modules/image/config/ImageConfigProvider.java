package com.privateflow.modules.image.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ImageConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(ImageConfigProvider.class);
  private static final String DEFAULT_PROMPT = "你是一个聊天截图分析助手。请分析这张微信/企业微信聊天截图，提取nickname、phone、messages和timestamp，并严格返回JSON。";
  private final SystemConfigRepository configRepository;
  private final SecretCipher secretCipher;
  private final AtomicReference<ImageConfig> current = new AtomicReference<>(defaults());

  public ImageConfigProvider(SystemConfigRepository configRepository, SecretCipher secretCipher) {
    this.configRepository = configRepository;
    this.secretCipher = secretCipher;
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public ImageConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("image.")) {
      refresh();
    }
  }

  public void refresh() {
    try {
      ImageConfig previous = current.get();
      current.set(new ImageConfig(
          string("image.api_base_url", previous.apiBaseUrl()),
          secret("image.api_key", previous.apiKey()),
          integer("image.timeout_ms", previous.timeoutMs()),
          integer("image.max_size_bytes", previous.maxSizeBytes()),
          integer("image.max_dimension_px", previous.maxDimensionPx()),
          integer("image.compress_quality", previous.compressQuality()),
          string("image.recognition_prompt", previous.recognitionPrompt()),
          string("image.model", previous.model()),
          integer("image.consecutive_failures_alert", previous.consecutiveFailuresAlert())));
    } catch (RuntimeException ex) {
      log.warn("image config refresh failed, keeping previous snapshot: {}", ex.getMessage());
    }
  }

  private String string(String key, String fallback) {
    return configRepository.findValue(key).orElse(fallback);
  }

  private String secret(String key, String fallback) {
    return configRepository.findValue(key).map(secretCipher::decrypt).orElse(fallback);
  }

  private int integer(String key, int fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private static ImageConfig defaults() {
    return new ImageConfig("", "", 5000, 5242880, 1920, 85, DEFAULT_PROMPT, "qwen3-vl-plus", 3);
  }
}
