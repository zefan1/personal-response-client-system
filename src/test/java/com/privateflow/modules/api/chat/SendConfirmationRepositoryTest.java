package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SendConfirmationRepositoryTest {

  private SendConfirmationRepository repository;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:send_confirmations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS send_confirmations");
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_send_confirmation_state");
    jdbcTemplate.execute("""
        CREATE TABLE send_confirmations (
          operator VARCHAR(64) NOT NULL,
          confirmation_id VARCHAR(80) NOT NULL,
          phone VARCHAR(32) NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (operator, confirmation_id)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE pending_send_confirmation_state (
          confirmation_id VARCHAR(80) NOT NULL,
          operator VARCHAR(64) NOT NULL,
          customer_id BIGINT,
          phone VARCHAR(32),
          nickname VARCHAR(120),
          copied_text VARCHAR(4000) NOT NULL,
          reply_source VARCHAR(64),
          status VARCHAR(32) NOT NULL,
          reminder_count INT NOT NULL DEFAULT 0,
          last_reminder_at TIMESTAMP,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (confirmation_id, operator)
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

  @Test
  void claimsAwaitingReplyForSendAndMarksItSentOnlyAfterConfirmationCompletes() {
    insertPending("confirm-1", "AWAITING_DECISION");

    repository.claimPendingForSend("confirm-1", "keeper-1");
    assertThat(statusOf("confirm-1")).isEqualTo("CONFIRMING");

    repository.markPendingSent("confirm-1", "keeper-1", 42L);
    assertThat(statusOf("confirm-1")).isEqualTo("SENT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNSENT", "RECOGNITION_RETRY"})
  void refusesStaleSendConfirmationAfterEmployeeHasMadeAnotherDecision(String status) {
    insertPending("confirm-1", status);

    assertThatThrownBy(() -> repository.claimPendingForSend("confirm-1", "keeper-1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("不能再确认发送");
    assertThat(statusOf("confirm-1")).isEqualTo(status);
  }

  private void insertPending(String confirmationId, String status) {
    jdbcTemplate.update("""
        INSERT INTO pending_send_confirmation_state
          (confirmation_id, operator, copied_text, status)
        VALUES (?, 'keeper-1', '建议回复', ?)
        """, confirmationId, status);
  }

  private String statusOf(String confirmationId) {
    return jdbcTemplate.queryForObject("""
        SELECT status FROM pending_send_confirmation_state
        WHERE confirmation_id = ? AND operator = 'keeper-1'
        """, String.class, confirmationId);
  }
}
