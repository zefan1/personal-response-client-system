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
    boolean completeCurrentFollowup,
    String operator
) {

  public CustomerMessageSentEvent(
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
      String operator) {
    this(
        phone,
        nickname,
        isNewCustomer,
        sourceTable,
        leadType,
        conversationSummary,
        rawMessages,
        sentText,
        selectedDirection,
        followupSuggest,
        false,
        operator);
  }

  public record ChatMessage(String role, String text, String timestamp) {
  }

  public record FollowupSuggestPayload(String nextFollowupAt, String nextFollowupDir) {
  }
}
