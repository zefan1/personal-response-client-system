package com.privateflow.modules.api.help;

import java.util.List;
import java.util.Map;

public record HelpRequestPayload(
    String phone,
    String clientMessage,
    List<HelpSuggestionPayload> aiSuggestions,
    String keeperNote,
    String question,
    Map<String, Object> context) {

  public String effectiveClientMessage() {
    return clientMessage == null || clientMessage.isBlank() ? question : clientMessage;
  }
}
