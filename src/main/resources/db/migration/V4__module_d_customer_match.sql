INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('match.tag_removal_rules', '["L1-","L2-","A-","VIP-","V-"]', '昵称标记去除规则，占位默认值，JSON Array'),
  ('match.max_candidates', '5', '模糊匹配最多返回候选数，范围 3-10'),
  ('match.fuzzy_search_timeout_ms', '2000', '匹配查询超时毫秒，范围 1000-5000'),
  ('match.confidence_ratio_threshold', '0.5', 'HIGH 置信度覆盖比阈值，范围 0.3-0.8'),
  ('match.confidence_min_length', '2', '置信度判定最小 cleanedNickname 长度，范围 2-4')
ON DUPLICATE KEY UPDATE description = VALUES(description);
