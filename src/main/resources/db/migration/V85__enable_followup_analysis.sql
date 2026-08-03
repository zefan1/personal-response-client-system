INSERT INTO system_configs (config_key, config_value, description)
VALUES (
  'llm.followup_analysis.enabled',
  'true',
  'Enable structured LLM follow-up analysis after confirmed sending'
)
ON DUPLICATE KEY UPDATE description = VALUES(description);
