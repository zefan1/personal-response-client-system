package com.privateflow.modules.communication;

import java.util.List;

public record CommunicationMessagePage(
    List<ArchivedCommunicationMessage> messages,
    Long nextBeforeId) {
}
