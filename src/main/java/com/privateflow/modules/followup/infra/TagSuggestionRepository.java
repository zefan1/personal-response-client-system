package com.privateflow.modules.followup.infra;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TagSuggestionRepository {

  private final JdbcTemplate jdbcTemplate;

  public TagSuggestionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Long> findPending(String phone, String tagName) {
    return jdbcTemplate.query("""
        SELECT id FROM system_tag_suggestions
        WHERE phone = ? AND tag_name = ? AND status = 'PENDING'
        LIMIT 1
        """, (rs, rowNum) -> rs.getLong("id"), phone, tagName).stream().findFirst();
  }

  public boolean ignoredRecently(String phone, String tagName, int dedupDays) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM system_tag_suggestions
        WHERE phone = ? AND tag_name = ? AND status = 'IGNORED'
          AND ignored_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
        """, Integer.class, phone, tagName, dedupDays);
    return count != null && count > 0;
  }

  public Long upsertPending(String phone, String tagName, long ruleId, int dedupDays) {
    Optional<Long> existing = findPending(phone, tagName);
    if (existing.isPresent()) {
      return existing.get();
    }
    if (ignoredRecently(phone, tagName, dedupDays)) {
      return null;
    }
    jdbcTemplate.update("""
        INSERT INTO system_tag_suggestions (
          phone, customer_id, tag_name, rule_id, status, validation_status
        )
        VALUES (?, (SELECT id FROM customers WHERE phone = ? LIMIT 1), ?, ?, 'PENDING', 'UNVALIDATED_RULE_TEXT')
        """, phone, phone, tagName, ruleId);
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }
}
