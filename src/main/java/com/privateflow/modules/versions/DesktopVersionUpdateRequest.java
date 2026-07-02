package com.privateflow.modules.versions;

public record DesktopVersionUpdateRequest(
    String version,
    String downloadUrl,
    String changelog,
    UpdateStrategy updateStrategy,
    Integer gradualPercent,
    Long fileSize
) {
}
