package com.privateflow.modules.analytics;

import com.privateflow.modules.customer.admin.CustomerQuerySpec;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagAnalyticsRepository {

  private final JdbcTemplate jdbcTemplate;

  public TagAnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<String> resolveEnabledKeeperPhones(List<Long> teamLeaderIds) {
    return List.of();
  }

  public TagAnalyticsResponse analyze(
      CustomerQuerySpec customerSpec,
      CustomerQuerySpec optionSpec,
      TagAnalyticsWindow window) {
    return TagAnalyticsResponse.empty(window);
  }
}
