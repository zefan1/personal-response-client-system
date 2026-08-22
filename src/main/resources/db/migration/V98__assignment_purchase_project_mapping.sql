-- The assignment sheet stores the purchased product as free-form text.
-- Keep that fact in customers.purchased_project instead of sending it to the
-- customer-master single-select field intended_project.
UPDATE datasource_field_mappings
SET target_field = 'purchasedProject', updated_at = CURRENT_TIMESTAMP
WHERE source_table LIKE 'ASSIGNMENT:%'
  AND source_field = '购买项目'
  AND target_field = 'intendedProject';
