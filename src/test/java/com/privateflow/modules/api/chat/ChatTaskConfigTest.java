package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChatTaskConfigTest {

  private JdbcTemplate jdbcTemplate;
  private ChatTaskConfig config;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:chat_task_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS system_configs");
    jdbcTemplate.execute("""
        CREATE TABLE system_configs (
          config_key VARCHAR(100) PRIMARY KEY,
          config_value TEXT NOT NULL
        )
        """);
    config = new ChatTaskConfig(new SystemConfigRepository(jdbcTemplate));
  }

  @Test
  void clampsPendingReplyRetentionAndGenerationRecoveryRanges() {
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES (?, ?)",
        "chat.pending_reply_ttl_hours", "80");
    jdbcTemplate.update("INSERT INTO system_configs (config_key, config_value) VALUES (?, ?)",
        "chat.pending_reply_generating_timeout_s", "10");

    config.refresh();

    assertThat(config.pendingReplyTtlHours()).isEqualTo(72);
    assertThat(config.pendingReplyGeneratingTimeoutSeconds()).isEqualTo(30);
  }
}
