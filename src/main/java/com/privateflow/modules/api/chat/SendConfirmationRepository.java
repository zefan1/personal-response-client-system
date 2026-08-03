package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SendConfirmationRepository {

  private final JdbcTemplate jdbcTemplate;

  public SendConfirmationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean claim(String confirmationId, String operator, String phone) {
    int inserted = jdbcTemplate.update("""
        INSERT IGNORE INTO send_confirmations (operator, confirmation_id, phone)
        VALUES (?, ?, ?)
        """, operator, confirmationId, phone);
    if (inserted == 1) {
      return true;
    }
    String existingPhone = jdbcTemplate.queryForObject("""
        SELECT phone FROM send_confirmations
        WHERE operator = ? AND confirmation_id = ?
        """, String.class, operator, confirmationId);
    if (phone.equals(existingPhone)) {
      return false;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "确认编号已用于其他客户，请重新复制回复");
  }
}
