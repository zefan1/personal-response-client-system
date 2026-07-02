package com.privateflow.modules.skill.infra;

import com.privateflow.modules.skill.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class SkillCallLogger {

  private static final Logger log = LoggerFactory.getLogger(SkillCallLogger.class);
  private final JdbcTemplate jdbcTemplate;

  public SkillCallLogger(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Async("skillLogExecutor")
  public void logCall(Scene scene, String leadType, String caller, String requestSummary, long responseTimeMs, boolean success, String errorMsg) {
    try {
      jdbcTemplate.update("""
          INSERT INTO skill_call_logs (scene, lead_type, caller, request_summary, response_time, success, error_msg, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
          """,
          scene == null ? null : scene.name(),
          leadType,
          caller == null || caller.isBlank() ? "UNKNOWN" : caller,
          requestSummary,
          (int) Math.min(Integer.MAX_VALUE, responseTimeMs),
          success ? 1 : 0,
          errorMsg == null ? null : errorMsg.substring(0, Math.min(errorMsg.length(), 500)));
    } catch (RuntimeException ex) {
      log.error("skill_call_logs insert failed, scene={}, success={}, reason={}", scene, success, ex.getMessage());
    }
  }
}
