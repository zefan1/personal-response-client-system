ALTER TABLE monthly_assignment_tables
  ADD COLUMN unique_field_title VARCHAR(200) NOT NULL DEFAULT '' AFTER view_id;

UPDATE monthly_assignment_tables t
JOIN system_configs c ON c.config_key = 'table.assignment.unique_field_title'
SET t.unique_field_title = c.config_value
WHERE t.status = 'ACTIVE'
  AND t.unique_field_title = '';
