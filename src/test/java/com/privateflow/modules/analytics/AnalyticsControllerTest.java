package com.privateflow.modules.analytics;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalyticsControllerTest {

  private AnalyticsService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(AnalyticsService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AnalyticsController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void overviewBindsDefaultDaysAndOptionalFilters() throws Exception {
    when(service.overview(7, "TUAN_GOU", "alice")).thenReturn(Map.of(
        "totalCustomers", 12,
        "conversionRate", 0.42));

    mockMvc.perform(get("/admin/api/v1/analytics/overview")
            .param("leadType", "TUAN_GOU")
            .param("caller", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalCustomers").value(12))
        .andExpect(jsonPath("$.data.conversionRate").value(0.42));

    verify(service).overview(7, "TUAN_GOU", "alice");
  }

  @Test
  void daysBasedReportsBindDaysLeadTypeAndCaller() throws Exception {
    when(service.staff(30, "XIAN_SUO", "manager")).thenReturn(Map.of("staff", List.of(Map.of("name", "Alice", "count", 3))));
    when(service.sources(30, "XIAN_SUO", "manager")).thenReturn(Map.of("sources", List.of(Map.of("source", "ad", "count", 4))));
    when(service.health(30, "XIAN_SUO", "manager")).thenReturn(Map.of("score", 91));
    when(service.risks(30, "XIAN_SUO", "manager")).thenReturn(Map.of("risks", List.of(Map.of("level", "HIGH", "count", 2))));
    when(service.contentRanking(30, "XIAN_SUO", "manager")).thenReturn(Map.of("items", List.of(Map.of("title", "话术", "uses", 9))));

    mockMvc.perform(get("/admin/api/v1/analytics/staff").param("days", "30").param("leadType", "XIAN_SUO").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.staff[0].name").value("Alice"));
    mockMvc.perform(get("/admin/api/v1/analytics/sources").param("days", "30").param("leadType", "XIAN_SUO").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sources[0].source").value("ad"));
    mockMvc.perform(get("/admin/api/v1/analytics/health").param("days", "30").param("leadType", "XIAN_SUO").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.score").value(91));
    mockMvc.perform(get("/admin/api/v1/analytics/risks").param("days", "30").param("leadType", "XIAN_SUO").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.risks[0].level").value("HIGH"));
    mockMvc.perform(get("/admin/api/v1/analytics/content-ranking").param("days", "30").param("leadType", "XIAN_SUO").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].uses").value(9));

    verify(service).staff(30, "XIAN_SUO", "manager");
    verify(service).sources(30, "XIAN_SUO", "manager");
    verify(service).health(30, "XIAN_SUO", "manager");
    verify(service).risks(30, "XIAN_SUO", "manager");
    verify(service).contentRanking(30, "XIAN_SUO", "manager");
  }

  @Test
  void nonDaysReportsBindLeadTypeAndCaller() throws Exception {
    when(service.funnels("PENDING", "manager")).thenReturn(Map.of("steps", List.of(Map.of("name", "识别", "count", 10))));
    when(service.stages("PENDING", "manager")).thenReturn(Map.of("stages", List.of(Map.of("stage", "NEW", "count", 6))));
    when(service.lifecycle("PENDING", "manager")).thenReturn(Map.of("lifecycle", List.of(Map.of("bucket", "today", "count", 2))));

    mockMvc.perform(get("/admin/api/v1/analytics/funnels").param("leadType", "PENDING").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.steps[0].name").value("识别"));
    mockMvc.perform(get("/admin/api/v1/analytics/stages").param("leadType", "PENDING").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stages[0].stage").value("NEW"));
    mockMvc.perform(get("/admin/api/v1/analytics/lifecycle").param("leadType", "PENDING").param("caller", "manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lifecycle[0].bucket").value("today"));

    verify(service).funnels("PENDING", "manager");
    verify(service).stages("PENDING", "manager");
    verify(service).lifecycle("PENDING", "manager");
  }

  @Test
  void invalidDaysQueryReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/analytics/overview").param("days", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void servicePermissionFailureMapsToForbidden() throws Exception {
    when(service.funnels(null, null)).thenThrow(new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied"));

    mockMvc.perform(get("/admin/api/v1/analytics/funnels"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.FORBIDDEN));
  }
}
