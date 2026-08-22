package com.privateflow.common.events;

import java.util.List;

/** Facts found in the recognized chat may update the profile before the employee confirms sending. */
public record RecognizedConversationEvent(
    Long customerId,
    String phone,
    List<CustomerMessageSentEvent.ChatMessage> rawMessages,
    String operator,
    Long failureId) {

  public RecognizedConversationEvent(
      Long customerId,
      String phone,
      List<CustomerMessageSentEvent.ChatMessage> rawMessages,
      String operator) {
    this(customerId, phone, rawMessages, operator, null);
  }
}
