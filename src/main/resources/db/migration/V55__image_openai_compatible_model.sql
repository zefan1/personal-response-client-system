INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('image.model', 'qwen3-vl-plus', 'OpenAI-compatible image recognition model name')
ON DUPLICATE KEY UPDATE description = VALUES(description);
