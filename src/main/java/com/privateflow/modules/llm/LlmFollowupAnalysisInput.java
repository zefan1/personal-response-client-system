package com.privateflow.modules.llm;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import java.util.List;

public record LlmFollowupAnalysisInput(
    Customer customer,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String sentText,
    String selectedDirection,
    String caller
) {
}
