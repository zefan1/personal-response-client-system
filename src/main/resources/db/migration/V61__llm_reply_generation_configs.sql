INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.reply_generation.enabled', 'false', 'Enable LLM for reply generation before Skill fallback'),
  ('llm.reply_generation.fallback_to_skill', 'true', 'Fallback to Skill when LLM reply generation fails'),
  ('llm.reply_generation.temperature', '', 'Optional LLM reply generation temperature override'),
  ('llm.reply_generation.max_tokens', '900', 'Optional LLM reply generation max tokens override'),
  ('llm.reply_generation.system_prompt',
   'You generate reply suggestions for a private-domain postpartum recovery sales assistant. Return JSON only. Schema: {"suggestions":[{"text":"ready-to-send reply","direction":"OPENING|NEXT_STEP|ANSWER|SOFT_CLOSE","reason":"short reason"}],"customer_analysis":{"intent":"","emotion":"","personality_type_suggest":"","confidence":""},"followup_suggest":{"next_contact_at":"","next_contact_direction":""}}. Rules: suggestions must contain exactly 3 items; text should be ready to send to the customer; use Simplified Chinese unless the customer used another language; be warm, concise and professional; do not invent medical diagnosis or guarantees; avoid exposing internal notes.',
   'System prompt for LLM reply generation')
ON DUPLICATE KEY UPDATE description = VALUES(description);
