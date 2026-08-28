package com.privateflow.modules.versions;

import java.time.LocalDateTime;

public record DesktopReleaseInfo(
    String version,
    Long fileSize,
    String changelog,
    LocalDateTime publishedAt
) {
}
