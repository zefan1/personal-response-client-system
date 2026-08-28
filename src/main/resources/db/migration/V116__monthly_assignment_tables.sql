CREATE TABLE IF NOT EXISTS monthly_assignment_tables (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  table_name      VARCHAR(200) NOT NULL,
  month_key       VARCHAR(7)   NOT NULL,
  document_id     VARCHAR(200) NOT NULL,
  sheet_id        VARCHAR(200) NOT NULL,
  view_id         VARCHAR(200) NOT NULL,
  document_url    VARCHAR(1000) NOT NULL,
  status          VARCHAR(20)  NOT NULL,
  error_message   VARCHAR(500) DEFAULT NULL,
  created_by      VARCHAR(100) DEFAULT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  activated_at    DATETIME DEFAULT NULL,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_monthly_assignment_name (table_name),
  INDEX idx_monthly_assignment_month (month_key, created_at DESC),
  INDEX idx_monthly_assignment_status (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Monthly assignment Smart Sheet history';
