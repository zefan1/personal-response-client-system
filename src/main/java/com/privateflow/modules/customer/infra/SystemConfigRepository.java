package com.privateflow.modules.customer.infra;

import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
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

  public Map<String, String> findByPrefix(String prefix) {
    return jdbcTemplate.query(
        "SELECT config_key, config_value FROM system_configs WHERE config_key LIKE ?",
        (rs, rowNum) -> Map.entry(rs.getString("config_key"), rs.getString("config_value")),
        prefix + "%").stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
