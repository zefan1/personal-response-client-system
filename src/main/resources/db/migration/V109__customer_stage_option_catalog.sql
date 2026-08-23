CREATE TABLE IF NOT EXISTS customer_stage_options (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_table    VARCHAR(100) NOT NULL,
  field_name      VARCHAR(200) NOT NULL,
  option_id       VARCHAR(200) NOT NULL,
  option_text     VARCHAR(200) NOT NULL,
  status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
  first_seen_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at    DATETIME     DEFAULT NULL,
  confirmed_by    VARCHAR(100) DEFAULT NULL,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_stage_option_identity (source_table, field_name, option_id),
  INDEX idx_stage_option_status (source_table, field_name, status),
  INDEX idx_stage_option_text (source_table, field_name, option_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Customer stage option snapshots from WeCom Smart Sheet';
