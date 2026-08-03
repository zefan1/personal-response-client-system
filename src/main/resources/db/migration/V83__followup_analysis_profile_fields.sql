ALTER TABLE customers
  ADD COLUMN internal_note TEXT DEFAULT NULL COMMENT '私域人员内部提醒' AFTER customer_stage,
  ADD COLUMN customer_profile_summary TEXT DEFAULT NULL COMMENT '客户B档案摘要' AFTER internal_note,
  ADD COLUMN first_tracking_capture TEXT DEFAULT NULL COMMENT '第一次追踪捕捉' AFTER customer_profile_summary,
  ADD COLUMN second_tracking_capture TEXT DEFAULT NULL COMMENT '第二次追踪捕捉' AFTER first_tracking_capture,
  ADD COLUMN third_tracking_capture TEXT DEFAULT NULL COMMENT '第三次追踪捕捉' AFTER second_tracking_capture;
