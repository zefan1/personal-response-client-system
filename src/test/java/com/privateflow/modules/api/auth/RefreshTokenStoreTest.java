package com.privateflow.modules.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RefreshTokenStoreTest {

  private JdbcTemplate jdbcTemplate;
  private RefreshTokenStore store;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:refresh_tokens;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS auth_refresh_sessions");
    jdbcTemplate.execute("""
        CREATE TABLE auth_refresh_sessions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          username VARCHAR(64) NOT NULL,
          token_hash CHAR(64) NOT NULL,
          expires_at TIMESTAMP NOT NULL,
          revoked_at TIMESTAMP NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          last_used_at TIMESTAMP NULL,
          CONSTRAINT uk_auth_refresh_token_hash UNIQUE (token_hash)
        )
        """);
    store = new RefreshTokenStore(jdbcTemplate);
  }

  @Test
  void keepsMultipleSessionsForTheSameAccountIndependent() {
    String desktopToken = store.issue("admin", Duration.ofDays(30));
    String adminToken = store.issue("admin", Duration.ofDays(30));

    assertThat(store.rotate("admin", desktopToken, Duration.ofDays(30))).isPresent();
    assertThat(store.rotate("admin", adminToken, Duration.ofDays(30))).isPresent();
  }

  @Test
  void rotatingASessionInvalidatesThePresentedToken() {
    String original = store.issue("admin", Duration.ofDays(30));

    String rotated = store.rotate("admin", original, Duration.ofDays(30)).orElseThrow();

    assertThat(rotated).isNotEqualTo(original);
    assertThat(store.rotate("admin", original, Duration.ofDays(30))).isEmpty();
    assertThat(store.rotate("admin", rotated, Duration.ofDays(30))).isPresent();
  }

  @Test
  void rejectsExpiredSessions() {
    String token = store.issue("admin", Duration.ofDays(30));
    jdbcTemplate.update("UPDATE auth_refresh_sessions SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP)");

    assertThat(store.rotate("admin", token, Duration.ofDays(30))).isEmpty();
  }

  @Test
  void revokingAnAccountInvalidatesAllSessions() {
    String desktopToken = store.issue("admin", Duration.ofDays(30));
    String adminToken = store.issue("admin", Duration.ofDays(30));

    store.revoke("admin");

    assertThat(store.rotate("admin", desktopToken, Duration.ofDays(30))).isEmpty();
    assertThat(store.rotate("admin", adminToken, Duration.ofDays(30))).isEmpty();
  }

  @Test
  void revokingOneSessionKeepsOtherDevicesSignedIn() {
    String desktopToken = store.issue("admin", Duration.ofDays(30));
    String adminToken = store.issue("admin", Duration.ofDays(30));

    store.revoke("admin", desktopToken);

    assertThat(store.rotate("admin", desktopToken, Duration.ofDays(30))).isEmpty();
    assertThat(store.rotate("admin", adminToken, Duration.ofDays(30))).isPresent();
  }
}
