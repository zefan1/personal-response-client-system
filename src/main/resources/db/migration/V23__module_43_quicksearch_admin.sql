ALTER TABLE quick_search_items
  ADD COLUMN created_by VARCHAR(50) NULL AFTER is_enabled,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER created_by;

CREATE TABLE IF NOT EXISTS cos_cleanup_queue (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  image_url     VARCHAR(500) NOT NULL,
  deleted_at    DATETIME     NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='COS cleanup queue';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('quicksearch.admin.page_size', '20', 'Quick search admin list page size'),
  ('quicksearch.admin.image_max_size_mb', '10', 'Quick search admin image max size MB'),
  ('quicksearch.admin.cos_retention_days', '30', 'Quick search old COS object retention days')
ON DUPLICATE KEY UPDATE description = VALUES(description);
