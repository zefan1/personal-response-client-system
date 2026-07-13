CREATE TABLE IF NOT EXISTS llm_environments (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  env_name       VARCHAR(50)   NOT NULL COMMENT 'environment name',
  provider       VARCHAR(50)   NOT NULL DEFAULT 'llm',
  base_url       VARCHAR(500)  NOT NULL,
  api_key        VARCHAR(500)  NOT NULL,
  api_key_last4  VARCHAR(4)    NOT NULL,
  model          VARCHAR(100)  NOT NULL,
  protocol       VARCHAR(50)   NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
  timeout_ms     INT           NOT NULL DEFAULT 10000,
  temperature    DECIMAL(4,2)  NOT NULL DEFAULT 0.20,
  max_tokens     INT           NOT NULL DEFAULT 1024,
  is_active      TINYINT       NOT NULL DEFAULT 0,
  last_test_at   DATETIME      DEFAULT NULL,
  last_test_ok   TINYINT       DEFAULT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_env_name (env_name),
  INDEX idx_provider_active (provider, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM environment configs';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('llm.api_base_url', '', 'LLM API base URL'),
  ('llm.api_key', '', 'LLM API key'),
  ('llm.model', '', 'LLM model name'),
  ('llm.protocol', 'OPENAI_COMPATIBLE', 'LLM protocol adapter'),
  ('llm.timeout_ms', '10000', 'LLM request timeout ms'),
  ('llm.temperature', '0.2', 'LLM sampling temperature'),
  ('llm.max_tokens', '1024', 'LLM max output tokens')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO llm_environments (env_name, provider, base_url, api_key, api_key_last4, model, protocol, timeout_ms, temperature, max_tokens, is_active)
SELECT
  '当前 LLM 配置',
  'llm',
  base_url.config_value,
  api_key.config_value,
  RIGHT(api_key.config_value, 4),
  COALESCE(NULLIF(model.config_value, ''), 'gpt-4.1-mini'),
  COALESCE(NULLIF(protocol.config_value, ''), 'OPENAI_COMPATIBLE'),
  CAST(COALESCE(NULLIF(timeout_ms.config_value, ''), '10000') AS UNSIGNED),
  CAST(COALESCE(NULLIF(temperature.config_value, ''), '0.2') AS DECIMAL(4,2)),
  CAST(COALESCE(NULLIF(max_tokens.config_value, ''), '1024') AS UNSIGNED),
  1
FROM system_configs base_url
JOIN system_configs api_key ON api_key.config_key = 'llm.api_key'
LEFT JOIN system_configs model ON model.config_key = 'llm.model'
LEFT JOIN system_configs protocol ON protocol.config_key = 'llm.protocol'
LEFT JOIN system_configs timeout_ms ON timeout_ms.config_key = 'llm.timeout_ms'
LEFT JOIN system_configs temperature ON temperature.config_key = 'llm.temperature'
LEFT JOIN system_configs max_tokens ON max_tokens.config_key = 'llm.max_tokens'
WHERE base_url.config_key = 'llm.api_base_url'
  AND base_url.config_value <> ''
  AND api_key.config_value <> ''
  AND NOT EXISTS (SELECT 1 FROM llm_environments)
ON DUPLICATE KEY UPDATE env_name = env_name;
