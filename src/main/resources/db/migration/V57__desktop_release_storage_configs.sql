INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('version.storage.root', '', 'desktop installer package local storage root; supplied by deployment environment'),
  ('version.storage.public_base_url', '/downloads/desktop-releases', 'desktop installer package public download base URL')
ON DUPLICATE KEY UPDATE description = VALUES(description);
