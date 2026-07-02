CREATE TABLE IF NOT EXISTS desktop_versions (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  version             VARCHAR(20)  NOT NULL COMMENT 'desktop version X.Y.Z',
  platform            VARCHAR(10)  NOT NULL COMMENT 'WINDOWS / MAC',
  status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / REVOKED',
  update_strategy     VARCHAR(20)  NOT NULL DEFAULT 'OPTIONAL' COMMENT 'FORCED / OPTIONAL / GRADUAL',
  gradual_percent     INT          DEFAULT NULL COMMENT '1-99 when update_strategy is GRADUAL',
  download_url        VARCHAR(500) DEFAULT NULL COMMENT 'installer download URL',
  file_size           BIGINT       DEFAULT NULL COMMENT 'installer size in bytes',
  changelog           TEXT         NOT NULL COMMENT 'plain text changelog',
  revoked_at          DATETIME     DEFAULT NULL COMMENT 'revoke time',
  revoke_reason       VARCHAR(500) DEFAULT NULL COMMENT 'revoke reason',
  alternative_version VARCHAR(20)  DEFAULT NULL COMMENT 'suggested published alternative version',
  published_at        DATETIME     DEFAULT NULL COMMENT 'publish time',
  created_by          VARCHAR(20)  NOT NULL COMMENT 'creator phone',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_version_platform (version, platform),
  INDEX idx_status (status),
  INDEX idx_platform_status_published (platform, status, published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='desktop release version management';

CREATE TABLE IF NOT EXISTS desktop_client_versions (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_id           VARCHAR(100) NOT NULL COMMENT 'stable desktop client id',
  version             VARCHAR(20)  NOT NULL COMMENT 'installed version',
  platform            VARCHAR(10)  NOT NULL COMMENT 'WINDOWS / MAC',
  os_version          VARCHAR(100) DEFAULT NULL COMMENT 'operating system version',
  last_reported_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_client_id (client_id),
  INDEX idx_version (version),
  INDEX idx_last_reported (last_reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='desktop client version report';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('version.max_file_size_mb', '500', 'desktop installer max upload size in MB, range 100-1000'),
  ('version.cos_upload_timeout_s', '120', 'desktop installer COS upload timeout seconds, range 60-600'),
  ('version.report_interval_hours', '24', 'desktop client version report interval hours, range 6-72')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
