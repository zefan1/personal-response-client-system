package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TagConfigProviderTest {

  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:tag_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("CREATE TABLE system_configs (config_key VARCHAR(100) PRIMARY KEY, config_value TEXT NOT NULL)");
  }

  @Test
  void readsDatabaseConfigurationAndClampsUnsafeValues() {
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES ('tag.cache_refresh_interval_s', '20')");
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES ('tag.value_max_per_category', '500')");
    TagConfigProvider provider = new TagConfigProvider(new SystemConfigRepository(jdbcTemplate), 300, 50);

    provider.load();

    assertThat(provider.get().cacheRefreshIntervalSeconds()).isEqualTo(60);
    assertThat(provider.get().valueMaxPerCategory()).isEqualTo(200);
  }

  @Test
  void keepsFallbackWhenDatabaseValuesAreInvalid() {
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES ('tag.cache_refresh_interval_s', 'broken')");
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES ('tag.value_max_per_category', '')");
    TagConfigProvider provider = new TagConfigProvider(new SystemConfigRepository(jdbcTemplate), 180, 40);

    provider.load();

    assertThat(provider.get()).isEqualTo(new TagRuntimeConfig(180, 40));
  }
}
