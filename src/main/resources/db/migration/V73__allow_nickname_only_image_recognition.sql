UPDATE system_configs
SET config_value = CONCAT(
  config_value,
  '\n\nNickname-only success rule: If the current chat title or contact nickname is clearly visible and at least one message is readable and attributable to the client or keeper, return status=OK even when phone and customerIdentifier are null. Put the visible title in nickname. Do not treat missing phone or platform account alone as failure. Return UNABLE_TO_DETERMINE only when the current chat or readable messages cannot be confirmed.\n',
  '只要当前聊天标题或联系人昵称清晰可见，且至少一条消息可读并能归属客户或管家，即使手机号和 customerIdentifier 都为空，也必须返回 status=OK；将可见标题写入 nickname。不要仅因缺少手机号或平台账号而失败。'
)
WHERE config_key = 'image.recognition_prompt'
  AND config_value NOT LIKE '%Nickname-only success rule%';
