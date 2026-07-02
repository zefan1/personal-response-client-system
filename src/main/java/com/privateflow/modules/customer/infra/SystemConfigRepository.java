package com.privateflow.modules.customer.infra;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigRepository {

  private final JdbcTemplate jdbcTemplate;

  public SystemConfigRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> findValue(String key) {
    return jdbcTemplate.query("SELECT config_value FROM system_configs WHERE config_key = ?",
        (rs, rowNum) -> rs.getString("config_value"), key).stream().findFirst();
  }
}
