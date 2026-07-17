UPDATE unmatched_legacy_tag_values u
JOIN system_tag_suggestions s ON s.unmatched_legacy_value_id = u.id
JOIN followup_rules r ON r.id = s.rule_id
SET u.status = 'IGNORED',
    u.resolution_note = COALESCE(NULLIF(u.resolution_note, ''),
      'Legacy free-text tag suggestion archived by Step 9F; not a formal customer tag'),
    u.resolved_by = COALESCE(NULLIF(u.resolved_by, ''), 'SYSTEM_MIGRATION_9F'),
    u.resolved_at = COALESCE(u.resolved_at, NOW())
WHERE u.status = 'PENDING'
  AND s.status = 'PENDING'
  AND s.validation_status = 'UNMATCHED_LEGACY'
  AND u.source_type = 'SYSTEM_TAG_SUGGESTION'
  AND r.is_builtin = 1
  AND r.id IN (4, 5)
  AND r.action_type = 'TAG_CHANGE';

UPDATE system_tag_suggestions s
JOIN followup_rules r ON r.id = s.rule_id
SET s.status = 'IGNORED',
    s.ignored_at = COALESCE(s.ignored_at, NOW())
WHERE s.status = 'PENDING'
  AND s.validation_status = 'UNMATCHED_LEGACY'
  AND s.unmatched_legacy_value_id IS NOT NULL
  AND r.is_builtin = 1
  AND r.id IN (4, 5)
  AND r.action_type = 'TAG_CHANGE';

UPDATE followup_rules
SET action_type = CASE id
  WHEN 4 THEN 'ALERT'
  WHEN 5 THEN 'NOTIFY_LEADER'
END
WHERE is_builtin = 1
  AND action_type = 'TAG_CHANGE'
  AND id IN (4, 5)
  AND name IN ('沉睡风险', '可能流失');
