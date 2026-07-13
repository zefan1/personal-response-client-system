package com.privateflow.modules.llm;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.util.List;

public record LlmSummaryInput(
    String phone,
    String nickname,
    String leadType,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String sentText,
    String selectedDirection,
    String caller
) {
}
