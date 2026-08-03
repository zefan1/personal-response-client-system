CREATE TABLE IF NOT EXISTS pending_followup_analyses (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_key    VARCHAR(160) NOT NULL,
  phone          VARCHAR(20)  NOT NULL,
  payload        TEXT         NOT NULL,
  retry_count    INT          NOT NULL DEFAULT 0,
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RESOLVED / FAILED',
  next_retry_at  DATETIME     NOT NULL,
  error_msg      VARCHAR(500) DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_followup_analysis_request (request_key),
  INDEX idx_followup_analysis_retry (status, next_retry_at),
  INDEX idx_followup_analysis_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM followup analysis retry queue';
