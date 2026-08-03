package com.privateflow.modules.llm;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.util.List;

public record FollowupAnalysisRetryPayload(
    String phone,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String sentText,
    String selectedDirection,
    String operator
) {
}
