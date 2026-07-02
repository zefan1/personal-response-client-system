package com.privateflow.modules.followup.infra;

import com.privateflow.modules.followup.ReminderType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderLogRepository {

  private final JdbcTemplate jdbcTemplate;

  public ReminderLogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean alreadySent(String phone, long ruleId, LocalDate sentDate) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM reminder_sent_log
        WHERE phone = ? AND rule_id = ? AND sent_date = ?
        """, Integer.class, phone, ruleId, java.sql.Date.valueOf(sentDate));
    return count != null && count > 0;
  }

  public void markSent(String phone, long ruleId, ReminderType type) {
    jdbcTemplate.update("""
        INSERT INTO reminder_sent_log (phone, rule_id, reminder_type, sent_date)
        VALUES (?, ?, ?, CURDATE())
        ON DUPLICATE KEY UPDATE sent_at = sent_at
        """, phone, ruleId, type.name());
  }

  public List<String> findTodayPhones(ReminderType type) {
    return jdbcTemplate.query("""
        SELECT DISTINCT phone FROM reminder_sent_log
        WHERE sent_date = CURDATE() AND reminder_type = ?
        """, (rs, rowNum) -> rs.getString("phone"), type.name());
  }
}
