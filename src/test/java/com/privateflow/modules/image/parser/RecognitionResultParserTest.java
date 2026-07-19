package com.privateflow.modules.image.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageRecognitionException;
import org.junit.jupiter.api.Test;

class RecognitionResultParserTest {

  private final RecognitionResultParser parser = new RecognitionResultParser(new ObjectMapper());

  @Test
  void keepsTheLegacyRecognitionSchemaCompatible() {
    var result = parser.parse("""
        {"nickname":"Alice","phone":"138-0000-0001","messages":[{"role":"client","text":"hello"}],"timestamp":"12:00"}
        """);

    assertThat(result.nickname()).isEqualTo("Alice");
    assertThat(result.phone()).isEqualTo("13800000001");
    assertThat(result.messages()).hasSize(1);
  }

  @Test
  void parsesDouyinIdentityWithoutInventingAPhoneNumber() {
    var result = parser.parse("""
        {
          "status":"OK",
          "platform":"DOUYIN_WEB",
          "nickname":null,
          "phone":null,
          "customerIdentifier":"douyin_user_88",
          "messages":[{"role":"client","text":"interested"}],
          "timestamp":null,
          "confidence":0.92,
          "failureReason":null
        }
        """);

    assertThat(result.nickname()).isEqualTo("douyin_user_88");
    assertThat(result.phone()).isNull();
    assertThat(result.customerIdentifier()).isEqualTo("douyin_user_88");
    assertThat(result.platform()).isEqualTo("DOUYIN_WEB");
    assertThat(result.confidence()).isEqualTo(0.92);
  }

  @Test
  void surfacesTheModelFailureReasonWithoutGuessing() {
    assertThatThrownBy(() -> parser.parse("""
        {
          "status":"UNABLE_TO_DETERMINE",
          "platform":"UNKNOWN",
          "messages":[],
          "confidence":0,
          "failureReason":"main conversation is not visible"
        }
        """))
        .isInstanceOf(ImageRecognitionException.class)
        .hasMessage("main conversation is not visible");
  }
}
