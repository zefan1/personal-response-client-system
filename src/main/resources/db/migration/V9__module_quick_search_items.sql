CREATE TABLE IF NOT EXISTS quick_search_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  content_type VARCHAR(32) NOT NULL,
  scene VARCHAR(64),
  lead_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
  title VARCHAR(200) NOT NULL,
  shortcut_code VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  image_url VARCHAR(500),
  sort_order INT NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_quick_search_shortcut (shortcut_code),
  KEY idx_quick_search_enabled (is_enabled),
  KEY idx_quick_search_type (content_type),
  KEY idx_quick_search_lead_type (lead_type)
);
