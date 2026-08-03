package com.privateflow.modules.templates;

import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TemplateControllerTest {

  private PersonalTemplateService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(PersonalTemplateService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new TemplateController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void employeeCanSaveAndListPersonalTemplatesWithoutCandidateState() throws Exception {
    PersonalTemplate template = template();
    when(service.save(any())).thenReturn(template);
    when(service.listMine()).thenReturn(List.of(template));
    ObjectMapper objectMapper = new ObjectMapper();
    PersonalTemplateRequest request = new PersonalTemplateRequest(
        "Opening", "Edited body", "Original AI body",
        new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm")), "reply-session-1");

    mockMvc.perform(post("/api/v1/templates/personal")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.title").value("Opening"))
        .andExpect(jsonPath("$.data.status").doesNotExist())
        .andExpect(jsonPath("$.data.decidedBy").doesNotExist());

    mockMvc.perform(get("/api/v1/templates/personal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("Opening"))
        .andExpect(jsonPath("$.data[0].status").doesNotExist());
    verify(service).save(any());
    verify(service).listMine();
  }

  @Test
  void employeeCanReadPublishedTeamTemplatesAndRecordCopyWithoutSendConfirmation() throws Exception {
    when(service.listTeamTemplates()).thenReturn(List.of(teamTemplate()));
    when(service.recordPersonalTemplateUse(41L)).thenReturn(java.util.Map.of("recorded", true, "source", "PERSONAL"));
    when(service.recordTeamTemplateUse(77L)).thenReturn(java.util.Map.of("recorded", true, "source", "TEAM"));

    mockMvc.perform(get("/api/v1/templates/team"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].quickSearchItemId").value(77))
        .andExpect(jsonPath("$.data[0].title").value("Team opening"));
    mockMvc.perform(post("/api/v1/templates/personal/41/use"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("PERSONAL"));
    mockMvc.perform(post("/api/v1/templates/team/77/use"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("TEAM"));
    verify(service).recordPersonalTemplateUse(41L);
    verify(service).recordTeamTemplateUse(77L);
  }

  private PersonalTemplate template() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 24, 13, 0);
    return new PersonalTemplate(
        41L,
        "Opening",
        "Edited body",
        new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm")),
        "reply-session-1",
        0,
        now,
        now);
  }

  private TeamTemplate teamTemplate() {
    return new TeamTemplate(
        77L,
        42L,
        "Team opening",
        "Edited body",
        "TM42",
        new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm")),
        LocalDateTime.of(2026, 7, 24, 13, 0));
  }
}
