CREATE TABLE IF NOT EXISTS profile_update_failures (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  phone VARCHAR(32) NULL,
  raw_messages_json LONGTEXT NOT NULL,
  operator VARCHAR(100) NULL,
  stage VARCHAR(80) NOT NULL,
  error_code VARCHAR(100) NULL,
  error_message VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  last_attempt_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_profile_update_failures_status (status, updated_at),
  INDEX idx_profile_update_failures_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
