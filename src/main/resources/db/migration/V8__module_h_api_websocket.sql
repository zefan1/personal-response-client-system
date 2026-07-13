CREATE TABLE IF NOT EXISTS accounts (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(50)  NOT NULL,
  password_hash  VARCHAR(100) NOT NULL,
  display_name   VARCHAR(100) NOT NULL,
  role           VARCHAR(20)  NOT NULL COMMENT 'ADMIN / LEADER / KEEPER',
  leader_id      BIGINT       DEFAULT NULL,
  is_enabled     TINYINT      NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_username (username),
  INDEX idx_role_enabled (role, is_enabled),
  INDEX idx_leader (leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='login accounts';

CREATE TABLE IF NOT EXISTS ws_offline_queue (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  message_id      BIGINT        NOT NULL,
  username        VARCHAR(50)   NOT NULL,
  message_type    VARCHAR(50)   NOT NULL,
  payload         TEXT          NOT NULL,
  delivered       TINYINT       NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at    DATETIME      DEFAULT NULL,
  UNIQUE INDEX idx_message_id (message_id),
  INDEX idx_user_delivered (username, delivered, message_id),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WS offline message queue';

CREATE TABLE IF NOT EXISTS system_alerts (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  alert_type      VARCHAR(50)   NOT NULL,
  level           VARCHAR(20)   NOT NULL,
  status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / RESOLVED',
  message         VARCHAR(500)  NOT NULL,
  source_module   VARCHAR(50)   DEFAULT NULL,
  occurred_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at     DATETIME      DEFAULT NULL,
  detail          TEXT          DEFAULT NULL,
  INDEX idx_type_status (alert_type, status),
  INDEX idx_status_occurred (status, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='system alerts';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('system.jwt_secret', 'change-me-in-production-private-domain-assistant', 'JWT HS256 secret; override in production'),
  ('system.jwt_expire_hours', '24', 'JWT token ttl hours, range 1-168'),
  ('system.jwt_refresh_days', '7', 'refresh token ttl days, range 1-30'),
  ('system.ws_heartbeat_s', '30', 'WS heartbeat interval seconds, range 15-60'),
  ('system.ws_timeout_s', '60', 'WS heartbeat timeout seconds, range 30-120'),
  ('system.ws_replay_queue_size', '100', 'WS offline replay queue size per user, range 50-500'),
  ('system.request_total_timeout_ms', '15000', 'request total timeout ms, range 10000-20000'),
  ('system.audit_log_retention_days', '90', 'audit log retention days, range 30-365'),
  ('system.login_fail_limit', '10', 'login fail limit per IP, range 3-20'),
  ('system.login_lock_minutes', '15', 'login lock minutes, range 5-60'),
  ('system.request_context_ttl_s', '300', 'request context ttl seconds, range 60-600'),
  ('system.ws_offline_retention_days', '7', 'WS offline queue retention days, range 1-30'),
  ('system.alert_retention_days', '30', 'resolved alert retention days, range 7-90'),
  ('system.config_change_channel', 'config:change', 'Redis config change pub/sub channel'),
  ('system.ws_push_channel', 'ws:push', 'Redis WS push pub/sub channel')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO accounts (username, password_hash, display_name, role, leader_id, is_enabled)
VALUES ('admin', '{plain}admin123', 'System Admin', 'ADMIN', NULL, 1)
ON DUPLICATE KEY UPDATE role = VALUES(role), is_enabled = VALUES(is_enabled);
