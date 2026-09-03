-- Keep the administrator-managed URLs for the two auxiliary Smart Sheets
-- available on existing installations. Do not overwrite a configured value.
INSERT INTO system_configs (config_key, config_value, description)
  SELECT 'table.assignment_document_url', '', 'Admin-managed assignment Smart Sheet browser URL'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.assignment_document_url');

INSERT INTO system_configs (config_key, config_value, description)
  SELECT 'table.arrival_document_url', '', 'Admin-managed arrival Smart Sheet browser URL'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.arrival_document_url');
