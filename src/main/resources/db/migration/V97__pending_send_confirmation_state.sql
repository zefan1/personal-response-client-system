CREATE TABLE IF NOT EXISTS pending_send_confirmation_state (
  confirmation_id VARCHAR(80) NOT NULL,
  operator VARCHAR(64) NOT NULL,
  customer_id BIGINT NULL,
  phone VARCHAR(32) NULL,
  nickname VARCHAR(255) NULL,
  copied_text TEXT NOT NULL,
  reply_source VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  reminder_count INT NOT NULL DEFAULT 0,
  last_reminder_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (confirmation_id),
  KEY idx_pending_send_operator_status (operator, status, updated_at),
  KEY idx_pending_send_customer (customer_id),
  CONSTRAINT fk_pending_send_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工复制回复后的发送确认状态';
