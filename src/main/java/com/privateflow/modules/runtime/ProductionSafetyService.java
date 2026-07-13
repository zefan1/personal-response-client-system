package com.privateflow.modules.runtime;

import com.privateflow.modules.api.config.SystemConfigProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductionSafetyService {

  static final String DEFAULT_JWT_SECRET = "change-me-in-production-private-domain-assistant";

  private final RuntimeModeService runtimeModeService;
  private final SystemConfigProvider systemConfigProvider;
  private final JdbcTemplate jdbcTemplate;

  public ProductionSafetyService(
      RuntimeModeService runtimeModeService,
      SystemConfigProvider systemConfigProvider,
      JdbcTemplate jdbcTemplate) {
    this.runtimeModeService = runtimeModeService;
    this.systemConfigProvider = systemConfigProvider;
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isProduction() {
    return runtimeModeService.isProductionEnvironment();
  }

  public void validateStartupSafety() {
    if (!isProduction()) {
      return;
    }
    if (DEFAULT_JWT_SECRET.equals(systemConfigProvider.get().jwtSecret())) {
      throw new IllegalStateException("system.jwt_secret/SYSTEM_JWT_SECRET must be changed in production");
    }
    Long plainPasswordCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE password_hash LIKE '{plain}%'",
        Long.class);
    if (plainPasswordCount != null && plainPasswordCount > 0) {
      throw new IllegalStateException("plain account passwords are forbidden in production");
    }
  }
}
