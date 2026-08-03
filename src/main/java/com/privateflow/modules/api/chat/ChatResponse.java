package com.privateflow.modules.api.chat;

import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.skill.SkillResponse;

public record ChatResponse(
    String phone,
    String nickname,
    boolean needsCustomerIdentifier,
    MatchResult match,
    SkillResponse skill,
    String warning,
    ChatReplySource replySource,
    Long customerId,
    RecognitionResult recognition,
    boolean awaitingCustomerSelection
) {
  public ChatResponse(
      String phone,
      String nickname,
      boolean needsCustomerIdentifier,
      MatchResult match,
      SkillResponse skill,
      String warning,
      ChatReplySource replySource) {
    this(phone, nickname, needsCustomerIdentifier, match, skill, warning, replySource, null, null, false);
  }

  public ChatResponse(
      String phone,
      String nickname,
      boolean needsCustomerIdentifier,
      MatchResult match,
      SkillResponse skill,
      String warning,
      ChatReplySource replySource,
      Long customerId) {
    this(phone, nickname, needsCustomerIdentifier, match, skill, warning, replySource, customerId, null, false);
  }
}
