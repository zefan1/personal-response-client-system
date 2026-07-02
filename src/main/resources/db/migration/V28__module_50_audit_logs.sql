ALTER TABLE audit_logs
  MODIFY COLUMN action VARCHAR(50) NOT NULL COMMENT 'audit action enum',
  MODIFY COLUMN operator VARCHAR(50) DEFAULT NULL COMMENT 'operator phone or SYSTEM',
  MODIFY COLUMN target_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'target type enum',
  MODIFY COLUMN target_id VARCHAR(100) DEFAULT NULL COMMENT 'target id',
  MODIFY COLUMN detail TEXT DEFAULT NULL COMMENT 'JSON detail or plain text';

CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_operator ON audit_logs (operator);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_action_created ON audit_logs (action, created_at);

CREATE TABLE IF NOT EXISTS audit_log_exports (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  export_id       VARCHAR(50)   NOT NULL COMMENT 'export business id',
  status          VARCHAR(20)   NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING / COMPLETED / FAILED',
  filters_json    TEXT          NOT NULL COMMENT 'export filters',
  total_count     BIGINT        NOT NULL DEFAULT 0,
  csv_content     LONGTEXT      DEFAULT NULL COMMENT 'UTF-8 BOM CSV content',
  download_url    VARCHAR(500)  DEFAULT NULL COMMENT 'download URL',
  message         VARCHAR(500)  DEFAULT NULL COMMENT 'status message',
  expire_at       DATETIME      NOT NULL COMMENT 'download expiration time',
  created_by      VARCHAR(20)   NOT NULL COMMENT 'admin phone',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at    DATETIME      DEFAULT NULL,
  UNIQUE KEY uk_export_id (export_id),
  INDEX idx_status_created (status, created_at),
  INDEX idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='audit log CSV exports';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('system.audit_log_cleanup_batch_size', '5000', 'audit log cleanup batch size, range 1000-10000'),
  ('audit.export_max_rows', '10000', 'audit CSV export max rows, range 1000-50000'),
  ('audit.export_cos_retention_hours', '168', 'audit CSV export retention hours, range 24-720'),
  ('audit.export_timeout_seconds', '120', 'audit CSV export timeout seconds, range 60-600'),
  ('audit.list_page_size_default', '20', 'audit list default page size, range 10-100'),
  ('audit.list_max_page_size', '100', 'audit list max page size, range 50-500')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
