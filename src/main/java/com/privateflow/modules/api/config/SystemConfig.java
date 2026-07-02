package com.privateflow.modules.api.config;

public record SystemConfig(
    String jwtSecret,
    int jwtExpireHours,
    int jwtRefreshDays,
    int wsHeartbeatS,
    int wsTimeoutS,
    int wsReplayQueueSize,
    int requestTotalTimeoutMs,
    int auditLogRetentionDays,
    int loginFailLimit,
    int loginLockMinutes,
    int requestContextTtlS,
    int wsOfflineRetentionDays,
    int alertRetentionDays,
    String configChangeChannel,
    String wsPushChannel
) {
}
