ALTER TABLE skill_scene_bindings
  ADD COLUMN IF NOT EXISTS last_tested_at DATETIME NULL AFTER enabled;

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('skill.admin.monitor_refresh_interval_s', '30', '运营A Skill 调用监控自动刷新间隔秒数'),
  ('skill.admin.monitor_default_days', '7', '运营A Skill 调用监控默认统计天数'),
  ('skill.admin.test_timeout_ms', '10000', '运营A Skill 在线测试请求超时毫秒'),
  ('skill.admin.test_message_max_chars', '2000', '运营A Skill 在线测试消息最大字符数')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
