ALTER TABLE customers
  ADD COLUMN wechat_id VARCHAR(100) DEFAULT NULL COMMENT '微信号' AFTER nickname,
  ADD COLUMN lead_capture_type VARCHAR(100) DEFAULT NULL COMMENT '平台留资类型' AFTER lead_type,
  ADD COLUMN lead_capture_method VARCHAR(100) DEFAULT NULL COMMENT '留资方式' AFTER lead_capture_type,
  ADD COLUMN platform_lead_at DATETIME DEFAULT NULL COMMENT '平台留资时间' AFTER lead_capture_method,
  ADD COLUMN assigned_at DATETIME DEFAULT NULL COMMENT '分配时间' AFTER assigned_keeper;

CREATE INDEX idx_customers_platform_lead_at ON customers (platform_lead_at);
CREATE INDEX idx_customers_assigned_at ON customers (assigned_at);
