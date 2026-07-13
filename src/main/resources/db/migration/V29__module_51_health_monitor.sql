INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('health.refresh_interval_s', '30', 'health dashboard refresh interval seconds, range 0 or 15-120'),
  ('health.alert_history_days', '7', 'health dashboard alert history days, range 1-30'),
  ('health.alert_history_max', '100', 'health dashboard alert history max rows, range 50-200')
ON DUPLICATE KEY UPDATE description = VALUES(description);
