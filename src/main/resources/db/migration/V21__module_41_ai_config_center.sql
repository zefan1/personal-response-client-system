CREATE TABLE IF NOT EXISTS skill_environments (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  env_name       VARCHAR(50)   NOT NULL COMMENT 'environment name',
  provider       VARCHAR(50)   NOT NULL DEFAULT 'skill',
  base_url       VARCHAR(500)  NOT NULL,
  api_key        VARCHAR(500)  NOT NULL,
  api_key_last4  VARCHAR(4)    NOT NULL,
  is_active      TINYINT       NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_env_name (env_name),
  INDEX idx_provider_active (provider, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill environment configs';

CREATE TABLE IF NOT EXISTS image_environments (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  env_name       VARCHAR(50)   NOT NULL COMMENT 'environment name',
  provider       VARCHAR(50)   NOT NULL DEFAULT 'image',
  base_url       VARCHAR(500)  NOT NULL,
  api_key        VARCHAR(500)  NOT NULL,
  api_key_last4  VARCHAR(4)    NOT NULL,
  is_active      TINYINT       NOT NULL DEFAULT 0,
  last_test_at   DATETIME      DEFAULT NULL,
  last_test_ok   TINYINT       DEFAULT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_env_name (env_name),
  INDEX idx_provider_active (provider, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Image environment configs';

CREATE TABLE IF NOT EXISTS skill_prompt_versions (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key     VARCHAR(100)  NOT NULL,
  version        INT           NOT NULL,
  content        MEDIUMTEXT    NOT NULL,
  is_stable      TINYINT       NOT NULL DEFAULT 0,
  operator       VARCHAR(50)   NOT NULL,
  change_note    VARCHAR(200)  DEFAULT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_config_key_version (config_key, version DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt version history';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('skill.system_prompt_format', '你是私域客户服务助手。请根据客户信息生成 3 条不同方向的回复建议，并严格返回 JSON。客户类型：{{leadType}}，客户阶段：{{customerStage}}。', 'Skill system prompt format section'),
  ('skill.system_prompt_red_lines', '[]', 'Skill system prompt red-line list as JSON array'),
  ('skill.regenerate_max_count', '0', 'Regenerate max count, 0 means unlimited'),
  ('skill.prompt_version_max', '50', 'Prompt version retention max count'),
  ('image.api_base_url', '', 'Image recognition API base URL'),
  ('image.api_key', '', 'Image recognition API key'),
  ('image.timeout_ms', '5000', 'Image recognition timeout ms'),
  ('image.max_size_bytes', '5242880', 'Image max upload size bytes'),
  ('image.max_dimension_px', '1920', 'Image max dimension pixels'),
  ('image.compress_quality', '85', 'Image compression quality'),
  ('image.recognition_prompt', '请识别聊天截图中的昵称、手机号、消息列表和时间，并严格返回 JSON。', 'Image recognition prompt'),
  ('image.consecutive_failures_alert', '3', 'Image consecutive failures alert threshold'),
  ('match.tag_removal_rules', '[]', 'Customer tag removal prefixes as JSON array')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
