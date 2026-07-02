package com.privateflow.modules.versions;

import java.time.LocalDateTime;

public record DesktopVersion(
    long id,
    String version,
    DesktopPlatform platform,
    VersionStatus status,
    UpdateStrategy updateStrategy,
    Integer gradualPercent,
    String downloadUrl,
    Long fileSize,
    String changelog,
    LocalDateTime revokedAt,
    String revokeReason,
    String alternativeVersion,
    LocalDateTime publishedAt,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
