CREATE TABLE IF NOT EXISTS wecom_smartsheet_callback_inbox (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key       VARCHAR(128) NOT NULL,
  table_role      VARCHAR(20) NOT NULL,
  source_table    VARCHAR(200) NOT NULL,
  document_id     VARCHAR(200) NOT NULL,
  sheet_id        VARCHAR(200) NOT NULL,
  change_type     VARCHAR(40) NOT NULL,
  record_ids_json TEXT NOT NULL,
  operator_name   VARCHAR(200) DEFAULT NULL,
  status          VARCHAR(20) NOT NULL,
  attempts        INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error      TEXT DEFAULT NULL,
  resolved_at     DATETIME DEFAULT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wecom_callback_event (event_key),
  KEY idx_wecom_callback_due (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Verified WeCom Smart Sheet row-change callback inbox';
