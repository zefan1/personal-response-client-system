package com.privateflow.modules.templates;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TemplatePromotionAdminControllerTest {

  private TemplatePromotionService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(TemplatePromotionService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new TemplatePromotionAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void supervisorCanListPublishAndNotPublishCandidates() throws Exception {
    when(service.listCandidates(TemplatePromotionCandidateStatus.CANDIDATE)).thenReturn(List.of(candidate()));
    when(service.publish(eq(42L), any())).thenReturn(Map.of("candidateId", 42L, "quickSearchItemId", 77L));
    doNothing().when(service).markNotPublished(43L);
    ObjectMapper objectMapper = new ObjectMapper();
    PublishTeamTemplateRequest request = new PublishTeamTemplateRequest("Team opening", "TM42", "LEAD", true);

    mockMvc.perform(get("/admin/api/v1/template-promotion-candidates").param("status", "CANDIDATE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].ownerUsername").value("keeper-a"))
        .andExpect(jsonPath("$.data[0].originalAiReply").value("Original AI reply"))
        .andExpect(jsonPath("$.data[0].personalTemplateUsageCount").value(3));

    mockMvc.perform(post("/admin/api/v1/template-promotion-candidates/42/publish")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quickSearchItemId").value(77));

    mockMvc.perform(post("/admin/api/v1/template-promotion-candidates/43/not-publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(service).markNotPublished(43L);
  }

  private TemplatePromotionCandidate candidate() {
    return new TemplatePromotionCandidate(
        42L,
        11L,
        "keeper-a",
        "Original AI reply",
        "Edited opening",
        "Edited body",
        new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm")),
        TemplatePromotionCandidateStatus.CANDIDATE,
        null,
        null,
        LocalDateTime.of(2026, 7, 24, 13, 0),
        3L);
  }
}
