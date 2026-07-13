package com.privateflow.modules.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LlmCallLogger {

  private static final Logger log = LoggerFactory.getLogger(LlmCallLogger.class);
  private final JdbcTemplate jdbcTemplate;

  public LlmCallLogger(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Async("llmLogExecutor")
  public void logCall(
      LlmScene scene,
      String leadType,
      String caller,
      Long routeId,
      Long environmentId,
      String model,
      String protocol,
      String requestSummary,
      long responseTimeMs,
      boolean success,
      String errorCode,
      String errorMsg) {
    try {
      jdbcTemplate.update("""
          INSERT INTO llm_call_logs
            (scene, lead_type, caller, route_id, llm_environment_id, model, protocol, request_summary, response_time, success, error_code, error_msg, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
          """,
          scene == null ? null : scene.name(),
          blankToNull(leadType),
          caller == null || caller.isBlank() ? "UNKNOWN" : caller,
          routeId,
          environmentId,
          blankToNull(model),
          blankToNull(protocol),
          trim(requestSummary, 2000),
          (int) Math.min(Integer.MAX_VALUE, responseTimeMs),
          success ? 1 : 0,
          blankToNull(errorCode),
          trim(errorMsg, 500));
    } catch (RuntimeException ex) {
      log.error("llm_call_logs insert failed, scene={}, success={}, reason={}", scene, success, ex.getMessage());
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String trim(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.substring(0, Math.min(value.length(), maxLength));
  }
}
