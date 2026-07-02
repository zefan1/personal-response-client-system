CREATE TABLE IF NOT EXISTS datasources (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(100)  NOT NULL,
  sheet_id        VARCHAR(200)  NOT NULL,
  source_table    VARCHAR(100)  NOT NULL,
  description     VARCHAR(500)  DEFAULT NULL,
  is_enabled      TINYINT       NOT NULL DEFAULT 1,
  created_by      VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_name (name),
  INDEX idx_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Datasource connection configs';

CREATE TABLE IF NOT EXISTS datasource_mapping_versions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  datasource_id   BIGINT        NOT NULL,
  version         INT           NOT NULL,
  mappings_json   TEXT          NOT NULL,
  mapping_count   INT           NOT NULL,
  change_summary  VARCHAR(500)  DEFAULT NULL,
  changed_by      VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_datasource_ver (datasource_id, version DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Datasource mapping version snapshots';

CREATE TABLE IF NOT EXISTS customer_import_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_name       VARCHAR(200)  NOT NULL,
  total_rows      INT           NOT NULL,
  created_count   INT           NOT NULL DEFAULT 0,
  updated_count   INT           NOT NULL DEFAULT 0,
  skipped_count   INT           NOT NULL DEFAULT 0,
  error_detail    TEXT          DEFAULT NULL,
  imported_by     VARCHAR(50)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_importer_time (imported_by, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CSV customer import logs';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('datasource.mapping_version_max', '50', 'Datasource mapping version max snapshots'),
  ('datasource.import_max_rows', '5000', 'CSV import max rows per upload'),
  ('datasource.manual_sync_timeout_s', '60', 'Manual datasource sync wait timeout seconds'),
  ('datasource.sync_status_refresh_s', '30', 'Datasource sync status refresh interval seconds')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
