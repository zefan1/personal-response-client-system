ALTER TABLE customers
  ADD COLUMN lead_initial_processed_at DATETIME DEFAULT NULL COMMENT '新客资初步处理时间' AFTER assigned_at,
  ADD COLUMN lead_initial_processed_by VARCHAR(50) DEFAULT NULL COMMENT '完成初步处理的负责人' AFTER lead_initial_processed_at,
  ADD COLUMN lead_retained_until DATETIME DEFAULT NULL COMMENT '已处理新客资保留到期时间' AFTER lead_initial_processed_by;

CREATE INDEX idx_customers_lead_processing
  ON customers (assigned_keeper, lead_initial_processed_at, lead_retained_until);

INSERT INTO system_configs (config_key, config_value, description)
VALUES (
  'followup.friend_request_templates_json',
  '["你好，我是负责跟进你的顾问，方便通过一下好友申请吗？"]',
  '添加好友申请话术，多条内容使用 JSON 数组保存，第一条为当前启用话术'
)
ON DUPLICATE KEY UPDATE description = VALUES(description);
