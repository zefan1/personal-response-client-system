package com.privateflow.modules.image.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "true")
public class MockImageRecognitionClient implements ImageRecognitionClient {

  @Override
  public String recognize(byte[] jpegImage) {
    return """
        {
          "nickname": "李女士",
          "phone": "138-0000-0001",
          "messages": [
            {"role": "客户", "text": "你好，我想了解一下腹直肌修复"},
            {"role": "管家", "text": "您好，可以先看看您的产后情况"}
          ],
          "timestamp": "2026-06-25 14:30"
        }
        """;
  }
}
