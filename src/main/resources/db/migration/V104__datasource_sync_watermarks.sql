CREATE TABLE IF NOT EXISTS datasource_sync_watermarks (
  source_table              VARCHAR(200) NOT NULL PRIMARY KEY,
  last_successful_sync_at   DATETIME NOT NULL,
  updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Durable successful incremental-sync watermarks per datasource';
