package com.privateflow.modules.versions;

public record VersionReportRequest(
    String clientId,
    String version,
    DesktopPlatform platform,
    String osVersion
) {
}
