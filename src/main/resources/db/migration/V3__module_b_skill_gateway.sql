CREATE TABLE IF NOT EXISTS skill_call_logs (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  scene           VARCHAR(50)   DEFAULT NULL COMMENT '调用场景：CHAT_RECOGNIZE/ACTIVE_REPLY/REGENERATE/PROFILE_EXTRACT/OPENING',
  lead_type       VARCHAR(20)   DEFAULT NULL COMMENT '线索类型：TUAN_GOU/XIAN_SUO/null',
  caller          VARCHAR(50)   DEFAULT NULL COMMENT '调用者（管家账号）',
  request_summary TEXT          DEFAULT NULL COMMENT '请求摘要（脱敏：客户消息前100字+phone后4位）',
  response_time   INT           DEFAULT NULL COMMENT 'Skill 响应耗时（毫秒）',
  success         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否成功：0=失败，1=成功',
  error_msg       VARCHAR(500)  DEFAULT NULL COMMENT '失败原因（如超时/熔断/格式异常）',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  INDEX idx_caller_time (caller, created_at),
  INDEX idx_success_time (success, created_at),
  INDEX idx_scene (scene),
  INDEX idx_lead_type (lead_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 调用日志表';

CREATE TABLE IF NOT EXISTS skill_scene_bindings (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  skill_id        VARCHAR(100)  NOT NULL COMMENT 'Skill 系统 Skill ID',
  skill_name      VARCHAR(100)  DEFAULT NULL COMMENT 'Skill 显示名',
  scene           VARCHAR(50)   NOT NULL COMMENT '业务场景枚举',
  lead_type       VARCHAR(20)   NOT NULL COMMENT '线索类型',
  priority        INT           NOT NULL DEFAULT 0 COMMENT '路由优先级（同场景多个 Skill 时）',
  enabled         TINYINT       NOT NULL DEFAULT 1 COMMENT '启用/禁用',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_scene_lead (scene, lead_type, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 场景绑定表';

CREATE TABLE IF NOT EXISTS personality_tags (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  tag_value       VARCHAR(50)   NOT NULL COMMENT '标签值（如 LOYALIST）',
  tag_label       VARCHAR(100)  NOT NULL COMMENT '标签中文名（如 忠诚者/怕风险型）',
  tag_description VARCHAR(500)  DEFAULT NULL COMMENT '标签说明',
  enabled         TINYINT       NOT NULL DEFAULT 1 COMMENT '启用/禁用',
  sort_order      INT           NOT NULL DEFAULT 0 COMMENT '排序权重',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_tag_value (tag_value),
  INDEX idx_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户性格标签表';

INSERT INTO personality_tags (tag_value, tag_label, tag_description, sort_order)
VALUES
  ('LOYALIST', '忠诚者（怕风险型）', '需要安全感和专业背书，对效果有顾虑', 10),
  ('PEACEMAKER', '和平者（优柔寡断型）', '难以做决定，需要温和引导', 20),
  ('PENDING', '待识别', '尚未判断性格类型', 30)
ON DUPLICATE KEY UPDATE tag_label = VALUES(tag_label), tag_description = VALUES(tag_description), enabled = 1;

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('skill.api_base_url', '', 'Skill 系统 API 基础地址，待运营B配置'),
  ('skill.api_key', '', 'Skill API Key，加密存储'),
  ('skill.phone_transfer_mode', 'LAST_FOUR', '传给 Skill 的手机号模式'),
  ('skill.phone_encryption_key', '', '加密完整手机号的 AES-256-GCM 密钥'),
  ('skill.timeout_ms', '10000', 'Skill HTTP 调用超时毫秒'),
  ('skill.circuit_breaker_window_s', '30', '熔断器滑动窗口大小秒'),
  ('skill.circuit_breaker_failure_rate', '0.5', '熔断触发失败率阈值'),
  ('skill.circuit_breaker_min_calls', '5', '熔断触发最小调用次数'),
  ('skill.circuit_breaker_open_s', '30', '熔断开启后持续时间秒'),
  ('skill.fallback_reply', '抱歉，AI 助手暂不可用，请手动回复或稍后再试。', 'Skill 不可用时的降级回复文本'),
  ('skill.tuan_skill_group_id', '', '团购客资 Skill 组 ID'),
  ('skill.xiansuo_skill_group_id', '', '线索客资 Skill 组 ID'),
  ('skill.default_skill_id', '', '未登记客户默认 Skill ID'),
  ('skill.system_prompt_template', '【输出格式要求】你必须返回一个 JSON 对象，包含 suggestions 恰好3条，每条包含 text、direction、reason；可选 customer_analysis、followup_suggest、profile_updates。场景：{{scene}}\n【企业红线】\n{{red_lines}}\n【可用客户标签】\n{{available_tags}}', '系统 Prompt 模板'),
  ('skill.red_lines', '', '企业红线内容'),
  ('skill.alert_failure_rate', '0.3', '健康告警失败率阈值'),
  ('skill.alert_failure_duration_minutes', '15', '失败率持续 N 分钟后触发告警'),
  ('profile.extract_timeout_ms', '8000', 'B.extractProfile 调用超时毫秒')
ON DUPLICATE KEY UPDATE description = VALUES(description);
