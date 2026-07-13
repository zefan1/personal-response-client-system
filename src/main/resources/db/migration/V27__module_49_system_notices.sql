CREATE TABLE IF NOT EXISTS system_notices (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  notice_id       VARCHAR(50)   NOT NULL COMMENT 'notice business id, notice-yyyyMMdd-seq or notice-auto-yyyyMMdd-seq',
  title           VARCHAR(200)  NOT NULL COMMENT 'notice title',
  content         VARCHAR(2000) NOT NULL COMMENT 'notice content',
  level           VARCHAR(10)   NOT NULL DEFAULT 'INFO' COMMENT 'INFO / WARN / ERROR',
  source          VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AUTO',
  status          VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED / SCHEDULED',
  is_stopped      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0 active, 1 stopped',
  publish_at      DATETIME      NOT NULL COMMENT 'planned publish time',
  pushed_at       DATETIME      DEFAULT NULL COMMENT 'actual WS push time',
  expire_at       DATETIME      NOT NULL COMMENT 'expiration time',
  stopped_at      DATETIME      DEFAULT NULL COMMENT 'stop time',
  created_by      VARCHAR(20)   NOT NULL COMMENT 'creator phone or SYSTEM',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_notice_id (notice_id),
  INDEX idx_status_stopped_expire (status, is_stopped, expire_at),
  INDEX idx_status_publish (status, publish_at),
  INDEX idx_source (source, is_stopped),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='system notices for desktop banners';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('notice.max_title_chars', '100', 'notice title max characters, range 50-200'),
  ('notice.max_content_chars', '500', 'notice content max characters, range 100-2000'),
  ('notice.default_expire_days', '7', 'notice default expire days, range 1-30'),
  ('notice.max_schedule_days', '30', 'notice max scheduled publish days, range 7-90'),
  ('notice.scan_interval_s', '30', 'scheduled notice scan interval seconds, range 15-120'),
  ('notice.auto_expire_hours', '1', 'automatic notice expire hours, range 1-24'),
  ('notice.list_page_size', '20', 'notice list page size, range 10-50')
ON DUPLICATE KEY UPDATE description = VALUES(description);
