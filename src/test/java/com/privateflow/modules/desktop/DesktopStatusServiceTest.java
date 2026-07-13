package com.privateflow.modules.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.llm.LlmCallAnalytics;
import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.runtime.RuntimeModeService;
import com.privateflow.modules.runtime.RuntimeModeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DesktopStatusServiceTest {

  private final SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
  private final RuntimeModeService runtimeModeService = Mockito.mock(RuntimeModeService.class);
  private final StubLlmCallAnalyticsRepository llmCallAnalyticsRepository = new StubLlmCallAnalyticsRepository();
  private final DesktopStatusService service = new DesktopStatusService(
      repository,
      Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC),
      runtimeModeService,
      llmCallAnalyticsRepository);

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void returnsUnknownWhenSkillExpiryIsBlank() {
    AuthContext.set(new AuthUser("18800001111", "小王", Role.KEEPER, null));
    when(repository.findValue(DesktopStatusService.SKILL_SUBSCRIPTION_EXPIRE_AT)).thenReturn(Optional.of(""));
    when(repository.findValue(DesktopStatusService.LLM_REPLY_GENERATION_ENABLED)).thenReturn(Optional.of("false"));
    when(runtimeModeService.currentMode()).thenReturn(
        new RuntimeModeStatus(true, "本地模拟模式", "外部表格、AI 技能和图片识别使用本地 Mock 响应。"));

    DesktopStatusResponse status = service.currentStatus();

    assertEquals("小王", status.accountName());
    assertEquals(Role.KEEPER, status.role());
    assertEquals(DesktopSkillStatus.UNKNOWN, status.skillStatus().status());
    assertEquals("技能有效期未配置", status.skillStatus().label());
    assertEquals(DesktopLlmStatus.OK, status.llmStatus().status());
    assertEquals(false, status.llmStatus().replyGenerationEnabled());
    assertEquals(true, status.runtimeMode().mockExternals());
    assertEquals("本地模拟模式", status.runtimeMode().label());
  }

  @Test
  void returnsOkExpiringAndExpiredByConfiguredDate() {
    assertEquals(DesktopSkillStatus.OK, service.skillStatus("2026-07-20").status());
    assertEquals(15, service.skillStatus("2026-07-20").daysLeft());
    assertEquals("有效至 2026-07-20", service.skillStatus("2026-07-20").label());

    assertEquals(DesktopSkillStatus.EXPIRING, service.skillStatus("2026-07-12T10:30:00+08:00").status());
    assertEquals(7, service.skillStatus("2026-07-12T10:30:00+08:00").daysLeft());
    assertEquals(DesktopSkillStatus.EXPIRING, service.skillStatus("2026-07-12T10:30:00").status());

    assertEquals(DesktopSkillStatus.EXPIRED, service.skillStatus("2026-07-04").status());
    assertEquals(-1, service.skillStatus("2026-07-04").daysLeft());
  }

  @Test
  void treatsInvalidDateAsUnknown() {
    DesktopSkillStatusResponse response = service.skillStatus("not-a-date");

    assertEquals(DesktopSkillStatus.UNKNOWN, response.status());
    assertEquals(null, response.expireAt());
    assertEquals(null, response.daysLeft());
  }

  @Test
  void returnsConfiguredClipboardScreenshotPromptSecondsWithFallback() {
    when(repository.findValue(DesktopStatusService.CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S)).thenReturn(Optional.of("15"));
    assertEquals(15, service.runtimeConfig().clipboardScreenshotConfirmPromptS());

    when(repository.findValue(DesktopStatusService.CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S)).thenReturn(Optional.of("0"));
    assertEquals(0, service.runtimeConfig().clipboardScreenshotConfirmPromptS());

    when(repository.findValue(DesktopStatusService.CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S)).thenReturn(Optional.of("2"));
    assertEquals(10, service.runtimeConfig().clipboardScreenshotConfirmPromptS());

    when(repository.findValue(DesktopStatusService.CLIPBOARD_SCREENSHOT_CONFIRM_PROMPT_S)).thenReturn(Optional.of("abc"));
    assertEquals(10, service.runtimeConfig().clipboardScreenshotConfirmPromptS());
  }

  @Test
  void returnsOkLlmStatusWhenReplyGenerationIsDisabled() {
    when(repository.findValue(DesktopStatusService.LLM_REPLY_GENERATION_ENABLED)).thenReturn(Optional.of("false"));

    DesktopLlmStatusResponse response = service.llmStatus();

    assertEquals(DesktopLlmStatus.OK, response.status());
    assertEquals("LLM 回复生成未启用", response.label());
    assertEquals(false, response.replyGenerationEnabled());
  }

  @Test
  void returnsWarnLlmStatusWhenReplyGenerationEnabledButRequiredConfigMissing() {
    when(repository.findValue(DesktopStatusService.LLM_REPLY_GENERATION_ENABLED)).thenReturn(Optional.of("true"));
    when(repository.findValue(DesktopStatusService.LLM_API_BASE_URL)).thenReturn(Optional.of("https://llm.example.com"));
    when(repository.findValue(DesktopStatusService.LLM_API_KEY)).thenReturn(Optional.of(""));
    when(repository.findValue(DesktopStatusService.LLM_MODEL)).thenReturn(Optional.of("gpt-4.1-mini"));

    DesktopLlmStatusResponse response = service.llmStatus();

    assertEquals(DesktopLlmStatus.WARN, response.status());
    assertEquals("LLM 配置不完整", response.label());
    assertEquals(true, response.replyGenerationEnabled());
  }

  @Test
  void returnsOkLlmStatusWhenReplyGenerationConfigIsComplete() {
    when(repository.findValue(DesktopStatusService.LLM_REPLY_GENERATION_ENABLED)).thenReturn(Optional.of("true"));
    when(repository.findValue(DesktopStatusService.LLM_API_BASE_URL)).thenReturn(Optional.of("https://llm.example.com"));
    when(repository.findValue(DesktopStatusService.LLM_API_KEY)).thenReturn(Optional.of("secret"));
    when(repository.findValue(DesktopStatusService.LLM_MODEL)).thenReturn(Optional.of("gpt-4.1-mini"));
    llmCallAnalyticsRepository.analytics = new LlmCallAnalytics(
        new LlmCallAnalytics.Summary(2, 0.0, 120),
        java.util.List.of());

    DesktopLlmStatusResponse response = service.llmStatus();

    assertEquals(DesktopLlmStatus.OK, response.status());
    assertEquals("LLM 回复生成已配置", response.label());
    assertEquals(true, response.replyGenerationEnabled());
  }

  @Test
  void returnsWarnLlmStatusWhenRecentReplyGenerationCallsKeepFailing() {
    when(repository.findValue(DesktopStatusService.LLM_REPLY_GENERATION_ENABLED)).thenReturn(Optional.of("true"));
    when(repository.findValue(DesktopStatusService.LLM_API_BASE_URL)).thenReturn(Optional.of("https://llm.example.com"));
    when(repository.findValue(DesktopStatusService.LLM_API_KEY)).thenReturn(Optional.of("secret"));
    when(repository.findValue(DesktopStatusService.LLM_MODEL)).thenReturn(Optional.of("gpt-4.1-mini"));
    llmCallAnalyticsRepository.analytics = new LlmCallAnalytics(
        new LlmCallAnalytics.Summary(4, 0.25, 900),
        java.util.List.of());

    DesktopLlmStatusResponse response = service.llmStatus();

    assertEquals(DesktopLlmStatus.WARN, response.status());
    assertEquals("LLM 近期调用失败率较高", response.label());
    assertEquals(true, response.detail().contains("成功率 25%"));
  }

  private static class StubLlmCallAnalyticsRepository extends LlmCallAnalyticsRepository {
    private LlmCallAnalytics analytics = new LlmCallAnalytics(
        new LlmCallAnalytics.Summary(0, 0.0, 0),
        java.util.List.of());

    StubLlmCallAnalyticsRepository() {
      super(null);
    }

    @Override
    public LlmCallAnalytics query(int days, String scene, String leadType) {
      return analytics;
    }
  }
}
