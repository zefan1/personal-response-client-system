INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment_document_url', 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a?scode=AH8A3wd1ABArdRWh2eAPgA7xRqAB0', 'Verified browser URL for the fixed assignment Smart Sheet'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'table.assignment_document_url'
);

INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.arrival_document_url', 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNRDGrbZAMSpK7ytM5_a?scode=AH8A3wd1ABArRb1alLAPgA7xRqAB0', 'Verified browser URL for the fixed arrival Smart Sheet'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'table.arrival_document_url'
);
