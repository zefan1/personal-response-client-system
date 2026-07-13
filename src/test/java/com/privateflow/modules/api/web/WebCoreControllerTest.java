package com.privateflow.modules.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthService;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.auth.LoginRequest;
import com.privateflow.modules.api.auth.LoginResponse;
import com.privateflow.modules.api.auth.RefreshRequest;
import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.api.config.SystemConfig;
import com.privateflow.modules.api.config.SystemConfigProvider;
import com.privateflow.modules.api.health.HealthService;
import com.privateflow.modules.api.help.HelpService;
import com.privateflow.modules.desktop.DesktopRuntimeConfigResponse;
import com.privateflow.modules.desktop.DesktopSkillStatus;
import com.privateflow.modules.desktop.DesktopSkillStatusResponse;
import com.privateflow.modules.desktop.DesktopStatusController;
import com.privateflow.modules.desktop.DesktopStatusResponse;
import com.privateflow.modules.desktop.DesktopStatusService;
import com.privateflow.modules.runtime.RuntimeModeStatus;
import com.privateflow.modules.quicksearch.ContentType;
import com.privateflow.modules.quicksearch.QuickSearchController;
import com.privateflow.modules.quicksearch.QuickSearchItem;
import com.privateflow.modules.quicksearch.QuickSearchService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebCoreControllerTest {

  private AuthService authService;
  private SystemConfigProvider configProvider;
  private ConfigAdminService configAdminService;
  private HealthService healthService;
  private HelpService helpService;
  private QuickSearchService quickSearchService;
  private DesktopStatusService desktopStatusService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authService = org.mockito.Mockito.mock(AuthService.class);
    configProvider = org.mockito.Mockito.mock(SystemConfigProvider.class);
    configAdminService = org.mockito.Mockito.mock(ConfigAdminService.class);
    healthService = org.mockito.Mockito.mock(HealthService.class);
    helpService = org.mockito.Mockito.mock(HelpService.class);
    quickSearchService = org.mockito.Mockito.mock(QuickSearchService.class);
    desktopStatusService = org.mockito.Mockito.mock(DesktopStatusService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(
            new AuthController(authService, configProvider),
            new ConfigController(configAdminService),
            new HealthController(healthService),
            new HelpController(helpService),
            new QuickSearchController(quickSearchService),
            new DesktopStatusController(desktopStatusService))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void authLoginRefreshAndConfigEndpointsReturnPayloads() throws Exception {
    AuthUser admin = new AuthUser("admin", "Admin", Role.ADMIN, null);
    when(authService.login(any(), eq("203.0.113.10"), eq(false))).thenReturn(new LoginResponse("access", "refresh", 3600, admin));
    when(authService.login(any(), eq("198.51.100.7"), eq(true))).thenReturn(new LoginResponse("admin-access", "admin-refresh", 3600, admin));
    when(authService.refresh(any(), any())).thenReturn(new LoginResponse("new-access", "new-refresh", 3600, admin));
    when(configProvider.get()).thenReturn(systemConfig());

    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", "203.0.113.10, 10.0.0.2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.account.role").value("ADMIN"));
    mockMvc.perform(post("/admin/api/v1/auth/login")
            .header("X-Forwarded-For", "198.51.100.7")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"admin\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("admin-access"));
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"refresh\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    mockMvc.perform(get("/api/v1/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.captchaEnabled").value(true))
        .andExpect(jsonPath("$.data.captchaProvider").value("turnstile"));

    ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
    verify(authService).login(loginCaptor.capture(), eq("203.0.113.10"), eq(false));
    org.junit.jupiter.api.Assertions.assertEquals("admin", loginCaptor.getValue().loginPhone());
    verify(authService).refresh(any(RefreshRequest.class), any());
  }

  @Test
  void configControllerBindsPrefixKeyAndUpdateBody() throws Exception {
    when(configAdminService.list("system.")).thenReturn(Map.of("system.captcha_enabled", "true"));
    when(configAdminService.get("system.captcha_enabled")).thenReturn(Map.of("key", "system.captcha_enabled", "value", "true"));
    when(configAdminService.update(eq("system.captcha_enabled"), any())).thenReturn(Map.of("key", "system.captcha_enabled", "value", "false"));

    mockMvc.perform(get("/admin/api/v1/configs").param("prefix", "system."))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data['system.captcha_enabled']").value("true"));
    mockMvc.perform(get("/admin/api/v1/configs/system.captcha_enabled"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.key").value("system.captcha_enabled"));
    mockMvc.perform(put("/admin/api/v1/configs/system.captcha_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"false\",\"changedBy\":\"admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.value").value("false"));

    verify(configAdminService).list("system.");
    verify(configAdminService).update(eq("system.captcha_enabled"), any());
  }

  @Test
  void healthHelpAndQuickSearchEndpointsReturnPayloads() throws Exception {
    when(healthService.health()).thenReturn(Map.of("status", "UP"));
    when(helpService.request(any())).thenReturn(Map.of("requestId", 7));
    when(helpService.resolve(any())).thenReturn(Map.of("resolved", true));
    when(desktopStatusService.currentStatus()).thenReturn(new DesktopStatusResponse(
        "Admin",
        Role.ADMIN,
        new DesktopSkillStatusResponse(DesktopSkillStatus.OK, "2026-08-01", 27, "有效至 2026-08-01"),
        new RuntimeModeStatus(false, "真实接口模式", "外部表格、AI 技能和图片识别调用真实接口。"),
        new DesktopRuntimeConfigResponse(10)));
    when(quickSearchService.listEnabledItems()).thenReturn(List.of(new QuickSearchItem(
        1L,
        ContentType.TEMPLATE,
        "OPENING",
        "TUAN_GOU",
        "Opening",
        "op",
        "hello",
        null,
        1,
        true,
        LocalDateTime.of(2026, 7, 3, 12, 0))));

    mockMvc.perform(get("/admin/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UP"));
    mockMvc.perform(post("/api/v1/help/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"13800000000\",\"question\":\"Need manager\",\"aiSuggestions\":[{\"text\":\"Reply\",\"direction\":\"comfort\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requestId").value(7));
    mockMvc.perform(post("/api/v1/help/resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":7,\"replyText\":\"Use this\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolved").value(true));
    mockMvc.perform(get("/api/v1/quick-search/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].contentType").value("TEMPLATE"))
        .andExpect(jsonPath("$.data[0].shortcutCode").value("op"));
    mockMvc.perform(get("/api/v1/desktop/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountName").value("Admin"))
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.skillStatus.status").value("OK"))
        .andExpect(jsonPath("$.data.skillStatus.label").value("有效至 2026-08-01"))
        .andExpect(jsonPath("$.data.llmStatus.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.runtimeMode.mockExternals").value(false))
        .andExpect(jsonPath("$.data.runtimeMode.label").value("真实接口模式"))
        .andExpect(jsonPath("$.data.runtimeConfig.clipboardScreenshotConfirmPromptS").value(10));
  }

  private SystemConfig systemConfig() {
    return new SystemConfig(
        "secret",
        1,
        7,
        30,
        60,
        100,
        15000,
        90,
        10,
        300,
        true,
        "turnstile",
        "app",
        "secret",
        300,
        7,
        30,
        "config:change",
        "ws:push");
  }
}
