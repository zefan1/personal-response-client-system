package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SendConfirmationRepositoryTest {

  private SendConfirmationRepository repository;

  @BeforeEach
  void setUp() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:send_confirmations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS send_confirmations");
    jdbcTemplate.execute("""
        CREATE TABLE send_confirmations (
          operator VARCHAR(64) NOT NULL,
          confirmation_id VARCHAR(80) NOT NULL,
          phone VARCHAR(32) NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (operator, confirmation_id)
        )
        """);
    repository = new SendConfirmationRepository(jdbcTemplate);
  }

  @Test
  void claimsAConfirmationOnlyOnceForTheSameEmployeeAndPhone() {
    assertThat(repository.claim("confirm-1", "keeper-1", "18800001111")).isTrue();
    assertThat(repository.claim("confirm-1", "keeper-1", "18800001111")).isFalse();
  }

  @Test
  void rejectsReusingAConfirmationForAnotherPhone() {
    repository.claim("confirm-1", "keeper-1", "18800001111");

    assertThatThrownBy(() -> repository.claim("confirm-1", "keeper-1", "18800002222"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("确认编号");
  }
}
