INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('desktop.clipboard_screenshot_confirm_prompt_s', '10', 'desktop clipboard screenshot confirm prompt seconds, 0 disables auto-dismiss, valid range 0 or 3-60')
ON DUPLICATE KEY UPDATE description = VALUES(description);
