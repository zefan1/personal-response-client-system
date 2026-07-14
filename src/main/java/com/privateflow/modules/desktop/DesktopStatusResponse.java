package com.privateflow.modules.desktop;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.runtime.RuntimeModeStatus;
import java.util.Set;

public record DesktopStatusResponse(
    String accountName,
    Role role,
    Set<String> permissions,
    DesktopSkillStatusResponse skillStatus,
    DesktopLlmStatusResponse llmStatus,
    RuntimeModeStatus runtimeMode,
    DesktopRuntimeConfigResponse runtimeConfig
) {
  private static final DesktopLlmStatusResponse DEFAULT_LLM_STATUS = new DesktopLlmStatusResponse(
      DesktopLlmStatus.UNKNOWN,
      "LLM 状态未加载",
      "",
      false);

  public DesktopStatusResponse(
      String accountName,
      Role role,
      Set<String> permissions,
      DesktopSkillStatusResponse skillStatus,
      RuntimeModeStatus runtimeMode,
      DesktopRuntimeConfigResponse runtimeConfig) {
    this(accountName, role, permissions, skillStatus, DEFAULT_LLM_STATUS, runtimeMode, runtimeConfig);
  }

  public DesktopStatusResponse(
      String accountName,
      Role role,
      DesktopSkillStatusResponse skillStatus,
      RuntimeModeStatus runtimeMode,
      DesktopRuntimeConfigResponse runtimeConfig) {
    this(accountName, role, Set.of(), skillStatus, DEFAULT_LLM_STATUS, runtimeMode, runtimeConfig);
  }

  public DesktopStatusResponse(
      String accountName,
      Role role,
      DesktopSkillStatusResponse skillStatus,
      RuntimeModeStatus runtimeMode) {
    this(accountName, role, Set.of(), skillStatus, DEFAULT_LLM_STATUS, runtimeMode, new DesktopRuntimeConfigResponse(10));
  }

  public DesktopStatusResponse(
      String accountName,
      Role role,
      DesktopSkillStatusResponse skillStatus,
      DesktopLlmStatusResponse llmStatus,
      RuntimeModeStatus runtimeMode,
      DesktopRuntimeConfigResponse runtimeConfig) {
    this(accountName, role, Set.of(), skillStatus, llmStatus, runtimeMode, runtimeConfig);
  }
}
