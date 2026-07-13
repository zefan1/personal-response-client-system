package com.privateflow.modules.llm;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.util.List;

public record LlmAbnormalDetectionInput(
    String phone,
    String nickname,
    String leadType,
    String conversationSummary,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String sentText,
    String caller
) {
}
