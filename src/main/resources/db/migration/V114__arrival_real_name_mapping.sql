-- The arrival sheet's 姓名 column is the customer's real name, never the WeChat nickname.
UPDATE datasource_field_mappings
SET target_field = 'customerName', updated_at = NOW()
WHERE source_table LIKE 'ARRIVAL:%'
  AND source_field = '姓名'
  AND target_field = 'nickname';
