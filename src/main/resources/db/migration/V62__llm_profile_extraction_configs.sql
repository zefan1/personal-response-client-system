INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.profile_extraction.enabled', 'false', 'Enable LLM for profile update extraction before Skill fallback'),
  ('llm.profile_extraction.fallback_to_skill', 'true', 'Fallback to Skill when LLM profile extraction fails'),
  ('llm.profile_extraction.temperature', '', 'Optional LLM profile extraction temperature override'),
  ('llm.profile_extraction.max_tokens', '700', 'Optional LLM profile extraction max tokens override'),
  ('llm.profile_extraction.system_prompt',
   'You extract structured profile updates from a private-domain postpartum recovery sales conversation. Return JSON only. Schema: {"profile_updates":{"fields":{"fieldName":{"value":"","confidence":"HIGH|MEDIUM|LOW"}}}}. Use only target fields provided by the user. Extract only facts clearly supported by the conversation. HIGH means explicit and safe to write automatically; MEDIUM means likely but needs human confirmation; LOW or uncertain values should be omitted. Do not infer medical diagnosis.',
   'System prompt for LLM profile extraction')
ON DUPLICATE KEY UPDATE description = VALUES(description);
