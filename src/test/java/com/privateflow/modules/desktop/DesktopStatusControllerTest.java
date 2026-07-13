package com.privateflow.modules.desktop;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.runtime.RuntimeModeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DesktopStatusControllerTest {

  private DesktopStatusService desktopStatusService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    desktopStatusService = org.mockito.Mockito.mock(DesktopStatusService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new DesktopStatusController(desktopStatusService))
        .build();
  }

  @Test
  void statusReturnsDesktopRuntimePayload() throws Exception {
    when(desktopStatusService.currentStatus()).thenReturn(new DesktopStatusResponse(
        "System Admin",
        Role.ADMIN,
        new DesktopSkillStatusResponse(DesktopSkillStatus.EXPIRING, "2026-07-12", 3, "即将到期"),
        new RuntimeModeStatus(true, "本地模拟模式", "外部表格、AI 技能和图片识别使用本地 Mock 响应。"),
        new DesktopRuntimeConfigResponse(15)));

    mockMvc.perform(get("/api/v1/desktop/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accountName").value("System Admin"))
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.skillStatus.status").value("EXPIRING"))
        .andExpect(jsonPath("$.data.skillStatus.expireAt").value("2026-07-12"))
        .andExpect(jsonPath("$.data.skillStatus.daysLeft").value(3))
        .andExpect(jsonPath("$.data.llmStatus.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.llmStatus.replyGenerationEnabled").value(false))
        .andExpect(jsonPath("$.data.runtimeMode.mockExternals").value(true))
        .andExpect(jsonPath("$.data.runtimeMode.label").value("本地模拟模式"))
        .andExpect(jsonPath("$.data.runtimeConfig.clipboardScreenshotConfirmPromptS").value(15));

    verify(desktopStatusService).currentStatus();
  }
}
