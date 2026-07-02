package com.privateflow.modules.image;

import java.util.List;

public record RecognitionResult(
    String nickname,
    String phone,
    List<Message> messages,
    String timestamp
) {
}
