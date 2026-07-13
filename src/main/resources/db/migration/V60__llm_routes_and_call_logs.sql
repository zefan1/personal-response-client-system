CREATE TABLE IF NOT EXISTS llm_scene_routes (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene              VARCHAR(50)  NOT NULL,
  lead_type          VARCHAR(20)  NOT NULL DEFAULT '',
  llm_environment_id BIGINT       NOT NULL,
  priority           INT          NOT NULL DEFAULT 0,
  enabled            TINYINT      NOT NULL DEFAULT 1,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_scene_lead_enabled (scene, lead_type, enabled, priority),
  INDEX idx_environment (llm_environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM scene route configs';

CREATE TABLE IF NOT EXISTS llm_call_logs (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene              VARCHAR(50)   DEFAULT NULL,
  lead_type          VARCHAR(20)   DEFAULT NULL,
  caller             VARCHAR(50)   DEFAULT NULL,
  route_id           BIGINT        DEFAULT NULL,
  llm_environment_id BIGINT        DEFAULT NULL,
  model              VARCHAR(100)  DEFAULT NULL,
  protocol           VARCHAR(50)   DEFAULT NULL,
  request_summary    TEXT          DEFAULT NULL,
  response_time      INT           DEFAULT NULL,
  success            TINYINT       NOT NULL DEFAULT 0,
  error_code         VARCHAR(50)   DEFAULT NULL,
  error_msg          VARCHAR(500)  DEFAULT NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_caller_time (caller, created_at),
  INDEX idx_success_time (success, created_at),
  INDEX idx_scene_lead_time (scene, lead_type, created_at),
  INDEX idx_environment_time (llm_environment_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM call logs';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.routing.enabled', 'true', 'Enable scene based LLM routing'),
  ('llm.admin.monitor_default_days', '7', 'Default days for LLM call analytics')
ON DUPLICATE KEY UPDATE description = VALUES(description);
