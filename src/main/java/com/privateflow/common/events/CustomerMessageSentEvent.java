package com.privateflow.common.events;

import java.util.List;

public record CustomerMessageSentEvent(
    String phone,
    String nickname,
    boolean isNewCustomer,
    String sourceTable,
    String leadType,
    String conversationSummary,
    List<ChatMessage> rawMessages,
    String sentText,
    String selectedDirection,
    FollowupSuggestPayload followupSuggest,
    String operator
) {

  public record ChatMessage(String role, String text, String timestamp) {
  }

  public record FollowupSuggestPayload(String nextFollowupAt, String nextFollowupDir) {
  }
}
