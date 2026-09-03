-- Production deployments must configure Smart Sheet targets explicitly.
/*
INSERT INTO system_configs (config_key, config_value, description)
SELECT 'table.assignment_document_url', 'https://doc.weixin.qq.com/smartsheet/s3_APgA7xRqAB0CNrQ1UhsSTQXykhFvt_a?scode=AH8A3wd1ABArdRWh2eAPgA7xRqAB0', 'Verified browser URL for the fixed assignment Smart Sheet'
WHERE NOT EXISTS (
  SELECT 1 FROM system_configs WHERE config_key = 'table.assignment_document_url'
);
*/

-- The arrival Smart Sheet URL is configured by the administrator after deployment.
