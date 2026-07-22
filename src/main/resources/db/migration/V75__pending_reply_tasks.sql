CREATE TABLE IF NOT EXISTS pending_reply_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id CHAR(36) NOT NULL,
  reply_session_id VARCHAR(80) NOT NULL,
  username VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  recognized_nickname VARCHAR(255) NULL,
  recognized_phone VARCHAR(32) NULL,
  platform_identifier VARCHAR(255) NULL,
  lead_type VARCHAR(32) NULL,
  source_table VARCHAR(255) NULL,
  client_message TEXT NOT NULL,
  chat_context_json MEDIUMTEXT NOT NULL,
  selected_phone VARCHAR(32) NULL,
  result_json MEDIUMTEXT NULL,
  error_code VARCHAR(32) NULL,
  generation_started_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pending_reply_task_id (task_id),
  KEY idx_pending_reply_owner_status (username, status, expires_at),
  KEY idx_pending_reply_recovery (status, generation_started_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多客户确认后续跑的回复任务，不保存截图';

CREATE TABLE IF NOT EXISTS pending_reply_task_candidates (
  task_id BIGINT NOT NULL,
  phone VARCHAR(32) NOT NULL,
  rank_no SMALLINT NOT NULL,
  PRIMARY KEY (task_id, phone),
  CONSTRAINT fk_pending_reply_candidate_task
    FOREIGN KEY (task_id) REFERENCES pending_reply_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待确认回复任务的原始候选客户';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('chat.pending_reply_ttl_hours', '24', 'pending reply task retention hours, range 1-72'),
  ('chat.pending_reply_generating_timeout_s', '120', 'pending reply generating recovery timeout seconds, range 30-600')
ON DUPLICATE KEY UPDATE description = VALUES(description);
