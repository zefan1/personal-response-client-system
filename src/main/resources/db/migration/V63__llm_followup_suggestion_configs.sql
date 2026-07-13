INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.followup_suggestion.enabled', 'false', 'Enable LLM followup suggestion when reply flow has no followup_suggest'),
  ('llm.followup_suggestion.temperature', '', 'Optional LLM followup suggestion temperature override'),
  ('llm.followup_suggestion.max_tokens', '500', 'Optional LLM followup suggestion max tokens override'),
  ('llm.followup_suggestion.system_prompt',
   'You suggest the next follow-up action for a private-domain postpartum recovery sales conversation. Return JSON only. Schema: {"followup_suggest":{"next_contact_at":"YYYY-MM-DDTHH:mm:ss","next_contact_direction":"short action direction"}}. Use Asia/Shanghai business context. Suggest one practical next contact time and one concise direction. Do not invent medical diagnosis or guarantees.',
   'System prompt for LLM followup suggestion')
ON DUPLICATE KEY UPDATE description = VALUES(description);
