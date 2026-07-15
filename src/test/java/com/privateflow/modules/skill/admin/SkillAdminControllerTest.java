package com.privateflow.modules.skill.admin;

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
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillAdminControllerTest {

  private SkillAdminService service;
  private SkillCallAnalyticsRepository analyticsRepository;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(SkillAdminService.class);
    analyticsRepository = org.mockito.Mockito.mock(SkillCallAnalyticsRepository.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new SkillAdminController(service, analyticsRepository))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void listBindsSceneAndLeadTypeFilters() throws Exception {
    when(service.list(Scene.OPENING, "PENDING")).thenReturn(List.of(binding(3L, Scene.OPENING, "PENDING", true)));

    mockMvc.perform(get("/admin/api/v1/skills")
            .param("scene", "OPENING")
            .param("leadType", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(3))
        .andExpect(jsonPath("$.data[0].scene").value("OPENING"))
        .andExpect(jsonPath("$.data[0].leadType").value("PENDING"));

    verify(service).list(Scene.OPENING, "PENDING");
  }

  @Test
  void invalidSceneQueryReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/skills").param("scene", "BROKEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void createReturnsCreatedBinding() throws Exception {
    SkillBindingRequest request = new SkillBindingRequest("skill-a", "Skill A", Scene.ACTIVE_REPLY, "TUAN_GOU", 8);
    when(service.create(any())).thenReturn(binding(4L, Scene.ACTIVE_REPLY, "TUAN_GOU", true));

    mockMvc.perform(post("/admin/api/v1/skills")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(4))
        .andExpect(jsonPath("$.data.scene").value("ACTIVE_REPLY"))
        .andExpect(jsonPath("$.data.leadType").value("TUAN_GOU"));
  }

  @Test
  void createValidationFailureUsesSkillAdminErrorShape() throws Exception {
    when(service.create(any())).thenThrow(new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "leadType invalid"));

    mockMvc.perform(post("/admin/api/v1/skills")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"skillId\":\"bad\",\"skillName\":\"bad\",\"scene\":\"OPENING\",\"leadType\":\"GENERAL\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(SkillAdminErrorCodes.BAD_REQUEST));
  }

  @Test
  void updateReturnsUpdatedBinding() throws Exception {
    SkillBindingRequest request = new SkillBindingRequest("skill-a", "Skill A", Scene.REGENERATE, "XIAN_SUO", 9);
    when(service.update(eq(4L), any())).thenReturn(binding(4L, Scene.REGENERATE, "XIAN_SUO", true));

    mockMvc.perform(put("/admin/api/v1/skills/4")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(4))
        .andExpect(jsonPath("$.data.scene").value("REGENERATE"))
        .andExpect(jsonPath("$.data.leadType").value("XIAN_SUO"));
  }

  @Test
  void toggleReturnsWarningAndEnabledState() throws Exception {
    when(service.toggle(4L, false)).thenReturn(new SkillToggleResponse(4L, false, "last enabled binding"));

    mockMvc.perform(put("/admin/api/v1/skills/4/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(4))
        .andExpect(jsonPath("$.data.enabled").value(false))
        .andExpect(jsonPath("$.data.warning").value("last enabled binding"));
  }

  @Test
  void deleteCallsServiceAndReturnsSuccess() throws Exception {
    doNothing().when(service).delete(4L);

    mockMvc.perform(delete("/admin/api/v1/skills/4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).delete(4L);
  }

  @Test
  void availableReturnsDistinctSkillList() throws Exception {
    when(service.availableSkills()).thenReturn(List.of(new AvailableSkill("skill-a", "Skill A")));

    mockMvc.perform(get("/admin/api/v1/skills/available"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].skillId").value("skill-a"));
  }

  @Test
  void testBindingReturnsSuggestionsAndRawResponse() throws Exception {
    Suggestion suggestion = new Suggestion("hello", "OPENING", "acceptance");
    SkillResponse raw = new SkillResponse(List.of(suggestion), null, null, ProfileUpdates.empty());
    when(service.test(eq(4L), any())).thenReturn(new SkillTestResponse(List.of(suggestion), 123L, raw, null));

    mockMvc.perform(post("/admin/api/v1/skills/4/test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"testMessage\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.responseTimeMs").value(123))
        .andExpect(jsonPath("$.data.suggestions[0].text").value("hello"))
        .andExpect(jsonPath("$.data.rawResponse.suggestions[0].direction").value("OPENING"));
  }

  @Test
  void profileTestBindingReturnsStructuredProfileAnalysis() throws Exception {
    when(service.test(eq(4L), any())).thenReturn(new SkillTestResponse(
        List.of(),
        88L,
        null,
        ProfileAnalysisResult.empty()));

    mockMvc.perform(post("/admin/api/v1/skills/4/test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"testMessage\":\"customer evidence\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.responseTimeMs").value(88))
        .andExpect(jsonPath("$.data.suggestions").isEmpty())
        .andExpect(jsonPath("$.data.profileAnalysis.profileUpdates.fields").isMap())
        .andExpect(jsonPath("$.data.profileAnalysis.tagDecisions").isArray());
  }

  @Test
  void analyticsPassesFiltersToRepository() throws Exception {
    SkillCallAnalytics analytics = new SkillCallAnalytics(
        new SkillCallAnalytics.Summary(10, 0.9, 120, 0.0),
        List.of(new SkillCallAnalytics.Detail("OPENING", "PENDING", 10, 9, 1, 120, 0.0)));
    when(analyticsRepository.query(14, "OPENING", "PENDING")).thenReturn(analytics);

    mockMvc.perform(get("/admin/api/v1/analytics/skill-calls")
            .param("days", "14")
            .param("scene", "OPENING")
            .param("leadType", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.summary.totalCalls").value(10))
        .andExpect(jsonPath("$.data.details[0].successCount").value(9));

    verify(analyticsRepository).query(14, "OPENING", "PENDING");
  }

  private SkillSceneBinding binding(long id, Scene scene, String leadType, boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new SkillSceneBinding(
        id,
        "skill-a",
        "Skill A",
        scene,
        leadType,
        8,
        enabled,
        null,
        now,
        now);
  }
}
