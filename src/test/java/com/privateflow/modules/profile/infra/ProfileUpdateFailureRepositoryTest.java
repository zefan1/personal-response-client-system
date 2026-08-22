package com.privateflow.modules.profile.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.CustomerMessageSentEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProfileUpdateFailureRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private ProfileUpdateFailureRepository repository;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:profile_update_failures;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS profile_update_failures");
    jdbcTemplate.execute("""
        CREATE TABLE profile_update_failures (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          customer_id BIGINT NOT NULL,
          phone VARCHAR(32),
          raw_messages_json CLOB NOT NULL,
          operator VARCHAR(100),
          stage VARCHAR(80) NOT NULL,
          error_code VARCHAR(100),
          error_message VARCHAR(1000),
          status VARCHAR(20) NOT NULL,
          retry_count INT NOT NULL DEFAULT 0,
          last_attempt_at TIMESTAMP,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """);
    repository = new ProfileUpdateFailureRepository(jdbcTemplate, new ObjectMapper());
  }

  @Test
  void persistsFailureAndTransitionsRetryToSuccess() {
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "腰痛", "12:00"));
    long id = repository.recordFailure(9L, "18800001111", messages, "keeper-1", "PROFILE_EXTRACTION",
        new IllegalStateException("model unavailable"));

    assertThat(repository.find(id).orElseThrow()).satisfies(record -> {
      assertThat(record.customerId()).isEqualTo(9L);
      assertThat(record.rawMessages()).containsExactlyElementsOf(messages);
      assertThat(record.status()).isEqualTo("FAILED");
    });
    assertThat(repository.markRetrying(id)).isTrue();
    assertThat(repository.find(id).orElseThrow().retryCount()).isEqualTo(1);
    repository.markSucceeded(id);
    assertThat(repository.find(id).orElseThrow().status()).isEqualTo("SUCCEEDED");
  }
}
