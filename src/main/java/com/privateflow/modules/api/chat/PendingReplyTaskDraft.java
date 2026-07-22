package com.privateflow.modules.api.chat;

import com.privateflow.modules.match.CustomerSummary;
import java.util.List;
import java.util.Map;

public record PendingReplyTaskDraft(
    String replySessionId,
    String username,
    String recognizedNickname,
    String recognizedPhone,
    String platformIdentifier,
    String leadType,
    String sourceTable,
    String clientMessage,
    List<Map<String, String>> chatContext,
    List<CustomerSummary> candidates
) {
}
