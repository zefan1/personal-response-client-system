package com.privateflow.modules.api.help;

import java.util.List;

public record HelpResolvePayload(Long requestId, Long helpId, String replyText, List<HelpReplyPayload> helperReplies) {

  public Long effectiveHelpId() {
    return helpId == null ? requestId : helpId;
  }
}
}
