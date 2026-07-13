INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('skill.subscription_expire_at', '', 'Skill订阅有效期，ISO日期或日期时间，空值表示未配置')
ON DUPLICATE KEY UPDATE description = VALUES(description);
