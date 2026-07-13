package com.privateflow.modules.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.config.SystemConfig;
import com.privateflow.modules.api.config.SystemConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyServiceTest {

  private JdbcTemplate jdbcTemplate;
  private SystemConfigProvider configProvider;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:production_safety;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS accounts");
    jdbcTemplate.execute("""
        CREATE TABLE accounts (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          password_hash VARCHAR(100) NOT NULL
        )
        """);
    configProvider = Mockito.mock(SystemConfigProvider.class);
  }

  @Test
  void productionRejectsDefaultJwtSecret() {
    when(configProvider.get()).thenReturn(config(ProductionSafetyService.DEFAULT_JWT_SECRET));
    ProductionSafetyService service = new ProductionSafetyService(
        new RuntimeModeService(false, new MockEnvironment().withProperty("app.environment", "production")),
        configProvider,
        jdbcTemplate);

    assertThatThrownBy(service::validateStartupSafety)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be changed");
  }

  @Test
  void productionRejectsPlainAccountPasswords() {
    jdbcTemplate.update("INSERT INTO accounts (password_hash) VALUES ('{plain}admin123')");
    when(configProvider.get()).thenReturn(config("changed-secret"));
    ProductionSafetyService service = new ProductionSafetyService(
        new RuntimeModeService(false, new MockEnvironment().withProperty("SPRING_PROFILES_ACTIVE", "prod")),
        configProvider,
        jdbcTemplate);

    assertThatThrownBy(service::validateStartupSafety)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("plain account passwords");
  }

  private SystemConfig config(String jwtSecret) {
    return new SystemConfig(
        jwtSecret,
        24,
        7,
        30,
        60,
        100,
        15000,
        90,
        10,
        300,
        false,
        "",
        "",
        "",
        300,
        7,
        30,
        "config:change",
        "ws:push");
  }
}
