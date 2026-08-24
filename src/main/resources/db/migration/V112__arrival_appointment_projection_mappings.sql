-- Keep the appointment facts separate from the manual arrival fields. The two
-- columns are created in the managed arrival Smart Sheet, then projected by the
-- existing mapping resolver just like the other arrival columns.
INSERT INTO datasource_field_mappings (source_table, source_field, target_field, transform_rule, is_enabled)
SELECT d.source_table, '首次预约时间', 'appointmentDateTime', NULL, 1
FROM datasources d
WHERE d.source_table LIKE 'ARRIVAL:%'
  AND NOT EXISTS (
    SELECT 1 FROM datasource_field_mappings m
    WHERE m.source_table = d.source_table AND m.target_field = 'appointmentDateTime'
  );

INSERT INTO datasource_field_mappings (source_table, source_field, target_field, transform_rule, is_enabled)
SELECT d.source_table, '预约项目', 'appointmentItem', NULL, 1
FROM datasources d
WHERE d.source_table LIKE 'ARRIVAL:%'
  AND NOT EXISTS (
    SELECT 1 FROM datasource_field_mappings m
    WHERE m.source_table = d.source_table AND m.target_field = 'appointmentItem'
  );
