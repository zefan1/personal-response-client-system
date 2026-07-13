package com.privateflow.modules.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LlmAdminControllerTest {

  private LlmRoutingService routingService;
  private LlmCallAnalyticsRepository analyticsRepository;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    routingService = org.mockito.Mockito.mock(LlmRoutingService.class);
    analyticsRepository = org.mockito.Mockito.mock(LlmCallAnalyticsRepository.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new LlmAdminController(routingService, analyticsRepository))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void routeCrudDelegatesToService() throws Exception {
    LlmRouteRequest request = new LlmRouteRequest(LlmScene.REPLY_GENERATION, "PENDING", 9L, 1, true);
    when(routingService.list(LlmScene.REPLY_GENERATION, "PENDING")).thenReturn(List.of(route(true)));
    when(routingService.create(any())).thenReturn(route(true));
    when(routingService.update(eq(3L), any())).thenReturn(route(true));
    when(routingService.toggle(3L, false)).thenReturn(route(false));
    doNothing().when(routingService).delete(3L);

    mockMvc.perform(get("/admin/api/v1/llm-routes")
            .param("scene", "REPLY_GENERATION")
            .param("leadType", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].scene").value("REPLY_GENERATION"))
        .andExpect(jsonPath("$.data[0].environmentId").value(9));

    mockMvc.perform(post("/admin/api/v1/llm-routes")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(3));

    mockMvc.perform(put("/admin/api/v1/llm-routes/3")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.model").value("gpt-4.1-mini"));

    mockMvc.perform(put("/admin/api/v1/llm-routes/3/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));

    mockMvc.perform(delete("/admin/api/v1/llm-routes/3"))
        .andExpect(status().isOk());

    verify(routingService).list(LlmScene.REPLY_GENERATION, "PENDING");
    verify(routingService).create(any());
    verify(routingService).update(eq(3L), any());
    verify(routingService).toggle(3L, false);
    verify(routingService).delete(3L);
  }

  @Test
  void analyticsReturnsSummary() throws Exception {
    when(analyticsRepository.query(7, null, null)).thenReturn(new LlmCallAnalytics(
        new LlmCallAnalytics.Summary(2, 0.5, 120),
        List.of(new LlmCallAnalytics.Detail("SUMMARY", "PENDING", 9L, "gpt-4.1-mini", 2, 1, 1, 120))));

    mockMvc.perform(get("/admin/api/v1/analytics/llm-calls"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.totalCalls").value(2))
        .andExpect(jsonPath("$.data.details[0].model").value("gpt-4.1-mini"));
  }

  private LlmSceneRoute route(boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 9, 19, 30);
    return new LlmSceneRoute(
        3L,
        LlmScene.REPLY_GENERATION,
        "PENDING",
        9L,
        "llm-prod",
        "gpt-4.1-mini",
        "OPENAI_COMPATIBLE",
        1,
        enabled,
        now,
        now);
  }
}
