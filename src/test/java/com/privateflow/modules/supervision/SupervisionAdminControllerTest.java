package com.privateflow.modules.supervision;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupervisionAdminControllerTest {

  private SupervisionMetricsService metricsService;
  private SupervisionAdminReadService readService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    metricsService = org.mockito.Mockito.mock(SupervisionMetricsService.class);
    readService = org.mockito.Mockito.mock(SupervisionAdminReadService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new SupervisionAdminController(metricsService, readService))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void administratorReadsMetricsWithAllBusinessFilters() throws Exception {
    AuthContext.set(admin());
    when(metricsService.report(any())).thenReturn(Map.of(
        "AI_USAGE_RATE", new SupervisionMetric(2, 4, 0.5, "已复制客户", "已生成客户", true)));

    mockMvc.perform(get("/admin/api/v1/supervision/metrics")
            .param("from", "2026-07-01")
            .param("to", "2026-07-07")
            .param("operator", "alice")
            .param("channel", "WECHAT")
            .param("leadSource", "ads-form"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.metrics.AI_USAGE_RATE.numerator").value(2))
        .andExpect(jsonPath("$.data.metrics.AI_USAGE_RATE.denominator").value(4))
        .andExpect(jsonPath("$.data.metrics.AI_USAGE_RATE.rate").value(0.5))
        .andExpect(jsonPath("$.data.metrics.AI_USAGE_RATE.conversionTargetConfigured").value(true));

    verify(metricsService).report(new SupervisionMetricsQuery(
        LocalDateTime.of(2026, 7, 1, 0, 0),
        LocalDateTime.of(2026, 7, 8, 0, 0),
        "alice",
        "WECHAT",
        "ads-form"));
  }

  @Test
  void nonAdministratorsCannotReadAnySupervisionEndpoint() throws Exception {
    AuthContext.set(new AuthUser("keeper", "Keeper", Role.KEEPER, null));

    for (String path : List.of(
        "/admin/api/v1/supervision/metrics",
        "/admin/api/v1/supervision/events",
        "/admin/api/v1/supervision/metadata")) {
      mockMvc.perform(get(path))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Test
  void administratorReadsDynamicMetadataAndSanitizedEventDetails() throws Exception {
    AuthContext.set(admin());
    when(readService.metadata()).thenReturn(new SupervisionMetadata(
        List.of("alice"),
        List.of("WECHAT"),
        List.of("ads-form"),
        List.of("已成交"),
        List.of("REPLY_COPIED")));
    when(readService.events(any())).thenReturn(new SupervisionEventPage(
        List.of(new SupervisionEventView(
            7L,
            SupervisionEventType.REPLY_COPIED,
            "alice",
            "138****0001",
            "WECHAT",
            "ads-form",
            "alice",
            "CHAT_RECOGNIZE",
            "LLM",
            "您好，已为您整理可选方案。",
            LocalDateTime.of(2026, 7, 3, 12, 0))),
        1,
        1,
        20));

    mockMvc.perform(get("/admin/api/v1/supervision/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.operators[0]").value("alice"))
        .andExpect(jsonPath("$.data.channels[0]").value("WECHAT"))
        .andExpect(jsonPath("$.data.leadSources[0]").value("ads-form"))
        .andExpect(jsonPath("$.data.customerStages[0]").value("已成交"))
        .andExpect(jsonPath("$.data.eventTypes[0]").value("REPLY_COPIED"));

    mockMvc.perform(get("/admin/api/v1/supervision/events")
            .param("from", "2026-07-01")
            .param("to", "2026-07-07")
            .param("eventType", "REPLY_COPIED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].customerPhoneMasked").value("138****0001"))
        .andExpect(jsonPath("$.data.items[0].replyPreview").value("您好，已为您整理可选方案。"))
        .andExpect(jsonPath("$.data.items[0].screenshot").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].ocr").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].base64").doesNotExist());
  }

  private AuthUser admin() {
    return new AuthUser("admin", "Admin", Role.ADMIN, null);
  }
}
