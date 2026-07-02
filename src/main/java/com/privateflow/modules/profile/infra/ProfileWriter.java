package com.privateflow.modules.profile.infra;

import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileWriter {

  private final JdbcTemplate jdbcTemplate;
  private final ProfileFieldRegistry fieldRegistry;
  private final ApplicationEventPublisher eventPublisher;

  public ProfileWriter(
      JdbcTemplate jdbcTemplate,
      ProfileFieldRegistry fieldRegistry,
      ApplicationEventPublisher eventPublisher) {
    this.jdbcTemplate = jdbcTemplate;
    this.fieldRegistry = fieldRegistry;
    this.eventPublisher = eventPublisher;
  }

  public int write(String phone, Map<String, Object> fields, Integer expectedVersion, boolean publishEvent) {
    Map<String, Object> accepted = acceptedFields(fields);
    if (accepted.isEmpty()) {
      return currentVersion(phone);
    }
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("UPDATE customers SET ");
    int index = 0;
    for (Map.Entry<String, Object> entry : accepted.entrySet()) {
      if (index++ > 0) {
        sql.append(", ");
      }
      ProfileFieldRegistry.FieldSpec spec = fieldRegistry.spec(entry.getKey());
      sql.append(spec.columnName()).append(" = ?");
      args.add(fieldRegistry.normalizeValue(entry.getKey(), entry.getValue()));
    }
    sql.append(", version = version + 1, updated_at = NOW() WHERE phone = ?");
    args.add(phone);
    if (expectedVersion != null) {
      sql.append(" AND version = ?");
      args.add(expectedVersion);
    }
    int updated = jdbcTemplate.update(sql.toString(), args.toArray());
    if (updated != 1) {
      throw new ProfileUpdateException(ProfileErrorCodes.VERSION_CONFLICT, "档案已被更新，请刷新后重试");
    }
    int version = currentVersion(phone);
    if (publishEvent) {
      eventPublisher.publishEvent(new ProfileUpdatedEvent(phone, List.copyOf(accepted.keySet())));
    }
    return version;
  }

  public void touchFollowup(String phone, String summary, Integer expectedVersion) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("lastFollowupAt", LocalDateTime.now());
    fields.put("followupNotes", summary);
    write(phone, fields, expectedVersion, true);
  }

  private Map<String, Object> acceptedFields(Map<String, Object> fields) {
    Map<String, Object> accepted = new LinkedHashMap<>();
    if (fields == null) {
      return accepted;
    }
    fields.forEach((key, value) -> {
      if (fieldRegistry.supports(key)) {
        accepted.put(key, value);
      }
    });
    return accepted;
  }

  public int currentVersion(String phone) {
    return jdbcTemplate.query(
        "SELECT version FROM customers WHERE phone = ? LIMIT 1",
        (rs, rowNum) -> rs.getInt("version"),
        phone).stream().findFirst().orElse(0);
  }
}
