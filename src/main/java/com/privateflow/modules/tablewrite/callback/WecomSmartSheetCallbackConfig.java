package com.privateflow.modules.tablewrite.callback;

import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetCallbackConfig {

  private final String token;
  private final String encodingAesKey;
  private final String corpId;

  @Autowired
  public WecomSmartSheetCallbackConfig(
      @Value("${WECOM_CALLBACK_TOKEN:}") String token,
      @Value("${WECOM_CALLBACK_ENCODING_AES_KEY:}") String encodingAesKey,
      WecomSmartSheetConfig smartSheetConfig) {
    this(token, encodingAesKey, smartSheetConfig == null ? "" : smartSheetConfig.corpId());
  }

  public WecomSmartSheetCallbackConfig(String token, String encodingAesKey, String corpId) {
    this.token = text(token);
    this.encodingAesKey = text(encodingAesKey);
    this.corpId = text(corpId);
  }

  public boolean configured() {
    return !token.isEmpty() && encodingAesKey.length() == 43 && !corpId.isEmpty();
  }

  public String token() {
    return token;
  }

  public String encodingAesKey() {
    return encodingAesKey;
  }

  public String corpId() {
    return corpId;
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
