package com.privateflow.modules.api.chat;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.util.List;

public record SendConfirmRequest(
    String phone,
    String nickname,
    boolean isNewCustomer,
    String sourceTable,
    String leadType,
    String conversationSummary,
    List<ChatMessageDto> rawMessages,
    String sentText,
    String selectedDirection,
    CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest
) {
}
