UPDATE tag_categories
SET category_name = '性格类型'
WHERE category_key = 'personality_type'
  AND category_name IN ('Personality Type', 'personality_type');

UPDATE tag_categories
SET category_name = '身体关注'
WHERE category_key = 'body_concerns'
  AND category_name IN ('Body Concerns', 'body_concerns');

UPDATE tag_categories
SET category_name = '客户顾虑'
WHERE category_key = 'worries'
  AND category_name IN ('Worries', 'worries');

UPDATE tag_categories
SET category_name = '意向等级'
WHERE category_key = 'intent_level'
  AND category_name IN ('Intent Level', 'intent_level');

UPDATE tag_values v
JOIN tag_categories c ON c.id = v.category_id
SET v.display_name = CASE v.tag_value
  WHEN 'LOYALIST' THEN '忠诚型'
  WHEN 'PEACEMAKER' THEN '温和型'
  WHEN 'DECISIVE' THEN '果断型'
  WHEN 'PENDING' THEN '待判断'
  ELSE v.display_name
END
WHERE c.category_key = 'personality_type'
  AND v.display_name IN ('Loyalist', 'Peacemaker', 'Decisive', 'Pending', 'LOYALIST', 'PEACEMAKER', 'DECISIVE', 'PENDING');

UPDATE tag_values v
JOIN tag_categories c ON c.id = v.category_id
SET v.display_name = CASE v.tag_value
  WHEN 'DIASTASIS_RECTI' THEN '腹直肌分离'
  WHEN 'PELVIC_FLOOR' THEN '盆底问题'
  WHEN 'URINE_LEAKAGE' THEN '漏尿'
  WHEN 'LUMBAGO' THEN '腰痛'
  WHEN 'PUBIC_PAIN' THEN '耻骨疼痛'
  WHEN 'STRETCH_MARKS' THEN '妊娠纹'
  WHEN 'BELLY_SAG' THEN '腹部松弛'
  WHEN 'WEIGHT_GAIN' THEN '体重增加'
  ELSE v.display_name
END
WHERE c.category_key = 'body_concerns'
  AND v.display_name IN ('Diastasis Recti', 'Pelvic Floor', 'Urine Leakage', 'Lumbago', 'Pubic Pain', 'Stretch Marks', 'Belly Sag', 'Weight Gain', 'DIASTASIS_RECTI', 'PELVIC_FLOOR', 'URINE_LEAKAGE', 'LUMBAGO', 'PUBIC_PAIN', 'STRETCH_MARKS', 'BELLY_SAG', 'WEIGHT_GAIN');

UPDATE tag_values v
JOIN tag_categories c ON c.id = v.category_id
SET v.display_name = CASE v.tag_value
  WHEN 'FEAR_NO_EFFECT' THEN '担心没有效果'
  WHEN 'FEAR_EXPENSIVE' THEN '担心价格高'
  WHEN 'FEAR_PAIN' THEN '担心疼痛'
  WHEN 'FEAR_HARD_SELL' THEN '担心强行推销'
  WHEN 'COMPARING' THEN '正在对比'
  WHEN 'HUSBAND_DISAGREE' THEN '丈夫不同意'
  WHEN 'FAMILY_UNSUPPORT' THEN '家人不支持'
  WHEN 'NO_TIME' THEN '没有时间'
  WHEN 'TOO_FAR' THEN '距离太远'
  ELSE v.display_name
END
WHERE c.category_key = 'worries'
  AND v.display_name IN ('Fear No Effect', 'Fear Expensive', 'Fear Pain', 'Fear Hard Sell', 'Comparing', 'Husband Disagree', 'Family Unsupport', 'No Time', 'Too Far', 'FEAR_NO_EFFECT', 'FEAR_EXPENSIVE', 'FEAR_PAIN', 'FEAR_HARD_SELL', 'COMPARING', 'HUSBAND_DISAGREE', 'FAMILY_UNSUPPORT', 'NO_TIME', 'TOO_FAR');

UPDATE tag_values v
JOIN tag_categories c ON c.id = v.category_id
SET v.display_name = CASE v.tag_value
  WHEN 'HIGH' THEN '高意向'
  WHEN 'MEDIUM' THEN '中意向'
  WHEN 'LOW' THEN '低意向'
  WHEN 'PENDING' THEN '待判断'
  WHEN 'CLOSED' THEN '已成交'
  WHEN 'LOST' THEN '已流失'
  ELSE v.display_name
END
WHERE c.category_key = 'intent_level'
  AND v.display_name IN ('High', 'Medium', 'Low', 'Pending', 'Closed', 'Lost', 'HIGH', 'MEDIUM', 'LOW', 'PENDING', 'CLOSED', 'LOST');
