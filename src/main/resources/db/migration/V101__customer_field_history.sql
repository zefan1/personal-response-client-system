CREATE TABLE IF NOT EXISTS customer_field_history (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id     BIGINT NOT NULL,
  field_name      VARCHAR(100) NOT NULL,
  field_value     TEXT DEFAULT NULL,
  source          VARCHAR(200) NOT NULL,
  source_field    VARCHAR(200) NOT NULL,
  operator        VARCHAR(100) NOT NULL,
  changed_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_customer_field_changed (customer_id, field_name, changed_at, id),
  INDEX idx_customer_changed (customer_id, changed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='客户唯一事实数据库字段历史';
