INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('table.api_base_url', '', 'WeCom smart table gateway base URL'),
  ('table.api_key', '', 'WeCom smart table gateway API key')
ON DUPLICATE KEY UPDATE description = VALUES(description);
