package com.privateflow.modules.desktop;

import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.auth.AccountPermissionRepository;
import com.privateflow.modules.api.auth.PermissionCodes;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.llm.LlmCallAnalytics;
import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.runtime.RuntimeModeService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DesktopStatusService {

  public static final String SKILL_SUBSCRIPTION_EXPIRE_AT = "skill.subscription_expire_at";
  public static final String CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S = "desktop.clipboard_screenshot_confirm_prompt_s";
  public static final String LLM_REPLY_GENERATION_ENABLED = "llm.reply_generation.enabled";
  public static final String LLM_API_BASE_URL = "llm.api_base_url";
  public static final String LLM_API_KEY = "llm.api_key";
  public static final String LLM_MODEL = "llm.model";
  private static final String LLM_REPLY_GENERATION_SCENE = "REPLY_GENERATION";
  private static final int EXPIRING_DAYS = 7;
  private static final int DEFAULT_CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S = 10;
  private static final int LLM_FAILURE_WINDOW_DAYS = 1;
  private static final long LLM_FAILURE_MIN_CALLS = 3;
  private static final double LLM_FAILURE_SUCCESS_RATE_THRESHOLD = 0.5;

  private final SystemConfigRepository configRepository;
  private final Clock clock;
  private final RuntimeModeService runtimeModeService;
  private final LlmCallAnalyticsRepository llmCallAnalyticsRepository;
  private final AccountPermissionRepository permissionRepository;

  @Autowired
  public DesktopStatusService(
      SystemConfigRepository configRepository,
      RuntimeModeService runtimeModeService,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository,
      AccountPermissionRepository permissionRepository) {
    this(configRepository, Clock.systemDefaultZone(), runtimeModeService, llmCallAnalyticsRepository, permissionRepository);
  }

  DesktopStatusService(
      SystemConfigRepository configRepository,
      Clock clock,
      RuntimeModeService runtimeModeService) {
    this(configRepository, clock, runtimeModeService, null, null);
  }

  DesktopStatusService(
      SystemConfigRepository configRepository,
      Clock clock,
      RuntimeModeService runtimeModeService,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository) {
    this(configRepository, clock, runtimeModeService, llmCallAnalyticsRepository, null);
  }

  DesktopStatusService(
      SystemConfigRepository configRepository,
      Clock clock,
      RuntimeModeService runtimeModeService,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository,
      AccountPermissionRepository permissionRepository) {
    this.configRepository = configRepository;
    this.clock = clock;
    this.runtimeModeService = runtimeModeService;
    this.llmCallAnalyticsRepository = llmCallAnalyticsRepository;
    this.permissionRepository = permissionRepository;
  }

  public DesktopStatusResponse currentStatus() {
    AuthUser user = AuthContext.current();
    String accountName = user == null ? "" : firstNonBlank(user.displayName(), user.username());
    return new DesktopStatusResponse(
        accountName,
        user == null ? null : user.role(),
        permissions(user),
        skillStatus(configRepository.findValue(SKILL_SUBSCRIPTION_EXPIRE_AT).orElse("")),
        llmStatus(),
        runtimeModeService.currentMode(),
        runtimeConfig());
  }

  private Set<String> permissions(AuthUser user) {
    if (user == null) {
      return Set.of();
    }
    Set<String> result = permissionRepository == null
        ? new LinkedHashSet<>()
        : new LinkedHashSet<>(permissionRepository.findEnabledByPhone(user.username()));
    if (user.role() == com.privateflow.modules.api.Role.ADMIN) {
      result.add(PermissionCodes.TAG_MANAGEMENT);
    }
    return Set.copyOf(result);
  }

  DesktopRuntimeConfigResponse runtimeConfig() {
    return new DesktopRuntimeConfigResponse(clipboardScreenshotConfirmPromptS());
  }

  DesktopLlmStatusResponse llmStatus() {
    boolean replyGenerationEnabled = booleanConfig(LLM_REPLY_GENERATION_ENABLED, false);
    if (!replyGenerationEnabled) {
      return new DesktopLlmStatusResponse(
          DesktopLlmStatus.OK,
          "LLM 回复生成未启用",
          "当前回复建议继续使用 Skill 链路，不会调用 LLM。",
          false);
    }

    String apiBaseUrl = configRepository.findValue(LLM_API_BASE_URL).orElse("");
    String apiKey = configRepository.findValue(LLM_API_KEY).orElse("");
    String model = configRepository.findValue(LLM_MODEL).orElse("");
    if (isBlank(apiBaseUrl) || isBlank(apiKey) || isBlank(model)) {
      return new DesktopLlmStatusResponse(
          DesktopLlmStatus.WARN,
          "LLM 配置不完整",
          "已启用 LLM 回复生成，但 API 地址、密钥或模型未配置完整。",
          true);
    }
    DesktopLlmStatusResponse recentFailureStatus = recentLlmFailureStatus();
    if (recentFailureStatus != null) {
      return recentFailureStatus;
    }
    return new DesktopLlmStatusResponse(
        DesktopLlmStatus.OK,
        "LLM 回复生成已配置",
        "回复建议会优先走 LLM，失败后按配置回落 Skill。",
        true);
  }

  private DesktopLlmStatusResponse recentLlmFailureStatus() {
    if (llmCallAnalyticsRepository == null) {
      return null;
    }
    try {
      LlmCallAnalytics analytics = llmCallAnalyticsRepository.query(
          LLM_FAILURE_WINDOW_DAYS,
          LLM_REPLY_GENERATION_SCENE,
          null);
      if (analytics == null || analytics.summary() == null) {
        return null;
      }
      long totalCalls = analytics.summary().totalCalls();
      double successRate = analytics.summary().successRate();
      if (totalCalls >= LLM_FAILURE_MIN_CALLS && successRate < LLM_FAILURE_SUCCESS_RATE_THRESHOLD) {
        return new DesktopLlmStatusResponse(
            DesktopLlmStatus.WARN,
            "LLM 近期调用失败率较高",
            "近 24 小时回复生成 LLM 调用 %d 次，成功率 %d%%，当前会按配置回落 Skill。"
                .formatted(totalCalls, Math.round(successRate * 100)),
            true);
      }
    } catch (RuntimeException ignored) {
      return null;
    }
    return null;
  }

  DesktopSkillStatusResponse skillStatus(String rawExpireAt) {
    String value = rawExpireAt == null ? "" : rawExpireAt.trim();
    if (value.isBlank()) {
      return new DesktopSkillStatusResponse(
          DesktopSkillStatus.UNKNOWN,
          null,
          null,
          "技能有效期未配置");
    }
    LocalDate expireDate = parseExpireDate(value);
    if (expireDate == null) {
      return new DesktopSkillStatusResponse(
          DesktopSkillStatus.UNKNOWN,
          null,
          null,
          "技能有效期未配置");
    }
    LocalDate today = LocalDate.now(clock);
    int daysLeft = Math.toIntExact(ChronoUnit.DAYS.between(today, expireDate));
    if (daysLeft < 0) {
      return new DesktopSkillStatusResponse(
          DesktopSkillStatus.EXPIRED,
          expireDate.toString(),
          daysLeft,
          "技能已到期");
    }
    if (daysLeft <= EXPIRING_DAYS) {
      return new DesktopSkillStatusResponse(
          DesktopSkillStatus.EXPIRING,
          expireDate.toString(),
          daysLeft,
          daysLeft == 0 ? "今日到期" : "即将到期");
    }
    return new DesktopSkillStatusResponse(
        DesktopSkillStatus.OK,
        expireDate.toString(),
        daysLeft,
        "有效至 " + expireDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
  }

  private LocalDate parseExpireDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      // Try date-time formats below.
    }
    try {
      return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // Try local date-time without timezone below.
    }
    try {
      return LocalDateTime.parse(value).toLocalDate();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private int clipboardScreenshotConfirmPromptS() {
    return configRepository.findValue(CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S)
        .map(this::parseClipboardScreenshotConfirmPromptS)
        .orElse(DEFAULT_CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S);
  }

  private boolean booleanConfig(String key, boolean fallback) {
    return configRepository.findValue(key)
        .map(value -> "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()))
        .orElse(fallback);
  }

  private int parseClipboardScreenshotConfirmPromptS(String rawValue) {
    String value = rawValue == null ? "" : rawValue.trim();
    if (value.isBlank()) {
      return DEFAULT_CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S;
    }
    int parsed;
    try {
      parsed = Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return DEFAULT_CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S;
    }
    if (parsed == 0 || (parsed >= 3 && parsed <= 60)) {
      return parsed;
    }
    return DEFAULT_CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S;
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second == null ? "" : second;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isBlank();
  }
}
