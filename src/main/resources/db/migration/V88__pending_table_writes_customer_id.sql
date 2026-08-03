ALTER TABLE pending_table_writes
  MODIFY phone VARCHAR(20) NULL,
  ADD COLUMN customer_id BIGINT NULL AFTER id,
  ADD KEY idx_pending_table_write_customer (customer_id);
