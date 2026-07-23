package com.privateflow.modules.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenStore {

  private final JdbcTemplate jdbcTemplate;

  public RefreshTokenStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public String issue(String username, Duration ttl) {
    String token = newToken();
    jdbcTemplate.update("""
        INSERT INTO auth_refresh_sessions (username, token_hash, expires_at)
        VALUES (?, ?, ?)
        """, username, hash(token), java.sql.Timestamp.from(Instant.now().plus(ttl)));
    return token;
  }

  @Transactional
  public Optional<String> rotate(String username, String token, Duration ttl) {
    if (blank(username) || blank(token)) {
      return Optional.empty();
    }
    String next = newToken();
    int updated = jdbcTemplate.update("""
        UPDATE auth_refresh_sessions
        SET token_hash = ?, expires_at = ?, last_used_at = CURRENT_TIMESTAMP
        WHERE username = ?
          AND token_hash = ?
          AND revoked_at IS NULL
          AND expires_at > CURRENT_TIMESTAMP
        """, hash(next), java.sql.Timestamp.from(Instant.now().plus(ttl)), username, hash(token));
    return updated == 1 ? Optional.of(next) : Optional.empty();
  }

  public void revoke(String username) {
    if (blank(username)) {
      return;
    }
    jdbcTemplate.update("""
        UPDATE auth_refresh_sessions
        SET revoked_at = CURRENT_TIMESTAMP
        WHERE username = ? AND revoked_at IS NULL
        """, username);
  }

  public void revoke(String username, String token) {
    if (blank(username) || blank(token)) {
      return;
    }
    jdbcTemplate.update("""
        UPDATE auth_refresh_sessions
        SET revoked_at = CURRENT_TIMESTAMP
        WHERE username = ? AND token_hash = ? AND revoked_at IS NULL
        """, username, hash(token));
  }

  private String newToken() {
    return UUID.randomUUID() + "." + UUID.randomUUID();
  }

  private String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
