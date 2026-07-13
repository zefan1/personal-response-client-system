CREATE TABLE IF NOT EXISTS tag_categories (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_key   VARCHAR(50) NOT NULL,
  category_name  VARCHAR(30) NOT NULL,
  bound_field    VARCHAR(50) NOT NULL,
  is_builtin     TINYINT NOT NULL DEFAULT 0,
  is_enabled     TINYINT NOT NULL DEFAULT 1,
  sort_order     INT NOT NULL DEFAULT 0,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bound_field (bound_field),
  UNIQUE KEY uk_category_key (category_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tag categories';

CREATE TABLE IF NOT EXISTS tag_values (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id    BIGINT NOT NULL,
  tag_value      VARCHAR(50) NOT NULL,
  display_name   VARCHAR(30) NOT NULL,
  is_enabled     TINYINT NOT NULL DEFAULT 1,
  sort_order     INT NOT NULL DEFAULT 0,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_id (category_id),
  UNIQUE KEY uk_category_value (category_id, tag_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tag values';

INSERT INTO tag_categories (id, category_key, category_name, bound_field, is_builtin, is_enabled, sort_order)
VALUES
  (1, 'personality_type', 'Personality Type', 'personalityType', 1, 1, 1),
  (2, 'body_concerns', 'Body Concerns', 'bodyConcerns', 1, 1, 2),
  (3, 'worries', 'Worries', 'worries', 1, 1, 3),
  (4, 'intent_level', 'Intent Level', 'intentLevel', 1, 1, 4)
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name), is_builtin = 1, is_enabled = VALUES(is_enabled);

INSERT INTO tag_values (category_id, tag_value, display_name, is_enabled, sort_order)
VALUES
  (1, 'LOYALIST', 'Loyalist', 1, 1),
  (1, 'PEACEMAKER', 'Peacemaker', 1, 2),
  (1, 'DECISIVE', 'Decisive', 1, 3),
  (1, 'PENDING', 'Pending', 1, 99),
  (2, 'DIASTASIS_RECTI', 'Diastasis Recti', 1, 1),
  (2, 'PELVIC_FLOOR', 'Pelvic Floor', 1, 2),
  (2, 'URINE_LEAKAGE', 'Urine Leakage', 1, 3),
  (2, 'LUMBAGO', 'Lumbago', 1, 4),
  (2, 'PUBIC_PAIN', 'Pubic Pain', 1, 5),
  (2, 'STRETCH_MARKS', 'Stretch Marks', 1, 6),
  (2, 'BELLY_SAG', 'Belly Sag', 1, 7),
  (2, 'WEIGHT_GAIN', 'Weight Gain', 1, 8),
  (3, 'FEAR_NO_EFFECT', 'Fear No Effect', 1, 1),
  (3, 'FEAR_EXPENSIVE', 'Fear Expensive', 1, 2),
  (3, 'FEAR_PAIN', 'Fear Pain', 1, 3),
  (3, 'FEAR_HARD_SELL', 'Fear Hard Sell', 1, 4),
  (3, 'COMPARING', 'Comparing', 1, 5),
  (3, 'HUSBAND_DISAGREE', 'Husband Disagree', 1, 6),
  (3, 'FAMILY_UNSUPPORT', 'Family Unsupport', 1, 7),
  (3, 'NO_TIME', 'No Time', 1, 8),
  (3, 'TOO_FAR', 'Too Far', 1, 9),
  (4, 'HIGH', 'High', 1, 1),
  (4, 'MEDIUM', 'Medium', 1, 2),
  (4, 'LOW', 'Low', 1, 3),
  (4, 'PENDING', 'Pending', 1, 4),
  (4, 'CLOSED', 'Closed', 1, 5),
  (4, 'LOST', 'Lost', 1, 6)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_enabled = VALUES(is_enabled), sort_order = VALUES(sort_order);

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('tag.cache_refresh_interval_s', '300', 'tag cache fallback refresh interval seconds'),
  ('tag.value_max_per_category', '50', 'tag value max count per category')
ON DUPLICATE KEY UPDATE description = VALUES(description);
