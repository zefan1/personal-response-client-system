package com.privateflow.modules.image;

import java.util.List;

public record RecognitionResult(
    String nickname,
    String phone,
    List<Message> messages,
    String timestamp,
    String customerIdentifier,
    String platform,
    double confidence
) {

  public RecognitionResult(String nickname, String phone, List<Message> messages, String timestamp) {
    this(nickname, phone, messages, timestamp, null, "UNKNOWN", 0.0);
  }
}
