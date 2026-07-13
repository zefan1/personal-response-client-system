package com.privateflow.modules.api.ai;

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
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiConfigControllerTest {

  private AiEnvironmentService environmentService;
  private PromptVersionService promptVersionService;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    environmentService = org.mockito.Mockito.mock(AiEnvironmentService.class);
    promptVersionService = org.mockito.Mockito.mock(PromptVersionService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AiConfigController(environmentService, promptVersionService))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void skillEnvironmentListUsesSkillType() throws Exception {
    when(environmentService.list(AiEnvironmentType.SKILL)).thenReturn(List.of(environment(1L, "skill-prod", "skill", true)));

    mockMvc.perform(get("/admin/api/v1/skill-environments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].provider").value("skill"))
        .andExpect(jsonPath("$.data[0].active").value(true));

    verify(environmentService).list(AiEnvironmentType.SKILL);
  }

  @Test
  void createSkillReturnsCreatedEnvironment() throws Exception {
    AiEnvironmentRequest request = new AiEnvironmentRequest("skill-prod", "https://skill.example.com", "key-1234");
    when(environmentService.create(eq(AiEnvironmentType.SKILL), any())).thenReturn(environment(2L, "skill-prod", "skill", false));

    mockMvc.perform(post("/admin/api/v1/skill-environments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(2))
        .andExpect(jsonPath("$.data.provider").value("skill"));
  }

  @Test
  void createValidationFailureMapsToBadRequest() throws Exception {
    when(environmentService.create(eq(AiEnvironmentType.SKILL), any()))
        .thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "baseUrl must be valid URL"));

    mockMvc.perform(post("/admin/api/v1/skill-environments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"envName\":\"bad\",\"baseUrl\":\"bad\",\"apiKey\":\"key\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void updateAndActivateSkillUseSkillType() throws Exception {
    AiEnvironmentRequest request = new AiEnvironmentRequest("skill-prod", "https://skill.example.com/v2", "key-5678");
    when(environmentService.update(eq(AiEnvironmentType.SKILL), eq(2L), any())).thenReturn(environment(2L, "skill-prod", "skill", false));
    when(environmentService.activate(AiEnvironmentType.SKILL, 2L)).thenReturn(environment(2L, "skill-prod", "skill", true));

    mockMvc.perform(put("/admin/api/v1/skill-environments/2")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(2));

    mockMvc.perform(put("/admin/api/v1/skill-environments/2/activate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));

    verify(environmentService).update(eq(AiEnvironmentType.SKILL), eq(2L), any());
    verify(environmentService).activate(AiEnvironmentType.SKILL, 2L);
  }

  @Test
  void deleteSkillCallsService() throws Exception {
    doNothing().when(environmentService).delete(AiEnvironmentType.SKILL, 2L);

    mockMvc.perform(delete("/admin/api/v1/skill-environments/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(environmentService).delete(AiEnvironmentType.SKILL, 2L);
  }

  @Test
  void imageEnvironmentCrudUsesImageType() throws Exception {
    AiEnvironmentRequest request = new AiEnvironmentRequest("image-prod", "https://image.example.com", "key-1234");
    when(environmentService.list(AiEnvironmentType.IMAGE)).thenReturn(List.of(environment(3L, "image-prod", "image", true)));
    when(environmentService.create(eq(AiEnvironmentType.IMAGE), any())).thenReturn(environment(4L, "image-prod", "image", false));
    when(environmentService.update(eq(AiEnvironmentType.IMAGE), eq(4L), any())).thenReturn(environment(4L, "image-prod", "image", false));
    when(environmentService.activate(AiEnvironmentType.IMAGE, 4L)).thenReturn(environment(4L, "image-prod", "image", true));

    mockMvc.perform(get("/admin/api/v1/image-environments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].provider").value("image"));

    mockMvc.perform(post("/admin/api/v1/image-environments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(4));

    mockMvc.perform(put("/admin/api/v1/image-environments/4")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.provider").value("image"));

    mockMvc.perform(put("/admin/api/v1/image-environments/4/activate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));
  }

  @Test
  void deleteActiveImageEnvironmentFailureMapsToBadRequest() throws Exception {
    org.mockito.Mockito.doThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "active environment cannot be deleted"))
        .when(environmentService).delete(AiEnvironmentType.IMAGE, 4L);

    mockMvc.perform(delete("/admin/api/v1/image-environments/4"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void testImageReturnsDiagnosticPayload() throws Exception {
    when(environmentService.testImage(4L)).thenReturn(new ImageEnvironmentTestResponse(
        true,
        123L,
        Map.of("nickname", "acceptance", "messagesCount", 1, "hasPhone", true),
        null,
        null,
        null));

    mockMvc.perform(post("/admin/api/v1/image-environments/4/test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.elapsedMs").value(123))
        .andExpect(jsonPath("$.data.result.nickname").value("acceptance"));
  }

  @Test
  void llmEnvironmentCrudAndTestUseLlmType() throws Exception {
    AiEnvironmentRequest request = new AiEnvironmentRequest(
        "llm-prod",
        "https://llm.example.com",
        "key-llm",
        "gpt-4.1-mini",
        "OPENAI_COMPATIBLE",
        10000,
        0.2,
        1024);
    when(environmentService.list(AiEnvironmentType.LLM)).thenReturn(List.of(llmEnvironment(5L, true)));
    when(environmentService.create(eq(AiEnvironmentType.LLM), any())).thenReturn(llmEnvironment(6L, false));
    when(environmentService.update(eq(AiEnvironmentType.LLM), eq(6L), any())).thenReturn(llmEnvironment(6L, false));
    when(environmentService.activate(AiEnvironmentType.LLM, 6L)).thenReturn(llmEnvironment(6L, true));
    when(environmentService.testLlm(6L)).thenReturn(new ImageEnvironmentTestResponse(
        true,
        234L,
        Map.of("model", "gpt-4.1-mini", "protocol", "OPENAI_COMPATIBLE", "content", "OK"),
        null,
        null,
        null));

    mockMvc.perform(get("/admin/api/v1/llm-environments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].provider").value("llm"))
        .andExpect(jsonPath("$.data[0].model").value("gpt-4.1-mini"));

    mockMvc.perform(post("/admin/api/v1/llm-environments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(6));

    mockMvc.perform(put("/admin/api/v1/llm-environments/6")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.provider").value("llm"));

    mockMvc.perform(put("/admin/api/v1/llm-environments/6/activate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));

    mockMvc.perform(post("/admin/api/v1/llm-environments/6/test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.result.content").value("OK"));

    verify(environmentService).list(AiEnvironmentType.LLM);
    verify(environmentService).create(eq(AiEnvironmentType.LLM), any());
    verify(environmentService).update(eq(AiEnvironmentType.LLM), eq(6L), any());
    verify(environmentService).activate(AiEnvironmentType.LLM, 6L);
    verify(environmentService).testLlm(6L);
  }

  @Test
  void promptVersionsAndRestoreDelegateToPromptService() throws Exception {
    PromptVersion version = new PromptVersion(3, "content", "admin", true, "change", LocalDateTime.of(2026, 7, 3, 12, 0));
    when(promptVersionService.list("format")).thenReturn(new PromptVersionPage("skill.system_prompt_format", 3, List.of(version)));
    when(promptVersionService.restore(eq("format"), any())).thenReturn(Map.of("configKey", "skill.system_prompt_format", "restoredVersion", 3));

    mockMvc.perform(get("/admin/api/v1/skill-prompt/format/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currentVersion").value(3))
        .andExpect(jsonPath("$.data.versions[0].stable").value(true));

    mockMvc.perform(post("/admin/api/v1/skill-prompt/format/restore")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":3,\"operator\":\"admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.restoredVersion").value(3));
  }

  @Test
  void unsupportedPromptTypeMapsToBadRequest() throws Exception {
    when(promptVersionService.list("unknown")).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "unsupported prompt type"));

    mockMvc.perform(get("/admin/api/v1/skill-prompt/unknown/versions"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  private AiEnvironment environment(long id, String name, String provider, boolean active) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new AiEnvironment(
        id,
        name,
        provider,
        "https://" + provider + ".example.com",
        "1234",
        active,
        active ? now : null,
        active ? Boolean.TRUE : null,
        now,
        now);
  }

  private AiEnvironment llmEnvironment(long id, boolean active) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new AiEnvironment(
        id,
        "llm-prod",
        "llm",
        "https://llm.example.com",
        "1234",
        "gpt-4.1-mini",
        "OPENAI_COMPATIBLE",
        10000,
        0.2,
        1024,
        active,
        active ? now : null,
        active ? Boolean.TRUE : null,
        now,
        now);
  }
}
