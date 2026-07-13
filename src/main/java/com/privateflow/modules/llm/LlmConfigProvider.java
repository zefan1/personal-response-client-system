package com.privateflow.modules.llm;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LlmConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(LlmConfigProvider.class);
  public static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
  private final SystemConfigRepository configRepository;
  private final SecretCipher secretCipher;
  private final AtomicReference<LlmConfig> current = new AtomicReference<>(defaults());

  public LlmConfigProvider(SystemConfigRepository configRepository, SecretCipher secretCipher) {
    this.configRepository = configRepository;
    this.secretCipher = secretCipher;
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public LlmConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("llm.")) {
      refresh();
    }
  }

  public void refresh() {
    try {
      LlmConfig previous = current.get();
      current.set(new LlmConfig(
          string("llm.api_base_url", previous.apiBaseUrl()),
          secret("llm.api_key", previous.apiKey()),
          string("llm.model", previous.model()),
          protocol("llm.protocol", previous.protocol()),
          integer("llm.timeout_ms", previous.timeoutMs()),
          decimal("llm.temperature", previous.temperature()),
          integer("llm.max_tokens", previous.maxTokens())));
    } catch (RuntimeException ex) {
      log.warn("llm config refresh failed, keeping previous snapshot: {}", ex.getMessage());
    }
  }

  private String string(String key, String fallback) {
    return configRepository.findValue(key).orElse(fallback);
  }

  private String secret(String key, String fallback) {
    return configRepository.findValue(key).map(secretCipher::decrypt).orElse(fallback);
  }

  private String protocol(String key, String fallback) {
    String value = string(key, fallback);
    return value == null || value.isBlank() ? OPENAI_COMPATIBLE : value.trim().toUpperCase();
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

  private double decimal(String key, double fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private static LlmConfig defaults() {
    return new LlmConfig("", "", "", OPENAI_COMPATIBLE, 10000, 0.2, 1024);
  }
}
