INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.summary.enabled', 'false', 'Enable LLM conversation summary when send confirmation has no summary'),
  ('llm.summary.temperature', '', 'Optional LLM summary temperature override'),
  ('llm.summary.max_tokens', '500', 'Optional LLM summary max tokens override'),
  ('llm.summary.system_prompt',
   'You summarize a private-domain postpartum recovery sales conversation for CRM follow-up notes. Return JSON only. Schema: {"summary":"one concise Chinese follow-up note, within 120 Chinese characters"}. Include customer intent, key concern, and agreed next step if present. Do not include full phone numbers or unsupported medical diagnosis.',
   'System prompt for LLM conversation summary')
ON DUPLICATE KEY UPDATE description = VALUES(description);
