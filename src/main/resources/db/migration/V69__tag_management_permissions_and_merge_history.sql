CREATE TABLE account_permissions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id       BIGINT NOT NULL,
  permission_code  VARCHAR(64) NOT NULL,
  is_enabled       TINYINT NOT NULL DEFAULT 1,
  granted_by       VARCHAR(100) DEFAULT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_account_permission (account_id, permission_code),
  KEY idx_account_permissions_code (permission_code, is_enabled),
  CONSTRAINT fk_account_permissions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT chk_account_permissions_enabled CHECK (is_enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='fine grained account permissions';

INSERT INTO account_permissions (account_id, permission_code, is_enabled, granted_by)
SELECT id, 'TAG_MANAGEMENT', 1, 'flyway_v69'
FROM accounts
WHERE role = 'ADMIN'
ON DUPLICATE KEY UPDATE is_enabled = 1, updated_at = NOW();

CREATE TABLE tag_merge_operations (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type         VARCHAR(16) NOT NULL,
  source_id           BIGINT NOT NULL,
  target_id           BIGINT NOT NULL,
  source_code         VARCHAR(100) NOT NULL,
  target_code         VARCHAR(100) NOT NULL,
  affected_customers  BIGINT NOT NULL DEFAULT 0,
  affected_rules      BIGINT NOT NULL DEFAULT 0,
  affected_history    BIGINT NOT NULL DEFAULT 0,
  detail_json         TEXT NOT NULL,
  operated_by         VARCHAR(100) NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tag_merge_source (entity_type, source_id, created_at),
  KEY idx_tag_merge_target (entity_type, target_id, created_at),
  CONSTRAINT chk_tag_merge_entity_type CHECK (entity_type IN ('CATEGORY', 'VALUE')),
  CONSTRAINT chk_tag_merge_detail_json CHECK (JSON_VALID(detail_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='immutable tag merge operation history';

ALTER TABLE tag_values
  DROP FOREIGN KEY fk_tag_values_merged_into;

ALTER TABLE tag_values
  ADD CONSTRAINT fk_tag_values_merged_into FOREIGN KEY (merged_into_id) REFERENCES tag_values (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
