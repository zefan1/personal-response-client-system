package com.privateflow.common.events;

import java.util.List;
import java.util.Map;

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
    Map<String, Object> followupFields,
    String operator,
    Long customerId
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
      boolean completeCurrentFollowup,
      Map<String, Object> followupFields,
      String operator) {
    this(phone, nickname, isNewCustomer, sourceTable, leadType, conversationSummary, rawMessages,
        sentText, selectedDirection, followupSuggest, completeCurrentFollowup, followupFields, operator, null);
  }

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
      boolean completeCurrentFollowup,
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
        completeCurrentFollowup,
        Map.of(),
        operator,
        null);
  }

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
        Map.of(),
        operator,
        null);
  }

  public record ChatMessage(String role, String text, String timestamp) {
  }

  public record FollowupSuggestPayload(String nextFollowupAt, String nextFollowupDir) {
  }
}
