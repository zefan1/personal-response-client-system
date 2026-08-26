CREATE TABLE IF NOT EXISTS intent_project_mapping_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  option_id VARCHAR(100) NOT NULL,
  option_text VARCHAR(200) NOT NULL,
  keywords_json TEXT NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  source_field VARCHAR(200) NOT NULL DEFAULT '意向项目',
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_intent_project_option (option_id),
  KEY idx_intent_project_rule_status (status, priority, option_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='企业微信意向项目选项与已购项目关键词映射规则';

UPDATE system_configs
SET config_value = JSON_ARRAY_APPEND(config_value, '$', 'intendedProject')
WHERE config_key = 'profile.extract_fields'
  AND JSON_VALID(config_value)
  AND JSON_TYPE(config_value) = 'ARRAY'
  AND JSON_SEARCH(config_value, 'one', 'intendedProject') IS NULL;

UPDATE system_configs
SET config_value = CONCAT(
  config_value,
  '\n\n意向项目业务规则：项目选项以企业微信客户主表当前选项为准。已有购买项目时优先参考已购项目关键词；没有购买项目时结合客户原话和同事明确确认或推荐的语境判断。恶露未净不否定产康意向，恶露状态只影响当前是否适合安排服务。意向项目有充分新证据时允许替换当前值；无法判断时不要猜测。')
WHERE config_key = 'llm.profile_extraction.system_prompt'
  AND config_value NOT LIKE '%意向项目业务规则%';
