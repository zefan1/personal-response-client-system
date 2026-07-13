INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.abnormal_detection.enabled', 'false', 'Enable LLM abnormal alert detection after send confirmation'),
  ('llm.abnormal_detection.temperature', '', 'Optional LLM abnormal detection temperature override'),
  ('llm.abnormal_detection.max_tokens', '500', 'Optional LLM abnormal detection max tokens override'),
  ('llm.abnormal_detection.system_prompt',
   'You detect customer complaint or churn risk in a private-domain postpartum recovery sales conversation. Return JSON only. Schema: {"abnormal_alert":{"triggered":true|false,"alert_type":"CUSTOMER_COMPLAINT|CHURN_RISK","level":"ERROR|WARN|INFO","message":"short actionable alert"}}. Trigger only when there is clear complaint, refund/escalation threat, strong dissatisfaction, loss/churn signal, or explicit refusal. Do not trigger for ordinary questions or mild hesitation.',
   'System prompt for LLM abnormal detection')
ON DUPLICATE KEY UPDATE description = VALUES(description);
