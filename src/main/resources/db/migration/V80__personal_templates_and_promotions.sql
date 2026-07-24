CREATE TABLE personal_templates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_username VARCHAR(64) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  channel_code VARCHAR(100) NULL,
  scene VARCHAR(100) NULL,
  lead_type VARCHAR(100) NULL,
  labels_json TEXT NOT NULL,
  source_reply_session_id VARCHAR(80) NULL,
  usage_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_personal_template_owner_time (owner_username, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE template_promotion_candidates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  personal_template_id BIGINT NOT NULL,
  owner_username VARCHAR(64) NOT NULL,
  original_ai_reply TEXT NOT NULL,
  edited_title VARCHAR(120) NOT NULL,
  edited_body TEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
  decided_by VARCHAR(64) NULL,
  decided_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_candidate_status_time (status, created_at),
  KEY idx_candidate_owner_time (owner_username, created_at),
  CONSTRAINT fk_candidate_personal_template
    FOREIGN KEY (personal_template_id) REFERENCES personal_templates(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_template_publications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  candidate_id BIGINT NOT NULL,
  quick_search_item_id BIGINT NOT NULL,
  published_by VARCHAR(64) NOT NULL,
  published_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_template_publication_candidate (candidate_id),
  UNIQUE KEY uk_team_template_publication_quick_search_item (quick_search_item_id),
  CONSTRAINT fk_team_template_publication_candidate
    FOREIGN KEY (candidate_id) REFERENCES template_promotion_candidates(id),
  CONSTRAINT fk_team_template_publication_quick_search_item
    FOREIGN KEY (quick_search_item_id) REFERENCES quick_search_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
