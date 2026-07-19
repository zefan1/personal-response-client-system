ALTER TABLE skill_environments
  ADD COLUMN protocol VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'Skill transport protocol';

INSERT INTO system_configs (config_key, config_value, description)
VALUES ('skill.protocol', 'OPENAI_COMPATIBLE', 'Skill transport protocol: OPENAI_COMPATIBLE or MCP_STREAMABLE_HTTP')
ON DUPLICATE KEY UPDATE description = VALUES(description);
