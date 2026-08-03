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
    CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest,
    boolean completeCurrentFollowup,
    String confirmationId,
    Long customerId
) {

  public SendConfirmRequest(
      String phone,
      String nickname,
      boolean isNewCustomer,
      String sourceTable,
      String leadType,
      String conversationSummary,
      List<ChatMessageDto> rawMessages,
      String sentText,
      String selectedDirection,
      CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest,
      boolean completeCurrentFollowup) {
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
        null,
        null);
  }

  public SendConfirmRequest(
      String phone,
      String nickname,
      boolean isNewCustomer,
      String sourceTable,
      String leadType,
      String conversationSummary,
      List<ChatMessageDto> rawMessages,
      String sentText,
      String selectedDirection,
      CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest,
      boolean completeCurrentFollowup,
      String confirmationId) {
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
        confirmationId,
        null);
  }

  public SendConfirmRequest(
      String phone,
      String nickname,
      boolean isNewCustomer,
      String sourceTable,
      String leadType,
      String conversationSummary,
      List<ChatMessageDto> rawMessages,
      String sentText,
      String selectedDirection,
      CustomerMessageSentEvent.FollowupSuggestPayload followupSuggest) {
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
        null,
        null);
  }
}
