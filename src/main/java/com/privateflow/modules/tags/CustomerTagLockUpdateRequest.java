package com.privateflow.modules.tags;

public record CustomerTagLockUpdateRequest(
    Integer version,
    boolean locked,
    String reason
) {
}
