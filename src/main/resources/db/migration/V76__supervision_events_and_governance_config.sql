CREATE TABLE IF NOT EXISTS supervision_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id CHAR(36) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  operator_username VARCHAR(64) NULL,
  customer_phone VARCHAR(32) NULL,
  channel_code VARCHAR(64) NULL,
  channel_account VARCHAR(255) NULL,
  lead_source VARCHAR(128) NULL,
  assigned_keeper VARCHAR(64) NULL,
  scene VARCHAR(64) NULL,
  task_id CHAR(36) NULL,
  reply_session_id VARCHAR(80) NULL,
  reply_source VARCHAR(64) NULL,
  dedupe_key VARCHAR(255) NULL,
  generated_reply_snapshot TEXT NULL,
  copied_reply_snapshot TEXT NULL,
  metadata_json TEXT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supervision_event_id (event_id),
  UNIQUE KEY uk_supervision_event_dedupe (dedupe_key),
  KEY idx_supervision_event_operator_time (operator_username, occurred_at),
  KEY idx_supervision_event_customer_time (customer_phone, occurred_at),
  KEY idx_supervision_event_channel_time (channel_code, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Immutable supervision events without image or OCR payloads';

CREATE TABLE IF NOT EXISTS supervision_monthly_metric_snapshots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  metric_month DATE NOT NULL,
  dimension_type VARCHAR(64) NOT NULL,
  dimension_value VARCHAR(255) NOT NULL,
  metric_key VARCHAR(100) NOT NULL,
  numerator BIGINT NOT NULL,
  denominator BIGINT NOT NULL,
  ratio DECIMAL(12,8) NOT NULL,
  generated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supervision_monthly_metric (
    metric_month, dimension_type, dimension_value, metric_key
  ),
  KEY idx_supervision_monthly_metric_month (metric_month, metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly supervision metric snapshots';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('supervision.record_retention_days', '180', 'Supervision record retention days, range 30-730'),
  ('supervision.technical_log_retention_days', '30', 'Supervision technical log retention days, range 7-180'),
  ('supervision.processing_sla_minutes', '1440', 'Supervision processing SLA minutes, range 15-10080'),
  ('supervision.conversion_target_stages_json', '[]', 'Supervision conversion target stages as a JSON array'),
  ('chat.expired_reply_task_retention_days', '3', 'Expired reply task retention days, range 1-14'),
  ('chat.unfinished_task_cap', '20', 'Maximum unfinished reply tasks, range 10-50'),
  ('chat.recent_task_display_cap', '30', 'Recent reply task display cap, range 20-100'),
  ('chat.recognition_concurrency', '4', 'Concurrent reply recognition limit, range 1-16')
ON DUPLICATE KEY UPDATE description = VALUES(description);
