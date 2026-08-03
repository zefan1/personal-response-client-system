CREATE TABLE communication_recognition_batches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id CHAR(36) NOT NULL,
  username VARCHAR(64) NOT NULL,
  platform_code VARCHAR(32) NOT NULL,
  platform_identifier VARCHAR(255) NULL,
  recognized_nickname VARCHAR(255) NULL,
  recognized_phone VARCHAR(32) NULL,
  customer_id BIGINT NULL,
  association_status VARCHAR(24) NOT NULL,
  raw_text MEDIUMTEXT NOT NULL,
  recognized_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_communication_batch_id (batch_id),
  KEY idx_communication_batch_owner_status (username, association_status, recognized_at),
  KEY idx_communication_batch_customer_time (customer_id, recognized_at),
  CONSTRAINT fk_communication_batch_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  customer_id BIGINT NULL,
  username VARCHAR(64) NOT NULL,
  platform_code VARCHAR(32) NOT NULL,
  sender_role VARCHAR(24) NOT NULL,
  content_type VARCHAR(24) NOT NULL DEFAULT 'TEXT',
  original_text TEXT NOT NULL,
  current_text TEXT NOT NULL,
  message_time DATETIME(6) NOT NULL,
  time_estimated TINYINT NOT NULL,
  sequence_no INT NOT NULL,
  dedupe_fingerprint CHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_communication_message_sequence (batch_id, sequence_no),
  KEY idx_communication_message_customer_time (customer_id, message_time, id),
  KEY idx_communication_message_dedupe (customer_id, platform_code, dedupe_fingerprint, message_time),
  KEY idx_communication_message_owner_time (username, message_time),
  CONSTRAINT fk_communication_message_batch
    FOREIGN KEY (batch_id) REFERENCES communication_recognition_batches(id),
  CONSTRAINT fk_communication_message_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_platform_identities (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform_code VARCHAR(32) NOT NULL,
  platform_identifier VARCHAR(255) NOT NULL,
  normalized_identifier VARCHAR(255) NOT NULL,
  customer_id BIGINT NOT NULL,
  linked_by VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_communication_identity_customer (platform_code, normalized_identifier, customer_id),
  KEY idx_communication_identity_lookup (platform_code, normalized_identifier),
  KEY idx_communication_identity_customer (customer_id),
  CONSTRAINT fk_communication_identity_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_pending_task_links (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id CHAR(36) NOT NULL,
  batch_id CHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_communication_pending_task (task_id),
  UNIQUE KEY uk_communication_pending_batch (batch_id),
  CONSTRAINT fk_communication_pending_task
    FOREIGN KEY (task_id) REFERENCES pending_reply_tasks(task_id),
  CONSTRAINT fk_communication_pending_batch
    FOREIGN KEY (batch_id) REFERENCES communication_recognition_batches(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_message_corrections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  previous_text TEXT NOT NULL,
  corrected_text TEXT NOT NULL,
  corrected_by VARCHAR(64) NOT NULL,
  corrected_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_communication_correction_message_time (message_id, corrected_at),
  CONSTRAINT fk_communication_correction_message
    FOREIGN KEY (message_id) REFERENCES communication_messages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_summary_versions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  summary_text TEXT NOT NULL,
  last_message_id BIGINT NOT NULL,
  generated_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_communication_summary_version (customer_id, version_no),
  KEY idx_communication_summary_customer_time (customer_id, generated_at),
  CONSTRAINT fk_communication_summary_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_communication_summary_last_message
    FOREIGN KEY (last_message_id) REFERENCES communication_messages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE communication_summary_states (
  customer_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  last_summarized_message_id BIGINT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(6) NULL,
  last_error VARCHAR(500) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (customer_id),
  KEY idx_communication_summary_retry (status, next_retry_at),
  CONSTRAINT fk_communication_summary_state_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_communication_summary_state_message
    FOREIGN KEY (last_summarized_message_id) REFERENCES communication_messages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
