package com.privateflow.modules.profile.infra;

import com.privateflow.modules.profile.ProfileSuggestion;
import com.privateflow.modules.profile.SuggestionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SuggestionRepository {

  private static final RowMapper<ProfileSuggestion> ROW_MAPPER = new SuggestionRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public SuggestionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<ProfileSuggestion> findPendingByPhoneAndField(String phone, String fieldName) {
    return jdbcTemplate.query("""
        SELECT * FROM profile_update_suggestions
        WHERE phone = ? AND field_name = ? AND status = 'PENDING'
        LIMIT 1
        """, ROW_MAPPER, phone, fieldName).stream().findFirst();
  }

  public List<ProfileSuggestion> findPending(String phone, List<Long> ids, int limit) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT * FROM profile_update_suggestions
        WHERE phone = ? AND status = 'PENDING'
        """);
    args.add(phone);
    if (ids != null && !ids.isEmpty()) {
      sql.append(" AND id IN (");
      for (int i = 0; i < ids.size(); i++) {
        if (i > 0) {
          sql.append(",");
        }
        sql.append("?");
        args.add(ids.get(i));
      }
      sql.append(")");
    }
    sql.append(" ORDER BY created_at ASC LIMIT ?");
    args.add(limit);
    return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
  }

  public void upsertPending(String phone, String fieldName, Object currentValue, Object suggestedValue, String confidence) {
    Optional<ProfileSuggestion> existing = findPendingByPhoneAndField(phone, fieldName);
    if (existing.isPresent()) {
      jdbcTemplate.update("""
          UPDATE profile_update_suggestions
          SET current_value = ?, suggested_value = ?, confidence = ?, created_at = NOW(), resolved_at = NULL
          WHERE id = ?
          """,
          stringify(currentValue),
          stringify(suggestedValue),
          confidence,
          existing.get().id());
      return;
    }
    jdbcTemplate.update("""
        INSERT INTO profile_update_suggestions (phone, field_name, current_value, suggested_value, confidence, status)
        VALUES (?, ?, ?, ?, ?, 'PENDING')
        """,
        phone,
        fieldName,
        stringify(currentValue),
        stringify(suggestedValue),
        confidence);
  }

  public int countPending(String phone) {
    return jdbcTemplate.query(
        "SELECT COUNT(*) FROM profile_update_suggestions WHERE phone = ? AND status = 'PENDING'",
        (rs, rowNum) -> rs.getInt(1),
        phone).stream().findFirst().orElse(0);
  }

  public int rejectOldestPending(String phone, int count) {
    if (count <= 0) {
      return 0;
    }
    return jdbcTemplate.update("""
        UPDATE profile_update_suggestions
        SET status = 'REJECTED', resolved_at = NOW()
        WHERE phone = ? AND status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT ?
        """, phone, count);
  }

  public int markStatus(List<Long> ids, SuggestionStatus status) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    StringBuilder sql = new StringBuilder("""
        UPDATE profile_update_suggestions
        SET status = ?, resolved_at = NOW()
        WHERE status = 'PENDING' AND id IN (
        """);
    List<Object> args = new ArrayList<>();
    args.add(status.name());
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) {
        sql.append(",");
      }
      sql.append("?");
      args.add(ids.get(i));
    }
    sql.append(")");
    return jdbcTemplate.update(sql.toString(), args.toArray());
  }

  public int rejectExpired(int expireDays) {
    return jdbcTemplate.update("""
        UPDATE profile_update_suggestions
        SET status = 'REJECTED', resolved_at = NOW()
        WHERE status = 'PENDING'
          AND created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
        """, expireDays);
  }

  private String stringify(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.length() > 500 ? text.substring(0, 500) : text;
  }

  private static final class SuggestionRowMapper implements RowMapper<ProfileSuggestion> {
    @Override
    public ProfileSuggestion mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new ProfileSuggestion(
          rs.getLong("id"),
          rs.getString("phone"),
          rs.getString("field_name"),
          rs.getString("current_value"),
          rs.getString("suggested_value"),
          rs.getString("confidence"),
          SuggestionStatus.valueOf(rs.getString("status")),
          rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
          rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toLocalDateTime());
    }
  }
}
