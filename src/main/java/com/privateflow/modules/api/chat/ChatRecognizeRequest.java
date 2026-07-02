package com.privateflow.modules.api.chat;

import java.util.List;

public record ChatRecognizeRequest(
    String imageBase64,
    String textMessage,
    String customerIdentifier,
    String leadType,
    String sourceTable,
    List<ChatMessageDto> rawMessages
) {
}
