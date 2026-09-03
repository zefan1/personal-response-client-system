-- Keep the pending intent example consistent with the category evidence threshold.
UPDATE tag_values v
JOIN tag_categories c ON c.id = v.category_id
SET v.positive_examples = '多条聊天记录中仍无明确的成交意向，暂时无法判断'
WHERE c.category_key = 'intent_level'
  AND v.tag_value = 'PENDING';
