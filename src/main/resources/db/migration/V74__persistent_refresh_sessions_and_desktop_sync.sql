CREATE TABLE IF NOT EXISTS auth_refresh_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_used_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
  KEY idx_auth_refresh_username (username, expires_at, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面与后台多端登录续期会话';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('desktop.workbench_refresh_interval_s', '60', '工作台自动刷新间隔秒，范围30-300')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('system.jwt_refresh_token_ttl_s', '2592000', 'refresh token ttl seconds, range 3600-2592000')
ON DUPLICATE KEY UPDATE
  config_value = CASE WHEN config_value = '604800' THEN VALUES(config_value) ELSE config_value END,
  description = VALUES(description);

