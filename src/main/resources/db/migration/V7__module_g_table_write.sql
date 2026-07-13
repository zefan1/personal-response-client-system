CREATE TABLE IF NOT EXISTS pending_table_writes (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone          VARCHAR(20)  NOT NULL,
  action_type    VARCHAR(20)  NOT NULL COMMENT 'INSERT / UPDATE',
  payload        TEXT         NOT NULL,
  retry_count    INT          NOT NULL DEFAULT 0,
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RESOLVED / FAILED',
  next_retry_at  DATETIME     NOT NULL,
  error_msg      VARCHAR(500) DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status_retry (status, next_retry_at),
  INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='table write retry queue';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('table.write_timeout_ms', '10000', 'wecom table write timeout ms, range 5000-20000'),
  ('table.retry_max_count', '5', 'max retry count for failed table writes, range 3-10'),
  ('table.retry_interval_s', '60', 'retry interval seconds for pending table writes, range 30-300'),
  ('table.alert_failure_hours', '1', 'alert when FAILED records stay unresolved for N hours, range 1-24'),
  ('table.alert_notify_target', 'ADMIN', 'alert notify target: ADMIN / LEADER / BOTH'),
  ('table.queue_warn_threshold', '100', 'warn when pending queue exceeds this threshold, range 50-500'),
  ('table.queue_alert_threshold', '1000', 'alert when pending queue exceeds this threshold, range 500-5000')
ON DUPLICATE KEY UPDATE description = VALUES(description);
