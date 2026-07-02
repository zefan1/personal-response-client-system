package com.privateflow.common.events;

public record FollowupWsMessageReadyEvent(String userId, String type, Object payload) {
}
