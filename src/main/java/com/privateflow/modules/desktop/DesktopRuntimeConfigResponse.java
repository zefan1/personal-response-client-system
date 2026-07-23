package com.privateflow.modules.desktop;

public record DesktopRuntimeConfigResponse(
    int clipboardScreenshotConfirmPromptS,
    int workbenchRefreshIntervalS
) {

  public DesktopRuntimeConfigResponse(int clipboardScreenshotConfirmPromptS) {
    this(clipboardScreenshotConfirmPromptS, 60);
  }
}
