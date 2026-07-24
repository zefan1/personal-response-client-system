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
}
