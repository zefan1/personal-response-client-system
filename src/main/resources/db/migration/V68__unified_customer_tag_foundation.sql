ALTER TABLE tag_categories
  MODIFY COLUMN category_name VARCHAR(100) NOT NULL,
  MODIFY COLUMN bound_field VARCHAR(50) NULL,
  ADD COLUMN purpose VARCHAR(500) NOT NULL DEFAULT '' AFTER category_name,
  ADD COLUMN selection_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' AFTER bound_field,
  ADD COLUMN system_inference_enabled TINYINT NOT NULL DEFAULT 0 AFTER selection_mode,
  ADD COLUMN manual_edit_enabled TINYINT NOT NULL DEFAULT 1 AFTER system_inference_enabled,
  ADD COLUMN auto_update_mode VARCHAR(20) NOT NULL DEFAULT 'RECORD_ONLY' AFTER manual_edit_enabled,
  ADD COLUMN min_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.8500 AFTER auto_update_mode,
  ADD COLUMN min_evidence_messages INT NOT NULL DEFAULT 1 AFTER min_confidence,
  ADD COLUMN cooldown_hours INT NOT NULL DEFAULT 0 AFTER min_evidence_messages,
  ADD COLUMN uncertain_policy VARCHAR(20) NOT NULL DEFAULT 'KEEP_CURRENT' AFTER cooldown_hours,
  ADD COLUMN use_for_reply TINYINT NOT NULL DEFAULT 1 AFTER uncertain_policy,
  ADD COLUMN use_for_filter TINYINT NOT NULL DEFAULT 1 AFTER use_for_reply,
  ADD COLUMN use_for_statistics TINYINT NOT NULL DEFAULT 1 AFTER use_for_filter,
  ADD COLUMN use_for_followup_rules TINYINT NOT NULL DEFAULT 1 AFTER use_for_statistics,
  ADD COLUMN merged_into_id BIGINT NULL AFTER sort_order,
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER merged_into_id,
  ADD UNIQUE KEY uk_tag_categories_id_selection_mode (id, selection_mode),
  ADD CONSTRAINT chk_tag_categories_selection_mode CHECK (selection_mode IN ('SINGLE', 'MULTI')),
  ADD CONSTRAINT chk_tag_categories_auto_update_mode CHECK (auto_update_mode IN ('ADD_ONLY', 'REPLACE', 'RECORD_ONLY')),
  ADD CONSTRAINT chk_tag_categories_confidence CHECK (min_confidence >= 0 AND min_confidence <= 1),
  ADD CONSTRAINT chk_tag_categories_evidence_messages CHECK (min_evidence_messages >= 0),
  ADD CONSTRAINT chk_tag_categories_cooldown CHECK (cooldown_hours >= 0),
  ADD CONSTRAINT chk_tag_categories_uncertain_policy CHECK (uncertain_policy IN ('KEEP_CURRENT', 'SET_PENDING')),
  ADD CONSTRAINT chk_tag_categories_flags CHECK (
    system_inference_enabled IN (0, 1) AND manual_edit_enabled IN (0, 1)
    AND use_for_reply IN (0, 1) AND use_for_filter IN (0, 1)
    AND use_for_statistics IN (0, 1) AND use_for_followup_rules IN (0, 1)
    AND is_builtin IN (0, 1) AND is_enabled IN (0, 1)
  ),
  ADD CONSTRAINT fk_tag_categories_merged_into FOREIGN KEY (merged_into_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD KEY idx_tag_categories_enabled_sort (is_enabled, sort_order),
  ADD KEY idx_tag_categories_merged_into (merged_into_id);

ALTER TABLE tag_values
  MODIFY COLUMN display_name VARCHAR(100) NOT NULL,
  ADD COLUMN meaning VARCHAR(500) NOT NULL DEFAULT '' AFTER display_name,
  ADD COLUMN applicable_when VARCHAR(1000) NOT NULL DEFAULT '' AFTER meaning,
  ADD COLUMN not_applicable_when VARCHAR(1000) NOT NULL DEFAULT '' AFTER applicable_when,
  ADD COLUMN positive_examples VARCHAR(1000) NOT NULL DEFAULT '' AFTER not_applicable_when,
  ADD COLUMN negative_examples VARCHAR(1000) NOT NULL DEFAULT '' AFTER positive_examples,
  ADD COLUMN synonyms_json VARCHAR(2000) NOT NULL DEFAULT '[]' AFTER negative_examples,
  ADD COLUMN system_selectable TINYINT NOT NULL DEFAULT 0 AFTER synonyms_json,
  ADD COLUMN manual_selectable TINYINT NOT NULL DEFAULT 1 AFTER system_selectable,
  ADD COLUMN merged_into_id BIGINT NULL AFTER sort_order,
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER merged_into_id,
  ADD UNIQUE KEY uk_tag_values_id_category (id, category_id),
  ADD KEY idx_tag_values_enabled_sort (category_id, is_enabled, sort_order),
  ADD KEY idx_tag_values_merged_into (merged_into_id),
  ADD CONSTRAINT chk_tag_values_synonyms_json CHECK (JSON_VALID(synonyms_json) AND JSON_TYPE(synonyms_json) = 'ARRAY'),
  ADD CONSTRAINT chk_tag_values_flags CHECK (system_selectable IN (0, 1) AND manual_selectable IN (0, 1) AND is_enabled IN (0, 1)),
  ADD CONSTRAINT fk_tag_values_category FOREIGN KEY (category_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE tag_values
  ADD CONSTRAINT fk_tag_values_merged_into FOREIGN KEY (merged_into_id, category_id) REFERENCES tag_values (id, category_id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE personality_tags
  ADD COLUMN canonical_tag_value_id BIGINT DEFAULT NULL AFTER tag_value,
  ADD COLUMN migration_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER canonical_tag_value_id,
  ADD COLUMN retired_at DATETIME DEFAULT NULL AFTER enabled,
  ADD KEY idx_personality_tags_canonical (canonical_tag_value_id),
  ADD CONSTRAINT fk_personality_tags_canonical FOREIGN KEY (canonical_tag_value_id) REFERENCES tag_values (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT chk_personality_tags_migration_status CHECK (migration_status IN ('PENDING', 'MAPPED', 'UNMATCHED'));

CREATE TABLE tag_analysis_runs (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  analysis_key             VARCHAR(64) NOT NULL,
  customer_id              BIGINT NOT NULL,
  source_type              VARCHAR(32) NOT NULL,
  status                   VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  effective_message_count  INT NOT NULL DEFAULT 0,
  customer_version         INT NOT NULL DEFAULT 0,
  caller                   VARCHAR(100) DEFAULT NULL,
  skill_id                 VARCHAR(100) DEFAULT NULL,
  llm_environment          VARCHAR(100) DEFAULT NULL,
  llm_model                VARCHAR(100) DEFAULT NULL,
  prompt_version           VARCHAR(100) DEFAULT NULL,
  error_message            VARCHAR(1000) DEFAULT NULL,
  started_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at              DATETIME DEFAULT NULL,
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tag_analysis_key (analysis_key),
  KEY idx_tag_analysis_customer_time (customer_id, created_at),
  KEY idx_tag_analysis_status_time (status, created_at),
  CONSTRAINT fk_tag_analysis_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_tag_analysis_message_count CHECK (effective_message_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='customer tag analysis runs';

CREATE TABLE tag_analysis_results (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  analysis_run_id      BIGINT NOT NULL,
  category_id          BIGINT NOT NULL,
  tag_value_id         BIGINT DEFAULT NULL,
  result_type          VARCHAR(24) NOT NULL,
  requested_action     VARCHAR(20) NOT NULL DEFAULT 'NONE',
  confidence           DECIMAL(5,4) DEFAULT NULL,
  evidence_text        TEXT DEFAULT NULL,
  validation_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  validation_reason    VARCHAR(1000) DEFAULT NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tag_analysis_result_run (analysis_run_id, id),
  KEY idx_tag_analysis_result_category (category_id, created_at),
  KEY idx_tag_analysis_result_value (tag_value_id, created_at),
  CONSTRAINT fk_tag_analysis_result_run FOREIGN KEY (analysis_run_id) REFERENCES tag_analysis_runs (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_tag_analysis_result_category FOREIGN KEY (category_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_tag_analysis_result_value_category FOREIGN KEY (tag_value_id, category_id) REFERENCES tag_values (id, category_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_tag_analysis_result_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='validated tag analysis results';

CREATE TABLE customer_tag_assignments (
  id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id                 BIGINT NOT NULL,
  category_id                 BIGINT NOT NULL,
  tag_value_id                BIGINT NOT NULL,
  selection_mode              VARCHAR(16) NOT NULL,
  is_active                   TINYINT NOT NULL DEFAULT 1,
  source_type                 VARCHAR(32) NOT NULL,
  confidence                  DECIMAL(5,4) DEFAULT NULL,
  evidence_text               TEXT DEFAULT NULL,
  evidence_message_count      INT NOT NULL DEFAULT 0,
  analysis_result_id          BIGINT DEFAULT NULL,
  skill_id                    VARCHAR(100) DEFAULT NULL,
  llm_environment             VARCHAR(100) DEFAULT NULL,
  llm_model                   VARCHAR(100) DEFAULT NULL,
  prompt_version              VARCHAR(100) DEFAULT NULL,
  operator_account            VARCHAR(100) DEFAULT NULL,
  is_manual_locked            TINYINT NOT NULL DEFAULT 0,
  locked_by                   VARCHAR(100) DEFAULT NULL,
  locked_at                   DATETIME DEFAULT NULL,
  supersedes_assignment_id    BIGINT DEFAULT NULL,
  customer_version            INT NOT NULL DEFAULT 0,
  invalidated_reason          VARCHAR(500) DEFAULT NULL,
  invalidated_at              DATETIME DEFAULT NULL,
  created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  active_tag_key              BIGINT GENERATED ALWAYS AS (CASE WHEN is_active = 1 THEN tag_value_id ELSE NULL END) STORED,
  active_single_category_key  BIGINT GENERATED ALWAYS AS (CASE WHEN is_active = 1 AND selection_mode = 'SINGLE' THEN category_id ELSE NULL END) STORED,
  UNIQUE KEY uk_customer_active_tag (customer_id, active_tag_key),
  UNIQUE KEY uk_customer_active_single_category (customer_id, active_single_category_key),
  KEY idx_customer_tags_current (customer_id, is_active, category_id),
  KEY idx_customer_tags_category_value (category_id, tag_value_id, is_active),
  KEY idx_customer_tags_analysis_result (analysis_result_id),
  KEY idx_customer_tags_supersedes (supersedes_assignment_id),
  CONSTRAINT fk_customer_tags_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_customer_tags_category_mode FOREIGN KEY (category_id, selection_mode) REFERENCES tag_categories (id, selection_mode) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_customer_tags_value_category FOREIGN KEY (tag_value_id, category_id) REFERENCES tag_values (id, category_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_customer_tags_analysis_result FOREIGN KEY (analysis_result_id) REFERENCES tag_analysis_results (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_customer_tags_supersedes FOREIGN KEY (supersedes_assignment_id) REFERENCES customer_tag_assignments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_customer_tags_selection_mode CHECK (selection_mode IN ('SINGLE', 'MULTI')),
  CONSTRAINT chk_customer_tags_active CHECK (is_active IN (0, 1)),
  CONSTRAINT chk_customer_tags_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
  CONSTRAINT chk_customer_tags_evidence_count CHECK (evidence_message_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='current and historical customer tag assignments';

CREATE TABLE customer_tag_category_locks (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id       BIGINT NOT NULL,
  category_id       BIGINT NOT NULL,
  is_locked         TINYINT NOT NULL DEFAULT 1,
  locked_by         VARCHAR(100) NOT NULL,
  lock_reason       VARCHAR(500) DEFAULT NULL,
  locked_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  unlocked_by       VARCHAR(100) DEFAULT NULL,
  unlocked_at       DATETIME DEFAULT NULL,
  version           INT NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_tag_category_lock (customer_id, category_id),
  KEY idx_customer_tag_locks_current (customer_id, is_locked),
  CONSTRAINT fk_customer_tag_locks_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_customer_tag_locks_category FOREIGN KEY (category_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_customer_tag_locks_state CHECK (is_locked IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='manual customer tag category locks';

CREATE TABLE unmatched_legacy_tag_values (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id           BIGINT NOT NULL,
  source_type           VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER_FIELD',
  source_record_id      BIGINT DEFAULT NULL,
  legacy_field          VARCHAR(50) NOT NULL,
  raw_value             VARCHAR(500) NOT NULL,
  raw_value_hash        CHAR(64) NOT NULL,
  category_id           BIGINT DEFAULT NULL,
  mapped_tag_value_id   BIGINT DEFAULT NULL,
  status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  resolution_note       VARCHAR(1000) DEFAULT NULL,
  resolved_by           VARCHAR(100) DEFAULT NULL,
  resolved_at           DATETIME DEFAULT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_unmatched_legacy_value (customer_id, legacy_field, raw_value_hash),
  KEY idx_unmatched_legacy_status (status, legacy_field, created_at),
  KEY idx_unmatched_legacy_category (category_id, status),
  CONSTRAINT fk_unmatched_legacy_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_unmatched_legacy_category FOREIGN KEY (category_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_unmatched_legacy_value_category FOREIGN KEY (mapped_tag_value_id, category_id) REFERENCES tag_values (id, category_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_unmatched_legacy_status CHECK (status IN ('PENDING', 'MAPPED', 'IGNORED', 'SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='unmatched legacy customer tag values';

CREATE TABLE tag_legacy_value_mappings (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type           VARCHAR(32) NOT NULL,
  legacy_category_key   VARCHAR(64) NOT NULL DEFAULT '',
  legacy_value          VARCHAR(200) NOT NULL,
  category_id           BIGINT DEFAULT NULL,
  tag_value_id          BIGINT DEFAULT NULL,
  mapping_status        VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
  mapping_note          VARCHAR(500) DEFAULT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tag_legacy_mapping (source_type, legacy_category_key, legacy_value),
  KEY idx_tag_legacy_mapping_target (category_id, tag_value_id),
  CONSTRAINT fk_tag_legacy_mapping_category FOREIGN KEY (category_id) REFERENCES tag_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_tag_legacy_mapping_value_category FOREIGN KEY (tag_value_id, category_id) REFERENCES tag_values (id, category_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_tag_legacy_mapping_status CHECK (mapping_status IN ('MAPPED', 'UNMATCHED', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='legacy tag code and dictionary mappings';

ALTER TABLE system_tag_suggestions
  ADD COLUMN customer_id BIGINT DEFAULT NULL AFTER phone,
  ADD COLUMN tag_value_id BIGINT DEFAULT NULL AFTER tag_name,
  ADD COLUMN analysis_result_id BIGINT DEFAULT NULL AFTER rule_id,
  ADD COLUMN validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED_RULE_TEXT' AFTER analysis_result_id,
  ADD COLUMN unmatched_legacy_value_id BIGINT DEFAULT NULL AFTER validation_status,
  ADD KEY idx_system_tag_customer (customer_id, status, created_at),
  ADD KEY idx_system_tag_value (tag_value_id, status),
  ADD KEY idx_system_tag_analysis_result (analysis_result_id),
  ADD KEY idx_system_tag_unmatched (unmatched_legacy_value_id),
  ADD CONSTRAINT fk_system_tag_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT fk_system_tag_value FOREIGN KEY (tag_value_id) REFERENCES tag_values (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT fk_system_tag_analysis_result FOREIGN KEY (analysis_result_id) REFERENCES tag_analysis_results (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT fk_system_tag_unmatched FOREIGN KEY (unmatched_legacy_value_id) REFERENCES unmatched_legacy_tag_values (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT chk_system_tag_validation_status CHECK (validation_status IN ('UNVALIDATED_RULE_TEXT', 'VALIDATED', 'REJECTED', 'UNMATCHED_LEGACY'));

UPDATE tag_categories
SET purpose = '识别客户长期稳定的沟通决策倾向，用于调整沟通方式，不能根据一次情绪或无关人口信息推断',
    selection_mode = 'SINGLE',
    system_inference_enabled = 1,
    manual_edit_enabled = 1,
    auto_update_mode = 'REPLACE',
    min_confidence = 0.9000,
    min_evidence_messages = 5,
    cooldown_hours = 168,
    uncertain_policy = 'KEEP_CURRENT',
    use_for_reply = 1,
    use_for_filter = 1,
    use_for_statistics = 1,
    use_for_followup_rules = 1
WHERE category_key = 'personality_type';

UPDATE tag_categories
SET purpose = '记录客户明确表达的身体关注，不进行医学诊断，不补充客户未提及的问题',
    selection_mode = 'MULTI',
    system_inference_enabled = 1,
    manual_edit_enabled = 1,
    auto_update_mode = 'ADD_ONLY',
    min_confidence = 0.9000,
    min_evidence_messages = 1,
    cooldown_hours = 0,
    uncertain_policy = 'KEEP_CURRENT',
    use_for_reply = 1,
    use_for_filter = 1,
    use_for_statistics = 1,
    use_for_followup_rules = 1
WHERE category_key = 'body_concerns';

UPDATE tag_categories
SET purpose = '记录客户明确表达且会影响决策的顾虑，不根据语气或模糊表达猜测原因',
    selection_mode = 'MULTI',
    system_inference_enabled = 1,
    manual_edit_enabled = 1,
    auto_update_mode = 'ADD_ONLY',
    min_confidence = 0.9000,
    min_evidence_messages = 1,
    cooldown_hours = 0,
    uncertain_policy = 'KEEP_CURRENT',
    use_for_reply = 1,
    use_for_filter = 1,
    use_for_statistics = 1,
    use_for_followup_rules = 1
WHERE category_key = 'worries';

UPDATE tag_categories
SET purpose = '反映客户当前成交意向和跟进优先级，成交与流失必须以真实业务结果或人工设置为准',
    selection_mode = 'SINGLE',
    system_inference_enabled = 1,
    manual_edit_enabled = 1,
    auto_update_mode = 'REPLACE',
    min_confidence = 0.9000,
    min_evidence_messages = 2,
    cooldown_hours = 24,
    uncertain_policy = 'KEEP_CURRENT',
    use_for_reply = 1,
    use_for_filter = 1,
    use_for_statistics = 1,
    use_for_followup_rules = 1
WHERE category_key = 'intent_level';

UPDATE tag_values v JOIN tag_categories c ON c.id = v.category_id
SET v.system_selectable = 1,
    v.manual_selectable = 1,
    v.meaning = CASE v.tag_value
      WHEN 'LOYALIST' THEN '重视安全感、稳定关系和专业背书，决策前需要降低风险感'
      WHEN 'PEACEMAKER' THEN '沟通温和但决策较慢，需要耐心梳理选项和下一步'
      WHEN 'DECISIVE' THEN '目标和判断较明确，偏好直接、简洁、可执行的沟通'
      WHEN 'PENDING' THEN '有效信息不足，暂时无法判断稳定性格倾向'
      ELSE v.meaning END,
    v.applicable_when = CASE v.tag_value
      WHEN 'LOYALIST' THEN '多轮对话中反复要求案例、资质、保障、风险说明，并在获得可信依据后推进'
      WHEN 'PEACEMAKER' THEN '多轮对话中持续表现为避免冲突、反复权衡、需要温和引导才能做决定'
      WHEN 'DECISIVE' THEN '多轮对话中明确提出目标、约束和行动时间，并快速确认下一步'
      WHEN 'PENDING' THEN '有效客户消息不足，或证据无法稳定支持其他性格标签'
      ELSE v.applicable_when END,
    v.not_applicable_when = CASE v.tag_value
      WHEN 'LOYALIST' THEN '仅一次询问效果、价格或案例，不能据此判断长期性格'
      WHEN 'PEACEMAKER' THEN '客户只是礼貌表达或暂时没有时间，不代表优柔寡断'
      WHEN 'DECISIVE' THEN '客户单次着急、催促或情绪强烈，不代表长期果断型'
      WHEN 'PENDING' THEN '已有足够多轮、明确且一致的证据支持其他标签'
      ELSE v.not_applicable_when END,
    v.positive_examples = CASE v.tag_value
      WHEN 'LOYALIST' THEN '客户多次要求查看同类案例、服务保障和专业资质后才愿意预约'
      WHEN 'PEACEMAKER' THEN '客户反复比较多个选择并表示都可以，需要帮助缩小范围'
      WHEN 'DECISIVE' THEN '客户明确预算、时间和目标，并直接确认可预约的时段'
      WHEN 'PENDING' THEN '只有一两句有效对话，无法识别稳定沟通倾向'
      ELSE v.positive_examples END,
    v.negative_examples = CASE v.tag_value
      WHEN 'LOYALIST' THEN '客户只问了一次有没有效果'
      WHEN 'PEACEMAKER' THEN '客户说今天忙，晚点回复'
      WHEN 'DECISIVE' THEN '客户因等待时间过长而催促一次'
      WHEN 'PENDING' THEN '客户已在多轮对话中持续明确目标并快速决策'
      ELSE v.negative_examples END,
    v.synonyms_json = CASE v.tag_value
      WHEN 'LOYALIST' THEN '["重安全感","重保障","谨慎信任型"]'
      WHEN 'PEACEMAKER' THEN '["温和型","慢决策型","反复权衡型"]'
      WHEN 'DECISIVE' THEN '["果断型","目标明确型","行动型"]'
      WHEN 'PENDING' THEN '["待判断","信息不足","暂未识别"]'
      ELSE v.synonyms_json END
WHERE c.category_key = 'personality_type';

UPDATE tag_values v JOIN tag_categories c ON c.id = v.category_id
SET v.system_selectable = 1,
    v.manual_selectable = 1,
    v.meaning = CASE v.tag_value
      WHEN 'DIASTASIS_RECTI' THEN '客户明确关注腹直肌分离或腹部核心分离恢复'
      WHEN 'PELVIC_FLOOR' THEN '客户明确关注盆底肌功能、松弛或盆底恢复'
      WHEN 'URINE_LEAKAGE' THEN '客户明确提到漏尿、憋不住尿等相关困扰'
      WHEN 'LUMBAGO' THEN '客户明确提到腰痛、腰背酸痛或腰部不适'
      WHEN 'PUBIC_PAIN' THEN '客户明确提到耻骨疼痛或耻骨区域不适'
      WHEN 'STRETCH_MARKS' THEN '客户明确关注妊娠纹或产后纹路改善'
      WHEN 'BELLY_SAG' THEN '客户明确关注腹部松弛、肚皮松垮或核心无力外观'
      WHEN 'WEIGHT_GAIN' THEN '客户明确关注产后体重增加或减重需求'
      ELSE v.meaning END,
    v.applicable_when = CASE v.tag_value
      WHEN 'DIASTASIS_RECTI' THEN '客户原话明确出现腹直肌分离、腹部中线分开或检查结果'
      WHEN 'PELVIC_FLOOR' THEN '客户明确说盆底肌松弛、盆底恢复或相关检查问题'
      WHEN 'URINE_LEAKAGE' THEN '客户明确描述咳嗽、运动或日常出现漏尿'
      WHEN 'LUMBAGO' THEN '客户明确表达腰痛、腰背酸痛且希望改善'
      WHEN 'PUBIC_PAIN' THEN '客户明确表达耻骨疼痛、走路或翻身时耻骨不适'
      WHEN 'STRETCH_MARKS' THEN '客户明确询问妊娠纹淡化或纹路修复'
      WHEN 'BELLY_SAG' THEN '客户明确描述腹部松弛、肚皮松垮或腹部支撑不足'
      WHEN 'WEIGHT_GAIN' THEN '客户明确表达产后体重上升、减重或体重管理需求'
      ELSE v.applicable_when END,
    v.not_applicable_when = '客户未明确提及该问题时不得根据常识、图片猜测或其他症状推断；不得输出医学诊断',
    v.positive_examples = CASE v.tag_value
      WHEN 'DIASTASIS_RECTI' THEN '客户说产检后发现腹直肌分离两指，想了解恢复方案'
      WHEN 'PELVIC_FLOOR' THEN '客户说盆底肌评估提示松弛，想做针对性恢复'
      WHEN 'URINE_LEAKAGE' THEN '客户说打喷嚏和跑步时会漏尿'
      WHEN 'LUMBAGO' THEN '客户说产后经常腰背酸痛，抱孩子时更明显'
      WHEN 'PUBIC_PAIN' THEN '客户说翻身和走路时耻骨位置疼'
      WHEN 'STRETCH_MARKS' THEN '客户说腹部妊娠纹明显，想咨询改善方法'
      WHEN 'BELLY_SAG' THEN '客户说产后肚皮松、腹部支撑感差'
      WHEN 'WEIGHT_GAIN' THEN '客户说产后体重增加十公斤，希望做体重管理'
      ELSE v.positive_examples END,
    v.negative_examples = '客户只说身体不舒服、想恢复或发来无法确认的问题图片，不能直接选择具体身体关注标签',
    v.synonyms_json = CASE v.tag_value
      WHEN 'DIASTASIS_RECTI' THEN '["腹直肌分离","腹肌分离","腹部中线分离"]'
      WHEN 'PELVIC_FLOOR' THEN '["盆底问题","盆底肌松弛","盆底恢复"]'
      WHEN 'URINE_LEAKAGE' THEN '["漏尿","尿失禁","憋不住尿"]'
      WHEN 'LUMBAGO' THEN '["腰痛","腰背酸痛","腰部不适"]'
      WHEN 'PUBIC_PAIN' THEN '["耻骨疼痛","耻骨痛","耻骨不适"]'
      WHEN 'STRETCH_MARKS' THEN '["妊娠纹","孕纹","产后纹路"]'
      WHEN 'BELLY_SAG' THEN '["腹部松弛","肚皮松","腹部松垮"]'
      WHEN 'WEIGHT_GAIN' THEN '["体重增加","产后变胖","减重需求"]'
      ELSE v.synonyms_json END
WHERE c.category_key = 'body_concerns';

UPDATE tag_values v JOIN tag_categories c ON c.id = v.category_id
SET v.system_selectable = 1,
    v.manual_selectable = 1,
    v.meaning = CASE v.tag_value
      WHEN 'FEAR_NO_EFFECT' THEN '客户明确担心项目无法达到预期效果'
      WHEN 'FEAR_EXPENSIVE' THEN '客户明确担心价格高、预算不足或性价比不合适'
      WHEN 'FEAR_PAIN' THEN '客户明确担心过程疼痛或难以承受'
      WHEN 'FEAR_HARD_SELL' THEN '客户明确担心到店后被强行推销或持续施压'
      WHEN 'COMPARING' THEN '客户明确表示正在比较其他门店、方案或产品'
      WHEN 'HUSBAND_DISAGREE' THEN '客户明确表示丈夫不同意或需要丈夫同意'
      WHEN 'FAMILY_UNSUPPORT' THEN '客户明确表示家人不支持或需要家庭成员同意'
      WHEN 'NO_TIME' THEN '客户明确表示时间安排困难或没有可用时间'
      WHEN 'TOO_FAR' THEN '客户明确表示距离太远、交通不便或到店成本高'
      ELSE v.meaning END,
    v.applicable_when = CASE v.tag_value
      WHEN 'FEAR_NO_EFFECT' THEN '客户明确说担心没效果、怕白做或需要效果证明'
      WHEN 'FEAR_EXPENSIVE' THEN '客户明确说太贵、预算不够或价格超出预期'
      WHEN 'FEAR_PAIN' THEN '客户明确询问是否疼并表示害怕疼痛'
      WHEN 'FEAR_HARD_SELL' THEN '客户明确担心强推、办卡压力或到店被营销'
      WHEN 'COMPARING' THEN '客户明确说还在看其他家、比较方案或比较价格'
      WHEN 'HUSBAND_DISAGREE' THEN '客户明确说丈夫不同意、需要先问丈夫'
      WHEN 'FAMILY_UNSUPPORT' THEN '客户明确说父母或其他家人不支持'
      WHEN 'NO_TIME' THEN '客户明确说近期排不开、工作带娃导致没时间'
      WHEN 'TOO_FAR' THEN '客户明确说门店远、交通时间长或不方便到店'
      ELSE v.applicable_when END,
    v.not_applicable_when = '客户仅说再看看、以后再说或暂未回复时，不能猜测具体顾虑；同一客户可以有多个明确顾虑',
    v.positive_examples = CASE v.tag_value
      WHEN 'FEAR_NO_EFFECT' THEN '客户说最担心花了钱没有效果，需要先看真实案例'
      WHEN 'FEAR_EXPENSIVE' THEN '客户说价格超出预算，暂时承担不了'
      WHEN 'FEAR_PAIN' THEN '客户说自己很怕疼，想先确认过程感受'
      WHEN 'FEAR_HARD_SELL' THEN '客户说不想去店里被一直推销办卡'
      WHEN 'COMPARING' THEN '客户说正在比较三家机构的方案'
      WHEN 'HUSBAND_DISAGREE' THEN '客户说丈夫目前不同意做这个项目'
      WHEN 'FAMILY_UNSUPPORT' THEN '客户说家里人觉得没有必要做'
      WHEN 'NO_TIME' THEN '客户说工作和带娃排得很满，没有时间到店'
      WHEN 'TOO_FAR' THEN '客户说过去要两个小时，距离太远'
      ELSE v.positive_examples END,
    v.negative_examples = '客户只说考虑一下、最近有点忙或暂时不预约，不能直接推断为价格、效果、疼痛或家庭顾虑',
    v.synonyms_json = CASE v.tag_value
      WHEN 'FEAR_NO_EFFECT' THEN '["担心没效果","怕无效","怕白花钱"]'
      WHEN 'FEAR_EXPENSIVE' THEN '["担心价格高","太贵","预算不足"]'
      WHEN 'FEAR_PAIN' THEN '["担心疼痛","怕疼","怕过程难受"]'
      WHEN 'FEAR_HARD_SELL' THEN '["担心强推","怕办卡","怕被营销"]'
      WHEN 'COMPARING' THEN '["正在对比","货比三家","比较方案"]'
      WHEN 'HUSBAND_DISAGREE' THEN '["丈夫不同意","老公不同意","需要问丈夫"]'
      WHEN 'FAMILY_UNSUPPORT' THEN '["家人不支持","父母不支持","家庭反对"]'
      WHEN 'NO_TIME' THEN '["没有时间","排不开","近期很忙"]'
      WHEN 'TOO_FAR' THEN '["距离太远","交通不便","到店太久"]'
      ELSE v.synonyms_json END
WHERE c.category_key = 'worries';

UPDATE tag_values v JOIN tag_categories c ON c.id = v.category_id
SET v.system_selectable = CASE WHEN v.tag_value IN ('CLOSED', 'LOST') THEN 0 ELSE 1 END,
    v.manual_selectable = 1,
    v.meaning = CASE v.tag_value
      WHEN 'HIGH' THEN '客户有明确需求和近期行动信号，应优先跟进'
      WHEN 'MEDIUM' THEN '客户需求存在但行动条件或决策仍不充分，需要持续跟进'
      WHEN 'LOW' THEN '客户当前需求或行动意愿较弱，适合低频培育'
      WHEN 'PENDING' THEN '有效信息不足，暂时无法判断意向等级'
      WHEN 'CLOSED' THEN '客户已发生真实成交，必须由业务数据、外部同步或员工确认'
      WHEN 'LOST' THEN '客户已明确流失，必须符合后台规则或由员工确认'
      ELSE v.meaning END,
    v.applicable_when = CASE v.tag_value
      WHEN 'HIGH' THEN '客户明确预约、确认到店时间、主动要求下单或给出近期行动计划'
      WHEN 'MEDIUM' THEN '客户持续了解方案并有需求，但尚未确认预约、预算或时间'
      WHEN 'LOW' THEN '客户需求模糊、长期观望或明确表示近期不考虑，但未确认流失'
      WHEN 'PENDING' THEN '聊天内容不足，无法可靠区分高、中、低意向'
      WHEN 'CLOSED' THEN '存在真实订单、成交同步结果或员工确认的成交事实'
      WHEN 'LOST' THEN '客户明确拒绝后续联系、确认选择其他方案，或满足后台流失规则'
      ELSE v.applicable_when END,
    v.not_applicable_when = CASE v.tag_value
      WHEN 'HIGH' THEN '只问一次价格、点赞或礼貌回复不能直接判为高意向'
      WHEN 'MEDIUM' THEN '没有明确需求或只有一次泛问，不能强行判为中意向'
      WHEN 'LOW' THEN '单条消极消息不能立即把高意向降为低意向'
      WHEN 'PENDING' THEN '已有明确预约、成交或拒绝事实时不能使用待判断'
      WHEN 'CLOSED' THEN 'LLM 不能仅凭聊天语气或客户说考虑购买就判定成交'
      WHEN 'LOST' THEN 'LLM 不能仅凭一次未回复、犹豫或价格异议判定流失'
      ELSE v.not_applicable_when END,
    v.positive_examples = CASE v.tag_value
      WHEN 'HIGH' THEN '客户确认本周六到店并询问需要准备什么'
      WHEN 'MEDIUM' THEN '客户认可方案，但需要确认预算和家人安排'
      WHEN 'LOW' THEN '客户说近期没有计划，先保留资料以后再了解'
      WHEN 'PENDING' THEN '当前只有问候和一条泛泛咨询'
      WHEN 'CLOSED' THEN '订单系统已同步支付成功记录'
      WHEN 'LOST' THEN '客户明确要求不要再联系并确认已选择其他机构'
      ELSE v.positive_examples END,
    v.negative_examples = CASE v.tag_value
      WHEN 'HIGH' THEN '客户只问了价格是多少'
      WHEN 'MEDIUM' THEN '客户只回复收到，谢谢'
      WHEN 'LOW' THEN '高意向客户因当天忙而晚回复一次'
      WHEN 'PENDING' THEN '客户已经确认明天到店'
      WHEN 'CLOSED' THEN '客户说有需要会购买，但没有订单事实'
      WHEN 'LOST' THEN '客户一天没有回复消息'
      ELSE v.negative_examples END,
    v.synonyms_json = CASE v.tag_value
      WHEN 'HIGH' THEN '["高意向","近期行动","优先跟进"]'
      WHEN 'MEDIUM' THEN '["中意向","持续了解","条件待确认"]'
      WHEN 'LOW' THEN '["低意向","长期观望","低频培育"]'
      WHEN 'PENDING' THEN '["待判断","意向未知","信息不足"]'
      WHEN 'CLOSED' THEN '["已成交","成交客户","订单完成"]'
      WHEN 'LOST' THEN '["已流失","明确拒绝","终止跟进"]'
      ELSE v.synonyms_json END
WHERE c.category_key = 'intent_level';

UPDATE personality_tags p
JOIN tag_categories c ON c.category_key = 'personality_type'
LEFT JOIN tag_values v ON v.category_id = c.id AND v.tag_value = p.tag_value
SET p.canonical_tag_value_id = v.id,
    p.migration_status = CASE WHEN v.id IS NULL THEN 'UNMATCHED' ELSE 'MAPPED' END,
    p.enabled = 0,
    p.retired_at = NOW();

INSERT INTO tag_legacy_value_mappings (
  source_type, legacy_category_key, legacy_value, category_id, tag_value_id, mapping_status, mapping_note
)
SELECT 'PERSONALITY_TAGS', c.category_key, p.tag_value, c.id, p.canonical_tag_value_id,
       CASE WHEN p.canonical_tag_value_id IS NULL THEN 'UNMATCHED' ELSE 'MAPPED' END,
       'V68 retired the duplicate personality tag dictionary'
FROM personality_tags p
JOIN tag_categories c ON c.category_key = 'personality_type';

CREATE TEMPORARY TABLE tmp_tag_split_digits (
  n INT NOT NULL PRIMARY KEY
);

INSERT INTO tmp_tag_split_digits (n) VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

CREATE TEMPORARY TABLE tmp_tag_split_numbers AS
SELECT ones.n + tens.n * 10 + hundreds.n * 100 + 1 AS n
FROM tmp_tag_split_digits ones
CROSS JOIN tmp_tag_split_digits tens
CROSS JOIN tmp_tag_split_digits hundreds
WHERE ones.n + tens.n * 10 + hundreds.n * 100 < 500;

ALTER TABLE tmp_tag_split_numbers ADD PRIMARY KEY (n);

CREATE TEMPORARY TABLE tmp_legacy_tag_inputs (
  customer_id      BIGINT NOT NULL,
  customer_version INT NOT NULL,
  category_id      BIGINT NOT NULL,
  legacy_field     VARCHAR(50) NOT NULL,
  raw_value        VARCHAR(500) NOT NULL,
  selection_mode   VARCHAR(16) NOT NULL,
  normalized_value VARCHAR(500) NOT NULL,
  PRIMARY KEY (customer_id, legacy_field)
);

INSERT INTO tmp_legacy_tag_inputs
SELECT c.id, c.version, tc.id, 'personalityType', c.personality_type, tc.selection_mode, TRIM(c.personality_type)
FROM customers c JOIN tag_categories tc ON tc.bound_field = 'personalityType'
WHERE c.personality_type IS NOT NULL AND TRIM(c.personality_type) <> '';

INSERT INTO tmp_legacy_tag_inputs
SELECT c.id, c.version, tc.id, 'bodyConcerns', c.body_concerns, tc.selection_mode,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(c.body_concerns,
         '，', ','), '、', ','), '；', ','), ';', ','), '|', ','), CHAR(13), ','), CHAR(10), ','), CHAR(9), ',')
FROM customers c JOIN tag_categories tc ON tc.bound_field = 'bodyConcerns'
WHERE c.body_concerns IS NOT NULL AND TRIM(c.body_concerns) <> '';

INSERT INTO tmp_legacy_tag_inputs
SELECT c.id, c.version, tc.id, 'worries', c.worries, tc.selection_mode,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(c.worries,
         '，', ','), '、', ','), '；', ','), ';', ','), '|', ','), CHAR(13), ','), CHAR(10), ','), CHAR(9), ',')
FROM customers c JOIN tag_categories tc ON tc.bound_field = 'worries'
WHERE c.worries IS NOT NULL AND TRIM(c.worries) <> '';

INSERT INTO tmp_legacy_tag_inputs
SELECT c.id, c.version, tc.id, 'intentLevel', c.intent_level, tc.selection_mode, TRIM(c.intent_level)
FROM customers c JOIN tag_categories tc ON tc.bound_field = 'intentLevel'
WHERE c.intent_level IS NOT NULL AND TRIM(c.intent_level) <> '';

CREATE TEMPORARY TABLE tmp_legacy_tag_tokens (
  customer_id      BIGINT NOT NULL,
  customer_version INT NOT NULL,
  category_id      BIGINT NOT NULL,
  legacy_field     VARCHAR(50) NOT NULL,
  raw_value        VARCHAR(500) NOT NULL,
  selection_mode   VARCHAR(16) NOT NULL,
  token_order      INT NOT NULL,
  token_value      VARCHAR(500) NOT NULL,
  PRIMARY KEY (customer_id, legacy_field, token_order)
);

INSERT INTO tmp_legacy_tag_tokens
SELECT customer_id, customer_version, category_id, legacy_field, raw_value, selection_mode, 1, TRIM(normalized_value)
FROM tmp_legacy_tag_inputs
WHERE selection_mode = 'SINGLE' AND TRIM(normalized_value) <> '';

INSERT INTO tmp_legacy_tag_tokens
SELECT i.customer_id, i.customer_version, i.category_id, i.legacy_field, i.raw_value, i.selection_mode, n.n,
       TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(i.normalized_value, ',', n.n), ',', -1))
FROM tmp_legacy_tag_inputs i
JOIN tmp_tag_split_numbers n
  ON n.n <= 1 + LENGTH(i.normalized_value) - LENGTH(REPLACE(i.normalized_value, ',', ''))
WHERE i.selection_mode = 'MULTI'
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(i.normalized_value, ',', n.n), ',', -1)) <> '';

CREATE TEMPORARY TABLE tmp_legacy_tag_matches AS
SELECT t.customer_id, t.customer_version, t.category_id, t.legacy_field, t.raw_value,
       t.selection_mode, t.token_order, t.token_value,
       COUNT(v.id) AS exact_match_count, MIN(v.id) AS matched_tag_value_id
FROM tmp_legacy_tag_tokens t
LEFT JOIN tag_values v ON v.category_id = t.category_id
  AND (v.tag_value = t.token_value OR v.display_name = t.token_value)
GROUP BY t.customer_id, t.customer_version, t.category_id, t.legacy_field, t.raw_value,
         t.selection_mode, t.token_order, t.token_value;

INSERT INTO customer_tag_assignments (
  customer_id, category_id, tag_value_id, selection_mode, is_active, source_type,
  evidence_text, operator_account, customer_version, invalidated_reason, invalidated_at
)
SELECT DISTINCT m.customer_id, m.category_id, v.id, m.selection_mode,
       CASE WHEN c.is_enabled = 1 AND v.is_enabled = 1 THEN 1 ELSE 0 END,
       'LEGACY_MIGRATION',
       CONCAT('历史字段 ', m.legacy_field, ' 原文：', m.raw_value),
       'SYSTEM_MIGRATION', m.customer_version,
       CASE WHEN c.is_enabled = 1 AND v.is_enabled = 1 THEN NULL ELSE 'LEGACY_TAG_DISABLED' END,
       CASE WHEN c.is_enabled = 1 AND v.is_enabled = 1 THEN NULL ELSE NOW() END
FROM tmp_legacy_tag_matches m
JOIN tag_categories c ON c.id = m.category_id
JOIN tag_values v ON v.id = m.matched_tag_value_id
WHERE m.exact_match_count = 1;

INSERT INTO unmatched_legacy_tag_values (
  customer_id, source_type, source_record_id, legacy_field, raw_value, raw_value_hash, category_id
)
SELECT i.customer_id, 'CUSTOMER_FIELD', i.customer_id, i.legacy_field, i.raw_value,
       SHA2(i.raw_value, 256), i.category_id
FROM tmp_legacy_tag_inputs i
JOIN tmp_legacy_tag_matches m
  ON m.customer_id = i.customer_id AND m.legacy_field = i.legacy_field
GROUP BY i.customer_id, i.legacy_field, i.raw_value, i.category_id
HAVING SUM(CASE WHEN m.exact_match_count = 1 THEN 0 ELSE 1 END) > 0;

CREATE TEMPORARY TABLE tmp_normalized_legacy_fields AS
SELECT m.customer_id, m.legacy_field,
       GROUP_CONCAT(v.tag_value ORDER BY m.token_order SEPARATOR ',') AS normalized_value
FROM tmp_legacy_tag_matches m
JOIN tag_values v ON v.id = m.matched_tag_value_id
GROUP BY m.customer_id, m.legacy_field
HAVING MIN(m.exact_match_count) = 1 AND MAX(m.exact_match_count) = 1;

UPDATE customers c JOIN tmp_normalized_legacy_fields n ON n.customer_id = c.id AND n.legacy_field = 'personalityType'
SET c.personality_type = n.normalized_value;

UPDATE customers c JOIN tmp_normalized_legacy_fields n ON n.customer_id = c.id AND n.legacy_field = 'bodyConcerns'
SET c.body_concerns = n.normalized_value;

UPDATE customers c JOIN tmp_normalized_legacy_fields n ON n.customer_id = c.id AND n.legacy_field = 'worries'
SET c.worries = n.normalized_value;

UPDATE customers c JOIN tmp_normalized_legacy_fields n ON n.customer_id = c.id AND n.legacy_field = 'intentLevel'
SET c.intent_level = n.normalized_value;

UPDATE system_tag_suggestions s
JOIN customers c ON c.phone = s.phone
SET s.customer_id = c.id,
    s.validation_status = 'UNMATCHED_LEGACY';

INSERT INTO unmatched_legacy_tag_values (
  customer_id, source_type, source_record_id, legacy_field, raw_value, raw_value_hash, category_id
)
SELECT s.customer_id, 'SYSTEM_TAG_SUGGESTION', s.id, 'systemTagSuggestion', s.tag_name,
       SHA2(CONCAT('system_tag_suggestions:', s.id, ':', s.tag_name), 256), NULL
FROM system_tag_suggestions s
WHERE s.customer_id IS NOT NULL;

UPDATE system_tag_suggestions s
JOIN unmatched_legacy_tag_values u
  ON u.source_type = 'SYSTEM_TAG_SUGGESTION' AND u.source_record_id = s.id
SET s.unmatched_legacy_value_id = u.id;

INSERT INTO tag_legacy_value_mappings (
  source_type, legacy_category_key, legacy_value, category_id, tag_value_id, mapping_status, mapping_note
)
SELECT DISTINCT 'SYSTEM_TAG_SUGGESTIONS', '', s.tag_name, NULL, NULL, 'UNMATCHED',
       'Legacy follow-up rule text is retained but is not an official customer tag'
FROM system_tag_suggestions s;

DROP TEMPORARY TABLE tmp_normalized_legacy_fields;
DROP TEMPORARY TABLE tmp_legacy_tag_matches;
DROP TEMPORARY TABLE tmp_legacy_tag_tokens;
DROP TEMPORARY TABLE tmp_legacy_tag_inputs;
DROP TEMPORARY TABLE tmp_tag_split_numbers;
DROP TEMPORARY TABLE tmp_tag_split_digits;
