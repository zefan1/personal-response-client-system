INSERT INTO system_configs (config_key, config_value, description)
VALUES
  ('image.api_base_url', '', '识图LLM API基础地址，待运营B配置'),
  ('image.api_key', '', '识图LLM API Key，加密存储后由运营B维护'),
  ('image.timeout_ms', '5000', '识图HTTP调用超时毫秒'),
  ('image.max_size_bytes', '5242880', '图片最大体积字节，默认5MB'),
  ('image.max_dimension_px', '1920', '图片长边最大像素'),
  ('image.compress_quality', '85', 'JPEG压缩质量，范围60-95'),
  ('image.recognition_prompt', '你是一个聊天截图分析助手。请分析这张微信/企业微信聊天截图，提取nickname、phone、messages和timestamp，并严格返回JSON。messages中客户/对方/顾客为client，同事/自己/管家/我为keeper。只提取文字内容，忽略表情包和图片。', '识图System Prompt'),
  ('image.consecutive_failures_alert', '3', '连续失败多少次后标记DOWN')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
