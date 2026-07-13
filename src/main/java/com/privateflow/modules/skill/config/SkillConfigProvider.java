package com.privateflow.modules.skill.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SkillConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(SkillConfigProvider.class);
  private static final String DEFAULT_PROMPT = "【输出格式要求】你必须返回一个 JSON 对象，包含 suggestions 恰好3条，每条包含 text、direction、reason；可选 customer_analysis、followup_suggest、profile_updates。场景：{{scene}}\n【企业红线】\n{{red_lines}}\n【可用客户标签】\n{{available_tags}}";
  private static final int DEFAULT_REGENERATE_MAX_COUNT = 3;
  private static final int MIN_REGENERATE_MAX_COUNT = 0;
  private static final int MAX_REGENERATE_MAX_COUNT = 10;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final SystemConfigRepository repository;
  private final SecretCipher secretCipher;
  private final AtomicReference<SkillConfig> current = new AtomicReference<>(defaults());

  public SkillConfigProvider(SystemConfigRepository repository, SecretCipher secretCipher) {
    this.repository = repository;
    this.secretCipher = secretCipher;
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public SkillConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null
        && (event.configKey().startsWith("skill.") || "profile.extract_timeout_ms".equals(event.configKey()))) {
      refresh();
    }
  }

  public void refresh() {
    try {
      SkillConfig previous = current.get();
      current.set(new SkillConfig(
          string("skill.api_base_url", previous.apiBaseUrl()),
          secret("skill.api_key", previous.apiKey()),
          string("skill.phone_transfer_mode", previous.phoneTransferMode()),
          string("skill.phone_encryption_key", previous.phoneEncryptionKey()),
          integer("skill.timeout_ms", previous.timeoutMs()),
          integer("skill.circuit_breaker_window_s", previous.circuitBreakerWindowS()),
          decimal("skill.circuit_breaker_failure_rate", previous.circuitBreakerFailureRate()),
          integer("skill.circuit_breaker_min_calls", previous.circuitBreakerMinCalls()),
          integer("skill.circuit_breaker_open_s", previous.circuitBreakerOpenS()),
          string("skill.fallback_reply", previous.fallbackReply()),
          string("skill.tuan_skill_group_id", previous.tuanSkillGroupId()),
          string("skill.xiansuo_skill_group_id", previous.xiansuoSkillGroupId()),
          string("skill.default_skill_id", previous.defaultSkillId()),
          string("skill.system_prompt_format", string("skill.system_prompt_template", previous.systemPromptTemplate())),
          redLines(previous.redLines()),
          decimal("skill.alert_failure_rate", previous.alertFailureRate()),
          integer("skill.alert_failure_duration_minutes", previous.alertFailureDurationMinutes()),
          integer("profile.extract_timeout_ms", previous.profileExtractTimeoutMs()),
          boundedInteger(
              "skill.regenerate_max_count",
              previous.regenerateMaxCount(),
              MIN_REGENERATE_MAX_COUNT,
              MAX_REGENERATE_MAX_COUNT)));
    } catch (RuntimeException ex) {
      log.warn("skill config refresh failed, keeping previous snapshot: {}", ex.getMessage());
    }
  }

  private String string(String key, String fallback) {
    return repository.findValue(key).orElse(fallback);
  }

  private String secret(String key, String fallback) {
    return repository.findValue(key).map(secretCipher::decrypt).orElse(fallback);
  }

  private String redLines(String fallback) {
    return repository.findValue("skill.system_prompt_red_lines")
        .map(this::redLineText)
        .or(() -> repository.findValue("skill.red_lines"))
        .orElse(fallback);
  }

  private String redLineText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.trim();
    if (!trimmed.startsWith("[")) {
      return value;
    }
    try {
      return String.join("\n", OBJECT_MAPPER.readValue(trimmed, new TypeReference<List<String>>() {}));
    } catch (Exception ex) {
      log.warn("skill red line config is not a valid JSON array, using raw value");
      return value;
    }
  }

  private int integer(String key, int fallback) {
    return repository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private int boundedInteger(String key, int fallback, int min, int max) {
    return repository.findValue(key).map(value -> {
      try {
        int parsed = Integer.parseInt(value);
        return parsed < min || parsed > max ? fallback : parsed;
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private double decimal(String key, double fallback) {
    return repository.findValue(key).map(value -> {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }

  private static SkillConfig defaults() {
    return new SkillConfig("", "", "LAST_FOUR", "", 10000, 30, 0.5, 5, 30,
        "抱歉，AI 助手暂不可用，请手动回复或稍后再试。", "", "", "", DEFAULT_PROMPT,
        "", 0.3, 15, 8000, DEFAULT_REGENERATE_MAX_COUNT);
  }
}
