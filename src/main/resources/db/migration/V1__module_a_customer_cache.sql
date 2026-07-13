CREATE TABLE IF NOT EXISTS customers (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone             VARCHAR(20)   NOT NULL COMMENT '手机号',
  nickname          VARCHAR(100)  DEFAULT NULL COMMENT '微信昵称/备注（去标记后）',
  source_channel    VARCHAR(50)   DEFAULT NULL COMMENT '来源渠道',
  lead_type         VARCHAR(20)   DEFAULT NULL COMMENT '线索类型：TUAN_GOU/XIAN_SUO/PENDING',
  personality_type  VARCHAR(50)   DEFAULT NULL COMMENT '客户性格类型',
  assigned_keeper   VARCHAR(50)   DEFAULT NULL COMMENT '分配管家',
  intended_store    VARCHAR(100)  DEFAULT NULL COMMENT '意向门店',
  intended_project  VARCHAR(100)  DEFAULT NULL COMMENT '意向项目',
  purchased_project VARCHAR(200)  DEFAULT NULL COMMENT '已购项目',
  postpartum_months DECIMAL(4,1)  DEFAULT NULL COMMENT '产后几个月',
  parity            VARCHAR(10)   DEFAULT NULL COMMENT '胎次',
  delivery_method   VARCHAR(20)   DEFAULT NULL COMMENT '分娩方式',
  breastfeeding     VARCHAR(20)   DEFAULT NULL COMMENT '母乳状态',
  lochia_period     VARCHAR(50)   DEFAULT NULL COMMENT '恶露/月经状态',
  pregnancy_weight  DECIMAL(5,1)  DEFAULT NULL COMMENT '孕期增重（公斤）',
  current_weight    DECIMAL(5,1)  DEFAULT NULL COMMENT '当前体重（公斤）',
  body_concerns     VARCHAR(500)  DEFAULT NULL COMMENT '身体关注点',
  diastasis_recti   VARCHAR(50)   DEFAULT NULL COMMENT '腹直肌分离情况',
  urine_leakage     VARCHAR(100)  DEFAULT NULL COMMENT '漏尿情况',
  pubic_lumbago     VARCHAR(100)  DEFAULT NULL COMMENT '耻骨/腰痛',
  prev_repair_exp   VARCHAR(500)  DEFAULT NULL COMMENT '之前修复经历',
  postpartum_check  VARCHAR(200)  DEFAULT NULL COMMENT '产后检查情况',
  exercise_habits   VARCHAR(200)  DEFAULT NULL COMMENT '运动习惯',
  intent_level      VARCHAR(10)   DEFAULT NULL COMMENT '意向度：HIGH/MEDIUM/LOW/PENDING',
  worries           VARCHAR(500)  DEFAULT NULL COMMENT '担忧点',
  customer_stage    VARCHAR(50)   DEFAULT NULL COMMENT '客户阶段',
  last_followup_at  DATETIME      DEFAULT NULL COMMENT '最近跟进时间',
  followup_notes    TEXT          DEFAULT NULL COMMENT '跟进记录（最近一次聊了什么）',
  next_followup_at  DATETIME      DEFAULT NULL COMMENT '下次跟进时间',
  next_followup_dir VARCHAR(200)  DEFAULT NULL COMMENT '下次跟进方向',
  appointment_date  DATE          DEFAULT NULL COMMENT '预约日期',
  appointment_store VARCHAR(100)  DEFAULT NULL COMMENT '预约门店',
  appointment_item  VARCHAR(100)  DEFAULT NULL COMMENT '预约项目',
  arrived           VARCHAR(10)   DEFAULT NULL COMMENT '是否到店',
  source_table      VARCHAR(100)  DEFAULT NULL COMMENT '数据来源表',
  source_row_id     VARCHAR(100)  DEFAULT NULL COMMENT '来源表行 ID',
  synced_at         DATETIME      DEFAULT NULL COMMENT '最后同步时间',
  version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_phone (phone),
  INDEX idx_nickname (nickname),
  INDEX idx_assigned_keeper (assigned_keeper),
  INDEX idx_next_followup (next_followup_at),
  INDEX idx_lead_type (lead_type),
  INDEX idx_last_followup (last_followup_at),
  INDEX idx_customer_stage (customer_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户档案表';

CREATE TABLE IF NOT EXISTS system_configs (
  config_key    VARCHAR(100) PRIMARY KEY,
  config_value  TEXT         NOT NULL,
  description   VARCHAR(500) DEFAULT NULL,
  updated_by    VARCHAR(50)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS datasource_field_mappings (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_table    VARCHAR(100)  NOT NULL COMMENT '数据源表名（如"推广组客资登记表"）',
  source_field    VARCHAR(200)  NOT NULL COMMENT '原表字段名（如"手机号/微信"）',
  target_field    VARCHAR(100)  NOT NULL COMMENT '目标字段名（如 phone，对应 Customer 模型的 camelCase 字段名）',
  transform_rule  VARCHAR(200)  DEFAULT NULL COMMENT '转换规则（如"contains(团购)→TUAN_GOU"），null 表示直接映射',
  is_enabled      TINYINT       NOT NULL DEFAULT 1 COMMENT '启用/禁用',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE INDEX idx_source_target (source_table, source_field, target_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源字段映射表';

CREATE TABLE IF NOT EXISTS sync_failure_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_table    VARCHAR(100)  NOT NULL COMMENT '数据源表名',
  source_row_id   VARCHAR(100)  DEFAULT NULL COMMENT '原表行 ID',
  phone           VARCHAR(20)   DEFAULT NULL COMMENT '提取出的手机号（可能为 null）',
  fail_reason     VARCHAR(500)  NOT NULL COMMENT '失败原因',
  raw_data        TEXT          DEFAULT NULL COMMENT '原始行数据（前 1000 字符，用于排查）',
  retry_count     INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
  resolved        TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已处理',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_resolved (resolved, created_at),
  INDEX idx_source_table (source_table, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步失败记录表';

INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('cache.sync_cron', '0 */30 * * * *', '模块A定时同步频率'),
  ('cache.ttl_seconds', '900', 'Redis客户缓存TTL秒数'),
  ('cache.load_batch_size', '500', '启动预热每批加载条数'),
  ('cache.sync_timeout_ms', '10000', '企微表格API调用超时毫秒'),
  ('cache.max_sync_rows_per_round', '10000', '单轮同步最大增量行数'),
  ('cache.lock_spin_max', '3', '缓存击穿锁自旋次数'),
  ('cache.lock_spin_interval_ms', '100', '缓存击穿锁自旋间隔毫秒'),
  ('cache.lock_ttl_s', '5', '缓存击穿锁TTL秒数')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO datasource_field_mappings (source_table, source_field, target_field, transform_rule)
VALUES
  ('推广组客资登记表', '手机号/微信', 'phone', NULL),
  ('推广组客资登记表', '意向门店', 'intendedStore', NULL),
  ('推广组客资登记表', '对接管家', 'assignedKeeper', NULL),
  ('推广组客资登记表', '下单项目', 'intendedProject', NULL),
  ('推广组客资登记表', '来源渠道', 'sourceChannel', NULL),
  ('私域客资管理表', '联系方式', 'phone', NULL),
  ('私域客资管理表', '备注称呼', 'nickname', NULL),
  ('私域客资管理表', '客资渠道', 'sourceChannel', NULL),
  ('私域客资管理表', '客资类型', 'leadType', NULL),
  ('私域客资管理表', '管家', 'assignedKeeper', NULL),
  ('私域客资管理表', '意向门店', 'intendedStore', NULL),
  ('私域客资管理表', '意向项目', 'intendedProject', NULL),
  ('私域客资管理表', '客户阶段', 'customerStage', NULL),
  ('私域客资管理表', '客户关注点', 'bodyConcerns', NULL),
  ('私域客资管理表', '跟进记录', 'followupNotes', NULL),
  ('私域客资管理表', '下次跟进时间', 'nextFollowupAt', NULL),
  ('私域客资管理表', '下次跟进方向', 'nextFollowupDir', NULL),
  ('新客管理衔接表', '手机号码', 'phone', NULL),
  ('新客管理衔接表', '客户姓名', 'nickname', NULL),
  ('新客管理衔接表', '所属门店', 'appointmentStore', NULL),
  ('新客管理衔接表', '到店日期', 'appointmentDate', NULL),
  ('新客管理衔接表', '是否到店', 'arrived', NULL),
  ('新客管理衔接表', '体验项目', 'appointmentItem', NULL),
  ('新客管理衔接表', '约课管家', 'assignedKeeper', NULL)
ON DUPLICATE KEY UPDATE target_field = VALUES(target_field), transform_rule = VALUES(transform_rule), is_enabled = 1;
