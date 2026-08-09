ALTER TABLE customers
  ADD COLUMN appointment_status VARCHAR(20) DEFAULT NULL COMMENT '预约状态',
  ADD COLUMN appointment_time VARCHAR(10) DEFAULT NULL COMMENT '预约时间',
  ADD COLUMN arrival_source_row_id VARCHAR(100) DEFAULT NULL COMMENT '到店衔接表行 ID';
