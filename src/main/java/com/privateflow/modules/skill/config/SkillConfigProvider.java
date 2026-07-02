package com.privateflow.modules.skill.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SkillConfigProvider {

  private static final Logger log = LoggerFactory.getLogger(SkillConfigProvider.class);
  private static final String DEFAULT_PROMPT = "【输出格式要求】你必须返回一个 JSON 对象，包含 suggestions 恰好3条，每条包含 text、direction、reason；可选 customer_analysis、followup_suggest、profile_updates。场景：{{scene}}\n【企业红线】\n{{red_lines}}\n【可用客户标签】\n{{available_tags}}";
  private final SystemConfigRepository repository;
  private final AtomicReference<SkillConfig> current = new AtomicReference<>(defaults());

  public SkillConfigProvider(SystemConfigRepository repository) {
    this.repository = repository;
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
          string("skill.api_key", previous.apiKey()),
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
          string("skill.system_prompt_template", previous.systemPromptTemplate()),
          string("skill.red_lines", previous.redLines()),
          decimal("skill.alert_failure_rate", previous.alertFailureRate()),
          integer("skill.alert_failure_duration_minutes", previous.alertFailureDurationMinutes()),
          integer("profile.extract_timeout_ms", previous.profileExtractTimeoutMs())));
    } catch (RuntimeException ex) {
      log.warn("skill config refresh failed, keeping previous snapshot: {}", ex.getMessage());
    }
  }

  private String string(String key, String fallback) {
    return repository.findValue(key).orElse(fallback);
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
        "", 0.3, 15, 8000);
  }
}
