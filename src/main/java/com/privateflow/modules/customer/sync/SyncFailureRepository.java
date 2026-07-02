package com.privateflow.modules.customer.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncFailureRepository {

  private final JdbcTemplate jdbcTemplate;

  public SyncFailureRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void record(String sourceTable, String sourceRowId, String phone, String reason, String rawData) {
    jdbcTemplate.update("""
        INSERT INTO sync_failure_log (source_table, source_row_id, phone, fail_reason, raw_data)
        VALUES (?, ?, ?, ?, ?)
        """,
        sourceTable,
        sourceRowId,
        phone,
        reason,
        rawData == null ? null : rawData.substring(0, Math.min(rawData.length(), 1000)));
  }
}
