INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.primary.document_id', 'dcSfoBdexcMBqmsSfkLxzmncb1N4_PPGdtNZ7svyQ951rdcBlrVY-4ahjSIHUkK1HR9SljiUfp0AdUzLkHkr7oWw', 'Admin-managed customer master document ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.primary.document_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.primary.sheet_id', 'th1zyU', 'Admin-managed customer master child sheet ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.primary.sheet_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.primary.view_id', 'vukaF8', 'Admin-managed customer master view ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.primary.view_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.primary.source_table', 'th1zyU', 'Admin-managed customer master datasource identifier'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.primary.source_table');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.primary.unique_field_title', '联系方式', 'Admin-managed customer master unique field'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.primary.unique_field_title');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment.document_id', 'dcTY2wATN_bnFXnGZhTI4453iqe2TWlCLhzYn6wpXM804n0OS8k8SyAuG108KTxeK99iiFbdMPu1S8B5JhVvJOUA', 'Admin-managed assignment document ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.assignment.document_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment.sheet_id', 'q979lj', 'Admin-managed assignment child sheet ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.assignment.sheet_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment.view_id', 'vukaF8', 'Admin-managed assignment view ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.assignment.view_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment.unique_field_title', '联系方式', 'Admin-managed assignment unique field'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.assignment.unique_field_title');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.arrival.document_id', 'dcVfYJ2JDA5P9ifFNwLX37m2_fKCExAIQPaIw-kHShu9OEm4YUyX3wytLn8SG3YxX7coBc1ZlhbOpovCvl5U63TA', 'Admin-managed arrival document ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.arrival.document_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.arrival.sheet_id', 'q979lj', 'Admin-managed arrival child sheet ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.arrival.sheet_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.arrival.view_id', 'vukaF8', 'Admin-managed arrival view ID'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.arrival.view_id');

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.arrival.unique_field_title', '手机号码', 'Admin-managed arrival unique field'
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'table.arrival.unique_field_title');
