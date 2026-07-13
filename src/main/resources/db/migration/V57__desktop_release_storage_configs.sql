INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('version.storage.root', 'uploads/desktop-releases', 'desktop installer package local storage root'),
  ('version.storage.public_base_url', '/downloads/desktop-releases', 'desktop installer package public download base URL')
ON DUPLICATE KEY UPDATE description = VALUES(description);
