CREATE TABLE IF NOT EXISTS send_confirmations (
  operator VARCHAR(64) NOT NULL,
  confirmation_id VARCHAR(80) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (operator, confirmation_id),
  KEY idx_send_confirmation_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工发送确认幂等记录';
