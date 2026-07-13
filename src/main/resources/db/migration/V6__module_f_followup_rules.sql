CREATE TABLE IF NOT EXISTS followup_rules (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(100) NOT NULL COMMENT '规则名称',
  condition_json  TEXT         NOT NULL COMMENT '条件组合 JSON',
  action_type     VARCHAR(50)  NOT NULL COMMENT 'ALERT / TAG_CHANGE / NOTIFY_LEADER / STATUS_CHANGE',
  action_config   TEXT         NOT NULL COMMENT '动作参数 JSON',
  priority        INT          NOT NULL DEFAULT 0 COMMENT '优先级，数字越大越优先',
  enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 0/1',
  is_builtin      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置规则 0/1，内置规则不可删除',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_rule_name (name),
  INDEX idx_enabled_priority (enabled, priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跟进规则定义表';

CREATE TABLE IF NOT EXISTS reminder_sent_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone           VARCHAR(20)  NOT NULL COMMENT '客户手机号',
  rule_id         BIGINT       NOT NULL COMMENT '规则 ID，0 表示新客资内置规则',
  reminder_type   VARCHAR(20)  NOT NULL COMMENT 'OVERDUE / DUE_TODAY / APPOINTMENT / NEW_LEAD / TAG_SUGGESTION',
  sent_date       DATE         NOT NULL COMMENT '发送日期（用于同日去重）',
  sent_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  UNIQUE INDEX idx_dedup (phone, rule_id, sent_date),
  INDEX idx_sent_date (sent_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒发送日志表';

CREATE TABLE IF NOT EXISTS system_tag_suggestions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone           VARCHAR(20)  NOT NULL COMMENT '客户手机号',
  tag_name        VARCHAR(50)  NOT NULL COMMENT '建议的标签名',
  rule_id         BIGINT       NOT NULL COMMENT '来源规则 ID',
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED / IGNORED',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at    DATETIME     DEFAULT NULL COMMENT '确认时间',
  ignored_at      DATETIME     DEFAULT NULL COMMENT '忽略时间',
  INDEX idx_phone_status (phone, status),
  INDEX idx_dedup (phone, tag_name, status),
  INDEX idx_cleanup (ignored_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统标签建议表';

INSERT INTO followup_rules (name, condition_json, action_type, action_config, priority, enabled, is_builtin)
VALUES
  ('团购超期告警', '{"operator":"AND","conditions":[{"field":"leadType","op":"EQ","value":"TUAN_GOU"},{"field":"lastFollowupHours","op":"GT","value":24}]}', 'ALERT', '{"alertLevel":"HIGH","reminderType":"OVERDUE"}', 10, 1, 1),
  ('线索超期提醒', '{"operator":"AND","conditions":[{"field":"leadType","op":"EQ","value":"XIAN_SUO"},{"field":"lastFollowupHours","op":"GT","value":72}]}', 'ALERT', '{"alertLevel":"NORMAL","reminderType":"OVERDUE"}', 9, 1, 1),
  ('PENDING超期提醒', '{"operator":"AND","conditions":[{"field":"leadType","op":"EQ","value":"PENDING"},{"field":"lastFollowupHours","op":"GT","value":24}]}', 'ALERT', '{"alertLevel":"NORMAL","reminderType":"OVERDUE"}', 8, 1, 1),
  ('沉睡风险', '{"operator":"AND","conditions":[{"field":"noMessageDays","op":"GT","value":7}]}', 'TAG_CHANGE', '{"tagName":"沉睡风险","notifyLeader":false}', 7, 1, 1),
  ('可能流失', '{"operator":"AND","conditions":[{"field":"noMessageDays","op":"GT","value":14}]}', 'TAG_CHANGE', '{"tagName":"可能流失","notifyLeader":true}', 6, 1, 1),
  ('高流失风险', '{"operator":"AND","conditions":[{"field":"messageKeywords","op":"CONTAINS","value":"不考虑,太贵了,再说,不需要,别联系"}]}', 'TAG_CHANGE', '{"tagName":"高流失风险","notifyLeader":true}', 5, 1, 1),
  ('预约提醒', '{"operator":"AND","conditions":[{"field":"appointmentDate","op":"BETWEEN","value":["NOW()","NOW()+24h"]}]}', 'ALERT', '{"alertLevel":"HIGH","reminderType":"APPOINTMENT"}', 10, 1, 1),
  ('管家超期未处理告警', '{"operator":"AND","conditions":[{"field":"lastFollowupHours","op":"GT","value":48}]}', 'NOTIFY_LEADER', '{"notifyLeader":true,"reason":"管家逾期未处理"}', 11, 1, 1)
ON DUPLICATE KEY UPDATE condition_json = VALUES(condition_json), action_config = VALUES(action_config), enabled = VALUES(enabled);

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('followup.full_scan_cron', '0 0 9 * * *', '全量扫描 cron'),
  ('followup.lightweight_scan_cron', '0 0 * * * *', '轻量扫描 cron'),
  ('followup.rule_refresh_interval_s', '30', '规则缓存刷新间隔秒，范围 10-300'),
  ('followup.tuan_alert_hours', '24', '团购客户逾期提醒阈值小时，范围 12-168'),
  ('followup.xiansuo_alert_hours', '72', '线索客户逾期提醒阈值小时，范围 24-336'),
  ('followup.pending_alert_hours', '24', 'PENDING 客户逾期提醒阈值小时，范围 12-168'),
  ('followup.sleep_risk_days', '7', '沉睡风险天数，范围 3-30'),
  ('followup.loss_risk_days', '14', '流失风险天数，范围 7-60'),
  ('followup.appointment_remind_hours', '24', '预约临近提醒小时，范围 1-72'),
  ('followup.scan_batch_size', '5000', '每批扫描客户数，范围 1000-10000'),
  ('followup.scan_timeout_s', '300', '全量扫描总超时秒，范围 120-600'),
  ('followup.reminder_dedup_days', '1', '同一客户+同一规则 N 天内不重复推送，范围 1-3'),
  ('followup.tag_suggestion_dedup_days', '7', '标签建议去重天数，范围 3-30'),
  ('followup.cursor_ttl_s', '3600', '扫描断点 Redis TTL 秒，范围 600-7200'),
  ('followup.keeper_overdue_leader_hours', '48', '管家逾期未处理通知组长小时，范围 24-168')
ON DUPLICATE KEY UPDATE description = VALUES(description);
