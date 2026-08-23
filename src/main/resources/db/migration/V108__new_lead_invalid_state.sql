ALTER TABLE customers
  ADD COLUMN lead_invalid BOOLEAN NOT NULL DEFAULT FALSE COMMENT '新客资是否被标记为无效' AFTER lead_retained_until;
