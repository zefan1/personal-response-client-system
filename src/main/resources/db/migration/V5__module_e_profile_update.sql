CREATE TABLE IF NOT EXISTS profile_update_suggestions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone           VARCHAR(20)   NOT NULL COMMENT '客户手机号',
  field_name      VARCHAR(50)   NOT NULL COMMENT '建议更新的字段名（camelCase）',
  current_value   VARCHAR(500)  DEFAULT NULL COMMENT '创建建议时客户当前值',
  suggested_value VARCHAR(500)  NOT NULL COMMENT '建议值',
  confidence      VARCHAR(10)   NOT NULL COMMENT '置信度：HIGH/MEDIUM/LOW',
  status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CONFIRMED/REJECTED/CONFLICT_SKIPPED',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at     DATETIME      DEFAULT NULL COMMENT '处理时间',
  INDEX idx_phone_status (phone, status),
  INDEX idx_created (created_at),
  INDEX idx_phone_field_status (phone, field_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='档案更新建议表';

CREATE TABLE IF NOT EXISTS audit_logs (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  action          VARCHAR(50)   NOT NULL COMMENT '操作类型',
  operator        VARCHAR(50)   DEFAULT NULL COMMENT '操作人',
  target_type     VARCHAR(50)   NOT NULL COMMENT '操作对象类型',
  target_id       VARCHAR(100)  DEFAULT NULL COMMENT '操作对象 ID',
  detail          VARCHAR(1000) DEFAULT NULL COMMENT '操作摘要',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_action_time (action, created_at),
  INDEX idx_operator_time (operator, created_at),
  INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('profile.extract_fields', '["postpartumMonths","parity","deliveryMethod","breastfeeding","lochiaPeriod","bodyConcerns","diastasisRecti","urineLeakage","pubicLumbago","prevRepairExp","postpartumCheck","exerciseHabits","worries","intentLevel","personalityType","nextFollowupAt","nextFollowupDir","followupNotes"]', 'AI 提取目标字段列表'),
  ('profile.extract_timeout_ms', '8000', 'B.extractProfile 调用超时毫秒，范围 5000-12000'),
  ('profile.send_confirm_window_s', '5', '已废弃：旧版复制后等待确认窗口'),
  ('profile.suggestion_expire_days', '7', '待确认建议 N 天后自动过期，范围 3-30'),
  ('profile.suggestion_cleanup_cron', '0 0 3 * * *', '过期建议清理定时任务 cron'),
  ('profile.suggestion_max_per_customer', '20', '每客户同时最多 PENDING 建议数，范围 10-50'),
  ('profile.dedup_window_s', '5', '事件去重窗口秒数，范围 3-15'),
  ('profile.fallback_summary_chars', '500', 'AI 摘要失败时文本截取字数上限，范围 200-1000')
ON DUPLICATE KEY UPDATE description = VALUES(description);
