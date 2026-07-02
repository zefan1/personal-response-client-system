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
    int loginFailWindowS,
    boolean captchaEnabled,
    String captchaProvider,
    String captchaAppId,
    String captchaSecret,
    int requestContextTtlS,
    int wsOfflineRetentionDays,
    int alertRetentionDays,
    String configChangeChannel,
    String wsPushChannel
) {
  public long jwtAccessTokenTtlS() {
    return jwtExpireHours * 3600L;
  }

  public long jwtRefreshTokenTtlS() {
    return jwtRefreshDays * 86400L;
  }
}
