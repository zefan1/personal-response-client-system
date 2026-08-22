ALTER TABLE datasource_sync_watermarks
  ADD COLUMN last_successful_remote_read_at DATETIME NULL AFTER last_successful_sync_at;
