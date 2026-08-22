CREATE TABLE IF NOT EXISTS recognition_job_restart_recovery (
  job_id VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  reply_session_id VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (job_id),
  KEY idx_recognition_job_recovery_owner (username, updated_at),
  KEY idx_recognition_job_recovery_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='识图任务重启恢复状态；不保存截图或识别内容';
