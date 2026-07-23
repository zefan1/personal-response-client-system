START TRANSACTION;

INSERT INTO customers (
  phone,
  nickname,
  source_channel,
  lead_type,
  personality_type,
  assigned_keeper,
  intended_store,
  intended_project,
  purchased_project,
  postpartum_months,
  parity,
  delivery_method,
  breastfeeding,
  lochia_period,
  pregnancy_weight,
  current_weight,
  body_concerns,
  diastasis_recti,
  urine_leakage,
  pubic_lumbago,
  prev_repair_exp,
  postpartum_check,
  exercise_habits,
  intent_level,
  worries,
  customer_stage,
  last_followup_at,
  followup_notes,
  next_followup_at,
  next_followup_dir,
  appointment_date,
  appointment_store,
  appointment_item,
  arrived,
  source_table,
  source_row_id,
  synced_at,
  version
) VALUES
  (
    '18810001001', '林晓雯', '抖音团购', 'TUAN_GOU', '谨慎型', 'admin',
    '南城西平店', '产后综合修复', NULL, 3.0, '二胎', '顺产', '混合喂养', '恶露已净',
    14.0, 58.5, '腹直肌分离约3指，久坐后腰背酸痛', '约3指', '无明显漏尿', '久坐后腰背酸痛',
    '自行跟练视频两周，改善不明显', '42天复查无异常', '每周散步2次', 'HIGH',
    '担心没有效果，也担心疗程价格超出预算', '跟进中',
    DATE_ADD(CURRENT_DATE - INTERVAL 6 DAY, INTERVAL 16 HOUR),
    '客户已看过同月龄恢复案例，认可先做评估，但希望确认价格和周末时间。',
    DATE_ADD(CURRENT_DATE - INTERVAL 3 DAY, INTERVAL 10 HOUR),
    '发送同月龄案例并确认周末到店时间', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-001', NOW(), 0
  ),
  (
    '18810001002', '陈雨晴', '小红书', 'XIAN_SUO', '务实型', 'admin',
    '万江店', '盆底肌评估', NULL, 6.0, '一胎', '顺产', '母乳喂养', '月经未恢复',
    12.5, 54.0, '盆底松弛，快走和打喷嚏时偶有漏尿', '轻度', '打喷嚏时偶有漏尿', '无明显疼痛',
    '未做过系统修复', '产后复查提示盆底肌力偏弱', '每天散步，暂无力量训练', 'MEDIUM',
    '工作较忙，担心没有固定时间到店', '待跟进',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 11 HOUR),
    '客户想先了解评估需要多久，午休或下班后比较方便。',
    DATE_ADD(CURRENT_DATE, INTERVAL 16 HOUR),
    '确认可预约的晚间时段并说明评估时长', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-002', NOW(), 0
  ),
  (
    '18810001003', '周雅婷', '微信转介绍', 'PENDING', '主动咨询型', 'admin',
    '东城店', '腹直肌检测', NULL, 2.0, '一胎', '剖宫产', '混合喂养', '恶露基本干净',
    16.0, 61.0, '起身时腹部无力，想确认腹直肌恢复情况', '待检测', '无', '偶有腰酸',
    '没有修复经历', '复查恢复正常', '产后以休息和散步为主', 'HIGH',
    '主要想确认检测流程和是否需要提前准备', '已预约',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 15 HOUR),
    '已确认今天到店做腹直肌检测，客户会提前十分钟到达。',
    NULL, NULL, CURRENT_DATE, '东城店', '腹直肌检测', '否',
    '本地模拟客户档案', 'FT20260721-CUST-003', NOW(), 0
  ),
  (
    '18810001004', '赵欣怡', '大众点评', 'TUAN_GOU', '温和型', 'admin',
    '万江店', '骨盆修复', NULL, 1.5, '二胎', '顺产', '母乳喂养', '恶露未完全干净',
    13.0, 56.5, '翻身和上下楼时耻骨区域疼痛', '约1指', '无', '耻骨疼痛较明显',
    '只做过居家热敷', '医生建议先评估再运动', '暂时没有运动', 'MEDIUM',
    '担心评估和修复过程疼痛', '跟进中',
    DATE_ADD(CURRENT_DATE - INTERVAL 3 DAY, INTERVAL 14 HOUR),
    '客户愿意先做低强度评估，希望提前说明哪些动作可能不舒服。',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 15 HOUR),
    '说明无痛评估流程并确认可到店日期', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-004', NOW(), 0
  ),
  (
    '18810001005', '黄思敏', '微信公众号', 'XIAN_SUO', '果断型', 'admin',
    '南城西平店', '体态与腰背评估', NULL, 8.0, '一胎', '顺产', '已断奶', '月经已恢复',
    15.0, 57.0, '抱娃后腰背酸痛，含胸和骨盆前倾明显', '约1.5指', '无', '抱娃后腰背酸痛',
    '跟过线上体态课程', '产后复查无异常', '每周瑜伽1次', 'HIGH',
    '希望尽快知道需要多少次才能看到改善', '待跟进',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 18 HOUR),
    '客户目标明确，希望先评估体态和腰背问题，再决定疗程。',
    DATE_ADD(CURRENT_DATE, INTERVAL 18 HOUR),
    '确认晚间评估名额并准备阶段性方案', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-005', NOW(), 0
  ),
  (
    '18810001006', '吴佳宁', '老客推荐', 'PENDING', '谨慎型', 'admin',
    '东城店', '盆底肌检测', NULL, 4.0, '一胎', '顺产', '母乳喂养', '月经未恢复',
    11.0, 52.0, '跑跳和咳嗽时漏尿，担心情况加重', '约1指', '跑跳和咳嗽时漏尿', '无明显疼痛',
    '做过两次凯格尔训练指导', '复查提示盆底肌力偏弱', '偶尔做凯格尔训练', 'MEDIUM',
    '比较在意隐私，也担心到店后被强行推销', '已预约',
    DATE_ADD(CURRENT_DATE - INTERVAL 2 DAY, INTERVAL 12 HOUR),
    '已说明检测为独立空间进行，客户确认今天下午到店。',
    NULL, NULL, CURRENT_DATE, '东城店', '盆底肌检测', '否',
    '本地模拟客户档案', 'FT20260721-CUST-006', NOW(), 0
  ),
  (
    '18810001007', '梁静雯', '抖音团购', 'TUAN_GOU', '对比型', 'admin',
    '南城西平店', '腹部塑形', NULL, 10.0, '二胎', '剖宫产', '已断奶', '月经已恢复',
    18.0, 68.0, '腹部松弛和体重增加，希望改善腰腹线条', '约2指', '无', '久站后腰酸',
    '咨询过两家机构，尚未体验', '产后复查正常', '每周快走2次', 'LOW',
    '正在对比不同机构的价格和项目内容', '暂缓考虑',
    DATE_ADD(CURRENT_DATE - INTERVAL 10 DAY, INTERVAL 10 HOUR),
    '客户仍在多家对比，暂未决定体验时间，希望不要频繁联系。',
    DATE_ADD(CURRENT_DATE - INTERVAL 7 DAY, INTERVAL 11 HOUR),
    '低频跟进，提供项目差异和真实改善周期', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-007', NOW(), 0
  ),
  (
    '18810001008', '何佩珊', '小红书', 'XIAN_SUO', '温和型', 'admin',
    '万江店', '剖宫产恢复评估', NULL, 1.0, '一胎', '剖宫产', '混合喂养', '恶露未净',
    14.5, 60.0, '剖宫产后起身困难，腰部容易疲劳', '待检测', '无', '起身时腰部酸痛',
    '没有修复经历', '伤口恢复正常，医生建议循序渐进', '暂未开始运动', 'PENDING',
    '担心产后时间太短，不确定现在是否适合评估', '新咨询',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 9 HOUR),
    '客户希望先由专业人员判断当前阶段能做哪些安全评估。',
    DATE_ADD(CURRENT_DATE, INTERVAL 19 HOUR),
    '说明早期评估边界并确认线上初步沟通时间', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-008', NOW(), 0
  ),
  (
    '18810001009', '郑婉婷', '微信转介绍', 'XIAN_SUO', '务实型', 'admin',
    '东城店', '腹直肌修复', NULL, 5.0, '二胎', '顺产', '混合喂养', '月经未恢复',
    13.0, 55.5, '腹直肌分离导致核心无力，抱娃容易累', '约2.5指', '无', '轻微腰酸',
    '做过一个月居家核心训练', '复查无异常', '每周居家训练2次', 'HIGH',
    '希望安排在家人可以帮忙带娃的时间', '跟进中',
    DATE_ADD(CURRENT_DATE, INTERVAL 10 HOUR),
    '客户认可先检测再制定计划，明天确认家人可以带娃的时间。',
    DATE_ADD(CURRENT_DATE + INTERVAL 1 DAY, INTERVAL 10 HOUR),
    '确认可到店时间并预留腹直肌检测名额', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-009', NOW(), 0
  ),
  (
    '18810001010', '罗子晴', '大众点评', 'TUAN_GOU', '谨慎型', 'admin',
    '南城西平店', '产后综合评估', NULL, 7.0, '一胎', '剖宫产', '已断奶', '月经已恢复',
    17.0, 63.0, '妊娠纹和腹部松弛，希望综合评估恢复空间', '约2指', '无', '无明显疼痛',
    '使用过家用塑形仪器', '复查正常', '偶尔游泳', 'MEDIUM',
    '住得较远，担心后续到店频率太高', '已沟通',
    DATE_ADD(CURRENT_DATE, INTERVAL 11 HOUR),
    '客户希望先了解评估结果，再根据距离决定到店频率。',
    DATE_ADD(CURRENT_DATE + INTERVAL 3 DAY, INTERVAL 14 HOUR),
    '提供低频到店方案并确认周末评估时间', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-010', NOW(), 0
  ),
  (
    '18810001011', '方雪莹', '到店咨询', 'PENDING', '果断型', 'admin',
    '万江店', '盆底肌修复疗程', '盆底肌修复10次疗程', 12.0, '二胎', '顺产', '已断奶', '月经已恢复',
    12.0, 51.0, '盆底肌力不足，长时间站立后有下坠感', '正常', '偶尔', '无明显疼痛',
    '在其他机构做过一次体验', '检查提示盆底肌力偏弱', '每周普拉提1次', 'CLOSED',
    '已完成方案沟通，无明显顾虑', '已成交',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 17 HOUR),
    '客户已到店完成评估并购买盆底肌修复10次疗程，等待首次正式服务。',
    NULL, NULL, CURRENT_DATE - INTERVAL 1 DAY, '万江店', '盆底肌评估', '是',
    '本地模拟客户档案', 'FT20260721-CUST-011', NOW(), 0
  ),
  (
    '18810001012', '彭梦琪', '视频号', 'XIAN_SUO', '家庭决策型', 'admin',
    '东城店', '体重管理', NULL, 9.0, '一胎', '顺产', '已断奶', '月经已恢复',
    20.0, 70.0, '产后体重增加，作息不规律，希望先调整生活方式', '约1指', '无', '偶尔腰酸',
    '尝试过节食，难以长期坚持', '复查无异常', '暂无固定运动', 'LOW',
    '家人暂不支持购买疗程，希望先自行调整一段时间', '暂缓跟进',
    DATE_ADD(CURRENT_DATE - INTERVAL 2 DAY, INTERVAL 13 HOUR),
    '客户决定先进行一个月生活方式调整，暂不安排下一次主动跟进。',
    NULL, NULL, NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260721-CUST-012', NOW(), 0
  ),
  (
    '18810001013', '周雅婷', '朋友转介绍', 'PENDING', '温和型', 'admin',
    '东城店', '腹直肌检测', NULL, 4.0, '二胎', '顺产', '混合喂养', '恶露已净',
    13.5, 56.0, '抱娃后腹部发力不足，想确认腹直肌恢复程度', '约2指', '无', '偶有腰酸',
    '曾跟练过产后核心训练视频', '产后42天复查无异常', '每周散步3次', 'MEDIUM',
    '工作时间不固定，担心到店后需要多次请假', '新咨询',
    DATE_ADD(CURRENT_DATE - INTERVAL 2 DAY, INTERVAL 10 HOUR),
    '客户由朋友介绍，想先了解腹直肌检测流程和可预约时间。',
    DATE_ADD(CURRENT_DATE + INTERVAL 2 DAY, INTERVAL 14 HOUR),
    '确认可预约的下午时段，并发送检测流程说明', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260722-CUST-013', NOW(), 0
  ),
  (
    '18810001014', '周雅宁', '小红书', 'XIAN_SUO', '主动咨询型', 'admin',
    '南城西平店', '盆底肌评估', NULL, 7.0, '一胎', '顺产', '母乳喂养', '月经未恢复',
    11.5, 53.5, '快走和咳嗽时偶有漏尿，希望改善盆底力量', '正常', '快走时偶有漏尿', '无明显疼痛',
    '未做过系统修复', '产后复查提示盆底肌力偏弱', '每天散步，暂无力量训练', 'HIGH',
    '担心疗程价格，也想先确认是否需要做完整疗程', '跟进中',
    DATE_ADD(CURRENT_DATE - INTERVAL 1 DAY, INTERVAL 16 HOUR),
    '客户主动询问盆底肌评估内容，愿意先完成评估再决定疗程。',
    DATE_ADD(CURRENT_DATE + INTERVAL 3 DAY, INTERVAL 11 HOUR),
    '发送盆底肌评估案例，并确认方便到店的时间', NULL, NULL, NULL, '否',
    '本地模拟客户档案', 'FT20260722-CUST-014', NOW(), 0
  )
ON DUPLICATE KEY UPDATE phone = VALUES(phone);

INSERT INTO customer_tag_assignments (
  customer_id,
  category_id,
  tag_value_id,
  selection_mode,
  is_active,
  source_type,
  evidence_text,
  evidence_message_count,
  operator_account,
  is_manual_locked,
  customer_version,
  created_at,
  updated_at
)
SELECT
  customer.id,
  category.id,
  tag_value_row.id,
  category.selection_mode,
  1,
  'MANUAL',
  fixture.evidence_text,
  1,
  'admin',
  0,
  customer.version,
  NOW(),
  NOW()
FROM (
  SELECT '18810001001' AS phone, 'intent_level' AS category_key, 'HIGH' AS tag_value, '模拟档案：客户认可评估并主动确认价格与到店时间。' AS evidence_text
  UNION ALL SELECT '18810001001', 'body_concerns', 'DIASTASIS_RECTI', '模拟档案：客户描述腹直肌分离约3指。'
  UNION ALL SELECT '18810001001', 'body_concerns', 'LUMBAGO', '模拟档案：客户描述久坐后腰背酸痛。'
  UNION ALL SELECT '18810001001', 'worries', 'FEAR_NO_EFFECT', '模拟档案：客户担心疗程没有效果。'
  UNION ALL SELECT '18810001001', 'worries', 'FEAR_EXPENSIVE', '模拟档案：客户担心价格超出预算。'
  UNION ALL SELECT '18810001002', 'intent_level', 'MEDIUM', '模拟档案：客户有评估意愿，但需要协调工作时间。'
  UNION ALL SELECT '18810001002', 'body_concerns', 'PELVIC_FLOOR', '模拟档案：客户产后复查提示盆底肌力偏弱。'
  UNION ALL SELECT '18810001002', 'body_concerns', 'URINE_LEAKAGE', '模拟档案：客户快走和打喷嚏时偶有漏尿。'
  UNION ALL SELECT '18810001002', 'worries', 'NO_TIME', '模拟档案：客户工作较忙，没有固定到店时间。'
  UNION ALL SELECT '18810001003', 'intent_level', 'HIGH', '模拟档案：客户已确认今天到店检测。'
  UNION ALL SELECT '18810001003', 'body_concerns', 'DIASTASIS_RECTI', '模拟档案：客户想确认腹直肌恢复情况。'
  UNION ALL SELECT '18810001004', 'intent_level', 'MEDIUM', '模拟档案：客户愿意评估，但担心疼痛。'
  UNION ALL SELECT '18810001004', 'body_concerns', 'PUBIC_PAIN', '模拟档案：客户翻身和上下楼时耻骨疼痛。'
  UNION ALL SELECT '18810001004', 'worries', 'FEAR_PAIN', '模拟档案：客户担心评估和修复过程疼痛。'
  UNION ALL SELECT '18810001005', 'intent_level', 'HIGH', '模拟档案：客户目标明确并希望尽快评估。'
  UNION ALL SELECT '18810001005', 'body_concerns', 'LUMBAGO', '模拟档案：客户抱娃后腰背酸痛。'
  UNION ALL SELECT '18810001006', 'intent_level', 'MEDIUM', '模拟档案：客户已预约检测，但仍关注隐私和服务方式。'
  UNION ALL SELECT '18810001006', 'body_concerns', 'PELVIC_FLOOR', '模拟档案：客户复查提示盆底肌力偏弱。'
  UNION ALL SELECT '18810001006', 'body_concerns', 'URINE_LEAKAGE', '模拟档案：客户跑跳和咳嗽时漏尿。'
  UNION ALL SELECT '18810001006', 'worries', 'FEAR_HARD_SELL', '模拟档案：客户担心到店后被强行推销。'
  UNION ALL SELECT '18810001007', 'intent_level', 'LOW', '模拟档案：客户正在多家对比，暂未决定体验。'
  UNION ALL SELECT '18810001007', 'body_concerns', 'BELLY_SAG', '模拟档案：客户关注腹部松弛。'
  UNION ALL SELECT '18810001007', 'body_concerns', 'WEIGHT_GAIN', '模拟档案：客户关注产后体重增加。'
  UNION ALL SELECT '18810001007', 'worries', 'COMPARING', '模拟档案：客户正在对比不同机构。'
  UNION ALL SELECT '18810001008', 'intent_level', 'PENDING', '模拟档案：客户需要先确认当前阶段是否适合评估。'
  UNION ALL SELECT '18810001008', 'body_concerns', 'LUMBAGO', '模拟档案：客户剖宫产后起身时腰部酸痛。'
  UNION ALL SELECT '18810001008', 'worries', 'FEAR_PAIN', '模拟档案：客户担心产后早期评估造成不适。'
  UNION ALL SELECT '18810001009', 'intent_level', 'HIGH', '模拟档案：客户认可先检测再制定修复计划。'
  UNION ALL SELECT '18810001009', 'body_concerns', 'DIASTASIS_RECTI', '模拟档案：客户腹直肌分离约2.5指。'
  UNION ALL SELECT '18810001010', 'intent_level', 'MEDIUM', '模拟档案：客户认可评估，但距离影响到店频率。'
  UNION ALL SELECT '18810001010', 'body_concerns', 'STRETCH_MARKS', '模拟档案：客户关注妊娠纹。'
  UNION ALL SELECT '18810001010', 'body_concerns', 'BELLY_SAG', '模拟档案：客户关注腹部松弛。'
  UNION ALL SELECT '18810001010', 'worries', 'TOO_FAR', '模拟档案：客户住得较远，担心到店频率。'
  UNION ALL SELECT '18810001011', 'intent_level', 'CLOSED', '模拟档案：客户已完成评估并购买疗程。'
  UNION ALL SELECT '18810001011', 'body_concerns', 'PELVIC_FLOOR', '模拟档案：客户盆底肌力不足并已购买修复疗程。'
  UNION ALL SELECT '18810001012', 'intent_level', 'LOW', '模拟档案：客户决定先自行调整，暂不购买疗程。'
  UNION ALL SELECT '18810001012', 'body_concerns', 'WEIGHT_GAIN', '模拟档案：客户关注产后体重增加。'
  UNION ALL SELECT '18810001012', 'worries', 'FAMILY_UNSUPPORT', '模拟档案：客户家人暂不支持购买疗程。'
  UNION ALL SELECT '18810001013', 'intent_level', 'MEDIUM', '模拟档案：客户有检测意愿，但到店时间需要协调。'
  UNION ALL SELECT '18810001013', 'body_concerns', 'DIASTASIS_RECTI', '模拟档案：客户想确认腹直肌恢复程度。'
  UNION ALL SELECT '18810001013', 'worries', 'NO_TIME', '模拟档案：客户工作时间不固定，担心请假。'
  UNION ALL SELECT '18810001014', 'intent_level', 'HIGH', '模拟档案：客户主动询问盆底肌评估。'
  UNION ALL SELECT '18810001014', 'body_concerns', 'PELVIC_FLOOR', '模拟档案：客户产后复查提示盆底肌力偏弱。'
  UNION ALL SELECT '18810001014', 'body_concerns', 'URINE_LEAKAGE', '模拟档案：客户快走和咳嗽时偶有漏尿。'
  UNION ALL SELECT '18810001014', 'worries', 'FEAR_EXPENSIVE', '模拟档案：客户担心疗程价格。'
) AS fixture
JOIN customers AS customer
  ON customer.phone = fixture.phone
JOIN tag_categories AS category
  ON category.category_key = fixture.category_key
  AND category.is_enabled = 1
JOIN tag_values AS tag_value_row
  ON tag_value_row.category_id = category.id
  AND tag_value_row.tag_value = fixture.tag_value
  AND tag_value_row.is_enabled = 1
LEFT JOIN customer_tag_assignments AS exact_assignment
  ON exact_assignment.customer_id = customer.id
  AND exact_assignment.category_id = category.id
  AND exact_assignment.tag_value_id = tag_value_row.id
  AND exact_assignment.is_active = 1
LEFT JOIN customer_tag_assignments AS active_single_assignment
  ON active_single_assignment.customer_id = customer.id
  AND active_single_assignment.category_id = category.id
  AND active_single_assignment.is_active = 1
  AND category.selection_mode = 'SINGLE'
WHERE exact_assignment.id IS NULL
  AND (category.selection_mode <> 'SINGLE' OR active_single_assignment.id IS NULL);

COMMIT;
