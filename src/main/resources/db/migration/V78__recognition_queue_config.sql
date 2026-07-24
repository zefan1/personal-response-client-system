INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('chat.recognition_temp_root', 'active', 'Temporary recognition image subdirectory below the application temporary root'),
  ('chat.recognition_temp_ttl_seconds', '600', 'Temporary recognition image lifetime in seconds, range 60-600'),
  ('chat.recognition_temp_max_total_bytes', '104857600', 'Temporary recognition image capacity in bytes, range 10485760-524288000')
ON DUPLICATE KEY UPDATE description = VALUES(description);
