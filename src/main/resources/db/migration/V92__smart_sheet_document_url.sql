INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.document_url', '', 'Verified browser URL for the API-owned WeCom Smart Sheet'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'table.document_url'
);
