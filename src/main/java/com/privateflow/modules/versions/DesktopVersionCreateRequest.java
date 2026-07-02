package com.privateflow.modules.versions;

public record DesktopVersionCreateRequest(
    String version,
    DesktopPlatform platform,
    String downloadUrl,
    String changelog,
    UpdateStrategy updateStrategy,
    Integer gradualPercent,
    Long fileSize
) {
}
