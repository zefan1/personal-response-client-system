INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('wecom.connection_mode', 'RELAY', 'WeCom official API connection mode: RELAY or DIRECT'),
  ('wecom.relay_base_url', '', 'WeCom relay base URL; blank retains the deployment WECOM_API_BASE_URL')
ON DUPLICATE KEY UPDATE description = VALUES(description);
