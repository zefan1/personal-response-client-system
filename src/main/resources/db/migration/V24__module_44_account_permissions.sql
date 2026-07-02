ALTER TABLE accounts
  ADD COLUMN IF NOT EXISTS phone VARCHAR(20) NULL COMMENT 'phone login credential' AFTER id,
  ADD COLUMN IF NOT EXISTS last_login_at DATETIME DEFAULT NULL COMMENT 'last login time' AFTER is_enabled;

UPDATE accounts SET phone = username WHERE phone IS NULL OR phone = '';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('system.jwt_access_token_ttl_s', '7200', 'JWT access token ttl seconds, range 300-86400'),
  ('system.jwt_refresh_token_ttl_s', '604800', 'JWT refresh token ttl seconds, range 3600-2592000'),
  ('system.login_fail_window_s', '300', 'login fail rate limit window seconds, range 60-3600'),
  ('system.captcha_enabled', 'false', 'login captcha enabled'),
  ('system.captcha_provider', '', 'login captcha provider'),
  ('system.captcha_app_id', '', 'login captcha app id'),
  ('system.captcha_secret', '', 'login captcha secret')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
